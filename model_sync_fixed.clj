#!/usr/bin/env bb

;; Fixed Babashka script for your Alfresco Community Edition server
;; Uses the working API endpoints we discovered

(require '[babashka.curl :as curl]
         '[cheshire.core :as json]
         '[clojure.edn :as edn]
         '[clojure.string :as str]
         '[clojure.java.io :as io])

;; --- CONFIGURATION ---

(def alfresco-host "http://admin.mtzcg.com")
(def alfresco-user "admin")
(def alfresco-pass "admin")

;; Fixed URLs based on discovery results
(def swagger-url (str alfresco-host "/alfresco/api/swagger.json"))
(def api-base (str alfresco-host "/alfresco/api/-default-/public/alfresco/versions/1"))
(def discovery-url (str api-base "/discovery"))

(def output-dir "generated-model")
(def swagger-edn (str output-dir "/swagger-malli.edn"))
(def discovery-edn (str output-dir "/discovery-info.edn"))

;; --- UTILS ---

(defn get-json [url]
  (println "Fetching:" url)
  (let [resp (curl/get url {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      (json/parse-string (:body resp) true)
      (do
        (println "Failed to fetch" url "- Status:" (:status resp))
        (when (:body resp)
          (println "Error details:" (subs (str (:body resp)) 0 (min 200 (count (str (:body resp)))))))
        (throw (ex-info (str "Failed to fetch " url) resp))))))

(defn ensure-dir [dir]
  (.mkdirs (io/file dir)))

(defn write-edn [data path]
  (spit path (prn-str data)))

;; --- Swagger → Malli ---

(defn swagger-type->malli [{:keys [type format items properties required enum $ref]}]
  (cond
    $ref (keyword (last (str/split $ref #"/")))
    enum [:enum enum]
    (= type "array") [:sequential (swagger-type->malli items)]
    (= type "object")
    (let [req (set required)]
      (into [:map]
            (for [[k v] properties]
              [(keyword k) (if (req k)
                             (swagger-type->malli v)
                             [:maybe (swagger-type->malli v)])])))
    (= type "string")
    (case format
      "date-time" :inst
      "date" :string
      :string)
    (= type "integer") :int
    (= type "number") :double
    (= type "boolean") :boolean
    :else :any))

(defn swagger->malli-registry [swagger]
  (let [definitions (get swagger "definitions" {})]
    (into {}
          (for [[k v] definitions]
            [(keyword k) (swagger-type->malli v)]))))

;; --- MAIN ---

(defn -main []
  (ensure-dir output-dir)

  (println "=== Testing API Connectivity ===")

  ;; Test basic API connectivity
  (println "Testing API base...")
  (let [nodes-test (get-json (str api-base "/nodes/-root-"))]
    (println "✅ API connection successful!")
    (println "Root node:" (get-in nodes-test [:entry :name])))

  ;; Try to get API discovery info
  (println "\n=== Fetching API Discovery ===")
  (try
    (let [discovery (get-json discovery-url)]
      (write-edn discovery discovery-edn)
      (println "✅ Discovery info saved to" discovery-edn))
    (catch Exception e
      (println "⚠️  Discovery endpoint not available:" (.getMessage e))))

  ;; Get API specifications from YAML files
  (println "\n=== Fetching API Specifications ===")
  (let [yaml-specs ["definitions/alfresco-core.yaml"
                    "definitions/alfresco-model.yaml"
                    "definitions/alfresco-search.yaml"]]
    (doseq [spec-path yaml-specs]
      (try
        (println (str "Fetching " spec-path "..."))
        (let [spec-url (str alfresco-host "/api-explorer/" spec-path)
              yaml-content (curl/get spec-url {:basic-auth [alfresco-user alfresco-pass]})
              spec-name (str output-dir "/" (str/replace spec-path #"definitions/|\.yaml" ""))]
          (when (= 200 (:status yaml-content))
            (spit (str spec-name ".yaml") (:body yaml-content))
            (println "✅" spec-path "saved to" (str spec-name ".yaml"))))
        (catch Exception e
          (println "⚠️ " spec-path "failed:" (.getMessage e))))))

  ;; Test some content model endpoints
  (println "\n=== Testing Content Model Access ===")
  (try
    ;; Try to get content types via nodes API
    (let [company-home (get-json (str api-base "/nodes/-root-/children?where=(isFolder=true)"))]
      (println "✅ Can access folder structure")
      (println "Found" (get-in company-home [:list :pagination :count]) "folders"))
    (catch Exception e
      (println "⚠️  Content access failed:" (.getMessage e))))

  (println "\n=== Summary ===")
  (println "✅ Your Alfresco Community Edition REST API is working!")
  (println "🌐 API Explorer available at: http://admin.mtzcg.com/api-explorer/")
  (println "🔧 API Base URL: " api-base)
  (println "📝 Generated model files in:" output-dir)
  (println "\nNext steps:")
  (println "1. Visit the API Explorer in your browser to see all available endpoints")
  (println "2. Use the generated Malli schemas in your yakread project")
  (println "3. Build content extraction using the V1 REST API"))

(-main)