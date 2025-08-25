(ns com.yakread.app.admin.advertise
  (:require
   [com.wsscode.pathom3.connect.operation :refer [?]]
   [com.yakread.lib.admin :as lib]
   [com.yakread.lib.middleware :as lib.mid]
   [com.yakread.lib.pipeline :as pipe]
   [com.yakread.lib.route :as lib.route :refer [defget defpost href]]
   [com.yakread.lib.ui :as ui])
  (:import
   [java.time ZonedDateTime]))

(declare page-route)

(defpost update-ad
  :start
  (fn [{{:keys [ad]} :params}]
    {:biff.pipe/next [(pipe/tx [(merge ad {:db/doc-type :ad :db/op :update})])]
     :status 200
     :body ""}))

(defget page-content-route "/admin/advertise/content"
  [{:admin/ads [:xt/id
                {:ad/user [:user/email]}
                :ad/updated-at
                :ad/balance
                :ad/state
                (? :ad/title)
                (? :ad/bid)
                (? :ad/budget)
                (? :ad/url)
                (? :ad/paused)
                (? :ad/payment-failed)
                (? :ad/payment-method)
                (? :ad/ui-preview-card)]}
   {:session/user [:user/timezone]}]
  (fn [ctx {:keys [admin/ads] admin :session/user}]
    (let [pending (filterv #(= :pending (:ad/state %)) ads)]
      [:<>
       [:.flex.flex-wrap
        (for [{:ad/keys [ui-preview-card] id :xt/id} pending]
          [:.pending-ad.flex.flex-col.gap-4.mb-8.mr-8
           [:.flex.gap-2
            (for [[state label icon] [[:approved "Approve" "check-solid"]
                                      [:rejected "Reject" "xmark-solid"]]]
              (ui/button {:hx-post (href update-ad {:ad {:xt/id id :ad/approve-state state}})
                          :hx-target "closest .pending-ad"
                          :hx-swap "outerHTML"
                          :ui/icon icon}
                label))]
           [:.max-w-screen-sm ui-preview-card]])]
       (ui/wide-page-well
        (ui/table
          ["Email" "Title" "State" "Balance" "Bid" "Budget" "Updated"]
          (for [{:ad/keys [user title state balance bid budget updated-at]}
                (sort-by :ad/updated-at #(compare %2 %1) ads)]
            [(:user/email user)
             title
             [:div.px-1 {:class [(case state
                                   :running "bg-tealv-50"
                                   (:paused :incomplete) "bg-yellv-50"
                                   (:rejected :payment-failed) "bg-redv-50"
                                   nil)]}
              (name state)]
             (some-> balance ui/fmt-cents)
             (some-> bid ui/fmt-cents)
             (some-> budget ui/fmt-cents)
             (.toLocalDate (ZonedDateTime/ofInstant updated-at (:user/timezone admin)))])))])))

(defget page-route "/admin/advertise"
  [:app.shell/app-shell]
  (fn [ctx {:keys [app.shell/app-shell]}]
    (app-shell
     {:wide true}
     (ui/page-header {:title "Admin"})
     (lib/navbar :advertise)
     (ui/lazy-load (href page-content-route)))))

(def module
  {:routes ["" {:middleware [lib.mid/wrap-admin]}
            page-route
            page-content-route
            update-ad]})
