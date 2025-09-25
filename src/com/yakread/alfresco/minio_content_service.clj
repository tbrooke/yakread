(ns com.yakread.alfresco.minio-content-service
  "Content service using MinIO for storage instead of local files
   Integrates with the existing Alfresco extraction pipeline"
  (:require
   [clojure.tools.logging :as log]
   [com.yakread.alfresco.content-extractor :as extractor]
   [com.yakread.alfresco.content-processor :as processor]
   [com.yakread.config.minio :as minio]
   [com.yakread.lib.pipeline :as pipe]
   [xtdb.api :as xt]
   [com.yakread.alfresco.bitemporal-content-tracker :as temporal]))

;; --- MINIO STORAGE PIPELINE ---

(defn store-extracted-content
  "Store extracted content in MinIO and save metadata to XTDB
   Returns storage metadata"
  [ctx feature-name content-items]
  (log/info "Storing" (count content-items) "items to MinIO for feature:" feature-name)
  
  (let [;; Process content for images
        processed-items (processor/process-extracted-content content-items)
        
        ;; Prepare website-ready content
        website-content (mapv (fn [item]
                               {:title (:alfresco-name item)
                                :html-content (:processed-html item)
                                :original-html (:content item)
                                :image-urls (:image-urls item)
                                :has-images (:has-images item)
                                :last-updated (:alfresco-modified-at item)
                                :source-node-id (:alfresco-node-id item)})
                             processed-items)
        
        ;; Store in MinIO using pipeline
        storage-pipeline (pipe/make
                          :store-raw (fn [ctx]
                                      (let [result (minio/store-content ctx 
                                                                       :extracted-content 
                                                                       feature-name 
                                                                       "content.edn"
                                                                       processed-items)]
                                        (log/info "Stored raw content:" (:latest-key result))
                                        (assoc ctx :raw-storage result)))
                          
                          :store-website (fn [ctx]
                                          (let [result (minio/store-content ctx
                                                                           :website-content
                                                                           feature-name
                                                                           "website.edn"
                                                                           website-content)]
                                            (log/info "Stored website content:" (:latest-key result))
                                            (assoc ctx :website-storage result)))
                          
                          :store-metadata (fn [{:keys [raw-storage website-storage] :as ctx}]
                                           (let [metadata {:feature feature-name
                                                          :items-count (count content-items)
                                                          :extracted-at (java.time.Instant/now)
                                                          :raw-key (:latest-key raw-storage)
                                                          :website-key (:latest-key website-storage)
                                                          :has-images (some :has-images processed-items)}
                                                 result (minio/store-content ctx
                                                                           :metadata
                                                                           feature-name
                                                                           "metadata.edn"
                                                                           metadata)]
                                             (log/info "Stored metadata:" (:latest-key result))
                                             (assoc ctx :metadata-storage result :metadata metadata))))
        
        ;; Execute storage pipeline
        pipeline-result (storage-pipeline (minio/minio-context ctx) :store-raw)]
    
    ;; Return storage summary
    {:success true
     :feature feature-name
     :items-stored (count content-items)
     :storage {:raw (:raw-storage pipeline-result)
              :website (:website-storage pipeline-result)
              :metadata (:metadata-storage pipeline-result)}
     :metadata (:metadata pipeline-result)}))

(defn save-content-metadata-to-xtdb
  "Save content metadata to XTDB for querying"
  [ctx storage-result]
  (let [tx-data [{:xt/id (keyword "content" (:feature storage-result))
                  :content/type :content.type/alfresco-extract
                  :content/feature (:feature storage-result)
                  :content/items-count (get-in storage-result [:metadata :items-count])
                  :content/has-images (get-in storage-result [:metadata :has-images])
                  :content/extracted-at (get-in storage-result [:metadata :extracted-at])
                  :content/minio-keys {:raw (get-in storage-result [:storage :raw :latest-key])
                                      :website (get-in storage-result [:storage :website :latest-key])
                                      :metadata (get-in storage-result [:storage :metadata :latest-key])}
                  :content/stored-at (java.time.Instant/now)}]]
    (xt/submit-tx (:biff/xtdb ctx) tx-data)
    (log/info "Saved content metadata to XTDB for feature:" (:feature storage-result))))

;; --- RETRIEVAL FUNCTIONS ---

(defn retrieve-website-content
  "Retrieve website-ready content from MinIO"
  [ctx feature-name]
  (try
    (let [content (minio/retrieve-content ctx :website-content feature-name "website.edn")]
      (if content
        {:success true :content content}
        {:success false :error "Content not found"}))
    (catch Exception e
      (log/error "Error retrieving content for" feature-name ":" (.getMessage e))
      {:success false :error (.getMessage e)})))

(defn get-content-metadata
  "Get content metadata from XTDB"
  [ctx feature-name]
  (let [db (xt/db (:biff/xtdb ctx))
        entity (xt/entity db (keyword "content" feature-name))]
    (when entity
      (select-keys entity [:content/items-count :content/has-images 
                          :content/extracted-at :content/stored-at
                          :content/minio-keys]))))

;; --- EXTRACTION AND STORAGE PIPELINE ---

(defn extract-and-store-feature
  "Complete pipeline: Extract from Alfresco and store in MinIO"
  [ctx feature-name folder-node-id]
  (log/info "Starting extraction and storage for feature:" feature-name)
  
  (try
    ;; Extract content from Alfresco
    (let [extraction-result (extractor/extract-folder-content ctx folder-node-id {:max-depth 2})]
      (if (seq extraction-result)
        (do
          (log/info "Extracted" (count extraction-result) "items from Alfresco")
          
          ;; Store in MinIO
          (let [storage-result (store-extracted-content ctx feature-name extraction-result)]
            (if (:success storage-result)
              (do
                ;; Save metadata to XTDB
                (save-content-metadata-to-xtdb ctx storage-result)
                
                (log/info "✅ Successfully extracted and stored content for" feature-name)
                storage-result)
              
              (do
                (log/error "Failed to store content in MinIO")
                {:success false :error "Storage failed"}))))
        
        (do
          (log/warn "No content extracted from Alfresco folder")
          {:success false :error "No content found"})))
    
    (catch Exception e
      (log/error "Error in extract-and-store pipeline:" (.getMessage e))
      {:success false :error (.getMessage e)})))

;; --- MIGRATION FROM LOCAL FILES ---

(defn migrate-local-files-to-minio
  "Migrate existing local EDN files to MinIO"
  [ctx]
  (log/info "Starting migration from local files to MinIO...")
  
  (let [local-files [{:file "mtzuix-feature1-content.edn" 
                     :feature "feature1"
                     :type :extracted-content}
                    {:file "mtzuix-feature2-website-content.edn"
                     :feature "feature2"  
                     :type :website-content}
                    {:file "mtzuix-calendar-events.edn"
                     :feature "calendar"
                     :type :extracted-content}]]
    
    (doseq [{:keys [file feature type]} local-files]
      (when (.exists (clojure.java.io/file file))
        (log/info "Migrating" file "to MinIO...")
        (try
          (minio/migrate-local-file-to-minio ctx file type feature)
          (log/info "✅ Migrated" file)
          (catch Exception e
            (log/error "Failed to migrate" file ":" (.getMessage e))))))
    
    (log/info "Migration complete!")))

;; --- PUBLISHING WORKFLOW ---

(defn publish-extracted-content
  "Publish extracted content to the website with bitemporal tracking
   This creates a new version in MinIO and tracks it in XTDB"
  [ctx feature-name & {:keys [published-by reason notes valid-from valid-to]}]
  (log/info "Publishing content for" feature-name)
  
  (let [;; Get the latest extracted content
        storage-key (:latest-key (store-extracted-content ctx feature-name []))
        
        ;; Create bitemporal record
        publish-result (temporal/publish-content-version 
                        ctx feature-name storage-key
                        :published-by (or published-by "system")
                        :reason (or reason "Content update")
                        :notes notes
                        :valid-from valid-from
                        :valid-to valid-to)]
    
    (if (:success publish-result)
      (do
        (log/info "✅ Published content for" feature-name 
                  "valid from" (:valid-from publish-result))
        publish-result)
      (do
        (log/error "Failed to publish content for" feature-name)
        publish-result))))

(defn schedule-content-publication
  "Schedule content to be published at a future date"
  [ctx feature-name publish-date published-by reason]
  (publish-extracted-content ctx feature-name
                            :published-by published-by
                            :reason reason
                            :valid-from publish-date))

(defn expire-content
  "Set an expiration date for current content"
  [ctx feature-name expire-date published-by reason]
  (let [current-version (temporal/get-content-at-time ctx feature-name 
                                                     (java.time.Instant/now))]
    (when current-version
      (temporal/publish-content-version ctx feature-name 
                                       (:minio-key current-version)
                                       :published-by published-by
                                       :reason reason
                                       :valid-to expire-date))))

;; --- FRONTEND INTEGRATION ---

(defn load-content-for-page
  "Load content from MinIO for HTMX frontend display
   Now uses bitemporal tracking to get the current version"
  [ctx feature-name & {:keys [as-of-time]}]
  ;; First check what version should be displayed using bitemporal data
  (let [temporal-result (temporal/load-content-for-display ctx feature-name 
                                                          :as-of-time as-of-time)]
    (if (:success temporal-result)
      (:content temporal-result)
      ;; Fallback to direct MinIO access if no temporal data
      (let [result (retrieve-website-content ctx feature-name)]
        (if (:success result)
          (:content result)
          (do
            (log/warn "Could not load content for" feature-name "from MinIO")
            ;; Final fallback to local file
            (try
              (when-let [local-file (case feature-name
                                     "feature1" "mtzuix-feature1-content.edn"
                                     "feature2" "mtzuix-feature2-website-content.edn"
                                     nil)]
                (when (and local-file (.exists (clojure.java.io/file local-file)))
                  (log/info "Falling back to local file:" local-file)
                  (clojure.edn/read-string (slurp local-file))))
              (catch Exception e
                (log/error "Fallback to local file failed:" (.getMessage e))
                nil))))))))

(comment
  ;; Development usage examples:
  
  ;; Migrate existing files to MinIO
  (migrate-local-files-to-minio {})
  
  ;; Extract and store new content
  (extract-and-store-feature {} "feature1" "node-id-here")
  
  ;; Retrieve content for display
  (retrieve-website-content {} "feature1")
  
  ;; Get metadata
  (get-content-metadata {} "feature1")
  
  ;; Load for frontend
  (load-content-for-page {} "feature1")
)