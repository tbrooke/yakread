(ns com.yakread.lib.middleware
  (:require [clojure.edn :as edn]
            [com.yakread.lib.route :as lib.route]
            [com.wsscode.pathom3.error :as p.error]))

(defn wrap-signed-in [handler]
  (fn [{:keys [session] :as ctx}]
    (if (:uid session)
      (handler ctx)
      {:status 303
       :headers {"location" "/signin?error=not-signed-in"}})))

(defn wrap-edn-json-params [handler]
  (fn [{:keys [params] :as ctx}]
    (handler
     (cond-> ctx
       (:edn params) (update :params merge (edn/read-string (:edn params)))))))

(defn wrap-router-response [handler]
  (fn [{:keys [biff/router] :as ctx}]
    (let [{route-name :biff.router/name
           params     :biff.router/params
           :as response} (handler ctx)]
      (if route-name
        (assoc-in response [:headers "location"] (lib.route/path router route-name params))
        response))))

(defn wrap-pathom-error [handler]
  (fn [ctx]
    (try
      (handler ctx)
      (catch clojure.lang.ExceptionInfo e
        (let [data (ex-data e)]
          (cond
            (and (= (::p.error/cause data) ::p.error/attribute-missing)
                 (some #(= "params" (namespace %)) (keys (:missing data))))
            {:status 400}

            (and (= (::p.error/cause data) ::p.error/attribute-missing)
                 (some #(= "session" (namespace %)) (keys (:missing data))))
            {:status 401}

            :else
            (throw e)))))))
