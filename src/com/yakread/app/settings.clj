(ns com.yakread.app.settings
  (:require [com.biffweb :as biff]
            [com.yakread.lib.middleware :as lib.mid]))

(def page
  ["/dev/settings"
   {:name :app.settings/page}])

(def module
  {:routes [page]})
