(ns com.yakread.app.admin
  (:require [com.biffweb :as biff]
            [com.yakread.middleware :as mid]))

(def page-route ["/dev/admin" {:name :app.admin/page}])

(def module
  {:routes [page-route]})
