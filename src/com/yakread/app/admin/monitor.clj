(ns com.yakread.app.admin.monitor
  (:require
   [com.yakread.lib.admin :as lib]
   [com.yakread.lib.middleware :as lib.mid]
   [com.yakread.lib.route :as lib.route :refer [defget href]]
   [com.yakread.lib.ui :as ui]
   [taoensso.tufte :as tufte]))

(declare page-route)

(defget page-content-route "/admin/monitor/content"
  []
  (fn [ctx {:admin.moderation/keys [next-batch remaining approved blocked ingest-failed]}]
    (ui/wide-page-well
     [:div "how now brown cow"])))

(defget page-route "/admin/monitor"
  [:app.shell/app-shell]
  (fn [{:keys [com.yakread/pstats]} {:keys [app.shell/app-shell]}]
    (let [days (take 7 (iterate #(.minusDays % 1) (java.time.LocalDate/now)))
          pstats (keep (comp @pstats str) days)
          pstats (if (empty? pstats)
                   nil
                   (reduce tufte/merge-pstats pstats))]
      [:pre
       (tufte/format-pstats
        (update @pstats :stats update-keys #(com.yakread.lib.content/truncate (str %) 50)))])))

(def module
  {:routes ["" {:middleware [lib.mid/wrap-admin]}
            page-route
            page-content-route]})
