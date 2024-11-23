(ns com.yakread.app.read-later
  (:require [com.biffweb :as biff]
            [com.yakread.middleware :as mid]))

(def page
  ["/dev/read-later"
   {:name :app.read-later/page}])

(def module
  {:routes [page]})
