(ns com.yakread.lib.middleware
  (:require [clojure.edn :as edn]
            [com.yakread.lib.route :as lib.route]
            [com.wsscode.pathom3.error :as p.error]
            [taoensso.nippy :as nippy]
            [rum.core :as rum]))

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

(defn wrap-render-body [handler]
  (fn [ctx]
    (let [response (handler ctx)]
      (cond-> response
        (vector? (:body response)) (update :body rum/render-static-markup)))))

(defn- render [body]
  (str "<!DOCTYPE html>\n" (rum/render-static-markup body)))

(defn wrap-render-rum [handler]
  (fn [ctx]
    (let [response (handler ctx)]
      (cond
        (vector? response)
        {:status 200
         :headers {"content-type" "text/html"}
         :body (render response)}

        (vector? (:body response))
        (update response :body render)

        :else
        response))))
