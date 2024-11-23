(ns com.yakread.app.subscriptions
  (:require [cheshire.core :as cheshire]
            [clojure.set :as set]
            [clojure.edn :as edn]
            [com.biffweb :as biff]
            [com.wsscode.pathom3.connect.operation :as pco]
            [com.yakread.lib.htmx :as lib.htmx]
            [com.yakread.lib.middleware :as lib.middle]
            [com.yakread.lib.pathom :as lib.pathom :refer [?]]
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

(def schema
  {:biff.attr/qualified-key qualified-keyword?
   :biff.attr/schema        any?
   :biff.attr/input         vector?
   :biff.attr/resolver      ifn?
   :biff.attr/rum           vector?
   :biff.attr/ref           [:map [:xt/id any?]]
   :biff/attr               map?
   :biff.route/path         :string
   :biff.index/get-doc      fn?
   :biff.index/doc          :biff.attr/ref
   :biff.index/op           [:enum ::xt/put ::xt/delete]
   :biff.index/args         [:vector [:map :biff.index/doc :biff.index/op]]
   :ui.page/attr            map?
   :ui.page/title           :string
   :ui.page/icon            :string})

(defn- card [{:sub/keys [title last-published kind newsletter feed unread pinned] :as sub}
             {:keys [pin-url view-sub-url]}]
  (let [ident (select-keys sub [:sub/newsletter :sub/conn-id])]
    [:.relative
     [:div {:class '[absolute
                     top-1.5
                     right-0]}
      (lib.ui/overflow-menu
       {:icon "ellipsis-vertical-regular"}
       (let [[hx-method label] (if pinned
                                 [:hx-delete "Unpin"]
                                 [:hx-put "Pin"])]
         (lib.ui/overflow-button
          {hx-method pin-url
           :hx-vals (lib.htmx/edn-hx-vals ident)
           :hx-target "#content"
           :hx-on:htmx:before-request "this.closest('.sub-card').remove()"}
          label)))]
     [:a {:href (view-sub-url ident)
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
      [:.text-neut-800.mr-6 unread " unread posts"]]]))

(defn- grid [{:keys [id] :as opts} subscriptions]
  (let [cnt (count subscriptions)]
    [:div {:id id
           :class '[grid
                    grid-cols-1
                    sm:grid-cols-2
                    lg:grid-cols-3
                    xl:grid-cols-4
                    "2xl:grid-cols-5"
                    gap-4]}
     (for [[i sub] (map-indexed vector subscriptions)]
       [:.sub-card {:style {:z-index (- (+ 10 cnt) i)}}
        (card sub opts)])]))

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
          [{:user/current [{:user/subscriptions
                            [:sub/title
                             :sub/kind
                             :sub/unread
                             :sub/last-published
                             :sub/pinned
                             (? :sub/newsletter)
                             (? :sub/feed)
                             (? :sub/conn-id)]}]}]
          (fn [{:keys [biff/router]}
               {{:user/keys [subscriptions]} :user/current}]
            (let [{pinned-subs true unpinned-subs false} (group-by :sub/pinned subscriptions)
                  pin-url (lib.route/path router :app.subscriptions.page/pin {})
                  grid-opts (fn [id]
                              {:id id
                               :pin-url pin-url
                               :view-sub-url (fn [entity]
                                               (lib.route/path router
                                                 :app.subscriptions.view/page
                                                 {:entity (lib.serialize/edn->base64 entity)}))})]
              (if (empty? subscriptions)
                (empty-state router)
                [:div.grow.flex.flex-col
                 (grid (grid-opts "pinned") pinned-subs)
                 (when (every? not-empty [pinned-subs unpinned-subs])
                   [:div#sub-divider {:class '[my-10
                                               border
                                               border-neut-200]}])
                 (grid (grid-opts "subscriptions") unpinned-subs)]))))}])


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
   (let [handler (lib.pipe/make
                  :start
                  (fn [{:keys [session params request-method]}]
                    (let [op (case request-method
                               :put :db/union
                               :delete :db/difference)
                          tx [(merge {:db/doc-type :pinned
                                      :db.op/upsert {:pinned/user (:uid session)}}
                                     (if (:sub/newsletter params)
                                       {:pinned/newsletters [op (:sub/newsletter params)]}
                                       {:pinned/rss [op (:sub/conn-id params)]}))]]
                      {:biff.pipe/next [:biff.pipe/tx :biff.pipe/render]
                       :biff.pipe.tx/input tx
                       :biff.pipe.render/route-name :app.subscriptions.page/content})))]
     {:name :app.subscriptions.page/pin
      :put handler
      :delete handler})])

(def module
  {:routes [page-route
            ["" {:middleware [lib.middle/wrap-signed-in]}
             page-content-route
             pin-route]]})

;; TODO
;; - sub page
;; - item page
;; - use tabs for pinned / not pinned... or something
