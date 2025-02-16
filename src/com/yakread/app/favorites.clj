(ns com.yakread.app.favorites
  (:require [com.biffweb :as biff]
            [com.yakread.lib.middleware :as lib.mid]))

(def page
  ["/dev/favorites"
   {:name :app.favorites/page}])

(def module
  {:routes [page]})
