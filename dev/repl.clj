(ns repl
  (:require
   [clojure.java.io :as io]
   [com.biffweb :as biff :refer [q]]
   [com.yakread :as main]
   [com.yakread.lib.pathom :as lib.pathom]
   [com.yakread.lib.smtp :as lib.smtp]
   [taoensso.tufte :as tufte :refer [p]]
   [clj-http.client :as http]
   [xtdb.api :as xt]
   [cheshire.core :as cheshire]))

;;;; export these vars to be used in rich-comment forms, e.g.

;; (comment
;;   (do-something (repl/context))
;;   )

(defn context [& {:keys [session-email]
                  :or {session-email "hello@obryant.dev"}}]
  (let [{:keys [biff/db] :as ctx} (biff/merge-context @main/system)]
    (merge ctx
         (when session-email
           {:session {:uid (biff/lookup-id db :user/email session-email)}}))))

(defn with-context [f & {:as opts}]
  (let [ctx (context opts)]
    (with-open [db (biff/open-db-with-index ctx)]
      (f (assoc ctx :biff/db db)))))

(def process lib.pathom/process)

(def ? lib.pathom/?)

(def hello-uid #uuid "57f88a5f-2b55-4eb3-8866-e32a48ec8baa")

(defn tapped* [f]
  (let [done (promise)
        results (atom [])
        tap-fn (fn [x]
                 (if (= x ::done)
                   (deliver done nil)
                   (swap! results conj x)))
        _ (add-tap tap-fn)
        f-result (try
                   (f)
                   (catch Exception e
                     e))]
    (tap> ::done)
    @done
    (remove-tap tap-fn)
    {:return f-result
     :tapped @results}))

(defmacro tapped [& body]
  `(tapped* (fn [] ~@body)))

;;;; ---------------------------------------------------------------------------

(defn- update-user! [email kvs]
  (biff/submit-tx @main/system
    [(merge {:db/doc-type :user
             :db.op/upsert {:user/email email}}
            kvs)]))

(defmacro print-profiled [& body]
  `(let [[result# pstats#] (tufte/profiled {} ~@body)]
     (println (tufte/format-pstats @pstats#))
     result#))

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

  (time
   (with-context
     (fn [{:keys [biff/db biff.xtdb/node session biff/secret mailersend/from mailersend/reply-to] :as ctx}]
       (biff/submit-job ctx
                        :work.subscription/sync-feed
                        {:feed/id (first
                                   (q db
                                      '{:find id
                                        :where [[id :feed/url]]}))
                         :biff/priority 0})
       )
     :session-email "hello@obryant.dev"))

  (update-user! "hello@obryant.dev" {:user/timezone :db/dissoc})


  )
