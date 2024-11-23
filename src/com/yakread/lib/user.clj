(ns com.yakread.lib.user
  (:require [com.biffweb :as biff :refer [q]]
            [clojure.string :as str]))

(let [reserved #{"hello"
                 "support"
                 "contact"
                 "admin"
                 "administrator"
                 "webmaster"
                 "hostmaster"
                 "postmaster"}]
  (defn normalize-email-username [username]
    (let [username (-> (or username "")
                       (str/lower-case)
                       (str/replace #"[^a-z0-9\.]" "")
                       (->> (take 20)
                            (apply str)))]
      (when-not (reserved username)
        username))))

(defn email-username-taken? [db username]
  (some?
   (first
    (q db
       '{:find doc
         :in [username]
         :where [(or [doc :conn.email/username username]
                     [doc :user/email-username* username])]}
       username))))
