(ns com.yakread.lib.pipeline
  (:require [cheshire.core :as cheshire]
            [clj-http.client :as http]
            [clojure.data.generators :as gen]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.walk :as walk]
            [com.biffweb :as biff]
            [com.yakread.lib.datastar :as lib.d*]
            [com.yakread.lib.error :as lib.error]
            [com.yakread.lib.htmx :as lib.htmx]
            [com.yakread.lib.pathom :as lib.pathom]
            [com.yakread.lib.s3 :as lib.s3]
            [clojure.tools.logging :as log]
            [remus]
            [taoensso.nippy :as nippy]
            [xtdb.api :as xt]))

;; TODO rename to :biff/now, :biff/seed
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
                 now (java.time.Instant/now)
                 ctx (merge ctx result
                            extra-params
                            {:biff.pipe/next remaining
                             :biff.pipe/now now})
                 handler (or (get id->handler current)
                             (throw (ex-info (str "No handler for " current) {})))
                 seed (long (* (rand) Long/MAX_VALUE))]
             (recur (try
                      (binding [gen/*rnd* (java.util.Random. seed)]
                        (handler ctx))
                      (catch Exception e
                        (if (= (:biff.pipe/catch ctx) current)
                          (assoc ctx :biff.pipe/exception e)
                          (throw (lib.error/merge-ex-data
                                  e
                                  (lib.error/request-ex-data ctx*)
                                  (apply dissoc ctx (keys ctx*))
                                  {:biff.pipe/seed seed
                                   :biff.pipe/db-basis (some-> (:biff/db ctx) xt/db-basis)})))))))))))))

;; TODO move into Biff with option
(defn- replace-db-now [tx]
  (let [now (java.time.Instant/now)]
    (walk/postwalk (fn [x]
                     (if (= x :db/now)
                       now
                       x))
                   tx)))

(defn pathom-query [query next-state]
  (constantly
   {:biff.pipe/next [:biff.pipe/pathom next-state]
    :biff.pipe.pathom/query query}))

(defn s3 [k & [body content-type]]
  {:biff.pipe/current  :biff.pipe/s3
   :biff.pipe.s3/input (if body
                         {:method  "PUT"
                          :key     (str k)
                          :body    body
                          :headers {"x-amz-acl"    "private"
                                    "content-type" content-type}}
                         {:method "GET"
                          :key    (str k)})})

(defn call-js [fn-name opts]
  (:body
   (cheshire/parse-string
    (biff/sh
     "node" "-e" "console.log(JSON.stringify(require('./main.js').main(JSON.parse(fs.readFileSync(0)))))"
     :dir (str "cloud-fns/packages/yakread/" fn-name)
     :in (cheshire/generate-string opts))
    true)))

(def global-handlers
  {:biff.pipe/http (fn [{:biff.pipe.http/keys [input] :as ctx}]
                     (assoc ctx :biff.pipe.http/output (-> (http/request input)
                                                           (assoc :url (:url input))
                                                           (dissoc :http-client))))
   :biff.pipe/email (fn [ctx]
                      ;; TODO
                      ctx)
   :biff.pipe/tx (fn [{:biff.pipe.tx/keys [input retry] :as ctx}]
                   (assoc ctx :biff.pipe.tx/output
                          (biff/submit-tx
                            (cond-> ctx
                              retry (assoc :biff.xtdb/retry retry))
                            (replace-db-now input))))
   :biff.pipe/pathom (fn [{:biff.pipe.pathom/keys [entity query] :as ctx}]
                       (assoc ctx
                              :biff.pipe.pathom/output
                              (lib.pathom/process ctx (or entity {}) query)))
   :biff.pipe/slurp (fn [{:keys [biff.pipe.slurp/input] :as ctx}]
                      (assoc ctx :biff.pipe.slurp/output (slurp input)))
   :biff.pipe/render (fn [{:keys [biff/router biff.pipe.render/route-name] :as ctx}]
                       #_(lib.route/call router route-name :get ctx))
   :biff.pipe/render* (fn [{:keys [biff.pipe.render/route] :as ctx}]
                        ((get-in @(resolve route) [1 :get]) ctx))
   :biff.pipe/render-sse (fn [{:keys [biff.pipe.render-sse/route] :as ctx}]
                           {:sse [(lib.d*/merge-fragments ((get-in @(resolve route) [1 :get]) ctx))]})
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
   :yakread.pipe/js (fn [{:yakread.pipe.js/keys [fn-name input] :as ctx}]
                      ;; TODO use "the cloud" if configured
                      (assoc ctx :yakread.pipe.js/output (call-js fn-name input)))
   :biff.pipe/s3 (fn [{:keys [biff.pipe.s3/input] :as ctx}]
                   ;; TODO use config to decide whether to use s3 or filesystem
                   (assoc ctx :biff.pipe.s3/output (lib.s3/mock-request #_biff/s3-request ctx input)))})
