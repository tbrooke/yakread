(ns com.yakread.config.minio
  "MinIO configuration for Mount Zion content storage
   MinIO provides S3-compatible object storage for extracted Alfresco content")

;; --- MINIO CONFIGURATION ---

(def minio-config
  "MinIO connection configuration"
  {:biff.s3/origin "http://localhost:9000"  ; MinIO endpoint
   :biff.s3/access-key "minioadmin"         ; Default MinIO access key
   :biff.s3/secret-key "minioadmin"         ; Default MinIO secret key
   :biff.s3/bucket "mtzion-content"})       ; Bucket for Mount Zion content

(def content-bucket
  "Bucket name for Mount Zion website content"
  "mtzion-content")

(def content-paths
  "Standard paths within the content bucket"
  {:extracted-content "extracted/"          ; Raw extracted content from Alfresco
   :processed-content "processed/"          ; Processed content ready for display
   :website-content "website/"              ; Website-ready content
   :images "images/"                        ; Cached images
   :metadata "metadata/"})                  ; Content metadata

(defn content-key
  "Generate a MinIO key for content storage
   Examples:
   - (content-key :extracted-content \"feature1\" \"content.edn\")
     => \"extracted/feature1/content.edn\"
   - (content-key :images \"node-id\" \"image.jpg\")
     => \"images/node-id/image.jpg\""
  [content-type & path-parts]
  (str (get content-paths content-type)
       (clojure.string/join "/" path-parts)))

(defn timestamped-key
  "Generate a timestamped key for versioning
   Example: extracted/feature1/2024-01-15T10-30-00/content.edn"
  [content-type feature-name filename]
  (let [timestamp (-> (java.time.Instant/now)
                     (.toString)
                     (clojure.string/replace ":" "-")
                     (clojure.string/replace "." "-"))]
    (content-key content-type feature-name timestamp filename)))

(defn latest-key
  "Generate a 'latest' key for current content version
   Example: extracted/feature1/latest/content.edn"
  [content-type feature-name filename]
  (content-key content-type feature-name "latest" filename))

;; --- MINIO INTEGRATION HELPERS ---

(defn minio-context
  "Create a context map with MinIO configuration for use with pipeline/S3 functions"
  [base-context]
  (merge base-context
         {:minio/config-ns 'com.yakread.config.minio}
         minio-config))

(defn ensure-bucket-exists
  "Ensure the MinIO bucket exists (would need to be run separately with MinIO client)"
  []
  (println "To create MinIO bucket, run:")
  (println (str "  mc alias set mtzion http://localhost:9000 minioadmin minioadmin"))
  (println (str "  mc mb mtzion/" content-bucket))
  (println (str "  mc policy set download mtzion/" content-bucket "/website/*")))

;; --- CONTENT STORAGE FUNCTIONS ---

(defn store-content
  "Store content in MinIO instead of local file
   Returns the storage key used"
  [ctx content-type feature-name filename content]
  (let [latest-k (latest-key content-type feature-name filename)
        timestamped-k (timestamped-key content-type feature-name filename)
        content-str (if (string? content) content (pr-str content))]
    
    ;; Store timestamped version for history
    (com.yakread.lib.pipeline/s3 
      'com.yakread.config.minio
      timestamped-k
      content-str
      "application/edn")
    
    ;; Store latest version for easy access
    (com.yakread.lib.pipeline/s3
      'com.yakread.config.minio
      latest-k
      content-str
      "application/edn")
    
    {:latest-key latest-k
     :timestamped-key timestamped-k
     :stored-at (java.time.Instant/now)}))

(defn retrieve-content
  "Retrieve content from MinIO"
  [ctx content-type feature-name filename]
  (let [k (latest-key content-type feature-name filename)
        result (com.yakread.lib.pipeline/s3 'com.yakread.config.minio k)]
    (when-let [content (get-in result [:biff.pipe.s3/output :body])]
      (if (string? content)
        (clojure.edn/read-string content)
        content))))

(defn list-content-versions
  "List all versions of content for a feature"
  [ctx content-type feature-name]
  ;; This would require S3 list-objects functionality
  ;; For now, return a placeholder
  {:message "List functionality would require S3 list-objects API"
   :path (content-key content-type feature-name)})

;; --- MIGRATION HELPERS ---

(defn migrate-local-file-to-minio
  "Migrate a local EDN file to MinIO storage"
  [ctx local-file content-type feature-name]
  (when (.exists (clojure.java.io/file local-file))
    (let [content (clojure.edn/read-string (slurp local-file))
          filename (-> local-file
                      clojure.java.io/file
                      .getName)]
      (store-content ctx content-type feature-name filename content))))

(comment
  ;; Example usage:
  
  ;; Migrate existing local files to MinIO
  (migrate-local-file-to-minio {} "mtzuix-feature1-content.edn" :extracted-content "feature1")
  (migrate-local-file-to-minio {} "mtzuix-feature2-website-content.edn" :website-content "feature2")
  
  ;; Store new content
  (store-content {} :extracted-content "feature1" "content.edn" 
                 {:title "Test" :content "Hello MinIO!"})
  
  ;; Retrieve content
  (retrieve-content {} :extracted-content "feature1" "content.edn")
)