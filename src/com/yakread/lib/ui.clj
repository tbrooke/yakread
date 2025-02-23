(ns com.yakread.lib.ui
  (:require [clojure.string :as str]
            [com.biffweb :as biff]
            [com.yakread.lib.icons :as lib.icons]
            [com.yakread.lib.route :as lib.route]
            [lambdaisland.uri :as uri]))

(defn- dissoc-ns [m ns-str]
  (into {}
        (remove #(= ns-str (namespace (key %))))
        m))

(defn with-classes [opts & classes]
  (-> opts
      (update :class concat (flatten classes))
      (dissoc-ns "ui")))

;;;; Utilities

(def spinner-gif "/img/spinner2.gif")

(def interpunct " · ")

(defn pluralize [n label]
  (str n " " label (when (not= 1 n) "s")))

(defn dom-id [x]
  (str/replace (str x) #"[^a-zA-Z0-9-]" "_"))

(defn weserv [opts]
  (str (apply uri/assoc-query "https://images.weserv.nl/"
              (apply concat opts))))

(defn signup-error [params]
  (case (not-empty (:error params))
    nil nil
    "recaptcha" (str "You failed the recaptcha test. Try again, "
                     "and make sure you aren't blocking scripts from Google.")
    "invalid-email" "Invalid email. Try again with a different address."
    "send-failed" (str "We weren't able to send an email to that address. "
                       "If the problem persists, try another address.")
    "There was an error."))

;;;; Misc

(defn lazy-load [href]
  [:.flex.justify-center
   {:hx-get href
    :hx-trigger "intersect once"
    :hx-swap "outerHTML"}
   [:img.h-10 {:src spinner-gif}]])

(defn lazy-load-spaced [href]
  [:<>
   [:.grow]
   (lazy-load href)
   [:.grow]])

(defn callout [{:ui/keys [icon] type* :ui/type :as opts} & contents]
  [:div (with-classes opts
          '[;border-l-4
            p-4
            text-neut-800
            flex items-start gap-3]
          (case type*
            :info 'bg-tealv-75
            :error 'bg-redv-50))
   (when-some [icon (cond
                      (contains? opts :ui/icon) icon
                      (= type* :info) "circle-info")]
     (lib.icons/base icon {:class '[w-5 pt-1 flex-shrink-0]}))
   contents])

(defn confirm-unsub-msg [title]
  (str "Unsubscribe from "
       (-> title str/trim (str/replace #"\s+" " "))
       "?"))

;;;; Buttons

(defn button [{:ui/keys [size] type_ :ui/type :as opts} & contents]
  [(if (contains? opts :href) :a :button)
   (with-classes opts
     '[inter font-medium
       disabled:opacity-70]
     (when (not= type_ :link)
       '[rounded-md
         "min-w-[5rem]"])
     (case type_
       :primary '[bg-tealv-500 hover:bg-tealv-600 disabled:hover:bg-tealv-500
                  text-neut-50]
       :secondary '[border
                    text-white
                    bg-neut-500 hover:bg-neut-400 disabled:hover:bg-neut-500]
       :danger '[bg-redv-700 hover:bg-redv-800 disabled:bg-redv-200 disabled:hover:bg-redv-200
                 text-neut-50 disabled:text-white]
       :link   '[text-blue-600]
       '[bg-neut-700 hover:bg-neut-800 disabled:hover:bg-neut-700
         text-neut-50])
     (case size
       :small '[text-sm
                px-3 py-1]
       :large '[max-sm:text-sm
                px-3 py-2]
       '[text-sm
         px-3 py-2]))
   contents])

(defn overflow-button [{:keys [href] :as opts} & contents]
  [(if href :a :button)
   (update opts
           :class
           concat
           '[block
             w-full
             px-3 py-2
             hover:bg-neut-75
             first:rounded-t last:rounded-b
             inter text-sm
             whitespace-nowrap text-left])
   contents])

(defn overflow-menu [{:ui/keys [direction icon rounded hover-shade]
                      :or {direction :down
                           icon "ellipsis-vertical-regular"
                           hover-shade :regular}
                      :as opts}
                     & contents]
  [:.relative.flex.items-center
   {:class [(case direction
              :up "translate-y-[-100%]"
              :down 'translate-y-full)]}
   (cond->> (list [:button (-> opts
                               (with-classes
                                 '[flex flex-none
                                   px-1 py-2
                                   h-full
                                   text-neut-600]
                                 (case hover-shade
                                   :regular 'hover:bg-neut-50
                                   :dark 'hover:bg-neut-200)
                                 (when rounded
                                   '[rounded-lg])
                                 (case direction
                                   :up 'translate-y-full
                                   :down "translate-y-[-100%]"))
                               (merge {:_ (str "on click toggle .hidden on the "
                                               (if (= direction :down)
                                                 "next"
                                                 "previous")
                                               " .dropdown then halt")}))
                   (lib.icons/base icon
                                   {:class '[w-8
                                             h-5
                                             flex-shrink-0]})]
                  [:div {:class (concat
                                 '[dropdown
                                   rounded
                                   absolute right-0
                                   hidden
                                   bg-white
                                   border border-neut-300
                                   shadow-sm]
                                 (if (= direction :down)
                                   '[top-0 mt-2]
                                   '[bottom-0 mb-2]))}
                   contents])
     (= direction :up) reverse)])

;;;; Inputs

(defn- text-input* [element {:ui/keys [size] type_ :type :as opts}]
  [element
   (with-classes opts
     '[w-full
       rounded-md
       shadow-inner
       border-0
       ring-1 ring-inset ring-neut-100
       text-black inter leading-6
       bg-neut-50 disabled:opacity-70]
     (case type_
       "file" '["file:py-[11.5px]" file:px-3
                file:border-0 focus:outline-tealv-600
                file:bg-neut-100
                file:text-neut-800 text-neut-600]
       '[focus:ring-inset
         focus:ring-tealv-600
         py-2])
     (case size
       :large '[max-sm:text-sm]
       '[text-sm]))])

(defn text-input [opts]
  (text-input* :input (merge {:type "text"} opts)))

(defn textarea [opts]
  (text-input* :textarea opts))

(defn overlay-text-input [{:ui/keys [icon postfix size] :as opts}]
  [:.relative
   [:div {:class '[absolute
                   inset-0
                   flex items-center
                   pointer-events-none]}
    (when icon
      (lib.icons/base icon {:class '[size-4
                                     ml-4
                                     text-stone-600]}))
    [:.grow]
    [:div {:class (concat
                   '[h-full
                     text-neut-600
                     flex items-center
                     p-3]
                   (case size
                     :large '[max-sm:text-sm]
                     '[text-sm]))}
     postfix]]
   (text-input (cond-> opts
                 icon (update :class concat '[pl-10])))])

(defn input-label [opts & contents]
  [:label.block.mb-2 opts
   [:div {:class '[inter font-medium tracking-wide
                   text-sm
                   text-neut-900]}
    contents]])

(defn input-description [& contents]
  (into [:.text-sm.text-gray-600.mt-1] contents))

(defn input-error [& contents]
  (into [:.text-sm.text-redv-500.mt-1] contents))

(defn form-input [{name_ :name :as opts}]
  (let [{:ui/keys [label error description
                   size indicator-id
                   submit-opts submit-text]
         :keys [id required] :as opts} (merge {:id name_} opts)]
    [:div.w-full
     (when label
       (input-label
        {:for id
         :required required}
        [:.flex.items-center.gap-2
         label
         (when indicator-id
           [:img.h-5.htmx-indicator.hidden
            {:id indicator-id
             :src spinner-gif}])]))
     (if-not submit-text
       (overlay-text-input opts)
       [:.flex.gap-3.w-full
        [:.grow (overlay-text-input opts)]
        [:.flex-shrink-0
         (button (-> {:type "submit"
                      :ui/size size}
                     (merge submit-opts)
                     (update :class concat '[leading-6]))
           submit-text)]])
     (case size
       :large [:.h-1]
       nil)
     (some->> error input-error)
     (some->> description input-description)]))

(defn checkbox [{:ui/keys [label] :as opts}]
  [:label.flex.items-center.gap-2.cursor-pointer
   [:input (with-classes (merge {:type "checkbox"} opts)
             '[text-tealv-500
               "border border-neut-300"
               form-checkbox
               cursor-pointer
               focus:ring-1 focus:ring-neut-300])]
   [:span.inter.text-sm.text-neut-800
    label]])

;;;; Layout

(defn page-header [& {:keys [title add-href back-href actions]}]
  [:div.max-sm:px-4
   (when back-href
     [:a {:href back-href
          :class '[text-neut-600
                   hover:text-neut-900
                   mb-2
                   flex
                   items-center
                   gap-1
                   text-sm
                   inter
                   font-medium
                   w-fit]}
      (lib.icons/default chevron-left-regular w-3 h-3)
      "Back"])
   [:.flex.items-center.mb-8.gap-4
    [:h2.font-bold.text-2xl title]
    [:.grow]
    actions
    (when add-href
      [:a {:href add-href
           :class '[text-tealv-500
                    hover:text-tealv-600
                    bg-neut-50
                    rounded-full]}
       (lib.icons/base "circle-plus-solid"
                       {:class '[h-8
                                 w-8
                                 md:h-10
                                 md:w-10]})])]])

(defn page-well [& contents]
  [:div {:class '[bg-white
                  p-4
                  sm:rounded
                  shadow
                  flex
                  flex-col
                  gap-10
                  "max-sm:-ml-[1rem]"
                  "max-sm:-mr-[1rem]"
                  max-w-screen-sm]}
   contents])

(defn section [{:keys [title]} & contents]
  [:div
   (when title
     [:<>
      [:h3.font-bold.text-lg title]
      [:.h-3]
      [:hr.border-neut-100]
      [:.h-6]])
   [:.flex.flex-col.gap-6
    contents]])

(defn modal [{:keys [id title]} & content]
  [:div {:id id
         :class '[fixed inset-0
                  flex flex-col items-center
                  hidden
                  overflow-y-auto
                  p-4
                  z-30]
         :aria-modal "true",
         :role "dialog"}
   [:.grow]
   [:div.fixed.inset-0.bg-gray-500.bg-opacity-75
    {:aria-hidden "true"
     :_ (str "on click toggle .hidden on #" id)}]
   [:.bg-white.rounded.max-w-screen-sm.w-full.transform
    [:.flex.h-12.border-b.items-center.p-3
     [:h3.font-bold.text-lg title]
     [:.grow]
     [:button.flex {:type "button"
                    :_ (str "on click toggle .hidden on #" id)}
      (lib.icons/base "xmark-regular" {:class "w-6 h-6"})]]
    content]
   [:.grow]])

(defn card-grid [{:ui/keys [cols] :as opts} cards]
  (let [cnt (count cards)]
    [:div (with-classes opts
            '["grid grid-cols-1 sm:grid-cols-2"
              gap-4]
            (case cols
              4 "xl:grid-cols-3 2xl:grid-cols-4"
              5 "lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5"))
     (for [[i card] (map-indexed vector cards)]
       [:.yak-card {:style {:z-index (- (+ 10 cnt) i)}}
        card])]))

;;;; Composites

(defn signup-box [{:keys [recaptcha/site-key
                          params]}
                  {:keys [on-success on-error title description]
                   :or {on-success "/home"
                        on-error "/"
                        title "Read stuff that matters."
                        description (str "Get a selection of trending articles in your inbox daily "
                                         "and unlock the full reading experience.")}}]
  [:div {:class '[max-sm:p-3
                  max-w-screen-sm]}
   (when title
     [:h1 {:class '[text-2xl sm:text-3xl
                    font-bold proximanova-bold underline]}
      title])
   (when (and title description) [:.h-1])
   (when description
     [:.sm:text-xl description])
   (when (or title description) [:.h-5])
   (biff/form
     {:id "signup-form"
      :action "/auth/send-link"
      :hx-boost "false"
      :hidden {:on-error on-error
               :redirect on-success}}
     [:input {:type "hidden" :name "href" :_ "on load set my value to window.location.href"}]
     [:input {:type "hidden" :name "referrer" :_ "on load set my value to document.referrer"}]
     (biff/recaptcha-callback "onSubscribe" "signup-form")
     [:.flex.gap-3.w-full
      [:input {:type "email"
               :id "email"
               :name "email"
               :placeholder "Enter your email"
               :class '[w-full
                        shadow
                        border-0 rounded
                        text-black inter leading-6
                        bg-neut-50 disabled:opacity-70
                        focus:ring-inset focus:ring-tealv-600
                        py-1.5
                        sm:text-lg
                        grow]}]
      [:div
       [:button {:type "submit"
                 :class '[g-recaptcha
                          px-5 py-2
                          bg-tealv-500 hover:bg-tealv-600 disabled:bg-tealv-500 disabled:opacity-70
                          text-neut-50 inter font-medium leading-6
                          sm:text-lg
                          whitespace-nowrap
                          shadow
                          rounded]
                 ;:_ "on click toggle @disabled until htmx:afterOnLoad"
                 :data-sitekey site-key
                 :data-callback "onSubscribe"
                 :data-action "subscribe"}
        "Sign up"]]]
     [:.h-1]
     (if-some [error (signup-error params)]
       (input-error error)
       (input-description
        "(Already have an account? "
        [:a.link {:href "/signin"} "Sign in"] ")"))
     [:.h-3])])

(defn empty-page-state [{:keys [icons text btn-label btn-href]}]
  [:.flex.flex-col.grow
   [:.grow]
   [:.flex.justify-center.gap-8.text-neut-800
    (for [icon icons]
      (lib.icons/base icon {:class '[h-12 w-12]}))]
   [:.h-4]
   [:p.text-center.text-neut-800.mb-0.text-lg
    text]
   [:.h-6]
   [:.flex.justify-center.gap-4
    (button {:href btn-href :ui/type :primary} btn-label)]
   [:.grow]])

(defn on-error-page [ctx]
  (biff/render [:h1 "TODO"]))
