(ns com.yakread.app.settings
  (:require
   [clojure.string :as str]
   [com.biffweb :as biff]
   [com.wsscode.pathom3.connect.operation :as pco :refer [?]]
   [com.yakread.lib.form :as lib.form]
   [com.yakread.lib.middleware :as lib.mid]
   [com.yakread.lib.pipeline :as lib.pipe]
   [com.yakread.lib.route :as lib.route :refer [defget defpost href]]
   [com.yakread.lib.ui :as ui])
  (:import
   [java.time Period LocalTime ZoneId]
   [java.time.format DateTimeFormatter]))

(defn days-checkboxes [selected-days]
  [:.grid.grid-cols-2.sm:grid-cols-4.gap-x-6#days
   (for [k [:monday :tuesday :wednesday :thursday :friday :saturday :sunday]]
     [:.py-2
      (ui/checkbox {:ui/label (str/capitalize (name k))
                    :name :user/digest-days
                    :value (name k)
                    :checked (when (contains? selected-days k)
                               "checked")})])])

(declare page)

(defpost set-timezone
  :start
  (fn [{:keys [session biff.form/params]}]
    {:biff.pipe/next [(lib.pipe/tx
                       [{:db/doc-type :user
                         :xt/id (:uid session)
                         :db/op :update
                         :user/timezone (:user/timezone params)}])]
     :status 204}))

(defpost save-settings
  :start
  (fn [{:keys [session biff.form/params]}]
    (biff/pprint params)
    {:biff.pipe/next [(lib.pipe/tx
                       [(merge {:db/doc-type :user
                                :xt/id (:uid session)
                                :db/op :update
                                :user/use-original-links false}
                               (select-keys params [:user/digest-days
                                                    :user/send-digest-at
                                                    :user/timezone
                                                    :user/use-original-links]))])]
     :status 303
     :headers {"location" (href page)}}))

(def page
  ["/dev/settings"
   {:name :app.settings/page}])

(defn time-text [local-time]
  (.format local-time (DateTimeFormatter/ofPattern "h:mm a")))

(defget page "/dev/settings"
  [:app.shell/app-shell
   {(? :session/user) [:xt/id
                       :user/email
                       :user/digest-days
                       :user/send-digest-at
                       :user/timezone
                       (? :user/use-original-links)]}]
  (fn [ctx
       {:keys [app.shell/app-shell
               session/user]}]
    (let [{:user/keys [digest-days send-digest-at timezone
                       use-original-links]} user]
      (app-shell
       {:title "Settings"}
       (ui/page-header {:title "Settings"})
       [:fieldset.disabled:opacity-60
        (ui/page-well
         (ui/section
          {}
          (biff/form
            {:action (href save-settings)
             :class '[flex flex-col gap-6]}
            [:div
             (ui/input-label {} "Which days would you like to receive the digest email?")
             (days-checkboxes digest-days)]
            [:div
             (ui/input-label {} "What time of day would you like to receive the digest email?")
             (ui/select {:name :user/send-digest-at
                         :ui/options (for [i (range 24)
                                           :let [value (LocalTime/of i 0)]]
                                       {:label (time-text value) :value (str value)})
                         :ui/default (str send-digest-at)})]
            [:div
             (ui/input-label {} "Your timezone:")
             (ui/select {:name :user/timezone
                         :ui/options (for [zone-str (sort (ZoneId/getAvailableZoneIds))]
                                       {:label zone-str :value zone-str})
                         :ui/default (str timezone)})]

            [:div
             (ui/checkbox {:name :user/use-original-links
                           :ui/label "Open links on the original website:"
                           :ui/label-position :above
                           :checked (when use-original-links
                                      "checked")})]

            [:div (ui/button {:type "submit"} "Save")])))]))))

(def unsubscribe-success
  ["/dev/unsubscribed"
   {:get (fn [_] (ui/plain-page {} "You have been unsubscribed."))}])

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

(def parser-overrides
  {:user/digest-days (fn [x]
                       (into #{}
                             (map keyword)
                             (cond-> x (not (vector? x)) vector)))})

(def module
  {:routes [page
            unsubscribe-success
            click-unsubscribe-route
            ["" {:middleware [lib.mid/wrap-signed-in
                              [lib.form/wrap-parse-form
                               {:overrides parser-overrides}]]}
             save-settings
             set-timezone]]})
