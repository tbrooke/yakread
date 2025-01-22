(ns repl
  (:require [com.biffweb :as biff :refer [q]]
            [com.yakread :as main]
            [com.yakread.lib.route :as lib.route]
            [com.yakread.lib.pathom :as lib.pathom :refer [?]]
            [reitit.core :as reitit]
            [xtdb.api :as xt]))

(defn with-context [f]
  (let [ctx @main/system]
    (with-open [db (biff/open-db-with-index ctx) #_(xt/open-db (:biff.xtdb/node ctx))]
      (f (assoc (biff/merge-context ctx)
                :biff/db db
                :session {:uid (biff/lookup-id db :user/email "hello@obryant.dev")})))))

(defn update-user! [email kvs]
  (biff/submit-tx @main/system
    [(merge {:db/doc-type :user
             :db.op/upsert {:user/email email}}
            kvs)]))

(defn tapped* [f]
  (let [done (promise)
        results (atom [])
        tap-fn (fn [x]
                 (if (= x ::done)
                   (deliver done nil)
                   (swap! results conj x)))
        _ (add-tap tap-fn)
        f-result (f)]
    (tap> ::done)
    @done
    (remove-tap tap-fn)
    {:return f-result
     :tapped @results}))

(defmacro tapped [& body]
  `(tapped* (fn [] ~@body)))

(comment

  (main/refresh)

  (tapped
   (with-context
     (fn [{:keys [biff/db session] :as ctx}]
       (lib.pathom/process
        (assoc ctx :path-params {:item-id "7CD1ilA9QyqOyM9sKIKByQ"})
        {}
        [{:params/item-unsafe [:xt/id
                               (? :item/content)
                               (? :item/content-key)
                               {:item/sub [:xt/id
                                           :sub/user]}]}]
        #_[{:user/current [{:sub/_user
                          [:sub/id
                           :sub/title
                           :sub/unread
                           :sub/published-at
                           {:sub/items [:xt/id
                                        :item/title
                                        ]}
                           ;:sub.view/card

                           ;(? :sub/published-at)
                           ;(? :sub/pinned-at)

                           ]}]}]))))


  (with-context
    (fn [{:keys [biff/db]}]
      (for [feed-id (q db
                       '{:find doc
                         :where [[doc :feed/url]]})]
        [feed-id (biff/index-get db :last-published feed-id)])))

(java.time.Instant/parse "1970-01-01T00:00:00Z")


  (com.yakread.app.subscriptions.add-test/get-current-ns)
  (com.yakread.lib.test/current-ns)

  (update-user! "hello@example.com" {:user/email-username* :db/dissoc})

  main/router

  (clj-http.client/get "https://example.com")
  )
