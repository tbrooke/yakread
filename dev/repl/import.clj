(ns repl.import
  (:require [com.biffweb :as biff :refer [q]]
            [com.yakread.util.biff-staging :as biffs]
            [com.yakread :as main]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [com.yakread :as main]
            [com.yakread.smtp :as smtp]
            [com.yakread.lib.route :as lib.route]
            [com.yakread.lib.pathom :as lib.pathom :refer [?]]
            [com.yakread.lib.smtp :as lib.smtp]
            [reitit.core :as reitit]
            [xtdb.api :as xt]
            [taoensso.nippy :as nippy]
            [malli.core :as malli])
  (:import (java.time LocalTime Instant)))

(defn update-some [m k f & args]
  (if (contains? m k)
    (apply update m k f args)
    m))

(defn assoc-some [m & kvs]
  (into m
        (keep (fn [[k v]]
                  (when (some? v)
                    [k v])))
        (partition 2 kvs)))

(def schema->keys (into {}
                        (map (juxt (comp :schema :properties)
                                   (comp keys :keys)))
                        (biffs/doc-asts main/malli-opts)))

(time
 (do
  (defn import-user-docs [db email]
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
                       :rec/report-reason :user-item/report-reason}
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
          user (biff/lookup db :user/email email)
          rss-conns (q db
                       '{:find (pull conn [*])
                         :in [user]
                         :where [[conn :conn/user user]
                                 [conn :conn.rss/url]]}
                       (:xt/id user))
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
                             (mapv :conn.rss/url rss-conns)))
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
                                      [item (:bookmark/created-at bookmark)]))]
      ;; TODO
      ;; - skips
      ;; - email subs, items
      ;; - ads, clicks, credit
      (->> (concat (for [{:rec/keys [created-at
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
                     user-item)
                   #_(for [conn rss-conns]
                       (-> conn
                           (base-update :sub/feed)
                           (merge (when (contains? (:pinned/rss pinned) (:xt/id conn))
                                    {:sub/pinned-at (.toInstant #inst "2025")})
                                  (when-some [feed (url->feed (:conn.rss/url conn))]
                                    {:sub.feed/feed (:xt/id feed)}))
                           (validate :sub/feed)))
                   #_(for [item rss-items]
                       (-> item
                           (base-update :item/feed)
                           (update-some :item/content-key parse-uuid)
                           (merge {:item.feed/feed (:xt/id (url->feed (:item.rss/feed-url item)))}
                                  (when (contains? (:item/flags item) :paywalled)
                                    {:item/paywalled true}))
                           (validate :item/feed)))
                   #_[(-> user
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
                   #_(for [feed (vals url->feed)]
                       (-> feed
                           (base-update :feed)
                           (merge (when (:rss/failed feed)
                                    {:feed/failed-syncs 1}))
                           (validate :feed))))
           shuffle
           (take 10))))
  #_(import-user-docs (xt/db export-node) "jacob@thesample.ai")
  ))

(comment

  (def export-node (:biff.xtdb/node (biff/use-xtdb {:biff.xtdb/topology :standalone,
                                                    :biff.xtdb/dir "storage/export-xtdb-2"})))

  
  (.close export-node)
  


  
  
  )
