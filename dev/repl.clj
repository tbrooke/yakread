(ns repl
  (:require [com.biffweb :as biff :refer [q]]
            [com.yakread :as main]
            [com.yakread.lib.route :as lib.route]
            [com.yakread.lib.pathom :as lib.pathom :refer [?]]
            [reitit.core :as reitit]
            [xtdb.api :as xt]))

(defn with-context [f]
  (let [ctx @main/system]
    (with-open [db (biff/open-db-with-index ctx)]
      (f (assoc (biff/merge-context ctx)
                :biff/db db
                :session {:uid (biff/lookup-id db :user/email "hello@example.com")})))))

(defn update-user! [email kvs]
  (biff/submit-tx @main/system
    [(merge {:db/doc-type :user
             :db.op/upsert {:user/email email}}
            kvs)]))

(comment

  (main/refresh)

  (with-context
    (fn [{:keys [biff/db session] :as ctx}]
      (lib.pathom/process ctx
                          {}
                          [{:user/current [:xt/id
                                           {:user/subscriptions [:sub/title]}
                                           ]}])))


  (with-context
    (fn [{:keys [biff/db]}]
      (vec (q db
              '{:find (pull conn [*])
                :where [[conn :conn/user]]}))))

  (com.yakread.app.subscriptions.add-test/get-current-ns)
  (com.yakread.lib.test/current-ns)

  (update-user! "hello@example.com" {:user/email-username* :db/dissoc})

  main/router

  (clj-http.client/get "https://example.com")
  )
