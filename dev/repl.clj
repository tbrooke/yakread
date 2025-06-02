(ns repl
  (:require
   [com.biffweb :as biff :refer [q]]
   [com.yakread :as main]
   [com.yakread.lib.pathom :as lib.pathom]
   [com.yakread.lib.smtp :as lib.smtp]
   [xtdb.api :as xt]))

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


  (context/with-context
    (fn [{:keys [biff/db biff.xtdb/node session] :as ctx}]
      #_(xt/entity db :admin/moderation)
      #_(biff/lookup-all db :item.direct/candidate-status :blocked)

      #_(time
       (do
         (mapv first
               (q db
                  '{:find [(pull direct-item [*])]
                    :in [direct]
                    :where [[usit :user-item/item any-item]
                            [usit :user-item/favorited-at]
                            [any-item :item/url url]
                            [direct-item :item/url url]
                            [direct-item :item/doc-type direct]]}
                  :item/direct))
         nil))


      (tapped
       (process ctx [{:user/ad-rec [:ad/title
                                    :ad/click-cost]}]))


      ))

  )
