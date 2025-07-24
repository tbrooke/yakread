(ns com.yakread.app.settings
  (:require
   [com.biffweb :as biff]
   [com.yakread.lib.pipeline :as lib.pipe]
   [com.yakread.lib.route :as lib.route :refer [href]]
   [com.yakread.lib.ui :as ui]))

(def page
  ["/dev/settings"
   {:name :app.settings/page}])


(def unsubscribe-success
  ["/dev/unsubscribed"
   {:get
    (fn [_]
      (ui/plain-page
       {}
       "You have been unsubscribed."))}])

(def click-unsubscribe-route
  ["/dev/unsubscribe/:ewt"
   {:get
    (fn [{:keys [uri]}]
      [:html
       [:body
        (biff/form {:action uri})
        [:script (biff/unsafe "document.querySelector('form').submit();")]]])

    :post
    (lib.route/wrap-nippy-params
     (lib.pipe/make
      :start
      (fn [{:biff/keys [safe-params]}]
        (let [{:keys [action user/id]} safe-params]
          (if (not= action :action/unsubscribe)
            (ui/on-error {:status 400})
            {:biff.pipe/next [(lib.pipe/tx
                               [{:db/doc-type :user
                                 :db/op :update
                                 :xt/id id
                                 :user/digest-days #{}}])]
             :status 303
             :headers {"location" (href unsubscribe-success)}})))))}])

(def module
  {:routes [page
            unsubscribe-success
            click-unsubscribe-route]})
