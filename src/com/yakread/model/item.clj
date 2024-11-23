(ns com.yakread.model.item
  (:require [com.biffweb :as biff :refer [q <<-]]
            [com.wsscode.pathom3.connect.operation :as pco :refer [defresolver ?]]
            [com.yakread.lib.serialize :as lib.serialize]
            [clojure.set :as set]
            [xtdb.api :as xt]))

(defresolver rec [{:keys [biff/db session]} items]
  #::pco{:input [:xt/id]
         :output [{:item/rec [:xt/id]}]
         :batch? true}
  (let [id->rec (into {} (q db
                            '{:find [item (pull rec [:xt/id])]
                              :in [user [item ...]]
                              :where [[rec :rec/user user]
                                      [rec :rec/item item]]}
                            (:uid session)
                            (mapv :xt/id items)))]
    (mapv (fn [item]
            (merge item
                   (when-some [rec (get id->rec (:xt/id item))]
                     {:item/rec rec})))
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

(defresolver unread [{:keys [item/rec]}]
  #::pco{:input [(? :item/rec)]}
  {:item/unread (nil? rec)})

(defresolver item-from-params [{:keys [biff/db session path-params]} _]
  #::pco{:output [{:params/item [:xt/id]}]}
  (let [item-id (biff/catchall (:item/id (lib.serialize/base64->edn (:entity path-params))))
        item (when (uuid? item-id)
               (xt/entity db item-id))
        conn (when item
               (biff/lookup db
                            :conn/user (:uid session)
                            :conn.rss/url (:item.rss/feed-url item)))]
    (when conn
      {:params/item (assoc item :item/conn conn)})))

(def module
  {:resolvers [rec
               unread
               image
               item-from-params]})
