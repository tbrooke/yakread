(ns com.yakread.app.subscriptions.add
  (:require [com.biffweb :as biff]
            [com.yakread.lib.middleware :as lib.middle]
            [com.yakread.lib.pathom :as lib.pathom :refer [?]]
            [com.yakread.lib.pipeline :as lib.pipe]
            [com.yakread.lib.route :as lib.route]
            [com.yakread.lib.rss :as lib.rss]
            [com.yakread.lib.ui :as lib.ui]
            [com.yakread.lib.user :as lib.user]))

(def page-route
  ["/dev/subscriptions/add"
   {:name :app.subscriptions.add/page
    :get (lib.pathom/handler
          [:app.shell/app-shell
           {(? :user/current) [:xt/id
                               (? :user/email-username)
                               (? :user/suggested-email-username)]}]

          (fn [{:keys [biff/router params] :as ctx}
               {:keys [app.shell/app-shell] user :user/current}]
            (app-shell
             {:title "Add subscriptions"}
             (lib.ui/page-header {:title     "Add subscriptions"
                                  :back-href (lib.route/path router :app.subscriptions/page {})})
             (when-not user
               [:div {:class '["max-sm:-mx-4"
                               mb-6]}
                (lib.ui/signup-box ctx
                                   {:on-success  :app.subscriptions.add/page
                                    :on-error    :app.subscriptions.add/page
                                    :title       nil
                                    :description (str "Create a Yakread account to subscribe to "
                                                      "newsletters and RSS feeds.")})])
             [:fieldset.disabled:opacity-60
              {:disabled (when-not user "disabled")}
              (lib.ui/page-well
               (lib.ui/section
                {:title "Newsletters"}
                (if-some [username (:user/email-username user)]
                  [:div "Sign up for newsletters with "
                   [:span.font-semibold username "@yakread.com"]]
                  (biff/form
                    {:action (lib.route/path router :app.subscriptions.add/username {})
                     :hx-indicator "#username-indicator"}
                    (lib.ui/uber-input
                     {:label "Username"
                      :description "You can subscribe to newsletters after you pick a username."
                      :postfix "@yakread.com"
                      :name "username"
                      :value (or (:email-username params)
                                 (:user/suggested-email-username user))
                      :required true
                      :submit-text "Save"
                      :indicator-id "username-indicator"
                      :error (when (= (:error params) "username-unavailable")
                               "That username is unavailable.")}))))
               (lib.ui/section
                {:title "RSS feeds"}
                (when-some [n (some-> (:added-feeds params) parse-long)]
                  [:div {:class '[bg-tealv-50
                                  border-l-4
                                  border-tealv-200
                                  p-3
                                  text-neut-800]}
                   "Subscribed to " (lib.ui/pluralize n "feed") "."])
                (biff/form
                  {:action (lib.route/path router :app.subscriptions.add/rss {})
                   :hx-indicator (str "#" (lib.ui/dom-id ::rss-indicator))}
                  (lib.ui/uber-input
                   {:label "Website or feed URL"
                    :name "url"
                    :value (:url params)
                    :required true
                    :submit-text "Subscribe"
                    :description [:<> "You can also "
                                  [:button.link {:_ "on click toggle .hidden on #bookmarklet-modal"
                                                 :type "button"}
                                   "subscribe via bookmarklet"] "."]
                    :indicator-id (lib.ui/dom-id ::rss-indicator)
                    :error (when (= (:error params) "invalid-rss-feed")
                             "We weren't able to subscribe to that URL.")})
                  (lib.ui/modal
                   {:id "bookmarklet-modal"
                    :title "Subscribe via bookmarklet"}
                   [:.p-4
                    [:p "You can install the bookmarklet by dragging this link "
                     "onto your browser toolbar or bookmarks menu:"]
                    [:p.my-6 [:a.text-xl.text-blue-600
                              {:href "javascript:window.location=\"https://yakread.com/subscriptions/add?url=\"+encodeURIComponent(document.location)"}
                              "Subscribe | Yakread"]]
                    [:p.mb-0 "Then click the bookmarklet to subscribe to the RSS feed for the current page."]]))
                (biff/form
                  {:action (lib.route/path router :app.subscriptions.add/opml {})
                   :hx-indicator (str "#" (lib.ui/dom-id ::opml-indicator))
                   :enctype "multipart/form-data"}
                  (lib.ui/uber-input
                   {:label "OPML file"
                    :name "opml"
                    :type "file"
                    :accept ".opml"
                    :required true
                    :submit-text "Import"
                    :submit-opts {:class '["w-[92px]"]}
                    :indicator-id (lib.ui/dom-id ::opml-indicator)
                    :error (when (= (:error params) "invalid-opml-file")
                             "We weren't able to import that file.")}))))])))}])

(def username-route
  ["/dev/subscriptions/add/username"
   {:name :app.subscriptions.add/username
    :post (let [response (fn [success username]
                           (merge {:status 303
                                   :biff.router/name :app.subscriptions.add/page}
                                  (when-not success
                                    {:biff.router/params
                                     {:error "username-unavailable"
                                      :email-username username}})))]
            (lib.pipe/make
             :start
             (fn [{:keys [biff/db params]}]
               (let [username (lib.user/normalize-email-username (:username params))]
                 (if (or (empty? username)
                         (lib.user/email-username-taken? db username))
                   (response false (:username params))
                   {:biff.pipe/next [:biff.pipe/pathom :save-username]
                    :biff.pipe.pathom/query [{:user/current [(? :user/email-username)]}]
                    ::username username})))

             :save-username
             (fn [{:keys [biff.pipe.pathom/output session ::username]}]
               (if (get-in output [:user/current :user/email-username])
                 (response true nil)
                 {:biff.pipe/next     [:biff.pipe/tx :end]
                  :biff.pipe.tx/input [{:db/doc-type :user
                                        :db/op :update
                                        :xt/id (:uid session)
                                        :user/email-username* [:db/unique username]}]
                  :biff.pipe/catch    :biff.pipe/tx
                  ::username          username}))

             :end
             (fn [{:keys [biff.pipe/exception ::username]}]
               (response (not exception) username))))}])

(def rss-route
  ["/dev/subscriptions/add/rss"
   {:name :app.subscriptions.add/rss
    :post (lib.pipe/make
           :start
           (fn [{:keys [params]}]
             {:biff.pipe/next       [:biff.pipe/http :add-urls]
              :biff.pipe.http/input {:url     (lib.rss/fix-url (:url params))
                                     :method  :get
                                     :headers {"User-Agent" "https://yakread.com/"}}
              :biff.pipe/catch      :biff.pipe/http})

           :add-urls
           (fn [{:keys [session params biff.pipe.http/output]}]
             (let [feed-urls (some->> output
                                      lib.rss/parse-urls
                                      (mapv :url)
                                      (take 20)
                                      vec)]
               (if (empty? feed-urls)
                 {:status             303
                  :biff.router/name   :app.subscriptions.add/page
                  :biff.router/params {:error "invalid-rss-feed"
                                       :url (:url params)}}
                 {:biff.pipe/next     [:biff.pipe/tx]
                  :biff.pipe.tx/input (for [url feed-urls]
                                        {:db/doc-type :conn/rss
                                         :db.op/upsert {:conn/user (:uid session)
                                                        :conn.rss/url url}
                                         :conn.rss/subscribed-at :db/now})
                  :status             303
                  :biff.router/name   :app.subscriptions.add/page
                  :biff.router/params {:added-feeds (count feed-urls)}}))))}])

(def opml-route
  ["/dev/subscriptions/add/opml"
   {:name :app.subscriptions.add/opml
    :post (lib.pipe/make
           :start
           (fn [{{{:keys [tempfile]} :opml} :params}]
             {:biff.pipe/next        [:biff.pipe/slurp :end]
              :biff.pipe.slurp/input tempfile})

           :end
           (fn [{:keys [session biff.pipe.slurp/output]}]
             (let [urls (lib.rss/extract-opml-urls output)
                   tx (for [url urls]
                        {:db/doc-type :conn/rss
                         :db.op/upsert {:conn/user (:uid session)
                                        :conn.rss/url url}
                         :conn.rss/subscribed-at :db/now
                         :conn.rss/source :manual})]
               (merge (when (not-empty urls)
                        {:biff.pipe/next [:biff.pipe/tx]
                         :biff.pipe.tx/input tx})
                      {:status 303
                       :biff.router/name :app.subscriptions.add/page
                       :biff.router/params (if (empty? urls)
                                             {:error "invalid-opml-file"}
                                             {:added-feeds (count urls)})}))))}])

(def module
  {:routes [page-route
            ["" {:middleware [lib.middle/wrap-signed-in]}
             username-route
             rss-route
             opml-route]]})
