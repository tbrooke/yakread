(ns com.yakread.lib.pipeline
  (:require [clj-http.client :as http]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.walk :as walk]
            [com.biffweb :as biff]
            [com.yakread.lib.error :as lib.error]
            [com.yakread.lib.pathom :as lib.pathom]
            [com.yakread.lib.route :as lib.route]
            [clojure.tools.logging :as log]
            [remus]
            [taoensso.nippy :as nippy]))

;; TODO
;; - include db basis in exception data
;; - include seed value for clojure.data.generators
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

;; TODO move into Biff with option
(defn- replace-db-now [tx]
  (let [now (java.time.Instant/now)]
    (walk/postwalk (fn [x]
                     (if (= x :db/now)
                       now
                       x))
                   tx)))

;; use the filesystem instead of S3
(defn mock-s3-request [{:biff.pipe.s3/keys [input] :as ctx}]
  (let [file (io/file "storage/mock-s3" (:key input))]
    (case (:method input)
      "PUT" (spit (doto file io/make-parents)
                  (pr-str (select-keys input [:headers :body])))
      "GET" (edn/read-string (slurp file))
      "DELETE" (do (.delete file) nil))))

(def global-handlers
  {:biff.pipe/http (fn [{:biff.pipe.http/keys [input] :as ctx}]
                     (assoc ctx :biff.pipe.http/output (-> (http/request input)
                                                           (assoc :url (:url input))
                                                           (dissoc :http-client))))
   :biff.pipe/tx (fn [{:keys [biff.pipe.tx/input] :as ctx}]
                   (assoc ctx :biff.pipe.tx/output (biff/submit-tx ctx (replace-db-now input))))
   :biff.pipe/pathom (fn [{:biff.pipe.pathom/keys [entity query] :as ctx}]
                       (assoc ctx
                              :biff.pipe.pathom/output
                              (lib.pathom/process ctx (or entity {}) query)))
   :biff.pipe/slurp (fn [{:keys [biff.pipe.slurp/input] :as ctx}]
                      (assoc ctx :biff.pipe.slurp/output (slurp input)))
   :biff.pipe/render (fn [{:keys [biff/router biff.pipe.render/route-name] :as ctx}]
                       (lib.route/call router route-name :get ctx))
   :biff.pipe/queue (fn [{:biff.pipe.queue/keys [id job wait-for-result] :as ctx}]
                      (assoc ctx
                             :biff.pipe.queue/output
                             (cond-> ((if wait-for-result
                                        biff/submit-job-for-result
                                        biff/submit-job)
                                      ctx
                                      id
                                      job)
                               wait-for-result deref)))
   :com.yakread.pipe/remus (fn [{:com.yakread.pipe.remus/keys [url opts] :as ctx}]
                             (assoc ctx
                                    :com.yakread.pipe.remus/output
                                    (update (remus/parse-url url opts) :response dissoc :http-client :body)))
   :biff.pipe/s3 (fn [{:keys [biff.pipe.s3/input] :as ctx}]
                   ;; TODO use config to decide whether to use s3 or filesystem
                   (assoc ctx :biff.pipe.s3/output (mock-s3-request ctx) #_(biff/s3-request ctx input)))})
