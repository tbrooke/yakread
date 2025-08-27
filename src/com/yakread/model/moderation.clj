(ns com.yakread.model.moderation
  (:require
   [com.biffweb :as biff :refer [q]]
   [com.wsscode.pathom3.connect.operation :as pco :refer [? defresolver]]))

(defresolver next-batch [{:keys [biff/db]} _]
  {::pco/output [{:admin.moderation/next-batch [:item/id :item.moderation/likes]}
                 :admin.moderation/remaining
                 :admin.moderation/approved
                 :admin.moderation/blocked
                 :admin.moderation/ingest-failed]}
  (let [direct-items (q db
                        '{:find (pull item [*])
                          :in [direct]
                          :where [[item :item/doc-type direct]]}
                        :item/direct)
        url->direct-item (into {} (map (juxt :item/url identity)) direct-items)
        liked-items (q db
                       '{:find [item (count usit)]
                         :keys [item/id item.moderation/likes]
                         :timeout 120000
                         :order-by [[(count usit) :desc]]
                         :where [[usit :user-item/item item]
                                 [usit :user-item/favorited-at]]})
        item->url (into {}
                        (q db
                           '{:find [item url]
                             :in [[item ...]]
                             :where [[item :item/url url]]}
                           (mapv :item/id liked-items)))
        direct-item-id->likes (->> liked-items
                                   (mapv (fn [{:keys [item/id item.moderation/likes]}]
                                           (when-some [id (-> id item->url url->direct-item :xt/id)]
                                             {id likes})))
                                   (apply merge-with +))
        liked-direct-items (->> (into []
                                      (comp (map #(assoc % :item.moderation/likes (direct-item-id->likes (:xt/id %))))
                                            (filter :item.moderation/likes)
                                            (remove :item.direct/candidate-status))
                                      direct-items)
                                (sort-by :item.moderation/likes >)
                                vec)
        statuses (into {} (q db
                             '{:find [status (count item)]
                               :where [[item :item.direct/candidate-status status]]}))]
    {:admin.moderation/remaining (count liked-direct-items)
     :admin.moderation/approved (get statuses :approved 0)
     :admin.moderation/blocked (get statuses :blocked 0)
     :admin.moderation/ingest-failed (get statuses :ingest-failed 0)
     :admin.moderation/next-batch (vec (take 50 liked-direct-items))}))

(def module {:resolvers [next-batch]})
