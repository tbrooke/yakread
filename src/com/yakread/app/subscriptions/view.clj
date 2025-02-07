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

(defn bar-button-icon-label [icon text]
  [:.flex.justify-center
   (lib.icons/base icon
                   {:class '[w-5
                             h-5
                             flex-shrink-0
                             "mt-[2px]"]})
   [:.w-1.sm:w-2]
   [:span text]])

(def bar-button-classes '[hover:bg-neut-50
                          inter
                          py-2
                          w-full])

(defresolver like-button [{:keys [biff/router]} {:item/keys [id user-item]}]
  #::pco{:input [:item/id
                 {(? :item/user-item) [(? :user-item/favorited-at)]}]}
  {:item/like-button
   (let [active (boolean (:user-item/favorited-at user-item))]
     [:button {:hx-post (lib.route/path router :app.subscriptions.view.read/favorite
                          {:item-id (lib.serialize/uuid->url id)})
               :hx-swap "outerHTML"
               :class (into bar-button-classes
                            (when active
                              '[font-semibold]))}
      (bar-button-icon-label (if active
                               "star-solid"
                               "star-regular")
                             "Like")])})

(defresolver share-button [{:item/keys [url]}]
  {:item/share-button
   [:button {:data-url url
             :_ "on click
                 writeText(@data-url) on navigator.clipboard
                 toggle .hidden on .toggle in me
                 wait 1s
                 toggle .hidden on .toggle in me"
             :class bar-button-classes}
    [:span.toggle (bar-button-icon-label "share-regular" "Share")]
    [:span.toggle.hidden.text-gray-700 "Copied to clipboard."]]})

(defresolver button-bar [{:keys [biff/router]}
                         {:item/keys [id sub like-button share-button]}]
  #::pco{:input [:item/id
                 :item/like-button
                 (? :item/share-button)
                 {(? :item/sub) [:xt/id]}]}
  {:item/button-bar
   [:div {:class '[bg-white
                   flex
                   sticky
                   bottom-0
                   bg-neut-50]
          :style {:box-shadow "0 -4px 6px -1px rgb(0 0 0 / 0.1), 0 -2px 4px -2px rgb(0 0 0 / 0.1)"}}
    (when share-button
      [:.flex-1 share-button])
    ;;(cond
    ;;  (:item.email/reply-to item)
    ;;  [:.flex-1 (reply-button ctx)]

    ;;  (some (or item {}) [:item.rss/feed-url :item/inferred-feed-url])
    ;;  [:.flex-1 (subscribe-button ctx)])
    [:.flex-1 like-button]


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
      (for [[route-name label]
            (concat [[:app.subscriptions.view.read/mark-unread "Mark unread"]
                     [:app.subscriptions.view.read/not-interested "Not interested"]]
                    (when sub
                      [[:app.subscriptions.view.read/unsubscribe "Unsubscribe"]])
                     ;; TODO report
                     )]
        (biff/form
          {:hx-post (lib.route/path router route-name {:item-id (lib.serialize/uuid->url id)})}
          (lib.ui/overflow-button {:type "submit"} label)))]
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
               [:div {:_ (str "on load wait 100 ms then call resumePosition(me) then "
                              ;; TODO figure out how to not make this global
                              "set window.elt to me "
                              "js document.addEventListener('scroll', (e) => { "
                              "  savePosition(elt); "
                              "});")
                      :data-item-id id}
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
          (fn [{:keys [biff/router session path-params] :as ctx}
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

(defn- redirect-to-sub [router sub-id]
  (if sub-id
    (lib.route/path router :app.subscriptions.view/page
      {:sub-id (lib.serialize/uuid->url sub-id)})
    (lib.route/path router :app.subscriptions/page {})))

(def mark-unread-route
  ["/dev/sub-item/:item-id/mark-unread"
   {:name :app.subscriptions.view.read/mark-unread
    :post (lib.pipe/make
           :start (lib.pipe/pathom-query [{:session/user [:xt/id]}
                                          {:params/item [:xt/id
                                                         {(? :item/sub) [:xt/id]}]}]
                                         :end)
           :end (fn [{:keys [biff/router]
                      {:keys [session/user params/item]} :biff.pipe.pathom/output}]
                  {:status 204
                   :headers {"HX-Location"
                             (redirect-to-sub router (get-in item [:item/sub :xt/id]))}
                   :biff.pipe/next [:biff.pipe/tx]
                   :biff.pipe.tx/input [{:db/doc-type :user-item
                                         :db.op/upsert {:user-item/user (:xt/id user)
                                                        :user-item/item (:xt/id item)}
                                         :user-item/viewed-at :db/dissoc
                                         :user-item/favorited-at :db/dissoc
                                         :user-item/disliked-at :db/dissoc
                                         :user-item/reported-at :db/dissoc
                                         :user-item/report-reason :db/dissoc}]}))}])

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

(def not-interested-route
  ["/dev/sub-item/:item-id/not-interested"
   {:name :app.subscriptions.view.read/not-interested
    :post (lib.pipe/make
           :start (lib.pipe/pathom-query [{:params/item [{(? :item/sub) [:xt/id]}
                                                         {:item/user-item [:xt/id]}]}]
                                         :end)
           :end (fn [{:keys [biff/router]
                      {:keys [params/item]} :biff.pipe.pathom/output}]
                  (let [user-item (:item/user-item item)]
                    {:status 204
                     :headers {"HX-Location"
                               (redirect-to-sub router (get-in item [:item/sub :xt/id]))}
                     :biff.pipe/next [:biff.pipe/tx]
                     :biff.pipe.tx/retry false
                     :biff.pipe.tx/input [{:db/doc-type :user-item
                                           :db/op :update
                                           :xt/id (:xt/id user-item)
                                           :user-item/favorited-at :db/dissoc
                                           :user-item/disliked-at :db/now}]})))}])

(def unsubscribe-route
  ["/dev/sub-item/:item-id/unsubscribe"
   {:name :app.subscriptions.view.read/unsubscribe
    :post (lib.pipe/make
           :start (lib.pipe/pathom-query [{:params/item [{:item/sub [:sub/id
                                                                     :sub/doc-type]}]}]
                                         :end)
           :end (fn [{:keys [biff/router]
                      {{{:sub/keys [id doc-type]}
                        :item/sub}
                       :params/item}
                      :biff.pipe.pathom/output}]
                  {:status 204
                   :headers {"HX-Location"
                             (lib.route/path router :app.subscriptions/page {})}
                   :biff.pipe/next [:biff.pipe/tx]
                   :biff.pipe.tx/retry false
                   :biff.pipe.tx/input [(if (= doc-type :sub/email)
                                          ;; TODO actually unsubscribe from the mailing list
                                          {:db/doc-type :sub/email
                                           :db/op :update
                                           :xt/id id
                                           :sub.email/unsubscribed-at :db/now}
                                          [::xt/delete id])]}))}])

(def module
  {:routes [["" {:middleware [lib.middle/wrap-signed-in]}
             page-route
             page-content-route
             read-page-route
             read-content-route
             mark-read-route
             mark-unread-route
             favorite-route
             not-interested-route
             unsubscribe-route]]
   :resolvers [button-bar
               like-button
               share-button]})
