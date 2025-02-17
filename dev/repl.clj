(ns repl
  (:require [com.biffweb :as biff :refer [q]]
            [com.yakread :as main]
            [com.yakread.smtp :as smtp]
            [com.yakread.lib.route :as lib.route]
            [com.yakread.lib.pathom :as lib.pathom :refer [?]]
            [com.yakread.lib.smtp :as lib.smtp]
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

  (do
    (-> (#'lib.smtp/deliver-opts "whatup@yakread.com" (slurp "resources/emails/indie1.txt"))
        (dissoc :raw :headers :content)
        )
    :done)

  (.getSubject (lib.smtp/parse (slurp "resources/emails/indie.txt")))

  (lib.smtp/send-local!
   {:path "resources/emails/hnblogs1.txt"
    :to "whatup@yakread.com"})

  (lib.smtp/send-local!
   {:from "hello@obryant.dev"
    :to "whatup@yakread.com"
    :subject "test message"
    :rum [:html
          [:head
           [:style (biff/unsafe "p { color: red; }")]]
          [:body
           [:p "how do you do "
            [:a {:href "https://example.com/"} "click me"]]]]})

  (tapped
   (try
     (with-context
       (fn [{:keys [biff/db session] :as ctx}]
         (lib.pathom/process
          (-> ctx
              (assoc :path-params {:item-id "eWrwK063TYypMDhtG0NP0Q"
                                   :sub-id "l0irZnG-ST2DzldKN_A6AQ"})
              #_(dissoc :session))
          [{:session/user [:xt/id]}
           {:params/sub [{:sub/items [:item/id :item/unread]}]}
           #_{:params/item [:xt/id
                          {(? :item/sub) [:xt/id]}
                          {:item/user-item [(? :user-item/disliked-at)
                                            (? :user-item/favorited-at)
                                            ]}
                          ]}]
          
          )))
     (catch clojure.lang.ExceptionInfo e
       (:missing (ex-data e))
       )
     )
   )


  (with-context
    (fn [{:keys [biff/db biff.xtdb/node] :as ctx}]
      #_(xt/pull db '[*] #uuid "796af02b-4eb7-4d8c-a930-386d1b434fd1")
      (q db '{:find (pull doc [*])
              :where [[doc :item.email/sub]]})
      ;(xt/submit-tx node [[::xt/delete #uuid "de587b6d-93b4-41db-ad21-7f1a6f669b30"]])

      ))

  (java.time.Instant/parse "1970-01-01T00:00:00Z")


  (com.yakread.app.subscriptions.add-test/get-current-ns)
  (com.yakread.lib.test/current-ns)

  (update-user! "hello@example.com" {:user/email-username* :db/dissoc})

  main/router

  (clj-http.client/get "https://example.com")
  )
