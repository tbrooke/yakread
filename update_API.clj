#!/usr/bin/env bb

;; Consolidated script to update Alfresco API specifications
;; Fetches YAML specs, converts to EDN, adds versioning and timestamps

(require '[babashka.curl :as curl]
         '[clj-yaml.core :as yaml]
         '[cheshire.core :as json]
         '[clojure.edn :as edn]
         '[clojure.string :as str]
         '[clojure.java.io :as io]
         '[clojure.pprint :as pprint])

;; --- CONFIGURATION ---

(def alfresco-host "http://localhost:8080")
(def alfresco-user "admin")
(def alfresco-pass "admin")
(def api-base (str alfresco-host "/alfresco/api/-default-/public/alfresco/versions/1"))

(def output-dir "api-specs")
(def metadata-file (str output-dir "/update-metadata.edn"))

;; API specifications to fetch
(def api-specs
  [{:name "core"
    :path "definitions/alfresco-core.yaml"
    :description "Core content management API"}
   {:name "model"
    :path "definitions/alfresco-model.yaml"
    :description "Content model definitions"}
   {:name "search"
    :path "definitions/alfresco-search.yaml"
    :description "Search functionality"}
   {:name "auth"
    :path "definitions/alfresco-auth.yaml"
    :description "Authentication API"}
   {:name "workflow"
    :path "definitions/alfresco-workflow.yaml"
    :description "Workflow management"}])

;; --- UTILS ---

(defn timestamp []
  (.format (java.time.LocalDateTime/now)
           (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss")))

(defn version-string []
  (.format (java.time.LocalDateTime/now)
           (java.time.format.DateTimeFormatter/ofPattern "yyyy.MM.dd-HHmm")))

(defn ensure-dir [dir]
  (.mkdirs (io/file dir)))

(defn get-json [url]
  (let [resp (curl/get url {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      (json/parse-string (:body resp) true)
      (throw (ex-info (str "Failed to fetch " url) resp)))))

(defn get-yaml [url]
  (let [resp (curl/get url {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      (:body resp)
      (throw (ex-info (str "Failed to fetch " url) resp)))))

;; --- API FETCHING AND CONVERSION ---

(defn fetch-and-convert-spec [spec version-dir]
  (let [{:keys [name path description]} spec
        spec-url (str alfresco-host "/api-explorer/" path)
        yaml-filename (str name ".yaml")
        edn-filename (str name ".edn")
        yaml-path (str version-dir "/" yaml-filename)
        edn-path (str version-dir "/" edn-filename)]

    (println (str "Fetching " name " API (" description ")..."))

    (try
      ;; Fetch YAML content
      (let [yaml-content (get-yaml spec-url)
            parsed-data (yaml/parse-string yaml-content)]

        ;; Save YAML
        (spit yaml-path yaml-content)
        (println "  ✅ YAML saved to" yaml-path)

        ;; Convert and save as EDN
        (with-open [writer (io/writer edn-path)]
          (pprint/pprint parsed-data writer))
        (println "  ✅ EDN saved to" edn-path)

        ;; Return metadata about this spec
        {:name name
         :description description
         :yaml-path yaml-path
         :edn-path edn-path
         :paths-count (count (get parsed-data :paths {}))
         :definitions-count (count (get parsed-data :definitions {}))
         :base-path (get parsed-data :basePath)
         :version (get-in parsed-data [:info :version])
         :title (get-in parsed-data [:info :title])
         :success true})

      (catch Exception e
        (println "  ❌ Failed to fetch" name ":" (.getMessage e))
        {:name name
         :description description
         :error (.getMessage e)
         :success false}))))

(defn test-api-connectivity []
  (println "=== Testing API Connectivity ===")
  (try
    (let [nodes-test (get-json (str api-base "/nodes/-root-"))]
      (println "✅ API connection successful!")
      (println "Root node:" (get-in nodes-test [:entry :name]))

      ;; Test folder access
      (let [folders (get-json (str api-base "/nodes/-root-/children?where=(isFolder=true)"))]
        (println "✅ Folder access working")
        (println "Found" (get-in folders [:list :pagination :count]) "folders"))

      true)
    (catch Exception e
      (println "❌ API connectivity failed:" (.getMessage e))
      false)))

(defn load-previous-metadata []
  (if (.exists (io/file metadata-file))
    (try
      (edn/read-string (slurp metadata-file))
      (catch Exception e
        (println "⚠️  Could not load previous metadata:" (.getMessage e))
        {}))
    {}))

(defn save-metadata [metadata]
  (with-open [writer (io/writer metadata-file)]
    (pprint/pprint metadata writer))
  (println "📝 Metadata saved to" metadata-file))

;; --- MAIN FUNCTION ---

(defn -main []
  (let [version (version-string)
        timestamp-now (timestamp)
        version-dir (str output-dir "/" version)]

    (println "=== Alfresco API Update Script ===")
    (println "Version:" version)
    (println "Timestamp:" timestamp-now)
    (println "Output directory:" version-dir)

    ;; Create directories
    (ensure-dir output-dir)
    (ensure-dir version-dir)

    ;; Test connectivity first
    (if (test-api-connectivity)
      (do
        (println "\n=== Fetching API Specifications ===")

        ;; Load previous metadata and fetch specs
        (let [previous-metadata (load-previous-metadata)
              spec-results (doall (map #(fetch-and-convert-spec % version-dir) api-specs))
              successful-specs (filter :success spec-results)
              failed-specs (filter #(not (:success %)) spec-results)

              ;; Create update metadata
              update-metadata
              {:update-info
               {:version version
                :timestamp timestamp-now
                :alfresco-host alfresco-host
                :total-specs (count api-specs)
                :successful-specs (count successful-specs)
                :failed-specs (count failed-specs)}

               :specs (into {} (map #(vector (:name %) %) spec-results))
               :previous-updates (get previous-metadata :previous-updates [])
               :current-version version
               :latest-successful-specs (map :name successful-specs)}

              ;; Add current update to history
              final-metadata
              (update update-metadata :previous-updates
                      conj (select-keys (:update-info update-metadata)
                                        [:version :timestamp :successful-specs :failed-specs]))]

          ;; Save metadata
          (save-metadata final-metadata)

          ;; Summary
          (println "\n=== Update Summary ===")
          (println "✅ Successfully updated:" (count successful-specs) "specifications")
          (when (seq failed-specs)
            (println "❌ Failed to update:" (count failed-specs) "specifications")
            (doseq [failed failed-specs]
              (println "  -" (:name failed) ":" (:error failed))))

          (println "\n📁 Files created in:" version-dir)
          (doseq [spec successful-specs]
            (println "  -" (:yaml-path spec))
            (println "  -" (:edn-path spec))
            (println "    Paths:" (:paths-count spec) "Definitions:" (:definitions-count spec)))

          (println "\n🔄 To use in yakread:")
          (println "1. Load EDN files: (edn/read-string (slurp \"" version-dir "/core.edn\"))")
          (println "2. Check metadata: (edn/read-string (slurp \"" metadata-file "\"))")
          (println "3. Use latest version path in your code")

          ;; Return success status
          (= (count successful-specs) (count api-specs))))

      (do
        (println "❌ Cannot proceed - API connectivity failed")
        false))))

(-main)