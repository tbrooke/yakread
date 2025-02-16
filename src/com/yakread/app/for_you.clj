(ns com.yakread.app.for-you
  (:require [com.biffweb :as biff]
            [com.yakread.lib.middleware :as lib.mid]))

(def page
  ["/dev/for-you"
   {:name :app.for-you/page}])

(def module
  {:routes [page]})
