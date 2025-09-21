#!/usr/bin/env bb

;; Working Malli schema generator using actual API responses
;; This demonstrates Clojure data transformation concepts

(require '[babashka.curl :as curl]
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

;; --- HTTP CLIENT FUNCTIONS ---

(defn get-json [url]
  "Makes HTTP request and parses JSON response with proper keyword conversion"
  (let [resp (curl/get url {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      ;; Parse JSON manually with keyword conversion
      (json/parse-string (:body resp) true)  ; true = convert keys to keywords
      (throw (ex-info (str "Failed to fetch " url) resp)))))

;; --- SCHEMA INFERENCE FROM ACTUAL DATA ---

(defn infer-type
  "Clojure function that looks at actual data and infers Malli schema type"
  [value]
  (cond
    (string? value) :string
    (integer? value) :int
    (boolean? value) :boolean
    (map? value) [:map]  ; Will be filled in by analyze-map
    (vector? value) [:sequential :any]  ; Could be refined
    :else :any))

(defn analyze-map
  "Takes a Clojure map and creates a Malli schema describing its structure"
  [data-map]
  (into [:map]  ; Start with :map schema
        (for [[key value] data-map]  ; Iterate through key-value pairs
          [key (infer-type value)])))  ; Create [key schema] pairs

;; --- SCHEMA GENERATION FROM LIVE DATA ---

(defn fetch-and-analyze-node []
  "Fetch real node data and create schema from it"
  (println "Fetching root node to analyze structure...")
  (let [root-response (get-json (str api-base "/nodes/-root-"))
        node-data (get root-response :entry)]  ; Use keyword access for Clojure maps

    ;; DEBUGGING: Let's see what we actually got
    (println "DEBUG: Response keys:" (keys root-response))
    (println "DEBUG: Node data type:" (type node-data))
    (when node-data
      (println "DEBUG: Node data keys:" (keys node-data)))

    (println "✅ Got node data, analyzing structure...")

    ;; This is the key Clojure transformation:
    ;; Take real API data -> generate Malli schema
    {:node-schema (if node-data (analyze-map node-data) [:map])
     :sample-data node-data}))

(defn fetch-and-analyze-children []
  "Fetch children list and analyze that structure too"
  (println "Fetching node children to analyze list structure...")
  (let [children-response (get-json (str api-base "/nodes/-root-/children"))
        list-data (get children-response "list")
        entries (get list-data "entries")]

    (println "✅ Got children data, analyzing...")

    {:list-schema (analyze-map list-data)
     :pagination-schema (analyze-map (get list-data "pagination"))
     :entry-count (count entries)}))

;; --- MAIN EXECUTION ---

(defn -main []
  (println "=== Working Schema Generator ===")
  (println "Connecting to Alfresco via SSH tunnel...")

  (try
    ;; Clojure's 'let' allows us to bind multiple values and use them
    (let [node-analysis (fetch-and-analyze-node)
          children-analysis (fetch-and-analyze-children)

          ;; Create a registry of schemas (Clojure map)
          schema-registry {:alfresco/Node (:node-schema node-analysis)
                          :alfresco/NodeList (:list-schema children-analysis)
                          :alfresco/Pagination (:pagination-schema children-analysis)}]

      ;; Save the schemas as EDN (Clojure's data format)
      (with-open [writer (io/writer "generated-model/live-schemas.edn")]
        (pprint/pprint schema-registry writer))

      ;; Save sample data for reference
      (with-open [writer (io/writer "generated-model/sample-data.edn")]
        (pprint/pprint {:node-sample (:sample-data node-analysis)
                       :children-count (:entry-count children-analysis)}
                       writer))

      (println "\n=== Success! ===")
      (println "Generated schemas based on live API data")
      (println "- Schema registry: generated-model/live-schemas.edn")
      (println "- Sample data: generated-model/sample-data.edn")
      (println "\nSchema summary:")
      (doseq [[schema-name schema-def] schema-registry]  ; Iterate and print
        (println "  " schema-name ":" (count (rest schema-def)) "fields")))

    (catch Exception e
      (println "❌ Error:" (.getMessage e)))))

;; Run the script
(-main)