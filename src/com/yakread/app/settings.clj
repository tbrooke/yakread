(ns com.yakread.app.settings
  (:require [com.biffweb :as biff]
            [com.yakread.middleware :as mid]))

(def page
  ["/dev/settings"
   {:name :app.settings/page}])

(def module
  {:routes [page]})
