(ns com.yakread.work.digest
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [com.biffweb :as biff :refer [q]]
   [com.yakread.lib.core :as lib.core]
   [com.yakread.lib.pipeline :as lib.pipe :refer [defpipe]]))

(defn in-send-time-window? [{:keys [biff/now]}
                            {:user/keys [digest-days send-digest-at timezone]
                             :or {digest-days #{:sunday :monday :tuesday :wednesday :thursday :friday :saturday}
                                  send-digest-at (java.time.LocalTime/of 8 0)
                                  timezone (java.time.ZoneId/of "US/Pacific")}}]
  (let [now-date (.. now
                     (atZone (java.time.ZoneId/of "US/Pacific"))
                     (toLocalDate))
        send-at-begin (java.time.ZonedDateTime/of
                       now-date
                       send-digest-at
                       timezone)
        send-at-begin (cond-> send-at-begin
                        (lib.core/increasing? now (.toInstant send-at-begin))
                        (.minusDays 1))
        send-at-end (.plusHours send-at-begin 2)
        now-day (.. now (atZone timezone) (getDayOfWeek))]
    (and (lib.core/increasing? (.toInstant send-at-begin)
                               now
                               (.toInstant send-at-end))
         (boolean
          (some (fn [day]
                  (= (java.time.DayOfWeek/valueOf (str/upper-case (name day)))
                     now-day))
                digest-days)))))

(defn send-digest? [{:keys [biff/now]} user]
  (and (lib.core/increasing? (:user/digest-last-sent user lib.core/epoch)
                             (.minusSeconds now (* 60 60 18)))
       (in-send-time-window? {:biff/now now} user)
       (not (:user/suppressed-at user))))

(defpipe queue-send-digest
  :start
  (fn [{:biff/keys [db queues] :as ctx}]
    (when (= 0 (.size (:work.digest/send-digest queues)))
      (let [users (filterv #(send-digest? ctx %)
                           (q db '{:find (pull user [*])
                                   :where [[user :user/email]]}))]
        (when (not-empty users)
          (log/info "Sending digest to" (count users) "users"))
        {:biff.pipe/next (for [user users]
                           (lib.pipe/queue :work.digest/send-digest user))}))))

;; user stuff:
;; - email
;; - send settings

;; notes:
;; - discover section should open links on original website by default (introduce another setting?)
;; - record items: ad, icymi, discover
;; - digest algorithm should also take into account previous digest sends (treat as skips)
;;   - thank goodness query performance doesn't matter as much here

;; - ad (1)
;; - recent subscription posts (all since last digest/up to 2 weeks/n max)
;; - recent bookmarks (all since last digest/up to 2 weeks/n max)
;; - in case you missed it (for you: subs/bookmarks, max 5)
;; - discover (max 5)


(defpipe send-digest!
  :start
  (fn [{:keys [biff/job]}]
    nil))

(def module
  {:tasks [;; TODO uncomment
           #_{:task #'queue-send-digest
            :schedule (lib.core/every-n-minutes 10)}]
   :queues [{:id :work.digest/send-digest
             :consumer #'send-digest!
             :n-threads 1}]})
