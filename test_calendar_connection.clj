#!/usr/bin/env bb

;; Test script to verify calendar connection and event loading

(require '[babashka.curl :as curl]
         '[cheshire.core :as json]
         '[clojure.pprint :as pprint])

;; Configuration from context document
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

(defn get-event-details [event-node-id]
  "Get details of a specific calendar event"
  (let [url (str api-base "/nodes/" event-node-id)
        resp (curl/get url {:basic-auth [(:username alfresco-config) (:password alfresco-config)]})]
    (if (= 200 (:status resp))
      {:success true :data (json/parse-string (:body resp) true)}
      {:success false :status (:status resp) :error (:body resp)})))

(defn main []
  (println "=== Mt Zion Calendar Connection Test ===\n")

  (println "Testing connection to Alfresco calendar...")
  (println "Calendar Node ID:" calendar-node-id)
  (println "API Base URL:" api-base)
  (println)

  (let [events-result (get-calendar-events)]
    (if (:success events-result)
      (do
        (println "✅ Successfully connected to calendar!")
        (let [entries (get-in events-result [:data :list :entries])]
          (println "Found" (count entries) "items in calendar:")
          (println)

          (doseq [entry entries]
            (let [event-data (:entry entry)
                  event-id (:id event-data)
                  event-name (:name event-data)]
              (println "📅 Event:" event-name)
              (println "   Node ID:" event-id)

              ;; Get detailed event information
              (let [details-result (get-event-details event-id)]
                (when (:success details-result)
                  (let [props (get-in details-result [:data :entry :properties])]
                    (when props
                      (println "   Properties:")
                      (doseq [[k v] props]
                        (when (or (re-find #"date|time|where|event" (str k))
                                  (re-find #"ia:" (str k)))
                          (println "    " k ":" v))))))
              (println)))))

      (do
        (println "❌ Failed to connect to calendar")
        (println "Status:" (:status events-result))
        (println "Error:" (:error events-result))
        (println)
        (println "Make sure:")
        (println "1. Alfresco is running at http://admin.mtzcg.com")
        (println "2. Username/password are correct")
        (println "3. Calendar node ID is valid"))))))

(main)