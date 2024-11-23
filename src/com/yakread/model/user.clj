(ns com.yakread.model.user
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [com.biffweb :as biff :refer [q]]
            [com.wsscode.pathom3.connect.operation :as pco :refer [defresolver ?]]
            [com.yakread.lib.user :as lib.user]))

(defresolver current-user [{:keys [session]} _]
  #::pco{:output [{:user/current [:xt/id]}]}
  (when (:uid session)
    {:user/current {:xt/id (:uid session)}}))

(defresolver admin [{:keys [user/roles]}]
  {:user/admin (contains? roles :admin)})

(defresolver roles [_ {:keys [user/email]}]
  {:user/roles (cond-> #{}
                 (= email "jacob@thesample.ai") (conj :admin))})

(defresolver email-username [{:keys [biff/db]} {user-id :xt/id :user/keys [email-username*]}]
  #::pco{:input [:xt/id (? :user/email-username*)]
         :output [:user/email-username]}
  (some->> (or email-username*
               (first
                (q db
                   '{:find username
                     :in [user]
                     :where [[conn :conn/user user]
                             [conn :conn.email/username username]]}
                   user-id)))
           (hash-map :user/email-username)))

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

(def module {:resolvers [current-user
                         admin
                         roles
                         email-username
                         suggested-email-username]})
