(ns com.yakread.model.moderation
  (:require
   [com.biffweb :as biff :refer [q]]
   [com.wsscode.pathom3.connect.operation :as pco :refer [? defresolver]]))

(defresolver moderation [_ _]
  {::pco/output [{:admin/moderation [:xt/id]}]}
  {:admin/moderation {:xt/id :admin/moderation}})

(defresolver next-batch [{:keys [biff/db]} {:keys [admin/moderation]}]
  {::pco/input [{:admin/moderation
                 [{(? :admin.moderation/latest-item)
                   [:item/id
                    :item/ingested-at]}]}]
   ::pco/output [{:admin.moderation/next-batch [:item/id]}
                 :admin.moderation/n-items]}
  (let [{:item/keys [id ingested-at]} (:admin.moderation/latest-item moderation)
        items (->> (q db
                      '{:find [item ingested-at]
                        :keys [item/id item/ingested-at]
                        :in [t direct]
                        :where [[item :item/doc-type direct]
                                [item :item/ingested-at ingested-at]
                                [(<= t ingested-at)]]
                        :order-by [[ingested-at :asc]
                                   [item :asc]]}
                      (or ingested-at (java.time.Instant/ofEpochMilli 0))
                      :item/direct)
                   (drop-while #(and id
                                     (= (:item/ingested-at %) ingested-at)
                                     (<= (compare (:item/id %) id) 0))))]
    {:admin.moderation/n-items (count items)
     :admin.moderation/next-batch (vec (take 20 items))}))

(def module {:resolvers [moderation
                         next-batch]})
