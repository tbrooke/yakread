#!/usr/bin/env bb

;; Enhanced Malli schema generator for both Web Site and Calendar nodes
;; Based on model_sync_working.clj but extended to handle calendar events

(require '[babashka.curl :as curl]
         '[cheshire.core :as json]
         '[clojure.edn :as edn]
         '[clojure.string :as str]
         '[clojure.java.io :as io]
         '[clojure.pprint :as pprint])

;; --- CONFIGURATION ---
(def alfresco-host "http://admin.mtzcg.com")
(def alfresco-user "admin")
(def alfresco-pass "admin")
(def api-base (str alfresco-host "/alfresco/api/-default-/public/alfresco/versions/1"))

;; Node IDs from the working system
(def calendar-node-id "4f6972f5-9d50-4ff3-a972-f59d500ff3f4")

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

(defn analyze-properties-map
  "Special analysis for Alfresco properties which contain calendar-specific fields"
  [properties-map]
  (when properties-map
    (let [schema-pairs (for [[key value] properties-map]
                         [key (cond
                                ;; Calendar event date fields
                                (and (string? key) (str/includes? key "Date")) :string
                                ;; Calendar event text fields
                                (and (string? key) (str/includes? key "Event")) :string
                                ;; Default inference
                                :else (infer-type value))])]
      (into [:map] schema-pairs))))

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
        list-data (get children-response :list)
        entries (get list-data :entries)]

    (println "✅ Got children data, analyzing...")

    {:list-schema (analyze-map list-data)
     :pagination-schema (analyze-map (get list-data :pagination))
     :entry-count (count entries)}))

(defn fetch-and-analyze-calendar-events []
  "Fetch calendar events from the specific calendar node"
  (println "Fetching calendar events from node:" calendar-node-id)
  (try
    (let [calendar-url (str api-base "/nodes/" calendar-node-id "/children?include=properties,aspectNames")
          calendar-response (get-json calendar-url)
          list-data (get calendar-response :list)
          entries (get list-data :entries)]

      (println "✅ Got calendar data, entries count:" (count entries))

      ;; Analyze a sample calendar event entry if available
      (if (seq entries)
        (let [sample-event (first entries)
              event-entry (get sample-event :entry)
              properties (get event-entry :properties)]

          (println "DEBUG: Calendar event keys:" (keys event-entry))
          (println "DEBUG: Calendar properties keys:" (when properties (keys properties)))

          {:calendar-list-schema (analyze-map list-data)
           :calendar-entry-schema (analyze-map event-entry)
           :calendar-properties-schema (analyze-properties-map properties)
           :sample-event event-entry
           :sample-properties properties
           :entry-count (count entries)})

        (do
          (println "WARNING: No calendar events found")
          {:calendar-list-schema (analyze-map list-data)
           :calendar-entry-schema [:map]
           :calendar-properties-schema [:map]
           :sample-event nil
           :sample-properties nil
           :entry-count 0})))

    (catch Exception e
      (println "ERROR fetching calendar events:" (.getMessage e))
      {:error (.getMessage e)})))

(defn fetch-and-analyze-sample-event-tags [sample-event]
  "Fetch tags for a sample event to understand tag structure"
  (when sample-event
    (try
      (let [event-id (get sample-event :id)
            tags-url (str api-base "/nodes/" event-id "/tags")
            tags-response (get-json tags-url)
            tag-list (get tags-response :list)
            tag-entries (get tag-list :entries)]

        (println "✅ Got event tags, count:" (count tag-entries))

        {:tags-list-schema (analyze-map tag-list)
         :tag-entry-schema (if (seq tag-entries)
                            (analyze-map (get (first tag-entries) :entry))
                            [:map])
         :sample-tags tag-entries})

      (catch Exception e
        (println "WARNING: Could not fetch tags for sample event:" (.getMessage e))
        {:tags-list-schema [:map]
         :tag-entry-schema [:map]
         :sample-tags []}))))

;; --- MAIN EXECUTION ---

(defn -main []
  (println "=== Enhanced Schema Generator for Web Site + Calendar ===")
  (println "Connecting to Alfresco at:" alfresco-host)

  (try
    ;; Clojure's 'let' allows us to bind multiple values and use them
    (let [node-analysis (fetch-and-analyze-node)
          children-analysis (fetch-and-analyze-children)
          calendar-analysis (fetch-and-analyze-calendar-events)
          tags-analysis (fetch-and-analyze-sample-event-tags (:sample-event calendar-analysis))

          ;; Create a comprehensive registry of schemas (Clojure map)
          schema-registry {:alfresco/Node (:node-schema node-analysis)
                          :alfresco/NodeList (:list-schema children-analysis)
                          :alfresco/Pagination (:pagination-schema children-analysis)
                          :alfresco/CalendarList (:calendar-list-schema calendar-analysis)
                          :alfresco/CalendarEntry (:calendar-entry-schema calendar-analysis)
                          :alfresco/CalendarProperties (:calendar-properties-schema calendar-analysis)
                          :alfresco/TagsList (:tags-list-schema tags-analysis)
                          :alfresco/TagEntry (:tag-entry-schema tags-analysis)}]

      ;; Save the schemas as EDN (Clojure's data format)
      (io/make-parents "generated-model/calendar-schemas.edn")
      (with-open [writer (io/writer "generated-model/calendar-schemas.edn")]
        (pprint/pprint schema-registry writer))

      ;; Save sample data for reference
      (with-open [writer (io/writer "generated-model/calendar-sample-data.edn")]
        (pprint/pprint {:node-sample (:sample-data node-analysis)
                       :children-count (:entry-count children-analysis)
                       :calendar-count (:entry-count calendar-analysis)
                       :calendar-sample (:sample-event calendar-analysis)
                       :calendar-properties (:sample-properties calendar-analysis)
                       :sample-tags (:sample-tags tags-analysis)}
                       writer))

      (println "\n=== Success! ===")
      (println "Generated schemas based on live API data")
      (println "- Schema registry: generated-model/calendar-schemas.edn")
      (println "- Sample data: generated-model/calendar-sample-data.edn")
      (println "\nSchema summary:")
      (doseq [[schema-name schema-def] schema-registry]  ; Iterate and print
        (println "  " schema-name ":" (count (rest schema-def)) "fields"))

      (when (:error calendar-analysis)
        (println "\n⚠️  Calendar analysis had errors:" (:error calendar-analysis))))

    (catch Exception e
      (println "❌ Error:" (.getMessage e))
      (.printStackTrace e))))

;; Run the script
(-main)