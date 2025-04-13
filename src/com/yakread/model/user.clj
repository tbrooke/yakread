(ns com.yakread.model.user
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [com.biffweb :as biff :refer [q]]
            [com.wsscode.pathom3.connect.operation :as pco :refer [defresolver ?]]
            [com.yakread.lib.user :as lib.user]))

(defresolver session-user [{:keys [session]} _]
  #::pco{:output [{:session/user [:xt/id]}]}
  (when (:uid session)
    {:session/user {:xt/id (:uid session)}}))

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

(def module {:resolvers [session-user
                         signed-in
                         current-user
                         suggested-email-username
                         user-id
                         xt-id]})
