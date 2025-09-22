#!/usr/bin/env bb

;; Debug script to see what's happening with calendar event loading

(require '[babashka.curl :as curl]
         '[cheshire.core :as json]
         '[clojure.pprint :as pprint])

;; Configuration
(def alfresco-config
  {:base-url "http://admin.mtzcg.com"
   :username "admin"
   :password "admin"})

(def calendar-node-id "4f6972f5-9d50-4ff3-a972-f59d500ff3f4")
(def api-base (str (:base-url alfresco-config) "/alfresco/api/-default-/public/alfresco/versions/1"))

(defn get-calendar-events []
  "Get events from the Mt Zion calendar folder"
  (let [url (str api-base "/nodes/" calendar-node-id "/children")
        resp (curl/get url {:basic-auth [(:username alfresco-config) (:password alfresco-config)]})]
    (if (= 200 (:status resp))
      {:success true :data (json/parse-string (:body resp) true)}
      {:success false :status (:status resp) :error (:body resp)})))

(defn extract-event-metadata [node-entry]
  "Extract event metadata like our Clojure function does"
  (let [entry (:entry node-entry)
        properties (:properties entry)]
    {:node-id (:id entry)
     :title (:name entry)
     :description (get properties "cm:description")
     :event-date (get properties "ia:fromDate")
     :event-time (get properties "ia:toDate")
     :location (get properties "ia:whereEvent")
     :created-at (:createdAt entry)
     :modified-at (:modifiedAt entry)
     :is-folder (:isFolder entry)
     :type (if (:isFolder entry) :event-folder :event-document)
     :mime-type (get-in entry [:content :mimeType])
     :has-calendar-aspects (some #(str/includes? % "calendar") (:aspectNames entry []))}))

(defn process-for-component-format [event]
  "Convert to component format like our Clojure function does"
  {:component-type "alfresco-event"
   :page "events"
   :layout "listing-page"
   :display-order 1
   :title (:title event)
   :event-date (:event-date event)
   :event-time (:event-time event)
   :location (:location event)
   :description (:description event)
   :node-id (:node-id event)})

(defn is-upcoming-event? [event]
  "Check if event is upcoming (like our date filtering)"
  (and (:event-date event)
       (try
         (let [event-date-str (:event-date event)]
           (println "Checking date:" event-date-str)
           ;; The date comes as "2025-09-28T18:30:00.000+0000"
           ;; We need to parse just the date part
           (let [date-part (first (clojure.string/split event-date-str #"T"))
                 today "2025-09-21"] ; Simulating today's date
             (println "Event date part:" date-part "vs today:" today)
             (>= 0 (compare date-part today))))
         (catch Exception e
           (println "Date parsing error:" (.getMessage e))
           false))))

(defn main []
  (println "=== Debug Calendar Event Loading ===\n")

  (let [events-result (get-calendar-events)]
    (if (:success events-result)
      (let [entries (get-in events-result [:data :list :entries])
            processed-events (map extract-event-metadata entries)]

        (println "Raw events from Alfresco:")
        (doseq [event processed-events]
          (println "- Event:" (:title event))
          (println "  Node ID:" (:node-id event))
          (println "  Event Date:" (:event-date event))
          (println "  Location:" (:location event))
          (println "  Is Folder:" (:is-folder event))
          (println "  Type:" (:type event))
          (println))

        (println "Filtering non-folder events...")
        (let [event-docs (filter #(not (:is-folder %)) processed-events)]
          (println "Event documents:" (count event-docs))
          (doseq [event event-docs]
            (println "- Event doc:" (:title event)))
          (println))

        (println "Checking upcoming events filter...")
        (let [event-docs (filter #(not (:is-folder %)) processed-events)
              upcoming-events (filter is-upcoming-event? event-docs)]
          (println "Upcoming events:" (count upcoming-events))
          (doseq [event upcoming-events]
            (println "- Upcoming:" (:title event)))
          (println))

        (println "Final component format:")
        (let [event-docs (filter #(not (:is-folder %)) processed-events)
              upcoming-events (filter is-upcoming-event? event-docs)
              component-events (map process-for-component-format upcoming-events)]
          (doseq [comp component-events]
            (println "Component:")
            (pprint/pprint comp)
            (println))))

      (println "Failed to get calendar events:" (:error events-result)))))

;; Add string split function
(defn split [s delimiter]
  (clojure.string/split s (re-pattern (java.util.regex.Pattern/quote delimiter))))

(main)