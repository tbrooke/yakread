(ns com.yakread.model.item
  (:require [clojure.string :as str]
            [com.biffweb :as biff :refer [q <<-]]
            [com.wsscode.pathom3.connect.operation :as pco :refer [defresolver ?]]
            [com.yakread.lib.serialize :as lib.serialize]
            [com.yakread.lib.user-item :as lib.user-item]
            [clojure.set :as set]
            [xtdb.api :as xt]
            [rum.core :as rum])
  (:import [org.jsoup Jsoup]))

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

;; TODO make sure we're syncing :rss/image correctly
(defresolver image [{:keys [biff/db]} items]
  #::pco{:input [(? :item.rss/feed-url)
                 (? :item/inferred-feed-url)
                 (? :item/image)]
         :output [:item/image-with-default]
         :batch? true}
  (let [url->image (into {} (q db
                               '{:find [url image]
                                 :in [[url ...]]
                                 :where [[rss :rss/url url]
                                         [rss :rss/image image]]}
                               (distinct
                                (concat
                                 (keep :item.rss/feed-url items)
                                 (keep :item/inferred-feed-url items)))))]
    (vec
     (for [{:keys [item.rss/feed-url
                   item/inferred-feed-url
                   item/image]
            :as item} items
           :let [image (or image (some url->image [feed-url inferred-feed-url]))]]
       (merge (when image {:item/image-with-default image})
              item)))))

(defresolver unread [{:keys [item/user-item]}]
  #::pco{:input [{(? :item/user-item) [:user-item/viewed-at
                                       :user-item/favorited-at
                                       :user-item/disliked-at
                                       :user-item/reported-at]}]}
  {:item/unread (not (lib.user-item/read? user-item))})

(defresolver sub [{:keys [biff/db session]} {:keys [item/id item.email/sub item.feed/feed]}]
  #::pco{:input [:item/id
                 (? :item.email/sub)
                 (? :item.feed/feed)]
         :output [{:item/sub [:xt/id]}]}
  {:item/sub
   (or sub
       (some->> (biff/lookup-id db :sub/user (:uid session) :sub.feed/feed (:xt/id feed))
                (hash-map :xt/id)))})

(defresolver from-params-unsafe [{:keys [path-params]} _]
  #::pco{:output [{:params/item-unsafe [:xt/id]}]}
  (when-some [item-id (lib.serialize/url->uuid (:item-id path-params))]
    {:params/item-unsafe {:xt/id item-id}}))

(defresolver from-params [{:keys [biff/db session path-params]} {:keys [params/item-unsafe]}]
  #::pco{:input [{:params/item-unsafe [:xt/id
                                       {:item/sub [:xt/id
                                                   :sub/user]}]}]
         :output [{:params/item [:xt/id
                                 {:item/sub [:xt/id]}]}]}
  (when (= (:uid session) (get-in item-unsafe [:item/sub :sub/user :xt/id]))
    {:params/item item-unsafe}))

(defresolver item-id [{:keys [xt/id]}]
  {:item/id id})

(defresolver xt-id [{:keys [item/id]}]
  {:xt/id id})

;; TODO add resolver/clause for :item.email/content-key
(defresolver content [ctx {:item/keys [content-key url]}]
  #::pco{:input [(? :item/content-key)
                 (? :item/url)]
         :output [:item/content]}
  (cond
    content-key
    {:item/content (:body (biff/s3-request ctx {:method "GET" :key (str content-key)}))}

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
                               item.email/user]}]
  #::pco{:input [(? :item.feed/feed)
                 (? :item.email/user)]
         :output [:item/doc-type]}
  (cond
    feed {:item/doc-type :item/feed}
    user {:item/doc-type :item/email}))

(def module
  {:resolvers [doc-type
               clean-html
               content
               item-id
               xt-id
               user-item
               unread
               #_image
               sub
               from-params-unsafe
               from-params]})
