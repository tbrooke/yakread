(ns com.yakread.util.biff-staging
  (:require [com.biffweb :as biff]
            [com.wsscode.pathom3.connect.operation :as-alias pco]
            [com.wsscode.pathom3.connect.planner :as-alias pcp]
            [malli.core :as malli]
            [malli.registry :as malr]
            [reitit.core :as reitit]
            [xtdb.api :as xt]
            [com.biffweb.protocols :as biff.proto]))

(defn all-document-attrs [{:keys [registry] :as malli-opts}]
  (set (for [schema-k (keys (malr/schemas (:registry malli-opts)))
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
         k)))

;; TODO make this a batch resolver?
(defn pull-resolver [malli-opts]
  (let [attrs (all-document-attrs malli-opts)]
    (letfn [(request-shape->pull-query [request-shape]
              (vec (keep (fn [[k v]]
                           (cond
                             (not attrs) nil
                             (empty? v) k
                             :else {k (request-shape->pull-query v)}))
                         request-shape)))]
      (pco/resolver `pull-resolver
                    {::pco/input [:xt/id]
                     ::pco/output (vec attrs)}
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
