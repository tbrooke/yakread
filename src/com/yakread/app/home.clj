(ns com.yakread.app.home)

(def home-page-route
  ["/"
   {:name :app.home/page
    :get (constantly {:status 303
                      :biff.router/name :app.subscriptions/page})}])

(def module
  {:routes [home-page-route]})
