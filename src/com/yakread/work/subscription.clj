(ns com.yakread.work.subscription
  (:require [clojure.data.generators :as gen]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [com.biffweb :as biff :refer [q]]
            [com.yakread.lib.content :as lib.content]
            [com.yakread.lib.core :as lib.core]
            [com.yakread.lib.pipeline :as lib.pipe]
            [rum.core :as rum]
            [xtdb.api :as xt])
  (:import [org.jsoup Jsoup]))

(def ^:private epoch (java.time.Instant/ofEpochMilli 0))

;; TODO modify sync waiting period based on user activity, :feed/failed-syncs
(def sync-all-feeds!
  (lib.pipe/make
   :start
   (fn [{:keys [biff/db biff/now]}]
     (let [feed-ids (q db
                       {:find 'feed
                        :in '[t0]
                        :where ['[feed :feed/url]
                                [(list 'get-attr 'feed :feed/synced-at epoch) '[synced-at ...]]
                                '[(< synced-at t0)]]}
                       (.minusSeconds now (* 60 60 4)))]
       (log/warn "sync-feed! is disabled; remember to re-enable it later.")
       ;; TODO uncomment
       {:biff.pipe/next [] #_(for [id feed-ids]
                               {:biff.pipe/current :biff.pipe/queue
                                :biff.pipe.queue/id :work.subscription/sync-feed
                                :biff.pipe.queue/job {:feed/id id}})}))))

(defn- entry->html [entry]
  (->> (concat (:contents entry) [(:description entry)])
       (sort-by (fn [{content-type :type}]
                  (str/includes? (or content-type "") "html"))
                #(compare %2 %1))
       first
       :value))

;; TODO update url for feeds that change their URL
(def sync-feed!
  (lib.pipe/make
   :start
   (fn [{:biff/keys [db base-url]
         {:keys [feed/id]} :biff/job}]
     (let [{:feed/keys [url etag last-modified]} (xt/entity db id)]
       ;; TODO uncomment
       nil
       #_{:biff.pipe/next             [:com.yakread.pipe/remus :end]
        :biff.pipe/catch            :com.yakread.pipe/remus
        :com.yakread.pipe.remus/url url

        :com.yakread.pipe.remus/opts
        {:headers            (into {}
                                   (remove (comp nil? val))
                                   {"User-Agent" base-url
                                    "If-None-Match" etag
                                    "If-Modified-Since" last-modified})

         :socket-timeout     5000
         :connection-timeout 5000}}))

   :end
   (fn [{:keys [biff/db
                biff.pipe/exception
                com.yakread.pipe.remus/output]
         {feed-id :feed/id} :biff/job}]
     (let [{:keys [title description image entries]} (:feed output)
           {:keys [headers]} (:response output)
           feed-doc (into {}
                          (remove (comp nil? val))
                          {:db/doc-type        :feed
                           :db/op              :update
                           :xt/id              feed-id
                           :feed/synced-at     :db/now
                           :feed/failed-syncs  (if exception [:db/add 1] :db/dissoc)
                           :feed/title         (some-> title (lib.content/truncate 100))
                           :feed/description   (some-> description (lib.content/truncate 300))
                           :feed/image-url     (if (string? image) image (:url image))
                           :feed/etag          (lib.core/pred-> (get headers "Etag") coll? first)
                           :feed/last-modified (lib.core/pred-> (get headers "Last-Modified") coll? first)})
           items    (for [entry (take 20 entries)
                          :let [html (entry->html entry)
                                text (or (:textContent entry)
                                         (some-> html (Jsoup/parse) (.text)))
                                title (or (:title entry)
                                          (some-> text not-empty (lib.content/truncate 75))
                                          "[no title]")
                                use-text-for-title (and (not (:title entry))
                                                        (not-empty text))
                                html (or html
                                         (when (:link entry)
                                           (rum/render-static-markup
                                            [:a {:href (:link entry)} (:link entry)])))]
                          :when (some? html)]
                      (into {}
                            (remove (comp nil? val))
                            {:db/doc-type       :item/feed
                             :item.feed/feed    feed-id
                             :item/title        title
                             :item/content-key  (when (< 1000 (count html))
                                                  (gen/uuid))
                             :item/content      html
                             :item/ingested-at  :db/now
                             :item/lang         (lib.content/lang html)
                             :item/paywalled    (some-> text str/trim (str/ends-with? "Read more"))
                             :item/url          (some-> (:link entry) str/trim)
                             :item/published-at (some-> (some entry [:published-date :updated-date]) (.toInstant))
                             :item/author-name  (or (-> entry :authors first :name)
                                                    (:feed/title feed-doc))
                             :item/author-url   (some-> entry :authors first :uri str/trim)
                             :item/excerpt      (lib.content/excerpt
                                                 (if use-text-for-title
                                                   (str (when (str/ends-with? title "…")
                                                          "…")
                                                        (subs text (count (str/replace title #"…$" ""))))
                                                   text))
                             :item/length       (count text)
                             :item/byline       (:byline entry)
                             :item/image-url    (some-> (:og/image entry) str/trim)
                             :item/site-name    (:siteName entry)
                             :item.feed/guid    (:uri entry)}))
           existing-titles (set (q db
                                   '{:find title
                                     :in [feed [title ...]]
                                     :where [[item :item.feed/feed feed]
                                             [item :item/title title]]}
                                   feed-id
                                   (keep :item/title items)))
           existing-guids  (set (q db
                                   '{:find guid
                                     :in [feed [guid ...]]
                                     :where [[item :item.feed/feed feed]
                                             [item :item.feed/guid guid]]}
                                   feed-id
                                   (keep :item.feed/guid items)))
           items (remove (fn [{:keys [item/title item.feed/guid]}]
                           (or (existing-titles title) (existing-guids guid)))
                         items)
           s3-inputs (for [{:item/keys [content content-key]} items
                           :when content-key]
                       {:biff.pipe/current  :biff.pipe/s3
                        :biff.pipe.s3/input {:config-ns 'yakread.s3.content
                                             :method  "PUT"
                                             :key     (str content-key)
                                             :body    content
                                             :headers {"x-amz-acl"    "private"
                                                       "content-type" "text/html"}}})
           items (mapv (fn [item]
                         (cond-> item
                           (:item/content-key item) (dissoc :item/content)))
                       items)]
       {:biff.pipe/next     (concat s3-inputs [:biff.pipe/tx])
        :biff.pipe.tx/input (concat [feed-doc] items)}))))

(def module
  {:tasks [{:task     #'sync-all-feeds!
            :schedule (lib.core/every-n-minutes 5)}]
   :queues [{:id        :work.subscription/sync-feed
             :consumer  #'sync-feed!
             :n-threads 10}]})
