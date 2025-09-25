#!/usr/bin/env bb

;; Setup MinIO for Mount Zion content storage
;; This script creates buckets and migrates existing local files to MinIO

(require '[babashka.curl :as curl]
         '[clojure.string :as str])

(println "🚀 Setting up MinIO for Mount Zion Content Storage")
(println "=================================================")

;; Configuration
(def minio-endpoint "http://localhost:9000")
(def minio-console "http://localhost:9001")
(def access-key "minioadmin")
(def secret-key "minioadmin")
(def bucket-name "mtzion-content")

(println "\n📋 MinIO Configuration:")
(println "   Endpoint:" minio-endpoint)
(println "   Console:" minio-console)
(println "   Bucket:" bucket-name)

;; Check MinIO is running
(println "\n🔍 Checking MinIO connection...")
(try
  (let [resp (curl/get minio-endpoint {:throw false})]
    (if (= 403 (:status resp))
      (println "   ✅ MinIO is running (403 is expected without auth)")
      (println "   ❌ MinIO returned unexpected status:" (:status resp))))
  (catch Exception e
    (println "   ❌ Cannot connect to MinIO. Make sure it's running:")
    (println "      docker-compose up -d minio")
    (System/exit 1)))

(println "\n📦 MinIO Setup Instructions:")
(println "\n1. Access MinIO Console:")
(println (str "   Open: " minio-console))
(println "   Login with: minioadmin / minioadmin")

(println "\n2. Create the bucket manually in the console:")
(println (str "   - Click 'Buckets' → 'Create Bucket'"))
(println (str "   - Bucket name: " bucket-name))
(println "   - Click 'Create Bucket'")

(println "\n3. Or use MinIO Client (mc):")
(println "   # Install mc:")
(println "   brew install minio/stable/mc")
(println "   ")
(println "   # Configure alias:")
(println (str "   mc alias set mtzion " minio-endpoint " " access-key " " secret-key))
(println "   ")
(println "   # Create bucket:")
(println (str "   mc mb mtzion/" bucket-name))
(println "   ")
(println "   # Set download policy for website content:")
(println (str "   mc anonymous set download mtzion/" bucket-name "/website/*"))

(println "\n📁 Content Structure in MinIO:")
(println (str "   " bucket-name "/"))
(println "   ├── extracted/        # Raw content from Alfresco")
(println "   │   ├── feature1/")
(println "   │   └── feature2/")
(println "   ├── processed/        # Processed content")
(println "   ├── website/          # Website-ready content")
(println "   ├── images/           # Cached images")
(println "   └── metadata/         # Content metadata")

;; Check for existing local files
(println "\n📂 Checking for existing local files to migrate:")
(def local-files ["mtzuix-feature1-content.edn"
                  "mtzuix-feature2-content.edn"
                  "mtzuix-feature2-processed-content.edn"
                  "mtzuix-feature2-website-content.edn"
                  "mtzuix-calendar-events.edn"])

(doseq [file local-files]
  (if (.exists (clojure.java.io/file file))
    (println (str "   ✅ Found: " file))
    (println (str "   ❌ Missing: " file))))

(println "\n🔄 Migration Steps:")
(println "1. After creating the bucket, run in REPL:")
(println "   (require '[com.yakread.alfresco.minio-content-service :as minio])")
(println "   (minio/migrate-local-files-to-minio {})")

(println "\n2. Test content retrieval:")
(println "   (minio/retrieve-website-content {} \"feature1\")")
(println "   (minio/retrieve-website-content {} \"feature2\")")

(println "\n3. Update extraction scripts to use MinIO:")
(println "   Instead of: (spit \"file.edn\" content)")
(println "   Use: (minio/store-content ctx :extracted-content \"feature1\" \"content.edn\" content)")

(println "\n🎯 Benefits of MinIO Storage:")
(println "   • Versioned content (automatic timestamps)")
(println "   • S3-compatible API")
(println "   • Web-accessible content URLs")
(println "   • Scalable object storage")
(println "   • No local file management")
(println "   • Easy backup and replication")

(println "\n✅ Setup Instructions Complete!")
(println "Follow the steps above to complete MinIO configuration.")