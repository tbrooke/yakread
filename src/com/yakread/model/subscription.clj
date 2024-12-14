(ns com.yakread.model.subscription
  (:require [com.biffweb :as biff :refer [q <<-]]
            [com.wsscode.pathom3.connect.operation :as pco :refer [defresolver ?]]
            [com.yakread.lib.serialize :as lib.serialize]
            [clojure.set :as set]
            [xtdb.api :as xt]))

(def schema
  {:sub/id             any?
   :sub/user           :biff.attr/ref
   :sub/newsletter     :string
   :sub/feed           :string
   :sub/kind           [:enum
                        :sub.kind/email
                        :sub.kind/rss]
   :sub/title          :string
   :sub/unread         :int
   :sub/last-published :time/instant
   :sub/pinned         :boolean
   :sub/total          :int
   :sub/read           :int
   :sub/items          [:vector :biff.attr/ref]})

(defn- rss-sub-doc [conn feed sub pinned]
  (let [{:conn.rss/keys [url title subscribed-at]} conn]
    {:sub/title          (or title url)
     :sub/kind           :sub.kind/rss
     :sub/unread         (- (:feed/n-items feed 0) (:sub/read sub 0))
     :sub/pinned         (contains? (:pinned/rss pinned) (:xt/id conn))
     :sub/last-published (max-key inst-ms subscribed-at (:feed/last-published feed #inst "1970"))
     :sub/feed           url
     :sub/conn-id        (:xt/id conn)
     :sub/user           (:conn/user conn)}))

(defresolver rss-sub-items [{:keys [biff/db]} {:keys [sub/feed]}]
  #::pco{:output [{:sub/items [:xt/id]}]}
  {:sub/items (vec (q db
                      '{:find (pull item [:xt/id])
                        :in [url]
                        :where [[item :item.rss/feed-url url]]}
                      feed))})

(defresolver rss-sub [{:keys [biff/db]} {:keys [conn/user conn.rss/url] :as conn}]
  #::pco{:input [:sub/conn-id
                 :conn/user
                 :conn.rss/url
                 :conn.rss/subscribed-at
                 (? :conn.rss/title)]
         :output [:sub/title
                  :sub/kind
                  :sub/unread
                  :sub/last-published
                  :sub/pinned
                  :sub/feed
                  :sub/conn-id
                  :sub/user]}
  (let [feed   (biff/index-get db :rss-subscriptions [:feed url])
        sub    (biff/index-get db :rss-subscriptions [:sub user url])
        pinned (biff/lookup db :pinned/user user)]
    (rss-sub-doc conn feed sub pinned)))

(defresolver rss-sub-from-params [{:keys [biff/db session path-params]} _]
  #::pco{:output [{:params/sub [:xt/id :sub/conn-id]}]}
  (let [conn-id (biff/catchall (:sub/conn-id (lib.serialize/base64->edn (:entity path-params))))
        conn (when (uuid? conn-id)
               (xt/entity db conn-id))]
    (when (and conn (= (:uid session) (:conn/user conn)))
      {:params/sub (merge conn {:sub/conn-id conn-id})})))

(defresolver subscriptions [{:keys [biff/db]} {user-id :xt/id}]
  #::pco{:output [{:user/subscriptions [:sub/title
                                        :sub/kind
                                        :sub/unread
                                        :sub/last-published
                                        :sub/pinned
                                        :sub/user
                                        (? :sub/newsletter)
                                        (? :sub/feed)
                                        (? :sub/conn-id)]}]}
  {:user/subscriptions
   (let [pinned     (biff/lookup db :pinned/user user-id)
         email-subs (->> (biff/index-get db :subscriptions [:user-subs user-id])
                         (mapv (fn [nl]
                                 [:sub user-id nl]))
                         (biff/index-get-many db :subscriptions)
                         (keep (fn [{:sub/keys [hidden total newsletter last-published] sub-read :sub/read}]
                                 (when (not= hidden total)
                                   {:sub/title newsletter
                                    :sub/kind :sub.kind/email
                                    ;; TODO this max shouldn't be necessary
                                    :sub/unread (max 0 (- total (or sub-read 0)))
                                    :sub/pinned (contains? (:pinned/newsletters pinned)
                                                           newsletter)
                                    :sub/last-published last-published
                                    :sub/newsletter newsletter}))))
         rss-conns (->> (biff/index-get db :rss-subscriptions [:conns user-id])
                        (mapv (fn [[conn-id conn]]
                                (assoc conn :xt/id conn-id))))
         feeds     (biff/index-get-many db
                                        :rss-subscriptions
                                        (for [{:keys [conn.rss/url]} rss-conns]
                                          [:feed url]))
         rss-subs  (biff/index-get-many db
                                        :rss-subscriptions
                                        (for [{:keys [conn.rss/url]} rss-conns]
                                          [:sub user-id url]))
         rss-subs  (mapv (fn [conn feed sub]
                           (rss-sub-doc conn feed sub pinned))
                         rss-conns
                         feeds
                         rss-subs)]
     (vec (sort-by :sub/last-published #(compare %2 %1) (concat email-subs rss-subs))))})

(def email-indexer
  {:id :subscriptions
   :version 1
   :indexer
   (fn [{:biff.index/keys [index-get op doc]}]
     (cond
       (and (= op ::xt/put) (:item.email/user doc))
       (let [{:keys [item.email/user
                     item/author-name
                     item.email/hidden]} doc
             author-name (or author-name "")
             item-id (:xt/id doc)
             sub-id [:sub user author-name]
             old-sub (index-get sub-id)
             last-published-ms (or (some-> (:sub/last-published old-sub) inst-ms) 0)
             new-sub (merge {:sub/newsletter author-name}
                            old-sub
                            (when (< last-published-ms (inst-ms (:item/fetched-at doc)))
                              {:sub/last-published (:item/fetched-at doc)
                               :sub/total (inc (:sub/total old-sub 0))}))

             item-exists (some? (index-get item-id))

             item-state-id [:item-state user item-id]
             old-state (when item-exists
                         (index-get item-state-id))
             new-state (merge old-state
                              {:item-state/hidden (boolean hidden)})
             hidden-changed (not= (boolean (:item-state/hidden old-state))
                                  (boolean hidden))
             read? (fn [{:item-state/keys [read hidden]}]
                     (boolean (or read hidden)))
             read-changed (not= (read? old-state) (read? new-state))
             new-sub (cond-> new-sub
                       hidden-changed (update :sub/hidden (fnil + 0) (if hidden 1 -1))
                       read-changed (update :sub/read (fnil + 0) (if (read? new-state) 1 -1)))

             user-subs-id [:user-subs user]]
         (merge (when (not= old-sub new-sub)
                  {sub-id new-sub})
                (when (not old-sub)
                  {user-subs-id ((fnil conj #{}) (index-get user-subs-id) author-name)})
                (when hidden-changed
                  {item-state-id new-state})
                (when-not item-exists
                  {[:author-name (:xt/id doc)] (or (:item/author-name doc) "")})))

       (:rec/user doc)
       (let [{:keys [rec/user] item-id :rec/item} doc
             item-state-id [:item-state user item-id]
             old-state     (update (index-get item-state-id) :item-state/read boolean)
             read-state    (= op ::xt/put)
             new-state     (merge old-state
                                  {:item-state/read read-state})
             changed       (not= read-state (boolean (:item-state/read old-state)))

             author-name  (or (index-get [:author-name item-id]) :none)
             sub-id       [:sub user author-name]
             old-sub      (index-get sub-id)
             read?        (fn [{:item-state/keys [read hidden]}]
                            (boolean (or read hidden)))
             read-changed (not= (read? old-state) (read? new-state))
             new-sub      (cond-> old-sub
                            read-changed (update :sub/read (fnil (if read-state inc dec) 0)))]
         (merge (when (not= old-state new-state)
                  {item-state-id new-state})
                (when (and old-sub read-changed)
                  {sub-id new-sub})))))})

(def rss-indexer
  {:id :rss-subscriptions
   :version 3
   :indexer
   (fn [{:biff.index/keys [index-get op doc]}]
     (cond
       (and (= op ::xt/put) (:item.rss/feed-url doc))
       (let [{item-id :xt/id :keys [item.rss/feed-url item/fetched-at]} doc
             feed-items-id  [:feed-items feed-url]
             old-feed-items (or (index-get feed-items-id) #{})
             new-feed-items (conj old-feed-items item-id)]
         (merge (when (not= old-feed-items new-feed-items)
                  {feed-items-id    new-feed-items
                   [:feed feed-url] {:feed/last-published fetched-at
                                     :feed/n-items (count new-feed-items)}})
                (when-not (index-get [:item-feed-url item-id])
                  {[:item-feed-url item-id] feed-url})))

       (:rec/user doc)
       (<<- (let [{:keys [rec/item rec/user]} doc
                  feed-url (index-get [:item-feed-url item])])
            (when feed-url)
            (let [items-read-id  [:items-read user feed-url]
                  old-items-read (or (index-get items-read-id) #{})
                  new-items-read ((if (= ::xt/put op) conj disj) old-items-read item)])
            (when (not= old-items-read new-items-read))
            {items-read-id        new-items-read
             [:sub user feed-url] {:sub/read (count new-items-read)}})

       (:conn.rss/url doc)
       (let [{conn-id :xt/id :conn/keys [user] :as conn} doc
             conns-id  [:conns user]
             old-conns (index-get conns-id)
             new-conns (if (= op ::xt/put)
                         (assoc old-conns conn-id (select-keys conn
                                                               [:conn.rss/url
                                                                :conn.rss/title
                                                                :conn.rss/subscribed-at]))
                         (dissoc old-conns conn-id))]
         (when (not= old-conns new-conns)
           {conns-id new-conns}))))})

(def module {:resolvers [#_subscriptions
                         #_rss-sub
                         #_rss-sub-from-params
                         #_rss-sub-items]
             :indexes [#_email-indexer
                       #_rss-indexer]})

(comment
  (inc 3)
  (def input (time (biff/indexer-input (:biff.xtdb/node @com.yakread/system)
                                       nil
                                       nil
                                       ;;#inst "2024-09-26T23:55:49.834-00:00"
                                       ;;#inst "2025-09-27T23:55:49.834-00:00"
                                       )))

  (count input)

  (update (biff/indexer-results (:indexer rss-indexer) input)
          :results
          (fn [results]
            (mapv (fn [{:keys [main-tx changes]}]
                    {:input (:biff.index/input main-tx)
                     :changes changes})
                  results)))


  )
