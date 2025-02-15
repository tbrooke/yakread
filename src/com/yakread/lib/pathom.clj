(ns com.yakread.lib.pathom
  (:require [com.biffweb :as biff]
            [com.wsscode.pathom3.interface.eql :as eql]
            [com.wsscode.pathom3.interface.smart-map :as psm]
            [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.connect.runner :as runner]
            [com.yakread.lib.error :as lib.error]
            [clojure.tools.logging :as log]
            [xtdb.api :as xt]))

;; TODO try to do this without monkey patching
(alter-var-root #'runner/processor-exception (constantly (fn [_ ex] ex)))
(alter-var-root #'runner/report-resolver-error (constantly (fn [_ _ ex] (throw ex))))

(def ? pco/?)

(defn process [ctx & args]
  (try
    (with-open [db (if (:biff.index/indexes ctx)
                     (biff/open-db-with-index ctx)
                     (xt/open-db (:biff.xtdb/node ctx)))]
      (apply eql/process
             (-> ctx
                 (assoc :biff/db db :biff.db/basis (xt/db-basis db))
                 (runner/with-resolver-cache (atom {})))
             args))
    (catch Exception e
      (if-some [unreachable (get-in (ex-data e)
                                    [:com.wsscode.pathom3.connect.planner/graph
                                     :com.wsscode.pathom3.connect.planner/unreachable-paths])]
        (throw (ex-info "Pathom can't find a path for the current entity"
                        {:pathom/unreachable-paths unreachable}))
        (throw e)))))

(defn handler [query f]
  (fn handler*
    ([request]
     (lib.error/with-ex-data (lib.error/request-ex-data request)
       (handler* request (process request query))))
    ([request input]
     (f request input))))

(def plan-cache-kw :com.wsscode.pathom3.connect.planner/plan-cache*)
