(ns com.yakread.model.subscription-test
  (:require [clojure.test :refer [deftest is]]
            [com.biffweb :as biff]
            [com.yakread.model.subscription :as sut]
            [com.yakread.lib.test :as lib.test]
            [com.yakread :as main]
            [xtdb.api :as xt]
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

(defspec last-published-index-test lib.test/*defspec-opts*
  (lib.test/indexer-prop
   {:indexer     (:indexer sut/last-published-index)
    :model-opts  {:biff/malli-opts main/malli-opts
                  :schemas #{:item/feed :item/email}
                  :rank-overrides {:sub/email 1}}
    :expected-fn (fn [docs]
                   (-> (group-by (some-fn :item.feed/feed :item.email/sub) docs)
                       (dissoc nil)
                       (update-vals (fn [docs]
                                      (->> docs
                                           (mapv (some-fn :item/published-at :item/ingested-at))
                                           (apply max-key inst-ms))))))}))

(comment

  (lib.test/write-examples! (get-context))
  ,)
