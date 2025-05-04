(ns com.yakread.model.item
  (:require
   [clojure.string :as str]
   [com.biffweb :as biff :refer [q]]
   [com.wsscode.pathom3.connect.operation :as pco :refer [? defresolver]]
   [com.yakread.lib.s3 :as lib.s3]
   [com.yakread.lib.serialize :as lib.serialize]
   [com.yakread.lib.user-item :as lib.user-item]
   [rum.core :as rum])
  (:import
   (org.jsoup Jsoup)))

(defresolver user-favorites [{:keys [biff/db]} {:keys [user/id]}]
  #::pco{:output [{:user/favorites [:item/id]}]}
  {:user/favorites (vec (q db
                           '{:find [item]
                             :keys [item/id]
                             :in [user]
                             :where [[user-item :user-item/user user]
                                     [user-item :user-item/item item]
                                     [user-item :user-item/favorited-at]]}
                           id))})

(defresolver user-bookmarks [{:keys [biff/db]} {:keys [user/id]}]
  #::pco{:output [{:user/bookmarks [:item/id]}]}
  {:user/bookmarks (vec (q db
                           '{:find [item]
                             :keys [item/id]
                             :in [user]
                             :where [[user-item :user-item/user user]
                                     [user-item :user-item/item item]
                                     [user-item :user-item/bookmarked-at]]}
                           id))})

(defresolver unread-bookmarks [{:keys [biff/db]} {:user/keys [bookmarks]}]
  #::pco{:input [{:user/bookmarks [:item/id
                                   :item/unread]}]
         :output [{:user/unread-bookmarks [:item/id]}]}
  {:user/unread-bookmarks (filterv :item/unread bookmarks)})

(defresolver n-skipped [{:keys [biff/db session]} items]
  #::pco{:input [:xt/id]
         :output [:item/n-skipped]
         :batch? true}
  (let [id->n-skipped (into {}
                            (q db
                               '{:find [item (count skip)]
                                 :in [user [item ...]]
                                 :where [[skip :skip/user user]
                                         [skip :skip/items item]]}
                               (:uid session)
                               (mapv :xt/id items)))]
    (mapv (fn [item]
            (assoc item :item/n-skipped (get id->n-skipped (:xt/id item) 0)))
          items)))

(defresolver user-item [{:keys [biff/db session]} items]
  #::pco{:input [:xt/id]
         :output [{:item/user-item [:xt/id]}]
         :batch? true}
  (let [id->user-item (update-vals
                       (into {} (q db
                                   '{:find [item user-item]
                                     :in [user [item ...]]
                                     :where [[user-item :user-item/user user]
                                             [user-item :user-item/item item]]}
                                   (:uid session)
                                   (mapv :xt/id items)))
                       #(hash-map :xt/id %))]
    (mapv (fn [item]
            (merge item
                   (some->> (get id->user-item (:xt/id item))
                            (hash-map :item/user-item))))
          items)))

(defresolver image-from-feed [{:keys [biff/db]} items]
  #::pco{:input [(? :item/feed-url)
                 {(? :item.feed/feed) [:feed/image-url]}]
         :output [:item/image-url]
         :batch? true}
  (let [url->image (into {} (q db
                               '{:find [url image]
                                 :in [[url ...]]
                                 :where [[feed :feed/url url]
                                         [feed :feed/image-url image]]}
                               (keep :item/feed-url items)))]
    (vec
     (for [{:keys [item.feed/feed item/feed-url] :as item} items]
       (when-some [image (or (:feed/image-url feed)
                             (url->image feed-url))]
         {:item/image-url image})))))

(defresolver unread [{:keys [item/user-item]}]
  #::pco{:input [{(? :item/user-item) [(? :user-item/viewed-at)
                                       (? :user-item/skipped-at)
                                       (? :user-item/favorited-at)
                                       (? :user-item/disliked-at)
                                       (? :user-item/reported-at)]}]}
  {:item/unread (not (lib.user-item/read? user-item))})

(defresolver history-items [{:keys [biff/db] :as ctx}
                            {:keys [session/user
                                    params/paginate-after]}]
  #::pco{:input [{:session/user [:xt/id]}
                 (? :params/paginate-after)]
         :output [{:user/history-items [:xt/id
                                        {:item/user-item [:xt/id]}]}]}
  {:user/history-items
   (let [{:keys [batch-size]
          :or {batch-size 100}} (pco/params ctx)]
     (->> (q db
           '{:find [item user-item (max t)]
             :keys [item user-item t]
             :in [user]
             :where [[user-item :user-item/user user]
                     [user-item :user-item/item item]
                     (or [user-item :user-item/viewed-at t]
                         [user-item :user-item/favorited-at t]
                         [user-item :user-item/disliked-at t]
                         [user-item :user-item/reported-at t])]}
           (:xt/id user))
        (sort-by (comp inst-ms :t) >)
        (drop-while (fn [{:keys [item]}]
                      (and paginate-after
                           (not= item paginate-after))))
        (remove (comp #{paginate-after} :item))
        (take batch-size)
        (mapv (fn [{:keys [item user-item]}]
                {:xt/id item
                 :item/user-item {:xt/id user-item}}))))})

(defresolver current-item [{:keys [session/user]}]
  #::pco{:input [{:session/user [{(list :user/history-items {:batch-size 1})
                                  [:item/id]}]}]
         :output [{:user/current-item
                   [:item/id :item/rec-type]}]}
  (when-some [item-id (-> user :user/history-items first :item/id)]
    {:user/current-item {:item/id item-id
                         :item/rec-type :item.rec-type/current}}))

(defresolver source [{:keys [item.email/sub item.feed/feed]}]
  {::pco/input [{(? :item.email/sub) [:xt/id]}
                {(? :item.feed/feed) [:xt/id]}]
   ::pco/output [{:item/source [:xt/id]}]}
  (when-some [source (or sub feed)]
    {:item/source source}))

(defresolver sub [{:keys [biff/db session]} {:keys [item/id item.email/sub item.feed/feed]}]
  {::pco/input [:item/id
                {(? :item.email/sub) [:xt/id]}
                {(? :item.feed/feed) [:xt/id]}]
   ::pco/output [{:item/sub [:xt/id]}]}
  (when-some [sub (or sub
                      (when feed
                        (some->> (biff/lookup-id db
                                                 :sub/user (:uid session)
                                                 :sub.feed/feed (:xt/id feed))
                                 (hash-map :xt/id))))]
    {:item/sub sub}))

(defresolver from-params-unsafe [{:keys [path-params params]} _]
  #::pco{:output [{:params/item-unsafe [:xt/id]}]}
  (when-some [item-id (or (some-> (:item-id path-params) lib.serialize/url->uuid)
                          (:item/id params))]
    {:params/item-unsafe {:xt/id item-id}}))

(defresolver from-params [{:keys [session yakread.model/candidate-ids]}
                          {:keys [params/item-unsafe]}]
  #::pco{:input [{:params/item-unsafe [:xt/id
                                       {(? :item/sub) [:xt/id
                                                       :sub/user]}
                                       {(? :item/user-item) [:xt/id]}]}]
         :output [{:params/item [:xt/id]}]}
  (when (or (= (:uid session) (get-in item-unsafe [:item/sub :sub/user :xt/id]))
            (not-empty (:item/user-item item-unsafe))
            (contains? candidate-ids (:xt/id item-unsafe)))
    {:params/item item-unsafe}))

(defresolver item-id [{:keys [xt/id]}]
  {:item/id id})

(defresolver xt-id [{:keys [item/id]}]
  {:xt/id id})

(defresolver content [ctx {:item/keys [content-key url]}]
  #::pco{:input [(? :item/content-key)
                 (? :item/url)]
         :output [:item/content]}
  (cond
    ;; TODO actually use s3 when configured
    content-key
    {:item/content (:body
                    (or (biff/catchall
                         (lib.s3/mock-request ctx {:method "GET" :key (str content-key)}))
                        (biff/s3-request ctx {:method "GET" :bucket "yakread-content" :key (str content-key)})))}

    url
    {:item/content (rum/render-static-markup [:a {:href url} url])}))

(defresolver clean-html [{:item/keys [content]}]
  {:item/clean-html
   (let [doc (Jsoup/parse content)]
     (-> doc
         (.select "a[href]")
         (.attr "target" "_blank"))
     (doseq [img (.select doc "img[src^=http://]")]
       (.attr img "src" (str/replace (.attr img "src")
                                     #"^http://"
                                     "https://")))
     (.outerHtml doc))})

(defresolver doc-type [{:keys [item.feed/feed
                               item.email/sub]}]
  #::pco{:input [{(? :item.feed/feed) [:xt/id]}
                 {(? :item.email/sub) [:xt/id]}]
         :output [:item/doc-type]}
  (cond
    feed {:item/doc-type :item/feed}
    sub {:item/doc-type :item/email}))

(def module
  {:resolvers [user-favorites
               user-bookmarks
               clean-html
               content
               doc-type
               from-params
               from-params-unsafe
               image-from-feed
               item-id
               sub
               unread
               user-item
               xt-id
               n-skipped
               unread-bookmarks
               history-items
               current-item
               source]})
