(ns com.yakread.app.subscriptions.view
  (:require [clojure.string :as str]
            [com.biffweb :as biff :refer [<<-]]
            [com.wsscode.pathom3.connect.operation :as pco :refer [defresolver]]
            [com.yakread.lib.htmx :as lib.htmx]
            [com.yakread.lib.icons :as lib.icons]
            [com.yakread.lib.middleware :as lib.middle]
            [com.yakread.lib.pathom :as lib.pathom :refer [?]]
            [com.yakread.lib.pipeline :as lib.pipe]
            [com.yakread.lib.route :as lib.route]
            [com.yakread.lib.serialize :as lib.serialize]
            [com.yakread.lib.ui :as lib.ui]
            [com.yakread.model.subscription :as model.sub]
            [lambdaisland.uri :as uri]
            [xtdb.api :as xt]
            [rum.core :as rum]))

(defresolver like-button [{:keys [biff/router]} {:item/keys [id user-item]}]
  #::pco{:input [:item/id
                 {(? :item/user-item) [(? :user-item/favorited-at)]}]}
  {:item/like-button
   (let [active (boolean (:user-item/favorited-at user-item))
         active-icon "star-solid"
         inactive-icon "star-regular"
         text "Like"]
     [:button {:hx-post (lib.route/path router :app.subscriptions.view.read/favorite
                          {:item-id (lib.serialize/uuid->url id)})
               :hx-swap "outerHTML"
               :class (concat '[flex
                                hover:bg-neut-50
                                inter
                                justify-center
                                py-2
                                w-full]
                              (when active
                                '[font-semibold]))}
      (lib.icons/base (if active active-icon inactive-icon)
                      {:class '[w-5
                                h-5
                                flex-shrink-0
                                "mt-[2px]"]})
      [:.w-1.sm:w-2]
      text])})

(defresolver button-bar [{:keys [biff/router]}
                         {:item/keys [id like-button]}]
  {:item/button-bar
   [:div {:class '[bg-white
                   flex
                   sticky
                   bottom-0
                   bg-neut-50]
          :style {:box-shadow "0 -4px 6px -1px rgb(0 0 0 / 0.1), 0 -2px 4px -2px rgb(0 0 0 / 0.1)"}}
    #_[:.flex-1 "reply button" #_(like-button ctx)]
    #_[:.flex-1 "subscribe button" #_(like-button ctx)]
    [:.flex-1 "share button" #_(like-button ctx)]
    [:.flex-1 like-button]

    ;;(when (:item/url item)
    ;;  [:.flex-1 (share-button ctx)])
    ;;(cond
    ;;  (:item.email/reply-to item)
    ;;  [:.flex-1 (reply-button ctx)]

    ;;  (some (or item {}) [:item.rss/feed-url :item/inferred-feed-url])
    ;;  [:.flex-1 (subscribe-button ctx)])


    [:.relative.flex.items-center
     {:class '["translate-y-[-100%]"]}
     [:div#button-bar-dropdown.dropdown
      {:class '[absolute
                bg-white
                border
                bottom-0
                hidden
                mb-2
                py-1
                right-0
                rounded
                rounded
                shadow-uniform]}
      [:.flex-1 "mark unread" #_(like-button ctx)]
      [:.flex-1 "not interested" #_(like-button ctx)]
      #_[:.flex-1 "unsubscribe" #_(like-button ctx)]

      #_(overflow-button (merge ctx {::text "Mark unread"
                                   ::endpoint "mark-unread"}))
      #_(overflow-button (merge ctx {::text "Not interested"
                                   ::endpoint "dislike"}))
      #_(unsubscribe-button ctx)
      #_(ui/overflow-button
       {:onclick "showModal(this)"
        :data-url (str "/for-you/report?item=" (:xt/id item))}
       "Report")]
     [:button.flex.p-2.hover:bg-neut-50.flex-none.h-full.translate-y-full
      {:_ "on click toggle .hidden on #button-bar-dropdown then halt"}
      (lib.icons/base "ellipsis-vertical-regular"
                      {:class '[w-8
                                h-5
                                flex-shrink-0
                                "mt-[2px]"]})]]]})

(def read-content-route
  ["/dev/sub-item/:item-id/content"
   {:name :app.subscriptions.view.read/content
    :get (lib.pathom/handler
          [{(? :params/item) [:item/id
                              :item/doc-type
                              (? :item/url)
                              (? :item/title)
                              (? :item/clean-html)
                              (? :item/details)
                              {:item/sub [:sub/id
                                          :sub/title]}
                              :item/button-bar]}]
          (fn [{:keys [biff/router]}
               {{:item/keys [id url details doc-type title sub clean-html button-bar]
                 :as item} :params/item}]
            (when item
              [:<>
               [:div {:hx-post (lib.route/path router :app.subscriptions.view.read/mark-read
                                 {:item-id (lib.serialize/uuid->url id)})
                      :hx-trigger "load"
                      :hx-swap "outerHTML"}]
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
                 [:div details]
                 (when url
                   [:a {:class '[underline
                                 whitespace-nowrap
                                 max-sm:hidden]
                        :href url :target "_blank"}
                    "View original"])]
                [:.h-1]
                [:h1.font-bold.text-2xl.max-sm:mx-4.text-neut-900 title]
                (when url
                  [:a {:class '[max-sm:mx-4
                                sm:hidden
                                text-neut-800
                                text-sm
                                underline
                                whitespace-nowrap]
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
                                 '[hover:prose-a:underline
                                   lg:prose-h1:text-4xl
                                   prose-a:text-tealv-600
                                   prose-blockquote:border-l-tealv-500
                                   prose-h1:text-3xl
                                   prose-lg
                                   prose-neut-900
                                   prose-quoteless
                                   py-4])}]]
                button-bar]

               [:div.h-10]
               (lib.ui/page-header {:title     (:sub/title sub)
                                    :back-href (lib.route/path router :app.subscriptions/page {})})
               [:div#content (lib.ui/lazy-load-spaced router
                                                      :app.subscriptions.view.page/content
                                                      {:sub-id (lib.serialize/uuid->url (:sub/id sub))})]])))}])

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

(def page-content-route
  ["/dev/subscription/:sub-id/content"
   {:name :app.subscriptions.view.page/content
    :get (lib.pathom/handler
          [{(? :params/sub) [:sub/id
                             {:sub/items
                              [:item/id
                               :item/unread
                               :item/details
                               (? :item/title)
                               (? :item/image-url)
                               (? :item/author-name)
                               (? :item/byline)
                               (? :item/excerpt)
                               (? :item/fetched-at)
                               (? :item/length)
                               (? :item/published-at)
                               (? :item/site-name)
                               (? :item/url)]}]}
           {:user/current [(? :user/use-original-links)]}]
          (fn [{:keys [biff/router session path-params]}
               {:keys [app.shell/app-shell]
                {:keys [user/use-original-links]} :user/current
                {:sub/keys [id items]} :params/sub}]
            (if-not id
              {:status 404 :body ""}
              [:<>
               [:div {:class '[flex
                               flex-col
                               gap-6
                               max-w-screen-sm]}
                (for [{:item/keys [id details title excerpt unread image-url url] :as item}
                      (sort-by :item/published-at #(compare %2 %1) items)]
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
                     details]
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
                     (when image-url
                       [:.relative.flex-shrink-0
                        [:img {:src (lib.ui/weserv {:url image-url
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

(def mark-read-route
  ["/dev/sub-item/:item-id/mark-read"
   {:name :app.subscriptions.view.read/mark-read
    :post (lib.pipe/make
           :start (lib.pipe/pathom-query [{:session/user [:xt/id]}
                                          {:params/item [:xt/id]}]
                                         :end)
           :end (fn [{{:keys [session/user params/item]} :biff.pipe.pathom/output}]
                  {:status 200
                   :biff.pipe/next [:biff.pipe/tx]
                   :biff.pipe.tx/input [{:db/doc-type :user-item
                                         :db.op/upsert {:user-item/user (:xt/id user)
                                                        :user-item/item (:xt/id item)}
                                         :user-item/viewed-at [:db/default :db/now]}]}))}])

(def favorite-route
  ["/dev/sub-item/:item-id/favorite"
   {:name :app.subscriptions.view.read/favorite
    :post (lib.pipe/make
           :start (lib.pipe/pathom-query [{:params/item
                                           [:item/id
                                            {:item/user-item
                                             [:xt/id
                                              (? :user-item/favorited-at)]}]}]
                                         :end)
           :end (fn [{:keys [biff/router]
                      {:keys [params/item]} :biff.pipe.pathom/output}]
                  (let [user-item (:item/user-item item)]
                    {:status 200
                     :headers {"Content-Type" "text/html"}
                     :body (rum/render-static-markup
                            (:item/like-button
                             (like-button {:biff/router router}
                                          (update-in item [:item/user-item :user-item/favorited-at] not))))
                     :biff.pipe/next [:biff.pipe/tx]
                     :biff.pipe.tx/retry false
                     :biff.pipe.tx/input [{:db/doc-type :user-item
                                           :db/op :update
                                           :xt/id (:xt/id user-item)
                                           :user-item/favorited-at (if (:user-item/favorited-at user-item)
                                                                     :db/dissoc
                                                                     :db/now)
                                           :user-item/disliked-at :db/dissoc
                                           :user-item/reported-at :db/dissoc
                                           :user-item/report-reason :db/dissoc}]})))}])

(def module
  {:routes [["" {:middleware [lib.middle/wrap-signed-in]}
             page-route
             page-content-route
             read-page-route
             read-content-route
             mark-read-route
             favorite-route]]
   :resolvers [button-bar
               like-button]})
