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
  (let [items (q db
                 '{:find [direct-item (count usit)]
                   :keys [item/id item.moderation/likes]
                   :in [direct]
                   :order-by [[(count usit) :desc]]
                   :where [[usit :user-item/item any-item]
                           [usit :user-item/favorited-at]
                           [any-item :item/url url]
                           [direct-item :item/url url]
                           [direct-item :item/doc-type direct]
                           (not [direct-item :item.direct/candidate-status])]}
                 :item/direct)
        statuses (into {} (q db
                             '{:find [status (count item)]
                               :where [[item :item.direct/candidate-status status]]}))]
    {:admin.moderation/remaining (count items)
     :admin.moderation/approved (get statuses :approved 0)
     :admin.moderation/blocked (get statuses :blocked 0)
     :admin.moderation/ingest-failed (get statuses :ingest-failed 0)
     :admin.moderation/next-batch (vec (take 50 items))}))

(def module {:resolvers [next-batch]})
