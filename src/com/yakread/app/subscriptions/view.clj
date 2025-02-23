(ns com.yakread.app.subscriptions.view
  (:require [clojure.string :as str]
            [cheshire.core :as cheshire]
            [com.biffweb :as biff :refer [<<-]]
            [com.wsscode.pathom3.connect.operation :as pco :refer [defresolver]]
            [com.yakread.lib.htmx :as lib.htmx]
            [com.yakread.lib.icons :as lib.icons]
            [com.yakread.lib.middleware :as lib.middle]
            [com.yakread.lib.pipeline :as lib.pipe]
            [com.yakread.lib.route :as lib.route :refer [defget defpost-pathom href ?]]
            [com.yakread.lib.serialize :as lib.serialize]
            [com.yakread.lib.ui :as ui]
            [com.yakread.routes :as routes]
            [com.yakread.model.subscription :as model.sub]
            [lambdaisland.uri :as uri]
            [xtdb.api :as xt]
            [rum.core :as rum]))

(defn- redirect-to-sub [sub-id]
  (if sub-id
    (href `page-route sub-id)
    (href routes/subs-page)))

(defpost-pathom mark-read
  [{:session/user [:xt/id]}
   {:params/item [:xt/id]}]
  (fn [_ {:keys [session/user params/item]}]
    {:status 200
     :biff.pipe/next [:biff.pipe/tx]
     :biff.pipe.tx/input [{:db/doc-type :user-item
                           :db.op/upsert {:user-item/user (:xt/id user)
                                          :user-item/item (:xt/id item)}
                           :user-item/viewed-at [:db/default :db/now]}]}))

(defpost-pathom mark-unread
  [{:session/user [:xt/id]}
   {:params/item [:xt/id
                  {(? :item/sub) [:xt/id]}]}]
  (fn [_ {:keys [session/user params/item]}]
    {:status 204
     :headers {"HX-Location" (redirect-to-sub (get-in item [:item/sub :xt/id]))}
     :biff.pipe/next [:biff.pipe/tx]
     :biff.pipe.tx/input [{:db/doc-type :user-item
                           :db.op/upsert {:user-item/user (:xt/id user)
                                          :user-item/item (:xt/id item)}
                           :user-item/viewed-at :db/dissoc
                           :user-item/favorited-at :db/dissoc
                           :user-item/disliked-at :db/dissoc
                           :user-item/reported-at :db/dissoc
                           :user-item/report-reason :db/dissoc
                           :user-item/skipped-at :db/dissoc}]}))

(defpost-pathom toggle-favorite
  [{:params/item
    [:item/like-button*
     {:item/user-item
      [:xt/id
       (? :user-item/favorited-at)]}]}]
  (fn [_ {:keys [params/item]}]
    (let [user-item (:item/user-item item)
          favorited (boolean (:user-item/favorited-at user-item))]
      {:status 200
       :headers {"Content-Type" "text/html"}
       :body ((:item/like-button* item) {:active (not favorited)})
       :biff.pipe/next [:biff.pipe/tx]
       :biff.pipe.tx/retry false
       :biff.pipe.tx/input [{:db/doc-type :user-item
                             :db/op :update
                             :xt/id (:xt/id user-item)
                             :user-item/favorited-at (if favorited :db/dissoc :db/now)
                             :user-item/disliked-at :db/dissoc
                             :user-item/reported-at :db/dissoc
                             :user-item/report-reason :db/dissoc}]})))

(defpost-pathom not-interested
  [{:params/item [{(? :item/sub) [:xt/id]}
                  {:item/user-item [:xt/id]}]}]
  (fn [_ {:keys [params/item]}]
    (let [user-item (:item/user-item item)]
      {:status 204
       :headers {"HX-Location" (redirect-to-sub (get-in item [:item/sub :xt/id]))}
       :biff.pipe/next [:biff.pipe/tx]
       :biff.pipe.tx/retry false
       :biff.pipe.tx/input [{:db/doc-type :user-item
                             :db/op :update
                             :xt/id (:xt/id user-item)
                             :user-item/favorited-at :db/dissoc
                             :user-item/disliked-at :db/now}]})))

(defpost-pathom mark-all-read
  [{:session/user [:xt/id]}
   {:params/sub [:sub/id
                 {:sub/items [:item/id
                              :item/unread
                              {(? :item/user-item) [:xt/id
                                                    (? :user-item/skipped-at)]}]}]}]
  (fn [_ {:keys [session/user params/sub]}]
    {:status 303
     :headers {"HX-Location" (href `page-route (:sub/id sub))}
     :biff.pipe/next [:biff.pipe/tx]
     :biff.pipe.tx/input
     (for [{:item/keys [id unread]} (:sub/items sub)
           :when unread]
       {:db/doc-type :user-item
        :db.op/upsert {:user-item/user (:xt/id user)
                       :user-item/item id}
        :user-item/skipped-at [:db/default :db/now]})}))

(defn bar-button-icon-label [icon text]
  [:.flex.justify-center
   (lib.icons/base icon
                   {:class '[w-5
                             h-5
                             flex-shrink-0
                             "mt-[2px]"]})
   [:.w-1.sm:w-2]
   [:span text]])

(defn bar-button [{:ui/keys [active icon] :keys [href] :as opts} & contents]
  [(if href :a :button) (ui/with-classes opts
                          '[block
                            hover:bg-neut-50
                            inter
                            py-2
                            w-full]
                          (when active
                            'font-semibold))
   (if icon
     (bar-button-icon-label icon contents)
     contents)])

(defresolver like-button* [{:item/keys [id]}]
  {:item/like-button*
   (fn [{:keys [active]}]
     (bar-button
      {:ui/active active
       :ui/icon (if active "star-solid" "star-regular")
       :hx-post (href toggle-favorite {:item/id id})
       :hx-swap "outerHTML"}
      "Like"))})

(defresolver like-button [{:item/keys [like-button* user-item]}]
  #::pco{:input [{(? :item/user-item) [(? :user-item/favorited-at)]}]}
  {:item/like-button
   (like-button* {:active (boolean (:user-item/favorited-at user-item))})})

(defresolver share-button [{:item/keys [url]}]
  {:item/share-button
   (bar-button
    {:data-url url
     :_ "on click
         writeText(@data-url) on navigator.clipboard
         toggle .hidden on .toggle in me
         wait 1s
         toggle .hidden on .toggle in me"}
    [:span.toggle (bar-button-icon-label "share-nodes-regular" "Share")]
    [:span.toggle.hidden.text-gray-700.text-sm "Copied to clipboard."])})

(defn query-encode [s]
  (some-> s
          (java.net.URLEncoder/encode "UTF-8")
          (str/replace "+" "%20")))

(defresolver button-bar [{:item/keys [id title sub like-button share-button]
                          :item.email/keys [reply-to]}]
  #::pco{:input [:item/id
                 :item/like-button
                 (? :item/title)
                 (? :item/share-button)
                 (? :item.email/reply-to)
                 {(? :item/sub) [:sub/id :sub/title]}]}
  {:item/button-bar
   [:div {:class '[bg-white
                   flex
                   sticky
                   bottom-0
                   bg-neut-50]
          :style {:box-shadow "0 -4px 6px -1px rgb(0 0 0 / 0.1), 0 -2px 4px -2px rgb(0 0 0 / 0.1)"}}
    (when share-button
      [:.flex-1 share-button])
    (when reply-to
      [:.flex-1
       (bar-button
        {:ui/icon "reply-regular"
         :href (str "mailto:" (query-encode reply-to) "?subject=" (query-encode (str "Re: " title)))}
        "Reply")])

    ;;(cond
    ;;  (some (or item {}) [:item.rss/feed-url :item/inferred-feed-url])
    ;;  [:.flex-1 (subscribe-button ctx)])
    [:.flex-1 like-button]


    (ui/overflow-menu
     {:ui/direction :up}
     (ui/overflow-button
      {:hx-post (href mark-unread {:item/id id})}
      "Mark unread")
     (ui/overflow-button
      {:hx-post (href not-interested {:item/id id})}
      "Not interested")
     (when sub
       (ui/overflow-button
        {:hx-post (href routes/unsubscribe! {:sub/id (:sub/id sub)})
         :hx-confirm (ui/confirm-unsub-msg (:sub/title sub))}
        "Unsubscribe"))
     ;; TODO report button
     )]})

(defget read-content-route "/dev/sub-item/:item-id/content"
  [{(? :params/item) [:item/id
                      :item/doc-type
                      (? :item/url)
                      (? :item/title)
                      (? :item/clean-html)
                      (? :item/details)
                      {:item/sub [:sub/id
                                  :sub/title]}
                      :item/button-bar]}]
  (fn [_ {{:item/keys [id url details doc-type title sub clean-html button-bar]
           :as item} :params/item}]
    (when item
      [:<>
       [:div {:hx-post (href mark-read {:item/id id})
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
       (ui/page-header {:title     (:sub/title sub)
                        :back-href (href routes/subs-page)})
       [:div#content (ui/lazy-load-spaced (href `page-content-route (:sub/id sub)))]])))

(defget read-page-route "/dev/sub-item/:item-id"
  [:app.shell/app-shell
   {(? :params/item) [:item/id
                      :item/title
                      {:item/sub [:sub/id
                                  :sub/title]}]}]
  (fn [_ {:keys [app.shell/app-shell]
          {:item/keys [id title sub content] :as item} :params/item}]
    (if (nil? item)
      {:status 303
       :headers {"Location" (href routes/subs-page)}}
      (app-shell
       {:title title}
       (ui/lazy-load-spaced (href read-content-route id))))))

(defn- clean-string [s]
  (str/replace (apply str (remove #{\newline
                                    (char 65279)
                                    (char 847)
                                    (char 8199)}
                                  s))
               #"\s+"
               " "))

(defget page-content-route "/dev/subscription/:sub-id/content"
  [{:params/sub [:sub/id
                 :sub/title
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
  (fn [_ {:keys [app.shell/app-shell]
          {:keys [user/use-original-links]} :user/current
          {:sub/keys [id title items]} :params/sub}]
    [:<>
     [:.flex.gap-4
      {:class '["-mt-4" mb-8]}
      (ui/button {:ui/type :secondary
                  :ui/size :small
                  :hx-post (href mark-all-read {:sub/id id})}
        "Mark all as read")
      (ui/button {:ui/type :secondary
                  :ui/size :small
                  :hx-post (href routes/unsubscribe! {:sub/id id})
                  :hx-confirm (ui/confirm-unsub-msg title)}
        "Unsubscribe")]
     [:div {:class '[flex
                     flex-col
                     gap-6
                     max-w-screen-sm]}
      (for [{:item/keys [id details title excerpt unread image-url url] :as item}
            (sort-by :item/published-at #(compare %2 %1) items)]
        [:a (if (and use-original-links url)
              {:href url :target "_blank"}
              {:href (href read-page-route id)})
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
              [:img {:src (ui/weserv {:url image-url
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
                              rounded]}]])]]])]]))

(defget page-route "/dev/subscription/:sub-id"
  [:app.shell/app-shell
   {:params/sub [:sub/id
                 :sub/title]}]
  (fn [_ {:keys [app.shell/app-shell]
          {:sub/keys [id title]} :params/sub}]
    (app-shell
     {:title title}
     (ui/page-header {:title     title
                      :back-href (href routes/subs-page)})
     [:div#content (ui/lazy-load-spaced (href page-content-route id))])))

(def module
  {:routes [["" {:middleware [lib.middle/wrap-signed-in]}
             page-route
             page-content-route
             read-page-route
             read-content-route
             mark-read
             mark-unread
             toggle-favorite
             not-interested
             mark-all-read]]
   :resolvers [button-bar
               like-button*
               like-button
               share-button]})
