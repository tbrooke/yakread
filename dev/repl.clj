(ns repl
  (:require [com.biffweb :as biff :refer [q]]
            [com.yakread :as main]
            [com.yakread.lib.route :as lib.route]
            [com.yakread.lib.pathom :as lib.pathom :refer [?]]
            [reitit.core :as reitit]
            [xtdb.api :as xt]))

(defn with-context [f]
  (let [ctx @main/system]
    (with-open [db #_(biff/open-db-with-index ctx) (xt/open-db (:biff.xtdb/node ctx))]
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
       (lib.pathom/process ctx
                           {}
                           [{:user/current [{:sub/_user
                                             [:xt/id
                                              :sub.view/card

                                              (? :sub/published-at)
                                              (? :sub/pinned-at)

                                              ]}]}]
                           )

       #_(lib.pathom/process ctx
                             #:xt{:id #uuid "8316de0a-8c41-4876-8347-e43dffde8f90"}
                             [;{:sub.feed/feed [:feed/url
                              ;                 (? :feed/title)]}
                              :sub/title
                              ]

                             )

       )))

  


  (with-context
    (fn [{:keys [biff/db]}]
      (xt/pull db '[*] #uuid "443307f5-e71a-4595-ac87-b04239ef5c7f")
      #_(q db
           '{:find (pull url [{:feed/_url [*]}])
             :in [url]
             ;:where [[sub :sub/user user]]

             }
           "http://timothypratley.blogspot.com/feeds/posts/default"
           ;#uuid "394a63c2-9191-4575-98d1-6b2fed04a7fc"
           )

      ))

  (com.yakread.app.subscriptions.add-test/get-current-ns)
  (com.yakread.lib.test/current-ns)

  (update-user! "hello@example.com" {:user/email-username* :db/dissoc})

  main/router

  (clj-http.client/get "https://example.com")
  )
