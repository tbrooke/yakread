(ns com.yakread.model.user
  (:require
   [clojure.string :as str]
   [com.wsscode.pathom3.connect.operation :as pco :refer [? defresolver]]
   [com.yakread.lib.core :as lib.core]
   [com.yakread.lib.user :as lib.user]))

(defresolver session-user [{:keys [session]} _]
  #::pco{:output [{:session/user [:xt/id]}]}
  (when (:uid session)
    {:session/user {:xt/id (:uid session)}}))

(defresolver session-anon [{:keys [session]} _]
  #::pco{:output [{:session/anon []}]}
  (when-not (:uid session)
    {:session/anon {}}))

(defresolver signed-in [{:keys [session]} _]
  #::pco{:output [:session/signed-in]}
  {:session/signed-in (some? (:uid session))})

;; TODO switch everything to :session/user
(defresolver current-user [{:keys [session]} _]
  #::pco{:output [{:user/current [:xt/id]}]}
  (when (:uid session)
    {:user/current {:xt/id (:uid session)}}))

(defresolver suggested-email-username [{:keys [biff/db]} {:user/keys [email email-username]}]
  #::pco{:input [:user/email (? :user/email-username)]
         :output [:user/suggested-email-username]}
  (when-not email-username
    (let [suggested (some-> email
                            (str/split #"@")
                            first
                            str/lower-case
                            (str/replace #"(\+.*|yakread)" "")
                            lib.user/normalize-email-username)]
      (when-not (some->> suggested (lib.user/email-username-taken? db))
        {:user/suggested-email-username suggested}))))

(defresolver user-id [{:keys [xt/id user/email]}]
  {:user/id id})

(defresolver xt-id [{:keys [user/id]}]
  {:xt/id id})

(defresolver default-digest-days [_]
  {::pco/input [:user/email]}
  {:user/digest-days #{:sunday :monday :tuesday :wednesday :thursday :friday :saturday}})

(defresolver default-send-digest-at [_]
  {::pco/input [:user/email]}
  {:user/send-digest-at (java.time.LocalTime/of 8 0)})

(defresolver default-timezone [{:user/keys [timezone]}]
  {::pco/input [:user/email
                (? :user/timezone)]
   ::pco/output [:user/timezone
                 :user/timezone*]}
  {:user/timezone* timezone
   :user/timezone (or timezone (java.time.ZoneId/of "US/Pacific"))})

(defresolver premium [{:keys [biff/now]} {:user/keys [plan cancel-at]}]
  {::pco/input [(? :user/plan)
                (? :user/cancel-at)]}
  {:user/premium (boolean
                  (and plan
                       (or (not cancel-at)
                           (lib.core/increasing? now cancel-at))))})

(def module {:resolvers [session-user
                         session-anon
                         signed-in
                         current-user
                         suggested-email-username
                         user-id
                         xt-id
                         default-digest-days
                         default-send-digest-at
                         default-timezone
                         premium]})
