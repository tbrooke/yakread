(ns com.yakread.app.subscriptions
  (:require [cheshire.core :as cheshire]
            [clojure.set :as set]
            [clojure.edn :as edn]
            [com.biffweb :as biff]
            [com.wsscode.pathom3.connect.operation :as pco :refer [defresolver ?]]
            [com.yakread.lib.htmx :as lib.htmx]
            [com.yakread.lib.middleware :as lib.middle]
            [com.yakread.lib.pathom :as lib.pathom]
            [com.yakread.lib.pipeline :as lib.pipe]
            [com.yakread.lib.route :as lib.route]
            [com.yakread.lib.serialize :as lib.serialize]
            [com.yakread.lib.ui :as lib.ui]
            [com.yakread.middleware :as mid]
            [com.yakread.util :as util]
            [xtdb.api :as xt]
            [taoensso.tufte :as tufte]
            [clojure.tools.logging :as log])
  (:import [java.time Instant]))

(defresolver sub-card [{:keys [biff/router]}
                       {:sub/keys [id title unread published-at pinned-at]}]
  #::pco{:input [:sub/id
                 :sub/title
                 :sub/unread
                 (? :sub/published-at)
                 (? :sub/pinned-at)]}
  {:sub.view/card
   [:.relative
    [:div {:class '[absolute
                    top-1.5
                    right-0]}
     (lib.ui/overflow-menu
      {:icon "ellipsis-vertical-regular"}
      (let [[hx-method label] (if pinned-at
                                [:hx-delete "Unpin"]
                                [:hx-put "Pin"])]
        (lib.ui/overflow-button
         {hx-method (lib.route/path router :app.subscriptions.page/pin {})
          :hx-vals (lib.htmx/edn-hx-vals {:sub/id id})
          :hx-target "#content"
          :hx-on:htmx:before-request "this.closest('.sub-card').remove()"}
         label)))]
    [:a {:href (lib.route/path router :app.subscriptions.view/page {:sub-id (lib.serialize/uuid->url id)})
         :class (concat '[block
                          bg-white
                          shadow
                          p-2
                          text-sm
                          hover:bg-neut-50]
                        (when (< 0 unread)
                          '[border-l-4
                            border-tealv-500]))}
     [:.truncate.font-semibold.mr-6 (or (not-empty title) "[no title]")]
     [:.text-neut-800.mr-6 unread " unread posts"]]]})

(defn- empty-state [router]
  (lib.ui/empty-page-state {:icons ["envelope-regular-sharp"
                                    "square-rss-regular-sharp"]
                            :text [:span {:class '["max-w-[22rem]"
                                                   inline-block]}
                                   "Customize your experience by subscribing to newsletters and RSS feeds."]
                            :btn-label "Add subscriptions"
                            :btn-href (lib.route/path router :app.subscriptions.add/page {})}))

(def page-content-route
  ["/dev/subscriptions/content"
   {:name :app.subscriptions.page/content
    :get (lib.pathom/handler
          [{:user/current [{:sub/_user
                            [:xt/id
                             :sub.view/card
                             (? :sub/published-at)
                             (? :sub/pinned-at)]}]}]
          (fn [{:keys [biff/router]}
               {{subscriptions :sub/_user} :user/current}]
            (let [{pinned-subs true unpinned-subs false} (group-by (comp some? :sub/pinned-at) subscriptions)]
              (if (empty? subscriptions)
                (empty-state router)
                [:div.grow.flex.flex-col
                 (biff/join [:div#sub-divider {:class '[my-10
                                                        border
                                                        border-neut-200]}]
                            (for [[id subscriptions] [["pinned" pinned-subs]
                                                      ["unpinned" unpinned-subs]]
                                  :when (not-empty subscriptions)
                                  :let [cnt (count subscriptions)]]
                              [:div {:id id
                                     :class '[grid
                                              grid-cols-1
                                              sm:grid-cols-2
                                              lg:grid-cols-3
                                              xl:grid-cols-4
                                              "2xl:grid-cols-5"
                                              gap-4]}
                               (for [[i sub] (->> subscriptions
                                                  (sort-by :sub/published-at #(compare %2 %1))
                                                  (map-indexed vector))]
                                 [:.sub-card {:style {:z-index (- (+ 10 cnt) i)}}
                                  (:sub.view/card sub)])]))]))))}])

(def page-route
  ["/dev/subscriptions"
   {:name :app.subscriptions/page
    :get (lib.pathom/handler
          [:app.shell/app-shell (? :user/current)]
          (fn [{:keys [biff/router]}
               {:keys [app.shell/app-shell] user :user/current}]
            (app-shell
             {:wide true}
             (lib.ui/page-header {:title    "Subscriptions"
                                  :add-href (lib.route/path router :app.subscriptions.add/page {})})
             (if user
               [:div#content (lib.ui/lazy-load-spaced router :app.subscriptions.page/content {})]
               (empty-state router)))))}])

(def pin-route
  ["/dev/subscriptions/pin"
   (let [handler (fn [new-value]
                   (lib.pipe/make
                    :start
                    (fn [{{:keys [sub/id]} :params}]
                      {:biff.pipe/next              [:biff.pipe/tx :biff.pipe/render]
                       :biff.pipe.tx/input          [{:db/doc-type   :sub/any
                                                      :db/op         :update
                                                      :xt/id         id
                                                      :sub/pinned-at new-value}]
                       :biff.pipe.render/route-name :app.subscriptions.page/content})))]
     {:name   :app.subscriptions.page/pin
      :put    (handler :db/now)
      :delete (handler :db/dissoc)})])

(def module
  {:resolvers [sub-card]
   :routes [page-route
            ["" {:middleware [lib.middle/wrap-signed-in]}
             page-content-route
             pin-route]]})
