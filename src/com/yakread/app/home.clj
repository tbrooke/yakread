(ns com.yakread.app.home
  (:require [com.yakread.lib.route :refer [redirect]]
            [com.yakread.routes :as routes]))

(def home-page-route
  ["/"
   {:name :app.home/page
    :get (constantly (redirect routes/subs-page))}])

(def module
  {:routes [home-page-route]})
