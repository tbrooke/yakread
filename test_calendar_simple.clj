#!/usr/bin/env bb

;; Simple test to verify calendar integration using the basic Alfresco client

(require '[babashka.curl :as curl]
         '[cheshire.core :as json]
         '[clojure.pprint :as pprint])

;; --- CONFIGURATION ---
(def alfresco-host "http://admin.mtzcg.com")
(def alfresco-user "admin")
(def alfresco-pass "admin")
(def api-base (str alfresco-host "/alfresco/api/-default-/public/alfresco/versions/1"))
(def calendar-node-id "4f6972f5-9d50-4ff3-a972-f59d500ff3f4")

;; --- HTTP CLIENT ---

(defn get-json [url]
  "Makes HTTP request and parses JSON response"
  (let [resp (curl/get url {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      (json/parse-string (:body resp) true)
      (throw (ex-info (str "Failed to fetch " url) resp)))))

;; --- CALENDAR FUNCTIONS ---

(defn get-calendar-events []
  "Get calendar events with properties and aspect names"
  (let [url (str api-base "/nodes/" calendar-node-id "/children?include=properties,aspectNames")]
    (get-json url)))

(defn get-event-tags [event-id]
  "Get tags for a specific event"
  (let [url (str api-base "/nodes/" event-id "/tags")]
    (get-json url)))

(defn process-calendar-event [event-entry]
  "Process a single calendar event entry"
  (let [entry (:entry event-entry)
        properties (:properties entry)
        event-id (:id entry)]

    ;; Get tags for this event
    (let [tags-response (try (get-event-tags event-id) (catch Exception e {}))
          tags (get-in tags-response [:list :entries])
          has-publish-tag (some #(= "publish" (get-in % [:entry :tag])) tags)

          ;; Extract event details (using keyword keys)
          event-date (get properties :ia:fromDate)
          event-title (or (get properties :ia:whatEvent) (:name entry))
          event-description (get properties :ia:descriptionEvent)
          event-location (get properties :ia:whereEvent)]

      {:event-id event-id
       :title event-title
       :description event-description
       :event-date event-date
       :location event-location
       :has-publish-tag has-publish-tag
       :tags (map #(get-in % [:entry :tag]) tags)
       :properties properties})))

;; --- MAIN TEST ---

(defn -main []
  (println "=== Simple Calendar Test ===")
  (println "Fetching calendar events from node:" calendar-node-id)

  (try
    (let [calendar-response (get-calendar-events)
          entries (get-in calendar-response [:list :entries])
          event-docs (filter #(not (:isFolder (:entry %))) entries)]

      (println "Found" (count entries) "total items")
      (println "Found" (count event-docs) "event documents")

      (doseq [event-entry event-docs]
        (let [processed-event (process-calendar-event event-entry)
              entry (:entry event-entry)
              properties (:properties entry)]
          (println "\n--- Event ---")
          (println "Title:" (:title processed-event))
          (println "Date:" (:event-date processed-event))
          (println "Location:" (:location processed-event))
          (println "Has publish tag:" (:has-publish-tag processed-event))
          (println "Tags:" (:tags processed-event))
          (when (:description processed-event)
            (println "Description:" (:description processed-event)))

          (println "\nDEBUG Properties:")
          (pprint/pprint properties)))

      (println "\n=== Test Complete ==="))

    (catch Exception e
      (println "ERROR:" (.getMessage e))
      (.printStackTrace e))))

;; Run the test
(-main)