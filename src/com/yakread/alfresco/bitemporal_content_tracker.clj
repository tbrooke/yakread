(ns com.yakread.alfresco.bitemporal-content-tracker
  "Bitemporal tracking for Mount Zion website content state
   XTDB tracks WHEN content was displayed, MinIO stores WHAT was displayed"
  (:require
   [clojure.tools.logging :as log]
   [com.yakread.config.minio :as minio]
   [xtdb.api :as xt]))

;; --- BITEMPORAL CONTENT TRACKING ---

(defn track-content-version
  "Track a content version in XTDB with bitemporal data
   - Valid time: When this content should be displayed on the website
   - Transaction time: When we recorded this change"
  [ctx feature-name version-info valid-from valid-to]
  (let [content-id (keyword "content" feature-name)
        tx-data [{:xt/id content-id
                  :content/type :content.type/website-display
                  :content/feature feature-name
                  :content/status :active
                  
                  ;; MinIO storage references (not the content itself)
                  :content/minio-key (:minio-key version-info)
                  :content/minio-version-id (:version-id version-info)
                  :content/storage-timestamp (:stored-at version-info)
                  
                  ;; Display metadata
                  :content/title (:title version-info)
                  :content/has-images (:has-images version-info)
                  :content/item-count (:item-count version-info)
                  :content/source-alfresco-nodes (:alfresco-nodes version-info)
                  
                  ;; Publishing metadata
                  :content/published-by (:published-by version-info)
                  :content/publish-reason (:reason version-info)
                  :content/publish-notes (:notes version-info)
                  
                  ;; Bitemporal validity
                  :xt/valid-from valid-from
                  :xt/valid-to (or valid-to #inst "9999-12-31")}]]
    
    (xt/submit-tx (:biff/xtdb ctx) tx-data)
    (log/info "Tracked content version for" feature-name 
              "valid from" valid-from "to" valid-to)))

(defn publish-content-version
  "Publish a specific content version for display on the website
   Creates a bitemporal record of what content should be shown when"
  [ctx feature-name minio-key & {:keys [valid-from valid-to published-by reason notes]}]
  (let [valid-from (or valid-from (java.time.Instant/now))
        ;; Retrieve content metadata from MinIO to store summary in XTDB
        content (minio/retrieve-content ctx :website-content feature-name "website.edn")
        version-info {:minio-key minio-key
                     :version-id (str (System/currentTimeMillis)) ; Would be actual S3 version
                     :stored-at (java.time.Instant/now)
                     :title (-> content first :title)
                     :has-images (some :has-images content)
                     :item-count (count content)
                     :alfresco-nodes (map :source-node-id content)
                     :published-by published-by
                     :reason reason
                     :notes notes}]
    
    (track-content-version ctx feature-name version-info valid-from valid-to)
    
    {:success true
     :feature feature-name
     :version version-info
     :valid-from valid-from
     :valid-to valid-to}))

(defn get-content-at-time
  "Get what content should be displayed at a specific point in time
   Returns the MinIO key to fetch the actual content"
  [ctx feature-name as-of-time]
  (let [db (xt/db (:biff/xtdb ctx) {:valid-time as-of-time})
        content-id (keyword "content" feature-name)
        entity (xt/entity db content-id)]
    (when entity
      {:feature feature-name
       :as-of-time as-of-time
       :minio-key (:content/minio-key entity)
       :title (:content/title entity)
       :published-by (:content/published-by entity)
       :publish-reason (:content/publish-reason entity)
       :valid-from (:xt/valid-from entity)
       :valid-to (:xt/valid-to entity)})))

(defn get-content-history
  "Get the full history of content versions for a feature
   Shows what content was displayed when"
  [ctx feature-name]
  (let [db (xt/db (:biff/xtdb ctx))
        content-id (keyword "content" feature-name)
        history (xt/entity-history db content-id :asc)]
    (map (fn [history-entry]
           (let [entity (:xtdb.api/doc history-entry)]
             {:valid-from (:xtdb.api/valid-from history-entry)
              :valid-to (:xtdb.api/valid-to history-entry)
              :tx-time (:xtdb.api/tx-time history-entry)
              :minio-key (:content/minio-key entity)
              :title (:content/title entity)
              :published-by (:content/published-by entity)
              :publish-reason (:content/publish-reason entity)}))
         history)))

(defn schedule-content-change
  "Schedule a future content change
   E.g., publish holiday content starting Dec 1st"
  [ctx feature-name minio-key valid-from valid-to published-by reason]
  (publish-content-version ctx feature-name minio-key
                          :valid-from valid-from
                          :valid-to valid-to
                          :published-by published-by
                          :reason reason))

(defn rollback-to-version
  "Rollback content to a previous version
   Creates a new bitemporal record pointing to old content"
  [ctx feature-name target-time rollback-reason published-by]
  (let [historical-version (get-content-at-time ctx feature-name target-time)]
    (if historical-version
      (publish-content-version ctx feature-name (:minio-key historical-version)
                              :published-by published-by
                              :reason (str "Rollback: " rollback-reason)
                              :notes (str "Rolled back to version from " target-time))
      {:success false :error "No content found at target time"})))

;; --- AUDIT QUERIES ---

(defn who-published-what-when
  "Audit trail of all content publications"
  [ctx]
  (let [db (xt/db (:biff/xtdb ctx))
        query '{:find [?feature ?published-by ?reason ?valid-from ?tx-time]
                :where [[?e :content/type :content.type/website-display ?tx-time]
                        [?e :content/feature ?feature ?tx-time]
                        [?e :content/published-by ?published-by ?tx-time]
                        [?e :content/publish-reason ?reason ?tx-time]
                        [?e :xt/valid-from ?valid-from ?tx-time]]}]
    (xt/q db query)))

(defn what-was-live-on-date
  "Get complete website content state for a specific date"
  [ctx target-date]
  (let [db (xt/db (:biff/xtdb ctx) {:valid-time target-date})
        features ["feature1" "feature2" "calendar" "about" "worship"]]
    (into {}
          (map (fn [feature]
                 [feature (get-content-at-time ctx feature target-date)])
               features))))

;; --- WEBSITE INTEGRATION ---

(defn get-current-content-key
  "Get the current MinIO key for a feature (what should be displayed now)"
  [ctx feature-name]
  (let [current (get-content-at-time ctx feature-name (java.time.Instant/now))]
    (:minio-key current)))

(defn load-content-for-display
  "Load content for website display - combines bitemporal lookup with MinIO retrieval"
  [ctx feature-name & {:keys [as-of-time]}]
  (let [as-of (or as-of-time (java.time.Instant/now))
        temporal-info (get-content-at-time ctx feature-name as-of)]
    (if temporal-info
      (let [;; Get actual content from MinIO using the key from XTDB
            content (minio/retrieve-content ctx :website-content feature-name "website.edn")]
        {:success true
         :content content
         :metadata temporal-info})
      {:success false
       :error "No published content found for this time"})))

;; --- DEVELOPMENT HELPERS ---

(defn create-test-timeline
  "Create a test timeline showing content changes over time"
  [ctx]
  (let [base-time (java.time.Instant/now)
        day-ago (.minus base-time 1 java.time.temporal.ChronoUnit/DAYS)
        week-ago (.minus base-time 7 java.time.temporal.ChronoUnit/DAYS)
        tomorrow (.plus base-time 1 java.time.temporal.ChronoUnit/DAYS)]
    
    ;; Simulate content timeline
    (println "Creating test timeline...")
    
    ;; Week ago: Initial content
    (publish-content-version ctx "feature1" "website/feature1/v1/website.edn"
                            :valid-from week-ago
                            :valid-to day-ago
                            :published-by "admin"
                            :reason "Initial website launch")
    
    ;; Day ago: Updated content  
    (publish-content-version ctx "feature1" "website/feature1/v2/website.edn"
                            :valid-from day-ago
                            :valid-to tomorrow
                            :published-by "content-team"
                            :reason "Weekly content update")
    
    ;; Tomorrow: Scheduled future content
    (publish-content-version ctx "feature1" "website/feature1/v3/website.edn"
                            :valid-from tomorrow
                            :published-by "content-team"
                            :reason "Special event announcement")
    
    ;; Show timeline
    (println "\nContent Timeline for feature1:")
    (doseq [entry (get-content-history ctx "feature1")]
      (println "  -" (:valid-from entry) "to" (:valid-to entry)
               ":" (:title entry) 
               "(by" (:published-by entry) ")")
      (println "    Reason:" (:publish-reason entry)))))

(comment
  ;; Example usage:
  
  ;; Publish current content
  (publish-content-version {} "feature1" "website/feature1/latest/website.edn"
                          :published-by "admin"
                          :reason "Regular content update")
  
  ;; Schedule holiday content
  (schedule-content-change {} "feature1" "website/feature1/holiday/website.edn"
                          #inst "2024-12-01"
                          #inst "2024-12-26"
                          "content-team"
                          "Holiday season content")
  
  ;; View content at specific time
  (get-content-at-time {} "feature1" #inst "2024-12-15")
  
  ;; Rollback if needed
  (rollback-to-version {} "feature1" 
                       #inst "2024-11-01"
                       "Content had errors"
                       "admin")
  
  ;; Audit trail
  (who-published-what-when {})
  
  ;; What was live on Christmas?
  (what-was-live-on-date {} #inst "2023-12-25")
)