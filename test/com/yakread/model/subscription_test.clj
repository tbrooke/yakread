(ns com.yakread.model.subscription-test
  (:require [clojure.test :refer [deftest is]]
            [com.biffweb :as biff]
            [com.yakread.model.subscription :as sut]
            [com.yakread.lib.item :as lib.item]
            [com.yakread.lib.test :as lib.test :refer [deftest-index]]
            [com.yakread.lib.user-item :as lib.user-item]
            [com.yakread :as main]
            [xtdb.api :as xt]
            [clojure.test.check :as tc]
            [clojure.test.check.clojure-test :refer [defspec]]))

(def index-examples
  (lib.test/index-examples
   :last-published
   [{:doc "New feed"
     :ctx #:biff.index{:index-get {}
                       :op ::xt/put
                       :doc {:item.feed/feed 1
                             :item/published-at (lib.test/instant 2024)}}}
    {:doc "Existing feed"
     :ctx #:biff.index{:index-get {1 (lib.test/instant 2024)}
                       :op ::xt/put
                       :doc {:item.feed/feed 1
                             :item/published-at (lib.test/instant 2025)}}}
    {:doc ":item/ingested-at"
     :ctx #:biff.index{:index-get {1 (lib.test/instant 2024)}
                       :op ::xt/put
                       :doc {:item.feed/feed 1
                             :item/ingested-at (lib.test/instant 2025)}}}
    {:doc "old item -- no-op"
     :ctx #:biff.index{:index-get {1 (lib.test/instant 2024)}
                       :op ::xt/put
                       :doc {:item.feed/feed 1
                             :item/ingested-at (lib.test/instant 2023)}}}

    {:doc "email item"
     :ctx #:biff.index{:index-get {}
                       :op ::xt/put
                       :doc {:item.email/sub 1
                             :item/published-at (lib.test/instant 2024)}}}]))

(defn get-context []
  {:biff/modules         (delay [sut/module])
   :biff.test/current-ns (lib.test/current-ns)
   :biff.test/examples   index-examples})

(deftest examples
  (lib.test/check-examples! (get-context)))

(deftest-index sut/last-published-index
  {:num-tests   25
   :model-opts  {:biff/malli-opts main/malli-opts
                 :schemas #{:item/feed :item/email}
                 :rank-overrides {:sub/email 1}}
   :expected-fn (fn [docs]
                  (-> (group-by lib.item/source-id docs)
                      (dissoc nil)
                      (update-vals (fn [docs]
                                     (->> docs
                                          (mapv lib.item/published-at)
                                          (apply max-key inst-ms))))))})

(deftest-index sut/unread-index
  {:num-tests   25
   :model-opts  {:biff/malli-opts main/malli-opts
                 :schemas #{:item/feed :item/email :user-item}
                 :rank-overrides {:sub/email 1}}
   :expected-fn (fn [docs]
                  (let [db (->> docs
                                (filterv lib.item/source-id)
                                (group-by lib.item/source-id)
                                (mapcat (fn [[source-id docs]]
                                          (into {source-id (count docs)}
                                                (for [{:keys [xt/id]} docs]
                                                  [id source-id]))))
                                (into {}))]
                    (merge db
                           (->> docs
                                (filterv lib.user-item/read?)
                                (group-by (juxt :user-item/user (comp db :user-item/item)))
                                (mapcat (fn [[id docs]]
                                          (into {id (count docs)}
                                                (for [{:keys [xt/id]} docs]
                                                  [id true]))))))))})

(comment

  (lib.test/write-examples! (get-context))
  ,)
