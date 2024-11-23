(ns com.yakread.lib.ui
  (:require [clojure.string :as str]
            [com.biffweb :as biff]
            [com.yakread.lib.icons :as lib.icons]
            [com.yakread.lib.route :as lib.route]
            [lambdaisland.uri :as uri]))

(defn pluralize [n label]
  (str n " " label (when (not= 1 n) "s")))

(defn dom-id [x]
  (str/replace (str x) #"[^a-zA-Z0-9-]" "_"))

(defn page-header [& {:keys [title add-href back-href]}]
  [:div
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
   [:.flex.items-center.mb-8
    [:h2.font-bold.text-2xl title]
    [:.grow]
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

(defn lazy-load [router route-name params]
  [:.flex.justify-center
   {:hx-get (lib.route/path router route-name params)
    :hx-trigger "intersect once"
    :hx-swap "outerHTML"}
   [:img.h-10 {:src "/img/spinner2.gif"}]])

(defn lazy-load-spaced [router route-name params]
  [:<>
   [:.grow]
   (lazy-load router route-name params)
   [:.grow]])

#_(defn button [{:keys [href ::type ::size ::min-w]
                 :or {size :base}
                 :as opts} & contents]
    (let [cls (concat
               '[rounded-md
                 inter
                 disabled:opacity-70
                 font-medium]
               [(or min-w "min-w-[5rem]")]
               (when-not type
                 '[bg-neut-700
                   hover:bg-neut-800
                   disabled:hover:bg-neut-700
                   text-neut-50])
               (when (= type :primary)
                 '[bg-tealv-500
                   hover:bg-tealv-600
                   disabled:hover:bg-tealv-500
                   text-neut-50])
               (when (= type :secondary)
                 '[border
                   text-white
                   bg-neut-500
                   hover:bg-neut-400
                   disabled:hover:bg-neut-500])
               (when (= type :danger)
                 '[bg-redv-700
                   hover:bg-redv-800
                   text-neut-50
                   disabled:bg-redv-200
                   disabled:hover:bg-redv-200
                   disabled:text-white
                   ])
               (if (= size :small)
                 '[text-sm
                   px-3
                   py-1]
                 '[text-sm
                   px-3
                   py-2]))
          opts (-> opts
                   (update :class concat cls)
                   (dissoc ::type ::size))]
      [(if (contains? opts :href)
         :a
         :button)
       opts
       contents]))

(defn btn-primary [{:keys [href size] btn-type :type} & contents]
  (let [classes (concat
                 '[rounded-md
                   inter
                   disabled:opacity-70
                   font-medium
                   bg-tealv-500
                   hover:bg-tealv-600
                   disabled:hover:bg-tealv-500
                   text-neut-50
                   "min-w-[5rem]"]
                 (if (= size :small)
                   '[text-sm
                     px-3
                     py-1]
                   '[text-sm
                     px-3
                     py-2]))]
    (if href
      [:a {:href href :class classes} contents]
      [:button (merge {:class classes}
                      (when btn-type
                        {:type btn-type}))
       contents])))

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
   [:.flex.justify-center
    (btn-primary {:href btn-href}
                 btn-label)]
   [:.grow]])

(defn overflow-button [{:keys [href] :as opts} & contents]
  (let [cls '[block
              py-2
              px-4
              hover:bg-neut-75
              font-medium
              inter
              whitespace-nowrap
              w-full
              text-left]]
    [(if href :a :button) (update opts :class concat cls) contents]))

(defn overflow-menu [{:keys [direction icon]
                      :or {direction :down
                           icon "ellipsis-regular"}}
                     & contents]
  [:.relative.flex.items-center
   {:class [(case direction
              :up "translate-y-[-100%]"
              :down 'translate-y-full)]}
   (cond->> (list [:button {:class (concat
                                    '[flex
                                      py-2
                                      px-1
                                      hover:bg-neut-50
                                      flex-none
                                      h-full
                                      rounded-full
                                      text-neut-600]
                                    [(case direction
                                       :up 'translate-y-full
                                       :down "translate-y-[-100%]")])
                            :_ (str "on click toggle .hidden on the "
                                    (if (= direction :down)
                                      "next"
                                      "previous")
                                    " .dropdown then halt")}
                   (lib.icons/base icon
                                   {:class '[w-8
                                             h-5
                                             flex-shrink-0]})]
                  [:div {:class (concat
                                 '[dropdown
                                   rounded
                                   absolute
                                   right-0
                                   hidden
                                   bg-white
                                   py-1
                                   rounded
                                   border
                                   shadow-uniform]
                                 (if (= direction :down)
                                   '[top-0
                                     mt-2]
                                   '[bottom-0
                                     mb-2]))}
                   contents])
     (= direction :up) reverse)])

(defn signup-error [params]
  (case (not-empty (:error params))
    nil nil
    "recaptcha" (str "You failed the recaptcha test. Try again, "
                     "and make sure you aren't blocking scripts from Google.")
    "invalid-email" "Invalid email. Try again with a different address."
    "send-failed" (str "We weren't able to send an email to that address. "
                       "If the problem persists, try another address.")
    "There was an error."))

(defn input-description [& contents]
  (into [:.text-sm.text-gray-600.mt-1] contents))

(defn input-error [& contents]
  (into [:.text-sm.text-redv-500.mt-1] contents))

(defn signup-box [{:keys [biff/router
                          recaptcha/site-key
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
     [:h1 {:class '[text-2xl
                    sm:text-3xl
                    font-bold
                    proximanova-bold
                    underline]}
      title])
   (when (and title description) [:.h-1])
   (when description
     [:.sm:text-xl description])
   (when (or title description) [:.h-5])
   (biff/form
     {:id "signup-form"
      :action "/auth/send-link"
      :hx-boost "false"
      ;; TODO always use router
      :hidden {:on-error (if (keyword? on-error)
                           (lib.route/path router on-error {})
                           on-error)
               :redirect (if (keyword? on-success)
                           (lib.route/path router on-success {})
                           on-success)}}
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
                        leading-6
                        border-0
                        text-black
                        disabled:opacity-70
                        bg-neut-50
                        inter
                        focus:ring-inset
                        focus:ring-tealv-600
                        py-1.5
                        sm:text-lg
                        grow
                        rounded]}]
      [:div
       [:button {:type "submit"
                 :class '[g-recaptcha
                          px-5
                          py-2
                          bg-tealv-500
                          hover:bg-tealv-600
                          text-neut-50
                          inter
                          font-medium
                          leading-6
                          disabled:opacity-70
                          disabled:bg-tealv-500
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
        ;; TODO use router
        [:a.link {:href "/signin"} "Sign in"] ")"))
     [:.h-3])])

(defn base-text-input [{:keys [type textarea size] :as opts}]
  [(if textarea
     :textarea
     :input)
   (-> (merge (when-not textarea
                {:type "text"})
              {:id (:name opts)}
              opts)
       (dissoc :textarea :size)
       (update :class
               concat
               '[w-full
                 rounded-md
                 shadow-inner
                 leading-6
                 border-0
                 ring-1
                 ring-inset
                 ring-neut-100
                 text-black
                 disabled:opacity-70
                 bg-neut-50
                 inter]
               (if-not (= type "file")
                 '[focus:ring-inset
                   focus:ring-tealv-600
                   py-2]
                 '["file:py-[11.5px]"
                   file:px-3
                   file:border-0
                   file:bg-neut-100
                   focus:outline-tealv-600
                   file:text-neut-800
                   text-neut-600])
               (case size
                 :large '[max-sm:text-sm]
                 '[text-sm])))])

(defn overlay-text-input [{:keys [icon postfix size] :as opts}]
  [:.relative
   [:div {:class '[absolute
                   inset-0
                   flex
                   items-center
                   pointer-events-none]}
    (when icon
      (lib.icons/base icon {:class '[w-4
                                     h-4
                                     ml-4
                                     text-stone-600]}))
    [:.grow]
    [:div {:class (concat
                   '[h-full
                     text-neut-600
                     flex
                     items-center
                     p-3]
                   (case size
                     :large '[max-sm:text-sm]
                     '[text-sm]))}
     postfix]]
   (base-text-input (cond-> opts
                      true (dissoc :icon :postfix)
                      icon (update :class concat '[pl-10])))])

(defn input-label [opts & contents]
  [:label.block.mb-2 opts
   [:div {:class '[inter
                   font-medium
                   text-sm
                   text-neut-900
                   tracking-wide]}
    contents]])

(defn input-submit [{:keys [size] :as opts} & contents]
  [:button (-> {:type "submit"
                :class (concat
                        '[px-3
                          py-2
                          rounded-md
                          bg-neut-700
                          hover:bg-neut-800
                          text-neut-50
                          inter
                          font-medium
                          leading-6
                          disabled:opacity-70]
                        (case size
                          :large '[max-sm:text-sm]
                          '[text-sm]))}
               (update :class concat (:class opts))
               (merge (dissoc opts :class :size)))
   contents])

(defn uber-input [{:keys [id name label description error submit-opts submit-text
                          indicator-id size required] :as opts}]
  (let [opts (dissoc opts :label :description :error :submit-opts :submit-text)]
    [:div.w-full
     (when label
       (input-label
        {:for (or id name)
         :required required}
        [:.flex.items-center.gap-2
         label
         (when indicator-id
           [:img.h-5.htmx-indicator.hidden
            {:id indicator-id
             :src "/img/spinner2.gif"}])]))
     (if submit-text
       [:.flex.gap-3.w-full
        [:.grow (overlay-text-input opts)]
        [:.flex-shrink-0 (input-submit (assoc submit-opts :size size) submit-text)]]
       (overlay-text-input opts))
     (case size
       :large [:.h-1]
       nil)
     (some->> error input-error)
     (some->> description input-description)]))

(defn modal [{:keys [id title]} & content]
  [:div {:id id
         :class '[fixed
                  flex
                  flex-col
                  hidden
                  inset-0
                  items-center
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

(defn weserv [opts]
  (str (apply uri/assoc-query "https://images.weserv.nl/"
              (apply concat opts))))

(defn on-error-page [ctx]
  (biff/render [:h1 "TODO"]))
