(ns com.yakread.lib.pipeline
  (:require [clj-http.client :as http]
            [com.biffweb :as biff]
            [com.yakread.lib.error :as lib.error]
            [com.yakread.lib.pathom :as lib.pathom]
            [com.yakread.lib.route :as lib.route]
            [clojure.tools.logging :as log]))

(defn make [& {:as id->handler}]
  (fn execute
    ([ctx handler-id]
     ((or (get id->handler handler-id)
          (throw (ex-info (str "No handler for " handler-id) {})))
       ctx))
    ([{:biff.pipe/keys [global-handlers] :as ctx*}]
     (let [id->handler (merge global-handlers id->handler)
           ctx (assoc ctx* :biff.pipe/next [:start])]
       (loop [{[next-step & remaining] :biff.pipe/next :as result} ctx]
         (if-not next-step
           result
           (let [{:keys [biff.pipe/current]
                  :as extra-params} (if (map? next-step)
                                      next-step
                                      {:biff.pipe/current next-step})
                 ctx (merge ctx result extra-params {:biff.pipe/next remaining})
                 handler (or (get id->handler current)
                             (throw (ex-info (str "No handler for " current) {})))]
             (recur (try
                      (handler ctx)
                      (catch Exception e
                        (if (= (:biff.pipe/catch ctx) current)
                          (assoc ctx :biff.pipe/exception e)
                          (throw (lib.error/merge-ex-data
                                  e
                                  (lib.error/request-ex-data ctx*)
                                  (apply dissoc ctx (keys ctx*)))))))))))))))

(def global-handlers
  {:biff.pipe/http (fn [{:biff.pipe.http/keys [input] :as ctx}]
                     (assoc ctx :biff.pipe.http/output (-> (http/request input)
                                                           (assoc :url (:url input))
                                                           (dissoc :http-client))))
   :biff.pipe/tx (fn [{:keys [biff.pipe.tx/input] :as ctx}]
                   (assoc ctx :biff.pipe.tx/output (biff/submit-tx ctx input)))
   :biff.pipe/pathom (fn [{:biff.pipe.pathom/keys [entity query] :as ctx}]
                       (assoc ctx
                              :biff.pipe.pathom/output
                              (lib.pathom/process ctx (or entity {}) query)))
   :biff.pipe/slurp (fn [{:keys [biff.pipe.slurp/input] :as ctx}]
                      (assoc ctx :biff.pipe.slurp/output (slurp input)))
   :biff.pipe/render (fn [{:keys [biff/router biff.pipe.render/route-name] :as ctx}]
                       (lib.route/call router route-name :get ctx))})
