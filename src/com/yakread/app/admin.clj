(ns com.yakread.app.admin
  (:require
   [com.yakread.routes :as routes]
   [com.yakread.lib.route :refer [href]]))

(def redirect
  ["/admin"
   {:get (fn [_]
           {:status 303
            :headers {"location" (href routes/admin-dashboard)}})}])

(def module
  {:routes [redirect]})
