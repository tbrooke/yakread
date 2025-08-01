(ns com.yakread.model.params
  (:require
   [com.wsscode.pathom3.connect.operation :as pco :refer [defresolver]]))

(defresolver redirect-url [{:keys [com.yakread/sign-redirect params]} {}]
  #::pco{:output [:params/redirect-url]}
  (let [{:keys [redirect redirect-sig]} params]
    (when (and (some? redirect-sig)
               (= redirect-sig (some-> redirect sign-redirect :redirect-sig)))
      {:params/redirect-url redirect})))

(defresolver paginate-after [{:keys [params]} _]
  #::pco{:output [:params/paginate-after]}
  (when (uuid? (:after params))
    {:params/paginate-after (:after params)}))

(def module
  {:resolvers [redirect-url
               paginate-after]})
