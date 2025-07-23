(ns com.yakread.app.for-you
  (:require
   [clojure.set :as set]
   [com.biffweb :as biff]
   [com.yakread.lib.middleware :as lib.mid]
   [com.yakread.lib.pipeline :as lib.pipe]
   [com.yakread.lib.route :as lib.route :refer [? defget defpost defpost-pathom href]]
   [com.yakread.lib.ui :as ui]
   [com.yakread.routes :as routes]))

(defn- skip-tx [{:keys [biff/db skip t]
                 user-id :user/id
                 rec-id :rec/id}]
  (when (and skip t)
    (let [existing-skip (biff/lookup db :skip/user user-id :skip/timeline-created-at t)
          old-clicked (:skip/clicked existing-skip #{})
          new-clicked (conj old-clicked rec-id)]
      (when (not= old-clicked new-clicked)
        [{:db/doc-type :skip
          :db.op/upsert {:skip/user user-id
                         :skip/timeline-created-at t}
          :skip/items (-> (:skip/items existing-skip #{})
                          (set/union skip)
                          (set/difference new-clicked))
          :skip/clicked new-clicked}]))))

(defpost record-item-click
  :start
  (fn [{:keys [biff/db biff/safe-params]}]
    (let [{:keys [action t skip] item-id :item/id user-id :user/id} safe-params]
      (if (not= action :action/click-item)
        (ui/on-error {:status 400})
        {:status 204
         :biff.pipe/next [:biff.pipe/tx]
         :biff.pipe.tx/input (concat
                              (skip-tx {:biff/db db
                                        :user/id user-id
                                        :rec/id item-id
                                        :skip skip
                                        :t t})
                              (when-not (biff/lookup-id
                                         db
                                         :user-item/user user-id
                                         :user-item/item item-id)
                                [{:db/doc-type :user-item
                                  :db.op/upsert {:user-item/user user-id
                                                 :user-item/item item-id}
                                  :user-item/viewed-at :db/now}]))}))))

(defpost record-ad-click
  :start
  (fn [{:biff/keys [db safe-params]}]
    (let [{:keys [action skip t ad/click-cost ad.click/source]
           ad-id :ad/id
           user-id :user/id} safe-params]
      (if (not= action :action/click-ad)
        (ui/on-error {:status 400})
        {:status 204
         :biff.pipe/next [:biff.pipe/tx]
         :biff.pipe.tx/input (concat
                              (skip-tx {:biff/db db
                                        :user/id user-id
                                        :rec/id ad-id
                                        :skip  skip
                                        :t t})
                              (when-not (biff/lookup-id db
                                                        :ad.click/user user-id
                                                        :ad.click/ad ad-id)
                                [{:db/doc-type :ad.click
                                  :db.op/upsert {:ad.click/user user-id
                                                 :ad.click/ad ad-id}
                                  :ad.click/created-at :db/now
                                  :ad.click/cost click-cost
                                  :ad.click/source (or source :web)}]))}))))

(defget page-content-route "/dev/for-you/content"
  [{(? :session/user)
    [{(? :user/current-item)
      [:item/ui-read-more-card]}
     {:user/for-you-recs
      [:xt/id
       :rec/ui-read-more-card]}]}
   {(? :session/anon)
    [{:user/discover-recs
      [:item/id
       :item/ui-read-more-card]}]}]
  (fn [{:keys [biff/now params]} {:keys [user/discover-recs session/user session/anon]
                                  {:user/keys [current-item for-you-recs]} :session/user}]
    [:div {:class '[flex flex-col gap-6
                    max-w-screen-sm]}
     (when-let [{:keys [item/ui-read-more-card]} (and (:show-continue params) current-item)]
       [:div
        (ui-read-more-card {:on-click-route `read-page-route
                            :highlight-unread false
                            :show-author true})
        [:.h-5.sm:h-4]
        [:div.text-center
         (ui/muted-link {:href (href routes/history)}
                        "View reading history")]])
     (if user
       (for [[i {:rec/keys [ui-read-more-card]}] (map-indexed vector for-you-recs)]
         (ui-read-more-card {:on-click-route `read-page-route
                             :on-click-params {:skip (set (mapv :xt/id (take i for-you-recs)))
                                               :t now}
                             :highlight-unread false
                             :show-author true}))
       (for [[i {:item/keys [ui-read-more-card]}] (map-indexed vector (:user/discover-recs anon))]
         (ui-read-more-card {:on-click-route `read-page-route
                             :highlight-unread false
                             :show-author true
                             :new-tab true})))]))

(defget page-route "/dev/for-you"
  [:app.shell/app-shell]
  (fn [_ {:keys [app.shell/app-shell]}]
    (app-shell
     {}
     [:div#content (ui/lazy-load-spaced (href page-content-route {:show-continue true}))])))

;; TODO
;; - propagate jwt or something for auth from email (redirect should work even if you're not signed in)
;; - when coming from email, do redirect-on-load if the user isn't signed in
(def read-page-route
  ["/dev/item/:item-id"
   {:name ::read-page-route
    :get
    (let [record-click-url
          (fn [{:keys [biff/href-safe
                       params
                       session
                       biff.pipe.pathom/output]}]
            (href-safe record-item-click
                       {:action  :action/click-item
                        :user/id (:uid session)
                        :item/id (get-in output [:params/item :item/id])
                        :skip    (:skip params)
                        :t       (:t params)}))]
      (lib.route/wrap-nippy-params
       (lib.pipe/make
        :start
        (lib.pipe/pathom-query [{(? :params/item) [:item/id
                                                   (? :item/url)]}
                                {(? :session/user) [(? :user/use-original-links)]}]
                               :start*)

        :start*
        (fn [{:keys [params biff.pipe.pathom/output] :as ctx}]
          (let [{:keys [params/item session/user]} output
                {:item/keys [id url]} item
                {:user/keys [use-original-links]} user]
            (cond
              (nil? id)
              {:status 303
               :headers {"Location" (href page-route)}}

              (nil? user)
              {:status 303
               :headers {"Location" url}}

              (and use-original-links url)
              (ui/redirect-on-load {:redirect-url url
                                    :beacon-url (when-not (:skip-record params)
                                                  (record-click-url ctx))})

              :else
              {:biff.pipe/next [:biff.pipe/pathom :render]
               :biff.pipe.pathom/entity output
               :biff.pipe.pathom/query [:app.shell/app-shell
                                        {:params/item [:item/ui-read-content
                                                       :item/id
                                                       :item/title]}]})))

        :render
        (fn [{:keys [params biff.pipe.pathom/output] :as ctx}]
          (let [{:keys [app.shell/app-shell params/item]} output
                {:item/keys [ui-read-content title]} item]
            (app-shell
             {:title title}
             (when-not (:skip-record params)
               [:div {:hx-post (record-click-url ctx)
                      :hx-trigger "load"
                      :hx-swap "outerHTML"}])
             (ui-read-content {:leave-item-redirect (href page-route)
                               :unsubscribe-redirect (href page-route)})
             [:div.h-10]
             ;; todo make sure mark-read finishes before this queries
             ;; make ui/lazy-load include a delay of a few seconds
             [:div#content (ui/lazy-load-spaced (href page-content-route))]))))))}])

(def click-ad-route
  ["/dev/c/:ewt"
   {:name ::click-ad-route
    :get (lib.route/wrap-nippy-params
          (fn [{:biff/keys [safe-params href-safe]}]
            (let [{:keys [action ad/url]} safe-params]
              (if (not= action :action/click-ad)
                (ui/on-error {:status 400})
                (ui/redirect-on-load
                 {:redirect-url url
                  :beacon-url (href-safe record-ad-click safe-params)})))))}])

(def click-item-route
  ["/dev/r/:ewt"
   {:name ::click-item-route
    :get (lib.route/wrap-nippy-params
          (fn [{:biff/keys [safe-params href-safe]}]
            (let [{:keys [action item/url item/id redirect]} safe-params]
              (if (not= action :action/click-item)
                (ui/on-error {:status 400})
                (ui/redirect-on-load
                 {:redirect-url (if redirect
                                  url
                                  (href read-page-route id {:skip-record true}))
                  :beacon-url (href-safe record-item-click safe-params)})))))}])

(def module
  {:routes [["" {:middleware [lib.mid/wrap-signed-in]}
             record-item-click]
            record-ad-click
            page-route
            ["" {:middleware [#_lib.mid/wrap-profiled]}
             page-content-route]
            read-page-route
            click-ad-route
            click-item-route]})
