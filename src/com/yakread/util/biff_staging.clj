(ns com.yakread.util.biff-staging
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [com.biffweb :as biff]
            [com.wsscode.pathom3.connect.operation :as-alias pco]
            [com.wsscode.pathom3.connect.planner :as-alias pcp]
            [com.wsscode.pathom3.interface.eql :refer [process]]
            [malli.core :as malli]
            [malli.dev.pretty :as mpretty]
            [malli.registry :as malr]
            [reitit.core :as reitit]
            [xtdb.api :as xt]
            [com.biffweb.impl.util :as util :refer [<<-]]
            [com.biffweb.impl.xtdb :as impl.xtdb]
            [com.yakread.lib.pathom :as lib.pathom]
            [clojure.tools.logging :as log]
            [com.biffweb.impl.index :as impl.index]
            [taoensso.nippy :as nippy]
            [com.biffweb.protocols :as biff.proto]
            [com.wsscode.pathom3.format.shape-descriptor :as pfsd])
  (:import (java.time Instant)
           (org.rocksdb BlockBasedTableConfig Checkpoint CompressionType FlushOptions LRUCache
                        DBOptions Options ReadOptions RocksDB RocksIterator
                        WriteBatchWithIndex WriteBatch WriteOptions Statistics StatsLevel
                        ColumnFamilyOptions ColumnFamilyDescriptor ColumnFamilyHandle BloomFilter)))

;; TODO make this a batch resolver?
(defn pull-resolver [malli-opts]
  (let [all-document-attrs (set
                            (for [schema-k (keys (malr/schemas (:registry malli-opts)))
                                  :let [schema (try (malli/deref-recursive schema-k malli-opts) (catch Exception _))]
                                  :when schema
                                  :let [schemas (volatile! [])
                                        _ (malli/walk schema (fn [schema _ _ _]
                                                              (vswap! schemas conj schema)))]
                                  schema @schemas
                                  :let [ast (malli/ast schema)]
                                  :when (and ast
                                             (= (:type ast) :map)
                                             (contains? (:keys ast) :xt/id))
                                  k (keys (:keys ast))
                                  k [k (keyword (namespace k) (str "_" (name k)))]]
                              k))]
    (letfn [(request-shape->pull-query [request-shape]
              (vec (keep (fn [[k v]]
                           (cond
                             (not (all-document-attrs k)) nil
                             (empty? v) k
                             :else {k (request-shape->pull-query v)}))
                         request-shape)))]
      (pco/resolver `pull-resolver
                    {::pco/input [:xt/id]
                     ::pco/output (vec all-document-attrs)}
                    (fn [{:keys [biff/db ::pcp/node ::pcp/graph] :as env} {:keys [xt/id]}]
                      (let [{::pcp/keys [expects]} node
                            query (request-shape->pull-query expects)]
                        (xt/pull db query id)))))))

;; TODO maybe use this somewhere
(defn wrap-db-with-index [handler]
  (fn [{:keys [biff/db] :as ctx}]
    (if (satisfies? biff.proto/IndexDatasource db)
      (handler ctx)
      (with-open [db (biff/open-db-with-index ctx)]
        (handler (assoc ctx :biff/db db))))))
