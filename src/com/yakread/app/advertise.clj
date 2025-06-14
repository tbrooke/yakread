(ns com.yakread.app.advertise
  (:require
   [clojure.data.generators :as gen]
   [clojure.string :as str]
   [com.biffweb :as biff]
   [com.yakread.lib.form :as lib.form]
   [com.yakread.lib.middleware :as lib.mid]
   [com.yakread.lib.pathom :refer [?]]
   [com.yakread.lib.route :refer [defget defpost defpost-pathom href]]
   [com.yakread.lib.ui :as ui]
   [rum.core :as rum]))

(declare page-route)
(declare image-upload-input)

(defn fmt-dollars [amount]
  (format "%.2f" (/ amount 100.0)))

(defn obfuscate-url!
  "Some ad blockers block requests to URLs that include 'advertise' in them."
  [route-var]
  (alter-var-root route-var update 0 str/replace #"ad" "womp"))

(defpost-pathom save-ad
  [{:session/user
    [:xt/id
     {(? :user/ad)
      [(? :ad/url)
       (? :ad/title)
       (? :ad/description)
       (? :ad/image-url)
       :ad/approve-state]}]}]
  (fn [{:keys [biff.form/params]} {:keys [session/user]}]
    (let [{old-ad :user/ad} user
          keys* [:ad/bid
                 :ad/budget
                 :ad/url
                 :ad/title
                 :ad/description
                 :ad/image-url
                 :ad/paused]
          new-ad (-> (merge (zipmap keys* (repeat :db/dissoc)) params)
                     (select-keys keys*)
                     (merge (when-some [budget (:ad/budget params)]
                              {:ad/budget (max budget (* 10 (:ad/bid params 0)))})))
          state (if (every? #(= (% old-ad) (% new-ad))
                            [:ad/url :ad/title :ad/description :ad/image-url])
                  (:ad/approve-state old-ad :pending)
                  :pending)
          tx [(merge {:db/doc-type :ad
                      :db.op/upsert {:ad/user (:xt/id user)}
                      :ad/approve-state state
                      :ad/balance [:db/default 0]
                      :ad/recent-cost [:db/default 0]
                      :ad/updated-at :db/now}
                     new-ad)]]
      {:status 303
       :headers {"Location" (href page-route)}
       :biff.pipe/next [:biff.pipe/tx]
       :biff.pipe.tx/input tx})))
(obfuscate-url! #'save-ad)


(defpost upload-image
  :start
  (fn [{:keys [biff.s3/edge params multipart-params]}]
    (let [image-id (gen/uuid)
          file-info (get multipart-params "image-file")
          url (str edge "/" image-id)]
      {:biff.pipe/next [:biff.pipe/s3 :biff.pipe/http]
       :biff.pipe.s3/input {:method "PUT"
                            :key (str image-id)
                            :body (:tempfile file-info)
                            :headers {"x-amz-acl" "public-read"
                                      "content-type" (:content-type file-info)}}
       :biff.pipe.http/input {:url     (ui/weserv {:url url
                                                   :w 150
                                                   :h 150
                                                   :fit "cover"
                                                   :a "attention"})
                              :method  :get
                              :headers {"User-Agent" "https://yakread.com/"}}
       :status 200
       :headers {"content-type" "text/html"}
       :body (rum/render-static-markup
              [:<>
               (image-upload-input {:value url})
               #_(preview (assoc ctx ::oob true))])})))
(obfuscate-url! #'upload-image)

(defpost change-image
  :start
  (constantly (image-upload-input {})))


;; (defn fetch-metadata [url]
;;   (let [result (biff/catchall (util/http-get url))
;;         parsed (or (util/panto-parse (:body result)) {})]
;;     {:title (some-> (some parsed [:title :og/title :dc/title])
;;                     (str/replace #" \| Substack$" "")
;;                     (util/truncate 75))
;;      :description (some-> (some parsed [:description :og/description])
;;                           (util/truncate 250))
;;      :image (util/pred-> (some parsed [:image :og/image :twitter/image])
;;                          :url
;;                          :url)}))

(def preview-hx-opts
  {}
  #_{:hx-get "/fiddlesticks/preview"
   :hx-target "#preview"
   :hx-swap "outerHTML"
   :hx-include "closest form"
   :hx-params "not __anti-forgery-token"})

(defn image-upload-input [{:keys [value error]}]
   [:div#image-upload
    [:input {:type "hidden" :name (pr-str :ad/image-url) :value value}]
    (if value
      [:<>
       (ui/input-label {} "Image")
       [:.flex.items-center
        [:img.rounded
         {:src (ui/weserv {:url value
                           :w 150
                           :h 150
                           :fit "cover"
                           :a "attention"})
          :style {:object-fit "cover"
                  :object-position "center"
                  :width "5.5rem"
                  :height "5.5rem"}}]
        [:.w-3]
        [:button.text-blue-600.hover:underline
         {:hx-trigger "click"
          :hx-post (href change-image)
          :hx-target "#image-upload"
          :hx-swap "outerHTML"}
         "Change"]]]
      (ui/form-input
       {:ui/label "Image"
        :ui/description "Should be at least 150x150"
        :type "file"
        :indicator-id "image-upload-indicator"
        :accept "image/apng, image/avif, image/gif, image/jpeg, image/png, image/svg+xml, image/webp"
        :name "image-file"
        :hx-post (href upload-image)
        :hx-target "#image-upload"
        :hx-swap "outerHTML"
        :hx-include "closest form"
        :hx-indicator "#image-upload-indicator"
        :hx-encoding "multipart/form-data"}))
    (when error
      (ui/input-error error))])

(defn form [{:keys [ad params]}]
  (biff/form
   {:action (href save-ad)
    :hx-boost "true"
    :class '[flex flex-col gap-6]}
   [:.grid.sm:grid-cols-2.gap-6
    (ui/form-input {:ui/label "Maximum cost-per-click"
                    :ui/icon "dollar-sign-regular"
                    :ui/description "The higher you bid, the more frequently your ad will be shown."
                    :name :ad/bid
                    :placeholder "1.50"
                    :value (or (:bid params)
                               (some-> (:ad/bid ad) fmt-dollars))})
    (ui/form-input {:ui/label "Maximum weekly budget"
                    :ui/icon "dollar-sign-regular"
                    :ui/description "Must be at least 10x your maximum cost-per-click."
                    :name :ad/budget
                    :placeholder "75.00"
                    :value (or (:budget params)
                               (some-> (:ad/budget ad) fmt-dollars))})]
   (ui/form-input {:ui/label "Ad URL"
                   :name :ad/url
                   :placeholder "https://example.com?utm_source=yakread"
                   :hx-get "/fiddlesticks/autocomplete"
                   :hx-target "closest form"
                   :hx-swap "outerHTML"
                   :hx-include "closest form"
                   :hx-params "not __anti-forgery-token"
                   :hx-indicator "#autocomplete-indicator"
                   :value (or (:url params) (:ad/url ad))})
   (ui/form-input (merge {:ui/label "Title"
                          :ui/description "Maximum 75 characters."
                          :name :ad/title
                          :value (or (:title params) (:ad/title ad))}
                         preview-hx-opts))
   (ui/form-input (merge {:ui/input-type :textarea
                          :ui/label "Description"
                          :ui/description "Maximum 250 characters."
                          :rows 3
                          :name :ad/description
                          :value (or (:description params) (:ad/description ad))}
                         preview-hx-opts))
   (image-upload-input {:value (or (:image params) (:ad/image-url ad))
                        :error (when (= (:error params) "image")
                                 "We weren't able to upload that file.")})
   (ui/checkbox {:ui/label "Pause ad"
                 :ui/label-position :above
                 :id "pause"
                 :name :ad/paused
                 :checked (when (or (:paused params) (:ad/paused ad))
                            "checked")})
   [:div (ui/button {:type "submit"} "Save")]))

;; TODO add param to app-shell for including plausible
(defget page-route "/advertise"
  [:app.shell/app-shell
   {(? :session/user)
    [:xt/id
     {(? :user/ad)
      [(? :ad/bid)
       (? :ad/budget)
       (? :ad/url)
       (? :ad/title)
       (? :ad/description)
       (? :ad/image-url)
       (? :ad/paused)
       :ad/approve-state
       (? :ad/balance)
       (? :ad/payment-failed)
       (? :ad/card-details)]}]}]
  (fn [{:keys [params]} {:keys [app.shell/app-shell session/user]}]
    (app-shell
     {:title "Advertise"}
     (ui/page-header {:title "Advertise"
                      :description "Grow your newsletter with Yakread's self-serve, pay-per-click ads."})
     [:fieldset.disabled:opacity-60
      {:disabled (when-not user "disabled")}

      (ui/page-well
       (ui/section
        {}
        [:div "Your ad will be displayed on the For You page and in the digest emails. "
         "You'll be charged for each unique click your ad receives."]
        [:.my-4 "<payment method>" #_(view-payment-method ctx)]
        (form {:params params :ad (:user/ad user)})
        [:.text-sm.text-neut-600
         "Ads are approved manually before running. See the "
         [:a.link.inline-block {:href "/ad-policy" :target "_blank"} "ad content policy"] "."])
       (ui/section
        {:title "Preview"}
        [:div "<preview>"]
        #_(preview ctx))
       (when user
         [:div "<results>"]
         #_(ui/section
          {:title "Results"}
          (ui/lazy-load {:href "/fiddlesticks/results"}))))])))

(def policy-page
  (ui/plain-page
   {:base/title "Ad content policy | Yakread"}
   [:div.text-black
    (ui/page-well
     (ui/section
      {:title "Ad content policy"}
      [:div.prose.text-left
       [:p "I reserve the right to reject an ad for any reason, including but not limited to:"]
       [:ul
        [:li "It's clickbait, ragebait, or another form of low-quality content."]
        [:li "It's NSFW."]
        [:li "It's advertising a competitor to Yakread (i.e. another reading app)."]
        [:li "I don't think it's a good fit for Yakread subscribers."]]
       [:p "If you're unsure about something, feel free to "
        [:a.link {:href "mailto:hello@obryant.dev"} "contact me"] "."]]))]))

(def module {:routes [page-route
                      ["" {:middleware [lib.mid/wrap-signed-in]}
                       save-ad
                       upload-image
                       change-image]]
             :static {"/ad-policy/" policy-page}})


;; (ns com.yakread.new.advertise
;;   (:require [com.biffweb :as biff :refer [q letd]]
;;             [com.yakread.middleware :as mid]
;;             [com.yakread.ui :as old-ui]
;;             [com.yakread.new.ui :as ui]
;;             [com.yakread.settings :as settings]
;;             [com.yakread.ui.icons :as icons]
;;             [com.yakread.util :as util]
;;             [com.yakread.util.s3 :as s3]
;;             [clj-http.client :as http]
;;             [clojure.data.csv :as csv]
;;             [clojure.string :as str]))
;; 
;; (defn maybe-fmt-currency [cents]
;;   (when (and cents (< 0 cents))
;;     (format "$%.2f" (/ cents 100.0))))
;; 
;; (defn status-alert [{:keys [ad ::oob]}]
;;   (let [{:keys [status steps]} (util/ad-status ad)]
;;     (when (not= status :none)
;;       (ui/alert
;;        ::ui/color
;;        (case status
;;          (:running :pending) :teal
;;          :rejected :red
;;          :yellow)
;; 
;;        ::ui/message
;;        (case status
;;          :running "Your ad is running."
;;          :pending "Your ad will start running once it's approved. Ads are usually reviewed within 24 hours."
;;          :rejected [:<>
;;                     "Your ad was not approved. See the "
;;                     [:a.underline {:href "/ad-policy" :target "_blank"}
;;                      "ad content policy"]
;;                     ". You may have received additional feedback via email. "
;;                     "After you update your ad, it will be reviewed again."]
;;          :paused "Your ad is paused."
;;          :incomplete [:<>
;;                       [:div "Complete the following steps to run your ad:"]
;;                       [:ul.mb-0 (for [step steps]
;;                                   [:li step])]])))))
;; 
;; (defn view-payment-method [{:keys [ad]}]
;;   [:div#payment-method
;;    (if-some [{:keys [brand last4 exp_year exp_month]} (:ad/card-details ad)]
;;      [:<>
;;       [:div
;;        (icons/base "check-solid" {:class "w-5 h-5 text-tealv-600 inline align-middle"})
;;        [:span.align-middle " " (str/upper-case brand) " ending in " last4
;;         " (expires " exp_month "/" exp_year ")"]]
;;       [:.h-3]
;;       [:.flex.items-center
;;        (ui/button
;;         {::ui/type :secondary
;;          :hx-delete "/fiddlesticks/payment-method"
;;          :hx-target "#payment-method"
;;          :hx-swap "outerHTML"
;;          :hx-indicator "#delete-pm-indicator"}
;;         "Remove card")
;;        [:.w-3]
;;        [:img.h-6.htmx-indicator.hidden
;;         {:id "delete-pm-indicator"
;;          :src "/img/spinner2.gif"}]]]
;;      (biff/form
;;       {:action "/fiddlesticks/payment-method"
;;        :hx-boost "false"}
;;       (ui/button
;;        {:type "Submit"}
;;        "Add a payment method")))])
;; 
;; 
;; 


;; (defn preview-card [ad]
;;   [:a.block {:href (:ad/url ad)
;;              :target "_blank"
;;              :style {:box-shadow "0 0 5px 1px #0000004d"}}
;;    (ui/ad-card ad)])
;; 
;; (defn preview [{:keys [ad params ::oob]}]
;;   (let [ad (merge {:ad/title "Lorem ipsum dolor sit amet"
;;                    :ad/description (str "Consectetur adipiscing elit, sed do eiusmod "
;;                                         "tempor incididunt ut labore et dolore magna aliqua. "
;;                                         "Ut enim ad minim veniam, quis nostrud exercitation "
;;                                         "ullamco laboris nisi ut aliquip ex ea commodo consequat.")
;;                    :ad/url "https://example.com"
;;                    :ad/image "https://yakread.com/android-chrome-512x512.png"}
;;                   ad
;;                   (-> (into {} (filter (comp not-empty val) params))
;;                       (biff/select-ns-as nil 'ad)))]
;;     [:.p-3#preview {:hx-swap-oob (when oob "true")}
;;      (preview-card ad)]))
;; 
;; (defn results [{:keys [biff/db biff/base-url user ad admin]}]
;;   (letd [{:keys [status]} (util/ad-status ad)
;;          clicks (biff/lookup-all db :ad.click/ad (:xt/id ad))
;;          referrals (biff/lookup-all db :user/referred-by (:xt/id user))
;;          ref-value (apply + (keep :user/referral-value referrals))
;;          n-clicks (->> clicks
;;                        (map :ad.click/user)
;;                        distinct
;;                        count)
;;          src->n-views (into {} (q db
;;                                   '{:find [source (count view)]
;;                                     :in [item]
;;                                     :where [[view :view/items item]
;;                                             [(get-attr view :view/source :web) [source ...]]]}
;;                                   (:xt/id ad)))
;;          n-views-min (get src->n-views :web 0)
;;          n-views-max (+ n-views-min (get src->n-views :email 0))
;;          [[recent-clicks
;;            median-cost]] (vec (q db
;;                                  '{:find [(count click) (median cost)]
;;                                    :in [t0]
;;                                    :where [[click :ad.click/created-at t]
;;                                            [click :ad.click/cost cost]
;;                                            [(<= t0 t)]
;;                                            [(< 0 cost)]]}
;;                                  (biff/add-seconds (java.util.Date.) (* -60 60 24 7))))
;;          recent-clicks (or recent-clicks 0)
;;          median-cost (or median-cost 0)
;;          threshold 3000]
;;     [:<>
;;      (if (= status :none)
;;        [:div "Your ad results will be displayed here."]
;;        [:<>
;;         [:ul.my-0.gap-2.flex.flex-col
;;          [:li
;;           [:span.font-bold n-views-min "+"]
;;           " impression" (when (not= 1 n-views-min) "s")
;;           " and "
;;           [:span.font-bold n-clicks] " click" (when (not= 1 n-clicks) "s")
;;           " so far."]
;;          (if (<= 0 (:ad/balance ad))
;;            [:li "Your balance is "
;;             [:span.font-bold "$" (fmt-dollars (:ad/balance ad))]
;;             ". You'll be charged after it reaches "
;;             [:span.font-bold "$" (fmt-dollars threshold)] ", or after one month."]
;;            [:li "You have "
;;             [:span.font-bold "$" (fmt-dollars (- (:ad/balance ad)))]
;;             " of ad credit. "
;;             (if (:ad/payment-method ad)
;;               "We'll start charging your card after your credit is used up."
;;               (str "After your credit is used up, you'll need to add a payment method "
;;                    "to continue advertising."))])
;;          [:li "Across Yakread in the past week, there "
;;           (if (= 1 recent-clicks)
;;             "has"
;;             "have")
;;           " been "
;;           [:span.font-bold recent-clicks] " click" (when (not= 1 recent-clicks) "s")
;;           " at a median cost of "
;;           [:span.font-bold "$" (fmt-dollars median-cost)] " per click."]
;;          [:li [:a.link {:href "/advertise/history" :hx-boost false :target "_blank"} "Download"]
;;           " your transaction history."]]])
;; 
;;      (when-some [code (:user/referral-code user)]
;;        [:div
;;         [:.h-4]
;;         (ui/section
;;         {:title "Referral program"}
;;         [:.flex.flex-col.gap-2
;;          [:div "We'll give you " [:span.font-bold "$" (fmt-dollars settings/referral-reward)]
;;           " of ad credit for each new user you refer to Yakread. You can refer people by sharing this link: "
;;           [:a.link {:href (str base-url "?ref=" code)}
;;            (str base-url "?ref=" code)] "."
;;           " See " [:a.link {:href "https://tfos.co/sharing-yakread/" :target "_blank"}
;;                    "resources for sharing Yakread"] "."]
;;          [:div "Please be considerate."
;;           " We recommend sharing your referral links in your "
;;           "newsletter or on your social media accounts. "
;;           "Please do not post your links in community spaces. "
;;           "See also the " [:a.link {:href (str base-url "/referral-policy") :target "_blank"}
;;                            "referral policy"] "."]
;;          [:div "You've referred " [:span.font-bold (count referrals)] " new user"
;;           (when (not= (count referrals) 1) "s")
;;           " to Yakread."]])])]))
;; 
;; (defn download-history [{:keys [biff/db ad user]}]
;;   (let [clicks (q db
;;                   '{:find (pull click [*])
;;                     :in [ad]
;;                     :where [[click :ad.click/ad ad]]}
;;                   (:xt/id ad))
;;         credit (q db
;;                   '{:find (pull credit [*])
;;                     :in [ad]
;;                     :where [[credit :ad.credit/ad ad]]}
;;                   (:xt/id ad))
;;         referrals (q db
;;                      '{:find (pull referral [*])
;;                        :in [user]
;;                        :where [[referral :user/referred-by user]]}
;;                      (:xt/id user))
;;         events (concat
;;                 (for [{:ad.click/keys [created-at cost]} clicks]
;;                   {:timestamp created-at
;;                    :debit cost
;;                    :description (if (< 0 cost)
;;                                   "Ad click"
;;                                   "Duplicate ad click")})
;;                 (for [{:ad.credit/keys [type
;;                                         amount
;;                                         created-at
;;                                         status]} credit
;;                       :when (= status :confirmed)]
;;                   {:timestamp created-at
;;                    :credit amount
;;                    :description (if (= type :charge)
;;                                   "Credit card charged"
;;                                   "Credit added to account manually")})
;;                 (for [{:user/keys [joined-at referral-value]} referrals
;;                       :when (some? referral-value)]
;;                   {:timestamp joined-at
;;                    :credit referral-value
;;                    :description "Referred user to Yakread"}))
;;         events (->> events
;;                     (sort-by :timestamp)
;;                     (reduce (fn [events event]
;;                               (let [balance (+ (:balance (peek events) 0)
;;                                                (:debit event 0)
;;                                                (- (:credit event 0)))]
;;                                 (conj events (assoc event :balance balance))))
;;                             []))
;;         body (str
;;               (with-open [w (java.io.StringWriter.)]
;;                 (csv/write-csv
;;                  w
;;                  (into [["Date" "Description" "Credit" "Debit" "Balance"]]
;;                        (for [event (reverse events)]
;;                          [(biff/format-date (:timestamp event))
;;                           (:description event)
;;                           (maybe-fmt-currency (:credit event 0))
;;                           (maybe-fmt-currency (:debit event 0))
;;                           (format "$%.2f" (/ (:balance event 0) 100.0))])))
;;                 w))]
;;     {:status 200
;;      :headers {"content-type" "text/csv"
;;                "content-disposition" "attachment; filename=\"yakread-transaction-history.csv\""}
;;      :body body}))
;; 
;; (defn create-customer! [{:keys [biff/secret user] :as ctx}]
;;   (let [id (-> (http/post "https://api.stripe.com/v1/customers"
;;                           {:basic-auth [(secret :stripe/api-key)]
;;                            :form-params {:email (:user/email user)}
;;                            :as :json})
;;                :body
;;                :id)]
;;     (biff/submit-tx ctx
;;       [{:db/doc-type :ad
;;         :db.op/upsert {:ad/user (:xt/id user)}
;;         :ad/customer-id id
;;         :ad/state [:db/default :pending]
;;         :ad/balance [:db/default 0]
;;         :ad/recent-cost [:db/default 0]
;;         :ad/updated-at :db/now}])
;;     id))
;; 
;; (defn add-payment-method [{:keys [biff/secret biff/base-url user ad] :as ctx}]
;;   (let [customer-id (or (:ad/customer-id ad)
;;                         (create-customer! ctx))
;;         response (http/post "https://api.stripe.com/v1/checkout/sessions"
;;                             {:basic-auth [(secret :stripe/api-key)]
;;                              :multi-param-style :array
;;                              :form-params {:payment_method_types ["card"]
;;                                            :mode "setup"
;;                                            :customer customer-id
;;                                            :success_url (str base-url "/fiddlesticks/payment-method?session-id={CHECKOUT_SESSION_ID}")
;;                                            :cancel_url (str base-url "/advertise")}
;;                              :as :json})
;;         {:keys [url id]} (:body response)]
;;     (biff/submit-tx ctx
;;       [{:db/doc-type :ad
;;         :db.op/upsert {:ad/user (:xt/id user)}
;;         :ad/session-id id
;;         :ad/updated-at :db/now}])
;;     {:status 303
;;      :headers {"Location" url}}))
;; 
;; (defn receive-payment-method [{:keys [biff/secret biff/db params] :as ctx}]
;;   (let [{:keys [session-id]} params
;;         pm (-> (http/get (str "https://api.stripe.com/v1/checkout/sessions/" session-id)
;;                          {:basic-auth [(secret :stripe/api-key)]
;;                           :multi-param-style :array
;;                           :as :json
;;                           :query-params {:expand ["setup_intent" "setup_intent.payment_method"]}})
;;                :body
;;                :setup_intent
;;                :payment_method)]
;;     (when-some [ad-id (biff/lookup-id db :ad/session-id session-id)]
;;       (biff/submit-tx ctx
;;         [{:db/doc-type :ad
;;           :db/op :update
;;           :xt/id ad-id
;;           :ad/session-id :db/dissoc
;;           :ad/payment-method (:id pm)
;;           :ad/card-details (select-keys (:card pm) [:brand :last4 :exp_year :exp_month])
;;           :ad/updated-at :db/now}])))
;;   {:status 303
;;    :headers {"Location" "/advertise"}})
;; 
;; (defn delete-payment-method [{:keys [biff/secret ad] :as ctx}]
;;   (let [ctx (update ctx :ad dissoc :ad/payment-method :ad/card-details)]
;;     (biff/submit-tx ctx
;;       [{:db/doc-type :ad
;;         :db/op :update
;;         :xt/id (:xt/id ad)
;;         :ad/payment-method :db/dissoc
;;         :ad/card-details :db/dissoc
;;         :ad/updated-at :db/now}])
;;     (http/post (str "https://api.stripe.com/v1/payment_methods/"
;;                     (:ad/payment-method ad)
;;                     "/detach")
;;                {:basic-auth [(secret :stripe/api-key)]})
;;     [:<>
;;      (view-payment-method ctx)
;;      #_(status-alert (assoc ctx ::oob true))]))
;; 
;; 
;; (defn autocomplete [{:keys [ad params] :as ctx}]
;;   (let [params (update params :url util/fix-url)
;;         new-params (if (->> (map params [:title :description :image])
;;                             (some empty?))
;;                      (fetch-metadata (:url params))
;;                      {})
;;         ctx (assoc ctx :params (->> (filter (comp not-empty val) params)
;;                                     (into new-params)))]
;;     [:<>
;;      (ad-form ctx)
;;      (preview (assoc ctx ::oob true))]))
;; 
;; 
;; (defn wrap-ad [handler]
;;   (fn [{:keys [biff/db user] :as ctx}]
;;     (handler (cond-> ctx
;;                user (assoc :ad (biff/lookup db :ad/user (:xt/id user)))))))
;; 
;; (def ^:biff plugin
;;   {:routes [["/advertise" {:middleware [mid/wrap-maybe-signed-in
;;                                         wrap-ad]}
;;              ["" {:get main}]]
;;             ["/fiddlesticks" {:middleware [mid/wrap-signed-in
;;                                            wrap-ad]}
;;              ["/save" {:post save-ad}]
;;              ["/payment-method" {:get receive-payment-method
;;                                  :post add-payment-method
;;                                  :delete delete-payment-method}]
;;              ["/autocomplete" {:get autocomplete}]
;;              ["/image"        {:post upload-image}]
;;              ["/change-image" {:get change-image}]
;;              ["/results"      {:get results}]]
;;             ["/advertise" {:middleware [mid/wrap-signed-in
;;                                         wrap-ad]}
;;              ["/history"      {:get download-history}]]]
;;    :static {"/referral-policy/" referral-policy}})
