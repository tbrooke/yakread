(ns com.yakread.app.subscriptions.view
  (:require [clojure.string :as str]
            [com.biffweb :as biff :refer [<<-]]
            [com.yakread.lib.middleware :as lib.middle]
            [com.yakread.lib.pathom :as lib.pathom :refer [?]]
            [com.yakread.lib.pipeline :as lib.pipe]
            [com.yakread.lib.route :as lib.route]
            [com.yakread.lib.serialize :as lib.serialize]
            [com.yakread.lib.ui :as lib.ui]
            [com.yakread.model.subscription :as model.sub]
            [lambdaisland.uri :as uri]
            [xtdb.api :as xt]))

;; TODO finish
(def read-content-route
  ["/dev/sub-item/:item-id/content"
   {:name :app.subscriptions.view.read/content
    :get (lib.pathom/handler
          [{(? :params/item) [:item/doc-type
                              (? :item/url)
                              (? :item/title)
                              (? :item/clean-html)
                              {:item/sub [:sub/id
                                          :sub/title]}]}]
          (fn [{:keys [biff/router]}
               {{:item/keys [url doc-type title sub clean-html]} :params/item}]
            [:<>
             [:div #_{:_ (str "on load wait 200 ms then call window.scrollTo(0, " position ")")}
              #_(biff/form {:class "hidden"
                            :id "save-position"
                            :hx-post (util/make-url base-url "save-position")
                            :hx-trigger "save-position"
                            :hidden {:position position
                                     :item (:xt/id item)}})
              [:div {:class '[text-sm
                              max-sm:mx-4
                              text-neut-800
                              flex
                              justify-between
                              gap-6]}
               ;[:div details]
               (when url
                 [:a {:class '[underline
                               whitespace-nowrap
                               max-sm:hidden]
                      :href url :target "_blank"}
                  "View original"])]
              [:.h-1]
              [:h1.font-bold.text-2xl.max-sm:mx-4.text-neut-900 title]
              (when url
                [:a.underline.whitespace-nowrap.sm:hidden.max-sm:mx-4.text-sm.text-neut-800
                 {:class '[underline
                           whitespace-nowrap
                           sm:hidden
                           max-sm:mx-4
                           text-sm
                           text-neut-800]
                  :href url
                  :target "_blank"}
                 "View original"])
              [:.h-6]
              [:.px-4.bg-white.contain-content.overflow-hidden
               [:div {:id "post-content"
                      :data-contents clean-html
                      :_ "on load call renderPost()"
                      :class (if (= doc-type :item/email)
                               '[py-4]
                               '[prose-lg
                                 prose-quoteless
                                 prose-neut-900
                                 prose-a:text-tealv-600
                                 hover:prose-a:underline
                                 prose-blockquote:border-l-tealv-500
                                 prose-h1:text-3xl
                                 lg:prose-h1:text-4xl])}]]
              ;(button-bar ctx)
              ]

             (lib.ui/page-header {:title     (:sub/title sub)
                                  :back-href (lib.route/path router :app.subscriptions/page {})})
             [:div#content (lib.ui/lazy-load-spaced router
                                                    :app.subscriptions.view.page/content
                                                    {:sub-id (lib.serialize/uuid->url (:sub/id sub))})]]))}])

(def read-page-route
  ["/dev/sub-item/:item-id"
   {:name :app.subscriptions.view/read
    :get (lib.pathom/handler
          [:app.shell/app-shell
           {(? :params/item) [:xt/id
                              :item/title
                              {:item/sub [:sub/id
                                          :sub/title]}]}]
          (fn [{:keys [biff/router session path-params]}
               {:keys [app.shell/app-shell]
                {:item/keys [title sub content] :as item} :params/item}]
            (if (nil? item)
              {:status 303
               :biff.router/name :app.subscriptions/page}
              (app-shell
               {:title title}
               (lib.ui/lazy-load-spaced router :app.subscriptions.view.read/content path-params)))))}])

(defn- clean-string [s]
  (str/replace (apply str (remove #{\newline
                                    (char 65279)
                                    (char 847)
                                    (char 8199)}
                                  s))
               #"\s+"
               " "))

(defn- item-byline [{:item/keys [byline
                                 author-name
                                 site-name
                                 url]}]
  (some->> [(or author-name byline)
            (or site-name
                (when url
                  (:host (uri/uri url))))]
           (keep #(some-> % str/trim not-empty))
           not-empty
           (str/join " | ")))

(defn- fancy-format-date [date]
  (let [date (java.util.Date/from date)
        date-fmt (if (apply = (map #(biff/crop-date % "yyyy") [date (java.util.Date.)]))
                   "d MMM"
                   "d MMM yyyy")]
    (biff/format-date date date-fmt)))

(defn- pluralize [n label]
  (str n " " label (when (not= 1 n) "s")))

(def ^:private interpunct " · ")

(defn- reading-minutes [n-characters]
  (max 1 (Math/round (/ n-characters 900.0))))

(defn- item-details [{:item/keys [url
                                  published-at
                                  fetched-at
                                  author-name
                                  byline
                                  site-name
                                  length]
                      :keys [item.extra/type
                             item.extra/date]
                      :as item}]
  (->> [(item-byline item)
        (fancy-format-date (or date published-at fetched-at))
        (when length
          (pluralize (reading-minutes length) "minute"))
        (when-some [label ({:bookmark "Bookmarked"
                            :subscription "Subscribed"
                            :new-subscription "New subscription"
                            :ad "Ad"
                            :discover "Discover"
                            :current "Continue reading"} type)]
          [:span.underline label])]
       (filter some?)
       (map #(vector :span.inline-block %))
       (biff/join interpunct)))

(def page-content-route
  ["/dev/subscription/:sub-id/content"
   {:name :app.subscriptions.view.page/content
    :get (lib.pathom/handler
          [{(? :params/sub) [:sub/id
                             {:sub/items
                              [:item/id
                               :item/unread
                               (? :item/title)
                               ;(? :item/image-with-default)
                               (? :item/author-name)
                               (? :item/byline)
                               (? :item/excerpt)
                               (? :item/fetched-at)
                               (? :item/length)
                               (? :item/published-at)
                               (? :item/site-name)
                               (? :item/url)]}]}
           ;{(? :params/item) [:xt/id
           ;                   :item/title]}
           {:user/current [(? :user/use-original-links)]}]
          (fn [{:keys [biff/router session path-params]}
               {:keys [app.shell/app-shell]
                current-item :params/item
                {:keys [user/use-original-links]} :user/current
                {:sub/keys [id items]} :params/sub}]
            (if-not id
              {:status 404 :body ""}
              [:<>
               [:div {:class '[flex
                               flex-col
                               gap-6
                               max-w-screen-sm
                               "max-sm:-mx-4"]}
                (for [{:item/keys [id title excerpt unread image-with-default url] :as item} items]
                  [:a (if (and use-original-links url)
                        {:href url :target "_blank"}
                        {:href (lib.route/path router :app.subscriptions.view/read
                                 {:item-id (lib.serialize/uuid->url id)})})
                   [:div {:class (concat '[bg-white
                                           hover:bg-neut-50
                                           p-4
                                           sm:shadow]
                                         (when unread
                                           '[max-sm:border-t-4
                                             sm:border-l-4
                                             border-tealv-500]))}
                    [:.text-neut-600.text-sm.line-clamp-2
                     (item-details item)]
                    [:.h-1]
                    [:h3 {:class '[font-bold
                                   text-xl
                                   text-neut-800
                                   leading-tight
                                   line-clamp-2]}
                     title]
                    [:.h-2]
                    [:.flex.gap-3.justify-between
                     [:div
                      (when (not= excerpt "Read more")
                        [:.line-clamp-4.text-neut-600.mb-1
                         {:style {:overflow-wrap "anywhere"}}
                         (clean-string excerpt)])
                      [:div {:class '[text-tealv-600
                                      font-semibold
                                      hover:underline
                                      inline-block]}
                       "Read more."]]
                     (when image-with-default
                       [:.relative.flex-shrink-0
                        [:img {:src (lib.ui/weserv {:url image-with-default
                                                    :w 150
                                                    :h 150
                                                    :fit "cover"
                                                    :a "attention"})
                               :_ "on error remove me"
                               :class '[rounded
                                        object-cover
                                        object-center
                                        "mt-[6px]"
                                        "w-[5.5rem]"
                                        "h-[5.5rem]"]}]
                        [:div {:style {:box-shadow "inset 0 0px 6px 1px #0000000d"}
                               :class '[absolute
                                        inset-x-0
                                        "top-[6px]"
                                        "h-[5.5rem]"
                                        rounded]}]])]]])]])))}])

(def page-route
  ["/dev/subscription/:sub-id"
   {:name :app.subscriptions.view/page
    :get (lib.pathom/handler
          [:app.shell/app-shell
           {(? :params/sub) [:sub/title]}]
          (fn [{:keys [biff/router session path-params]}
               {:keys [app.shell/app-shell]
                {:sub/keys [title]} :params/sub}]
            (if (nil? title)
              {:status 303
               :biff.router/name :app.subscriptions/page}
              (app-shell
               {:title title}
               (lib.ui/page-header {:title     title
                                    :back-href (lib.route/path router :app.subscriptions/page {})})
               [:div#content (lib.ui/lazy-load-spaced router :app.subscriptions.view.page/content path-params)]))))}])

(def module
  {:routes [["" {:middleware [lib.middle/wrap-signed-in]}
             page-route
             page-content-route
             read-page-route
             read-content-route]]})
