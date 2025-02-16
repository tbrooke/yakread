(ns com.yakread.app.read-later
  (:require [com.biffweb :as biff]
            [com.yakread.lib.middleware :as lib.mid]))

(def page
  ["/dev/read-later"
   {:name :app.read-later/page}])

(def module
  {:routes [page]})
