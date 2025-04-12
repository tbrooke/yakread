(ns com.yakread.app.for-you
  (:require [com.biffweb :as biff]
            [clojure.set :as set]
            [com.yakread.routes :as routes]
            [com.yakread.lib.content :as lib.content]
            [com.yakread.lib.middleware :as lib.mid]
            [com.yakread.lib.pipeline :as lib.pipe]
            [com.yakread.lib.route :as lib.route :refer [defget defpost-pathom href ?]]
            [com.yakread.lib.ui :as ui]
            [com.yakread.util.biff-staging :as biffs]
            [ring.middleware.anti-forgery :as csrf]))

(defpost-pathom record-click
  [{:session/user [:xt/id]}
   {:params/item [:xt/id]}]
  (fn [{:keys [biff/db params]} {:keys [session/user params/item]}]
    (let [existing-skip (biff/lookup db :skip/user (:xt/id user) :skip/timeline-created-at (:t params))]
      {:status 204
       :biff.pipe/next [:biff.pipe/tx]
       :biff.pipe.tx/input (concat
                            (when (:t params)
                              [(if existing-skip
                                 (let [clicked (conj (:skip/clicked existing-skip) (:xt/id item))]
                                   {:db/doc-type :skip
                                    :db/op :update
                                    :xt/id (:xt/id existing-skip)
                                    :skip/items (-> (:skip/items existing-skip)
                                                    (set/union (:skip params))
                                                    (set/difference clicked))
                                    :skip/clicked clicked})
                                 {:db/doc-type :skip
                                  :db/op :create
                                  :skip/user (:xt/id user)
                                  :skip/timeline-created-at (:t params)
                                  :skip/items (:skip params)
                                  :skip/clicked #{(:xt/id item)}})])
                            (when-not (biff/lookup-id
                                       db
                                       :user-item/user (:xt/id user)
                                       :user-item/item (:xt/id item))
                              [{:db/doc-type :user-item
                                :db.op/upsert {:user-item/user (:xt/id user)
                                               :user-item/item (:xt/id item)}
                                :user-item/viewed-at :db/now}]))})))

(defget page-content-route "/dev/for-you/content"
  [{:session/user
    [{(? :user/current-item)
      [:item/ui-read-more-card]}
     {:user/for-you-recs
      [:item/id
       :item/ui-read-more-card]}]}]
  (fn [{:keys [biff/now]} {{:user/keys [current-item for-you-recs]} :session/user}]
    [:div {:class '[flex flex-col gap-6
                    max-w-screen-sm]}
     (when-some [{:keys [item/ui-read-more-card]} current-item]
       [:div
        (ui-read-more-card {:on-click-route `read-page-route
                            :highlight-unread false
                            :show-author true})
        [:.h-5.sm:h-4]
        [:div.text-center
         [:a.underline {:href (href routes/history)}
          "View reading  history"]]])
     (for [[i {:item/keys [ui-read-more-card]}] (map-indexed vector for-you-recs)]
       (ui-read-more-card {:on-click-route `read-page-route
                           :on-click-params {:skip (set (mapv :item/id (take i for-you-recs)))
                                             :t now}
                           :highlight-unread false
                           :show-author true}))]))

(defget page-route "/dev/for-you"
  [:app.shell/app-shell]
  (fn [_ {:keys [app.shell/app-shell]}]
    (app-shell
     {}
     [:div#content (ui/lazy-load-spaced (href page-content-route))])))

;; TODO
;; - handle ads
;; - propagate jwt or something for auth from email (redirect should work even if you're not signed in)
(def read-page-route
  ["/dev/item/:item-id"
   {:name ::read-page-route
    :get
    (let [record-click-url (fn [params item]
                             (href record-click {:item/id (:item/id item)
                                                 :skip (:skip params)
                                                 :t (:t params)}))]
      (lib.route/wrap-nippy-params
       (lib.pipe/make
        :start
        (lib.pipe/pathom-query [{(? :params/item) [:item/id
                                                   (? :item/url)]}
                                {:session/user [(? :user/use-original-links)]}]
                               :start*)

        :start*
        (fn [{:keys [params biff.pipe.pathom/output]}]
          (let [{:keys [params/item session/user]} output
                {:item/keys [id url]} item
                {:user/keys [use-original-links]} user]
            (cond
              (nil? id)
              {:status 303
               :headers {"Location" (href page-route)}}

              (and use-original-links url)
              (ui/redirect-on-load {:redirect-url url
                                    :beacon-url (record-click-url params item)})

              :else
              {:biff.pipe/next [:biff.pipe/pathom :render]
               :biff.pipe.pathom/entity output
               :biff.pipe.pathom/query [:app.shell/app-shell
                                        {:params/item [:item/ui-read-content
                                                       :item/id
                                                       :item/title]}]})))

        :render
        (fn [{:keys [params biff.pipe.pathom/output]}]
          (let [{:keys [app.shell/app-shell params/item]} output
                {:item/keys [ui-read-content id title]} item]
            (app-shell
             {:title title}
             [:div {:hx-post (record-click-url params item) :hx-trigger "load" :hx-swap "outerHTML"}]
             (ui-read-content {:leave-item-redirect (href page-route)
                               :unsubscribe-redirect (href page-route)})
             [:div.h-10]
             ;; todo make sure mark-read finishes before this queries
             [:div#content (ui/lazy-load-spaced (href page-content-route))]))))))}])

(def module
  {:routes [record-click
            page-route
            page-content-route
            read-page-route]})
