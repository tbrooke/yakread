(ns com.yakread.app.admin.dashboard
  (:require
   [com.yakread.lib.admin :as lib]
   [com.yakread.lib.middleware :as lib.mid]
   [com.yakread.lib.route :as lib.route :refer [defget href]]
   [com.yakread.lib.ui :as ui])
  (:import [java.time Instant ZonedDateTime ZoneId LocalDate]
           [java.time.format DateTimeFormatter]))

(defn past-30-days [now timezone]
  (let [today-zdt (ZonedDateTime/ofInstant now (ZoneId/of timezone))]
    (->> (iterate #(.minusDays % 1) today-zdt)
         (take 30)
         (mapv #(.toLocalDate %)))))

(declare page-route)

(defn fmt-inst [inst fmt timezone]
  (-> inst
      (ZonedDateTime/ofInstant (ZoneId/of timezone))
      (.format (java.time.format.DateTimeFormatter/ofPattern fmt))))

(defn table [headers rows]
  [:table
   [:thead.text-left
    [:tr
     (for [header headers]
       [:th header])]]
   [:tbody
    (for [row rows]
      [:tr.even:bg-neut-50
       (for [cell row]
         [:td cell])])]])

(defget page-content-route "/admin/dashboard/content"
  [{:admin/recent-users
    [:user/email
     :user/joined-at]}
   :admin/dau
   :admin/revenue]
  (fn [{:keys [biff/now]} {:admin/keys [recent-users dau revenue]}]
    (ui/wide-page-well
     [:.grid.xl:grid-cols-2.gap-8
      (ui/section
       {:title "Recent signups"}
       (table
         ["Email" "Joined"]
         (for [{:user/keys [email joined-at]} (sort-by :user/joined-at #(compare %2 %1) recent-users)]
           [email
            ;; TODO use config for timezone
            (fmt-inst joined-at "yyyy-MM-dd hh:mm a" "America/Denver")])))

      (ui/section
       {:title "Daily metrics"}
       (table
         ["Date" "DAU" "Revenue"]
         (for [date (past-30-days now "America/Denver")]
           [date
            (get dau date 0)
            (format "$%.2f" (/ (get revenue date 0) 100.0))])))])))

(defget page-route "/admin/dashboard"
  [:app.shell/app-shell]
  (fn [ctx {:keys [app.shell/app-shell]}]
    (app-shell
     {:wide true}
     (ui/page-header {:title "Admin"})
     (lib/navbar :dashboard)
     (ui/lazy-load (href page-content-route)))))

(def module
  {:routes ["" {:middleware [lib.mid/wrap-admin]}
            page-route
            page-content-route]})
