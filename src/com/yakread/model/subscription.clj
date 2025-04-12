(ns com.yakread.model.subscription
  (:require [com.biffweb :as biff :refer [q <<-]]
            [com.yakread.util.biff-staging :as biffs]
            [com.wsscode.pathom3.connect.operation :as pco :refer [defresolver ?]]
            [com.yakread.lib.error :as lib.error]
            [com.yakread.lib.item :as lib.item]
            [com.yakread.lib.route :as lib.route]
            [com.yakread.lib.serialize :as lib.serialize]
            [com.yakread.lib.user-item :as lib.user-item]
            [clojure.string :as str]
            [clojure.set :as set]
            [xtdb.api :as xt])
  (:import [java.time Instant]))

(defresolver user-subs [{:keys [biff/db]} {:keys [user/id]}]
  #::pco{:output [{:user/subscriptions [:sub/id]}
                  {:user/unsubscribed [:sub/id]}]}
  (as-> id $
    (q db
       '{:find [sub t]
         :in [user]
         :where [[sub :sub/user user]
                 [(get-attr sub :sub.email/unsubscribed-at nil) [t ...]]]}
       $)
    (group-by (comp some? second) $)
    (update-vals $ #(mapv (fn [[id _]] {:sub/id id}) %))
    (merge {true [] false []} $)
    (set/rename-keys $ {false :user/subscriptions true :user/unsubscribed})))

(defresolver email-title [{:keys [sub.email/from]}]
  {:sub/title (str/replace from #"\s<.*>" "")})

(defresolver feed-sub-title [{:keys [sub.feed/feed]}]
  #::pco{:input [{:sub.feed/feed [:feed/title]}]}
  {:sub/title (:feed/title feed)})

(defresolver email-subtitle [{:keys [sub.email/latest-item]}]
  {::pco/input [{:sub.email/latest-item [:item.email/reply-to]}]}
  {:sub/subtitle (:item.email/reply-to latest-item)})

(defresolver feed-sub-subtitle [{:keys [sub.feed/feed]}]
  #::pco{:input [{:sub.feed/feed [:feed/url]}]}
  {:sub/subtitle (:feed/url feed)})

(defresolver latest-email-item [{:keys [biff/db]} {:sub/keys [doc-type id]}]
  {::pco/output [{:sub.email/latest-item [:item/id]}]}
  (when (= doc-type :sub/email)
    {:sub.email/latest-item
     {:item/id (ffirst (q db
                          '{:find [item t]
                            :in [sub]
                            :order-by [[t :desc]]
                            :limit 1
                            :where [[item :item.email/sub sub]
                                    [item :item/ingested-at t]]}
                          id))}}))

(defresolver sub-id->xt-id [{:keys [sub/id]}]
  {:xt/id id})

(defresolver sub-info [{:keys [xt/id sub/user sub.feed/feed sub.email/from]}]
  #::pco{:input [:xt/id
                 :sub/user
                 (? :sub.feed/feed)
                 (? :sub.email/from)]
         :output [:sub/id
                  :sub/source-id
                  :sub/doc-type]}
  (if from
    {:sub/id id
     :sub/source-id id
     :sub/doc-type :sub/email}
    {:sub/id id
     :sub/source-id (:xt/id feed)
     :sub/doc-type :sub/feed}))

(defresolver unread [{:keys [biff/db]} {:sub/keys [user source-id]}]
  {:sub/unread
   (let [total (or (biff/index-get db :unread source-id) 0)
         _read (or (biff/index-get db :unread [(:xt/id user) source-id]) 0)
         unread (- total _read)]
     unread)})

(defresolver published-at [{:keys [biff/db]} {:keys [sub/source-id]}]
  {::pco/output [:sub/published-at]}
  (when-some [published-at (biff/index-get db :last-published source-id)]
    {:sub/published-at published-at}))

;; TODO experiment with making this batch
(defresolver items [{:keys [biff/db]} {:sub/keys [source-id doc-type]}]
  {::pco/output [{:sub/items [:xt/id]}]}
  {:sub/items
   (mapv #(hash-map :xt/id %)
         (q db
            {:find 'item
             :in '[source]
             :where [['item
                      (case doc-type
                        :sub/feed :item.feed/feed
                        :sub/email :item.email/sub)
                      'source]]}
            source-id))})

(defresolver latest-item [{:keys [biff/db]} {:sub/keys [source-id doc-type]}]
  {::pco/output [{:sub/latest-item [:xt/id]}]}
  (when-some [id (ffirst
                  (q db
                     {:find '[item t]
                      :in '[source]
                      :where [['item
                               (case doc-type
                                 :sub/feed :item.feed/feed
                                 :sub/email :item.email/sub)
                               'source]
                              '[item :item/ingested-at t]]
                      :order-by '[[t :desc]]
                      :limit 1}
                     source-id))]
    {:sub/latest-item {:xt/id id}}))

(defresolver from-params [{:keys [biff/db biff/malli-opts session path-params params]} _]
  #::pco{:output [{:params/sub [:xt/id]}]}
  (let [sub-id (or (:sub/id params)
                   (lib.serialize/url->uuid (:sub-id path-params)))
        sub (when (some? sub-id)
              (xt/entity db sub-id))]
    (when (and sub (= (:uid session) (:sub/user sub)))
      {:params/sub (biffs/joinify @malli-opts sub)})))

(defresolver params-checked [{:keys [biff/db biff/malli-opts session params]} _]
  #::pco{:output [{:params.checked/subscriptions [:sub/id]}]}
  (let [subs* (mapv #(some->> (parse-uuid %) (xt/entity db))
                    (keys (:subs params)))]
    (when (every? #(= (:uid session) (:sub/user %)) subs*)
      {:params.checked/subscriptions (mapv #(biffs/joinify @malli-opts %) subs*)})))

(defn- index-update [index-get id f]
  (let [old-doc (index-get id)
        new-doc (f old-doc)]
    (when (not= old-doc new-doc)
      {id new-doc})))

(def last-published-index
  {:id :last-published
   :version 1
   :schema [:tuple :uuid :time/instant] ;; TODO maybe enforce this in tests/dev or something
   :indexer
   (fn [{:biff.index/keys [index-get op doc]}]
     (when-let [id (and (= op ::xt/put)
                        (lib.item/source-id doc))]
       (index-update index-get
                     id
                     (fn [last-published]
                       (->> [last-published (lib.item/published-at doc)]
                            (filterv some?)
                            (apply max-key inst-ms))))))})

(def unread-index
  {:id :unread
   :version 0
   :schema [:or {:registry {}}
            ;; user+source -> read
            [:tuple [:tuple :uuid :uuid] :int]
            ;; source -> total
            [:tuple :uuid :int]
            ;; item -> source
            [:tuple :uuid :uuid]
            ;; rec -> item-read?
            [:tuple :uuid [:enum true]]]
   :indexer
   (fn [{:biff.index/keys [index-get op doc]}]
     (let [source-id (lib.item/source-id doc)]
       (cond
         ;; When a new item is created, increment its source's unread count.
         (and (= op ::xt/put) source-id (-> (:xt/id doc) index-get nil?))
         {(:xt/id doc) source-id
          source-id ((fnil inc 0) (index-get source-id))}

         ;; When an item is read, decrement its source's unread count.
         (and (= op ::xt/put) (:user-item/user doc))
         (let [new-doc-read? (lib.user-item/read? doc)
               old-doc-read? (boolean (index-get (:xt/id doc)))
               source-id (index-get (:user-item/item doc))]
           (when (and source-id (not= new-doc-read? old-doc-read?))
             (let [id [(:user-item/user doc) source-id]
                   n-read ((fnil (if new-doc-read? inc dec) 0) (index-get id))]
               {(:xt/id doc) (when new-doc-read? true)
                id           (when (not= n-read 0) n-read)}))))))})

(defresolver unread-items [{:keys [sub/items]}]
  #::pco{:input [{:sub/items [:xt/id
                              :item/unread]}]
         :output [{:sub/unread-items [:xt/id]}]}
  {:sub/unread-items (filterv :item/unread items)})

(def module {:resolvers [user-subs
                         sub-info
                         sub-id->xt-id
                         email-title
                         feed-sub-title
                         unread
                         published-at
                         items
                         latest-item
                         from-params
                         params-checked
                         unread-items
                         feed-sub-subtitle
                         latest-email-item
                         email-subtitle]
             :indexes [last-published-index
                       unread-index]})
