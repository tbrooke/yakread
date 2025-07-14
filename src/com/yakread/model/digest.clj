(ns com.yakread.model.digest
  (:require
   [com.biffweb :as biff :refer [q]]
   [com.wsscode.pathom3.connect.operation :as pco :refer [? defresolver]])
  (:import
   [java.time Period]))

(defn recent-items [{:biff/keys [db now]
                     :user/keys [digest-last-sent]
                     :keys [all-item-ids]}]
  (mapv (fn [[id]]
          {:xt/id id})
        (q db
           '{:find [item ingested-at]
             :order-by [[ingested-at :desc]]
             :limit 50
             :in [t0 [item ...]]
             :where [[item :item/ingested-at ingested-at]
                     [(< t0 ingested-at)]]}
           (cond->> (.minus now (Period/ofWeeks 52)) ; TODO
             digest-last-sent (max-key inst-ms digest-last-sent))
           all-item-ids)))


(defresolver digest-sub-items [{:biff/keys [db now]} {:user/keys [digest-last-sent subscriptions]}]
  {::pco/input [(? :user/digest-last-sent)
                {:user/subscriptions [{:sub/items [:xt/id]}]}]
   ::pco/output [{:user/digest-sub-items [:xt/id]}]}
  {:user/digest-sub-items
   (recent-items
    {:biff/db db
     :biff/now now
     :user/digest-last-sent digest-last-sent
     :all-item-ids (mapv :xt/id (mapcat :sub/items subscriptions))})})

(defresolver digest-bookmarks [{:biff/keys [db now]} {:user/keys [digest-last-sent bookmarks]}]
  {::pco/input [:user/id
                (? :user/digest-last-sent)
                {:user/bookmarks [:xt/id]}]
   ::pco/output [{:user/digest-bookmarks [:xt/id]}]}
  {:user/digest-bookmarks
   (recent-items
    {:biff/db db
     :biff/now now
     :user/digest-last-sent nil #_digest-last-sent ; TODO
     :all-item-ids (mapv :xt/id bookmarks)})})

(def module
  {:resolvers [digest-sub-items
               digest-bookmarks]})
