#!/usr/bin/env bb

;; Sync Calendar Events from Alfresco to XTDB for mtzUIX display
;; Creates bitemporal records linking Alfresco calendar events to displayed content

(require '[babashka.curl :as curl]
         '[cheshire.core :as json]
         '[clojure.edn :as edn])

;; --- CONFIGURATION ---
(def alfresco-host "http://admin.mtzcg.com")
(def alfresco-user "admin")
(def alfresco-pass "admin")
(def api-base (str alfresco-host "/alfresco/api/-default-/public/alfresco/versions/1"))
(def calendar-node-id "4f6972f5-9d50-4ff3-a972-f59d500ff3f4")

;; --- HTTP CLIENT ---

(defn get-node-children [node-id options]
  (let [query-params (when options
                       (str "?" (clojure.string/join "&"
                                                     (for [[k v] options]
                                                       (str (name k) "=" v)))))
        url (str api-base "/nodes/" node-id "/children" query-params)
        resp (curl/get url {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      {:success true :data (json/parse-string (:body resp) true)}
      {:success false :error (:body resp)})))

(defn get-node-tags [node-id]
  (let [resp (curl/get (str api-base "/nodes/" node-id "/tags")
                       {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      {:success true :data (json/parse-string (:body resp) true)}
      {:success false :error (:body resp)})))

;; --- CALENDAR EVENT EXTRACTION ---

(defn extract-calendar-events []
  "Extract calendar events from Alfresco Calendar folder"
  (println "📅 Extracting Calendar Events from Alfresco...")

  (let [children-result (get-node-children calendar-node-id {:include "properties,aspectNames"})]

    (if (:success children-result)
      (let [entries (get-in children-result [:data :list :entries])
            event-docs (filter #(not (:isFolder (:entry %))) entries)]

        (println "   Found" (count entries) "items in Calendar folder")
        (println "   Event documents:" (count event-docs))

        ;; Process each calendar event
        (for [event-entry event-docs]
          (let [entry (:entry event-entry)
                properties (:properties entry)
                event-id (:id entry)
                event-name (:name entry)]

            (println "   Processing:" event-name)

            ;; Get tags for this event
            (let [tags-result (get-node-tags event-id)
                  tags (if (:success tags-result)
                         (get-in tags-result [:data :list :entries])
                         [])
                  has-publish-tag (some #(= "publish" (get-in % [:entry :tag])) tags)

                  ;; Extract event details using keyword properties
                  event-date (get properties :ia:fromDate)
                  event-title (or (get properties :ia:whatEvent) event-name)
                  event-description (get properties :ia:descriptionEvent)
                  event-location (get properties :ia:whereEvent)
                  event-end-time (get properties :ia:toDate)]

              (println "     Title:" event-title)
              (println "     Date:" event-date)
              (println "     Has publish tag:" has-publish-tag)

              {:success true
               :alfresco-node-id event-id
               :alfresco-name event-name
               :alfresco-modified-at (:modifiedAt entry)
               :alfresco-created-at (:createdAt entry)
               :alfresco-properties properties
               :event-title event-title
               :event-description event-description
               :event-date event-date
               :event-end-time event-end-time
               :event-location event-location
               :has-publish-tag has-publish-tag
               :tags (map #(get-in % [:entry :tag]) tags)
               :extracted-at (java.time.Instant/now)}))))

      {:error "Could not access Calendar folder" :details (:error children-result)})))

;; --- XTDB DOCUMENT CREATION ---

(defn create-xtdb-document [calendar-event]
  "Transform Alfresco calendar event into XTDB document for bitemporal storage"
  (let [doc-id (random-uuid)
        now (java.time.Instant/now)
        is-upcoming (if (:event-date calendar-event)
                      (try
                        (let [event-date (java.time.LocalDate/parse (subs (:event-date calendar-event) 0 10))]
                          (not (.isBefore event-date (java.time.LocalDate/now))))
                        (catch Exception e false))
                      false)]

    {:xt/id doc-id

     ;; Content classification
     :content/type :mtzuix-calendar-event
     :content/component :calendar-events
     :content/page :events
     :content/status (if (:has-publish-tag calendar-event) :published :draft)
     :content/display-order 1

     ;; Alfresco source tracking (bitemporal linkage)
     :alfresco/source-node-id (:alfresco-node-id calendar-event)
     :alfresco/source-name (:alfresco-name calendar-event)
     :alfresco/source-modified-at (:alfresco-modified-at calendar-event)
     :alfresco/source-created-at (:alfresco-created-at calendar-event)
     :alfresco/source-properties (:alfresco-properties calendar-event)

     ;; Calendar event data
     :calendar/title (:event-title calendar-event)
     :calendar/description (:event-description calendar-event)
     :calendar/start-date (:event-date calendar-event)
     :calendar/end-date (:event-end-time calendar-event)
     :calendar/location (:event-location calendar-event)
     :calendar/has-publish-tag (:has-publish-tag calendar-event)
     :calendar/is-upcoming is-upcoming
     :calendar/tags (:tags calendar-event)

     ;; Temporal tracking
     :sync/extracted-from-alfresco-at (:extracted-at calendar-event)
     :sync/stored-in-xtdb-at now
     :sync/version 1
     :sync/checksum (str (hash [(:event-title calendar-event) (:event-date calendar-event)]))

     ;; mtzUIX serving metadata
     :mtzuix/component-props {:title (:event-title calendar-event)
                              :component-type :calendar-event
                              :source-attribution "Mt Zion Alfresco Calendar"}
     :mtzuix/last-served-at nil
     :mtzuix/serve-count 0}))

;; --- XTDB SIMULATION (for now, we'll save to files) ---

(defn save-to-xtdb-simulation [xtdb-documents]
  "Simulate saving to XTDB by writing to files"
  (println "💾 Saving Calendar Events to XTDB (simulation)...")

  (doseq [doc xtdb-documents]
    (let [doc-id (:xt/id doc)
          filename (str "xtdb-calendar-" doc-id ".edn")]

      (spit filename (pr-str doc))
      (println "   ✅ Saved event:" filename)
      (println "     Event:" (:calendar/title doc) "on" (:calendar/start-date doc))))

  (println "   📝 In real yakread, this would be:")
  (println "     (biff/submit-tx ctx (map #(vector :xtdb.api/put %) xtdb-documents))")

  xtdb-documents)

;; --- CONTENT SERVING PREPARATION ---

(defn prepare-events-for-mtzuix [xtdb-documents]
  "Prepare calendar events in the format mtzUIX expects"
  (println "\n📤 Preparing calendar events for mtzUIX serving...")

  (for [doc xtdb-documents]
    {:component-id "events-calendar"
     :component-type "calendar-event"
     :event-id (:alfresco/source-node-id doc)
     :title (:calendar/title doc)
     :description (:calendar/description doc)
     :event-date (:calendar/start-date doc)
     :event-time (:calendar/end-date doc)
     :location (:calendar/location doc)
     :has-publish-tag (:calendar/has-publish-tag doc)
     :is-upcoming (:calendar/is-upcoming doc)
     :tags (:calendar/tags doc)
     :last-updated (:alfresco/source-modified-at doc)
     :source-info {:type "alfresco"
                   :node-id (:alfresco/source-node-id doc)
                   :name (:alfresco/source-name doc)}
     :xtdb-id (:xt/id doc)}))

;; --- MAIN SYNC FUNCTION ---

(defn sync-calendar-to-xtdb []
  "Complete sync of Calendar Events from Alfresco to XTDB"
  (println "🔄 Starting Calendar Events → XTDB sync for mtzUIX pipeline\n")

  (try
    ;; Extract from Alfresco
    (let [alfresco-events (extract-calendar-events)
          successful-extracts (filter :success alfresco-events)]

      (if (seq successful-extracts)
        (do
          (println "\n📋 Successfully extracted" (count successful-extracts) "calendar events")

          ;; Transform to XTDB documents
          (let [xtdb-documents (map create-xtdb-document successful-extracts)
                published-events (filter #(= :published (:content/status %)) xtdb-documents)]

            (println "\n🏗️  Created" (count xtdb-documents) "XTDB documents with bitemporal tracking")
            (println "📅 Published events:" (count published-events))

            ;; Save to XTDB (simulated)
            (save-to-xtdb-simulation xtdb-documents)

            (println "\n✅ Calendar sync completed successfully!")
            (println "📊 Results:")
            (println "   📅 Calendar events extracted:" (count successful-extracts))
            (println "   💾 XTDB documents created:" (count xtdb-documents))
            (println "   📢 Published events:" (count published-events))
            (println "   🔗 Bitemporal links established:" (count xtdb-documents))

            {:success true
             :extracted (count successful-extracts)
             :stored (count xtdb-documents)
             :published (count published-events)
             :documents xtdb-documents}))

        (do
          (println "❌ No calendar events could be extracted")
          {:success false :error "No extractable events"})))

    (catch Exception e
      (println "❌ Calendar sync failed:" (.getMessage e))
      {:success false :error (.getMessage e)})))

;; --- MAIN EXECUTION ---

(defn -main []
  (println "📅 Mt Zion Calendar Events Pipeline")
  (println "   Alfresco Calendar → XTDB → mtzUIX preparation")
  (println "   Calendar Node ID:" calendar-node-id "\n")

  (let [sync-result (sync-calendar-to-xtdb)]
    (when (:success sync-result)
      (let [mtzuix-events (prepare-events-for-mtzuix (:documents sync-result))
            published-events (filter :has-publish-tag mtzuix-events)]

        (println "\n🎯 Calendar events ready for mtzUIX:")
        (doseq [event published-events]
          (println "   📅" (:title event))
          (println "     Date:" (:event-date event))
          (println "     Location:" (:location event))
          (println "     XTDB ID:" (:xtdb-id event)))

        (println "\n💡 Next steps:")
        (println "   1. Load events from XTDB in home.clj")
        (println "   2. Display published events on /events page")
        (println "   3. Use bitemporal queries to show past events")

        ;; Save mtzUIX-ready events for next step
        (spit "mtzuix-calendar-events.edn" (pr-str mtzuix-events))
        (println "\n📁 mtzUIX calendar events saved to: mtzuix-calendar-events.edn")

        sync-result))))

;; Run the sync
(-main)