(ns repl.import
  (:require [com.biffweb :as biff :refer [q]]
            [com.yakread.util.biff-staging :as biffs]
            [com.yakread :as main]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [com.yakread :as main]
            [com.yakread.smtp :as smtp]
            [com.yakread.lib.core :as lib.core]
            [com.yakread.lib.route :as lib.route]
            [com.yakread.lib.pathom :as lib.pathom :refer [?]]
            [com.yakread.lib.smtp :as lib.smtp]
            [reitit.core :as reitit]
            [xtdb.api :as xt]
            [taoensso.nippy :as nippy]
            [malli.core :as malli]
            [clojure.data.generators :as gen])
  (:import (java.time LocalTime Instant)))

(defn update-some [m k f & args]
  (if (contains? m k)
    (apply update m k f args)
    m))

(def schema->keys (into {}
                        (map (juxt (comp :schema :properties)
                                   (comp keys :keys)))
                        (biffs/doc-asts main/malli-opts)))

(defn uuid-from [x]
  (binding [gen/*rnd* (java.util.Random. (hash x))]
    (gen/uuid)))

(defn all-user-docs [db email]
  (let [rename-keys {:user/subscribed-days :user/digest-days
                     :user/send-time       :user/send-digest-at
                     :user/last-sent       :user/digest-last-sent

                     :rss/url           :feed/url
                     :rss/synced-at     :feed/synced-at
                     :rss/title         :feed/title
                     :rss/description   :feed/description
                     :rss/image         :feed/image-url
                     :rss/etag          :feed/etag
                     :rss/last-modified :feed/last-modified

                     :item/fetched-at        :item/ingested-at
                     :item/content           :item/content-key
                     :item/inferred-feed-url :item/feed-url
                     :item/image             :item/image-url

                     :conn/user              :sub/user
                     :conn.rss/subscribed-at :sub/created-at
                     :conn/feed              :sub.feed/feed

                     :rec/user          :user-item/user
                     :rec/item          :user-item/item
                     :rec/viewed-at     :user-item/viewed-at
                     :rec/reported-at   :user-item/reported-at
                     :rec/report-reason :user-item/report-reason

                     :view/user        :skip/user
                     :view/timeline-id :skip/timeline-created-at
                     :view/items       :skip/items

                     :ad/image :ad/image-url
                     :ad/state :ad/approve-state

                     :ad.credit/type   :ad.credit/source
                     :ad.credit/status :ad.credit/charge-status

                     :item.email/raw-content :item.email/raw-content-key
                     :item.email/unsubscribe :item.email/list-unsubscribe}
        base-update (fn [doc schema]
                      (-> doc
                          (set/rename-keys rename-keys)
                          (select-keys (schema->keys schema))
                          (update-vals #(if (inst? %)
                                          (.toInstant %)
                                          %))))
        validate (fn [doc schema]
                   (if (malli/validate schema doc main/malli-opts)
                     doc
                     (throw (ex-info "invalid doc"
                                     {:doc doc
                                      :schema schema
                                      :explanation (malli/explain schema doc main/malli-opts)}))))
        base-update-item (fn [item schema]
                           (-> item
                               (base-update schema)
                               (update-some :item/content-key parse-uuid)
                               (merge (when (contains? (:item/flags item) :paywalled)
                                        {:item/paywalled true}))))
        user (biff/lookup db :user/email email)
        rss-conns (q db
                     '{:find (pull conn [*])
                       :in [user]
                       :where [[conn :conn/user user]
                               [conn :conn.rss/url]]}
                     (:xt/id user))
        pinned (biff/lookup db :pinned/user (:xt/id user))
        rss-items (q db
                     '{:find (pull item [*])
                       :in [[url ...]]
                       :where [[item :item.rss/feed-url url]]}
                     (mapv :conn.rss/url rss-conns))
        recs (biff/lookup-all db :rec/user (:xt/id user))
        item->bookmarked-at (into {}
                                  (for [bookmark (biff/lookup-all db :bookmark/user (:xt/id user))
                                        item (:bookmark/items bookmark)]
                                    [item (:bookmark/created-at bookmark)]))
        views (->> (biff/lookup-all db :view/user (:xt/id user))
                   (filterv (comp #{:web} :view/source))
                   (group-by :view/timeline-id)
                   (mapv (fn [[_ docs]]
                           (assoc (first docs)
                                  :view/items
                                  (mapcat :view/items docs)))))

        ad (biff/lookup db :ad/user (:xt/id user))
        ad-clicks (concat #_(when ad
                              (biff/lookup-all db :ad.click/ad (:xt/id ad)))
                          (biff/lookup-all db :ad.click/user (:xt/id user)))
        ad-credits (when ad
                     (biff/lookup-all db :ad.credit/ad (:xt/id ad)))

        sub->email-items
        (->> (biff/lookup-all db :item.email/user (:xt/id user))
             (group-by (some-fn :item/author-name :item.email/from-address))
             (mapv (fn [[from items]]
                     (let [sub-id (uuid-from from)] ; TODO pass user ID/email to uuid-from
                       [(-> (merge {:xt/id sub-id
                                    :sub/user (:xt/id user)
                                    :sub/created-at (apply min-key inst-ms (mapv :item/fetched-at items))
                                    :sub.email/from from}
                                   (when (contains? (:pinned/newsletters pinned)
                                                    from)
                                     {:sub/pinned-at #inst "2025"})
                                   (when (every? :item.email/hidden items)
                                     {:sub.email/unsubscribed-at
                                      (apply max-key inst-ms (mapv :item/fetched-at items))}))
                            (base-update :sub/email)
                            (validate :sub/email))
                        (for [item items]
                          (-> item
                              (base-update-item :item/email)
                              (update :item.email/raw-content-key parse-uuid)
                              (update-some :item.email/list-unsubscribe lib.smtp/decode-header)
                              (merge {:item.email/sub sub-id})
                              (cond->
                                (not (string? (:item/inferred-feed-url item))) (dissoc :item/feed-url))
                              (validate :item/email)))])))
             (into {}))

        rec-items (xt/pull-many db '[*] (mapv :rec/item recs))
        more-rss-items (filterv :item.rss/feed-url rec-items)
        rss-items (lib.core/distinct-by :xt/id (concat rss-items more-rss-items))
        url->feed (into {}
                        (map (fn [[url feed]]
                               [url
                                (-> feed
                                    (base-update :feed)
                                    (merge (when (:rss/failed feed)
                                             {:feed/failed-syncs 1}))
                                    (validate :feed))]))
                        (q db
                           '{:find [url (pull feed [*])]
                             :in [[url ...]]
                             :where [[feed :rss/url url]]}
                           (distinct
                            (concat (mapv :conn.rss/url rss-conns)
                                    (mapv :item.rss/feed-url rss-items)))))
        bookmark-items (->> (keys item->bookmarked-at)
                            (remove (set (mapv :xt/id rec-items)))
                            (xt/pull-many db '[*]))]
    (concat
     (for [item bookmark-items]
       (validate {:xt/id (uuid-from [:user-item (:xt/id user) (:xt/id item)])
                  :user-item/user (:xt/id user)
                  :user-item/item (:xt/id item)
                  :user-item/bookmarked-at (.toInstant (item->bookmarked-at (:xt/id item)))}
                 :user-item))
     (for [item (concat bookmark-items rec-items)
           :when (= :article (:item/type item))]
       (-> item
           (base-update-item :item/direct)
           (assoc :item/doc-type :item/direct)
           (validate :item/direct)))
     [(-> user
          (base-update :user)
          (update-some :user/send-digest-at #(LocalTime/of % 0))
          (merge (when (:user/suppressed user)
                   {:user/suppressed-at (.toInstant #inst "2025")})
                 (when-some [email-username (:conn.email/username
                                             (biff/lookup db
                                                          :conn/user (:xt/id user)
                                                          :conn/singleton-type :email))]
                   {:user/email-username email-username}))
          (validate :user))]
     (vals url->feed)
     (for [conn rss-conns]
       (-> conn
           (base-update :sub/feed)
           (merge (when (contains? (:pinned/rss pinned) (:xt/id conn))
                    {:sub/pinned-at (.toInstant #inst "2025")})
                  (when-some [feed (url->feed (:conn.rss/url conn))]
                    {:sub.feed/feed (:xt/id feed)}))
           (validate :sub/feed)))
     (for [item rss-items]
       (-> item
           (base-update-item :item/feed)
           (merge {:item.feed/feed (:xt/id (url->feed (:item.rss/feed-url item)))})
           (validate :item/feed)))
     (keys sub->email-items)
     (mapcat val sub->email-items)
     (for [view views]
       (-> view
           (base-update :skip)
           (update :skip/items set)
           (assoc :skip/clicked #{})
           (validate :skip)))
     (for [{:rec/keys [created-at
                       viewed-at]
            :as rec} recs
           :let [user-item (-> rec
                               (merge (when (:rec/skipped rec)
                                        {:user-item/skipped-at created-at})
                                      (when (= (:rec/rating rec) :like)
                                        {:user-item/favorited-at viewed-at})
                                      (when (= (:rec/rating rec) :dislike)
                                        {:user-item/disliked-at viewed-at})
                                      (when-some [t (item->bookmarked-at (:rec/item rec))]
                                        {:user-item/bookmarked-at t}))
                               (base-update :user-item))]
           :when (not-empty (dissoc user-item :xt/id :user-item/item :user-item/user))]
       (validate user-item :user-item))
     (when ad
       [(-> ad
            (base-update :ad)
            (validate :ad))])
     (for [ad-click ad-clicks]
       (-> ad-click
           (base-update :ad.click)
           (validate :ad.click)))
     (for [ad-credit ad-credits]
       (-> ad-credit
           (base-update :ad.credit)
           (validate :ad.credit))))))

(defn unimported-user-docs [from-db to-db email]
  (->> (all-user-docs from-db email)
       (remove #(= % (xt/entity to-db (:xt/id %))))
       doall))

(defn import-user-docs! [from-node to-node email]
  (xt/sync from-node)
  (xt/sync to-node)
  (let [from-db (xt/db from-node)
        to-db (xt/db to-node)
        docs (unimported-user-docs from-db to-db email)
        batches (partition-all 1000 docs)]
    (println "Importing" (count batches) "batches (" (count docs) " docs )")
    (doseq [batch batches]
      (xt/submit-tx to-node (for [doc batch]
                              [::xt/put doc])))))

(defn ad-user-emails [from-db]
  (sort (q from-db
           '{:find email
             :where [[user :user/email email]
                     [ad :ad/user user]]})))

(defn import-ad-users! [from-node to-node]
  (doseq [email (ad-user-emails (xt/db from-node))]
    (println "Importing" email)
    (import-user-docs! from-node to-node email)
    (println "Syncing...")
    (xt/sync to-node)))

(defn open-from-node []
  (:biff.xtdb/node (biff/use-xtdb {:biff.xtdb/topology :standalone,
                                   :biff.xtdb/dir "storage/export-xtdb-2"})))

(comment

  (def from-node (:biff.xtdb/node (biff/use-xtdb {:biff.xtdb/topology :standalone,
                                                  :biff.xtdb/dir "storage/export-xtdb-2"})))
  (def to-node (:biff.xtdb/node @com.yakread/system))

  (with-open [from-node (open-from-node)]
    (import-user-docs! from-node to-node ".."))

  (import-ad-users! from-node to-node)

  (.close from-node)

  (with-open [from-node (open-from-node)]
    (->> (all-user-docs (xt/db from-node) "...")
         shuffle
         (take 10)
         time))

  )
