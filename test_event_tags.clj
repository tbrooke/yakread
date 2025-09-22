#!/usr/bin/env bb

;; Test script to check event tags for publish filtering

(require '[babashka.curl :as curl]
         '[cheshire.core :as json]
         '[clojure.pprint :as pprint])

(def alfresco-config
  {:base-url "http://admin.mtzcg.com"
   :username "admin"
   :password "admin"})

(def calendar-node-id "4f6972f5-9d50-4ff3-a972-f59d500ff3f4")
(def api-base (str (:base-url alfresco-config) "/alfresco/api/-default-/public/alfresco/versions/1"))

(defn get-calendar-events-with-tags []
  "Get events with full properties and tags"
  (let [url (str api-base "/nodes/" calendar-node-id "/children?include=properties,aspectNames")
        resp (curl/get url {:basic-auth [(:username alfresco-config) (:password alfresco-config)]})]
    (if (= 200 (:status resp))
      {:success true :data (json/parse-string (:body resp) true)}
      {:success false :status (:status resp) :error (:body resp)})))

(defn get-event-tags [event-node-id]
  "Get tags for a specific event"
  (let [url (str api-base "/nodes/" event-node-id "/tags")
        resp (curl/get url {:basic-auth [(:username alfresco-config) (:password alfresco-config)]})]
    (if (= 200 (:status resp))
      {:success true :data (json/parse-string (:body resp) true)}
      {:success false :status (:status resp) :error (:body resp)})))

(defn main []
  (println "=== Event Tags Analysis ===\n")

  (let [events-result (get-calendar-events-with-tags)]
    (if (:success events-result)
      (let [entries (get-in events-result [:data :list :entries])]
        (println "Found" (count entries) "events in calendar:")
        (println)

        (doseq [entry entries]
          (let [event-data (:entry entry)
                event-id (:id event-data)
                event-name (:name event-data)
                properties (:properties event-data)]

            (println "📅 Event:" event-name)
            (println "   Node ID:" event-id)
            (println "   Event Title:" (get properties "ia:whatEvent"))
            (println "   Properties Keys:" (keys properties))
            (println)

            ;; Get tags for this event
            (let [tags-result (get-event-tags event-id)]
              (if (:success tags-result)
                (let [tags (get-in tags-result [:data :list :entries])]
                  (println "   Tags (" (count tags) "):")
                  (if (seq tags)
                    (doseq [tag tags]
                      (let [tag-name (get-in tag [:entry :tag])]
                        (println "     - " tag-name)
                        (when (= tag-name "publish")
                          (println "       ✅ PUBLISH TAG FOUND!"))))
                    (println "     No tags found"))
                  (println))
                (println "   Failed to get tags:" (:error tags-result))))

            (println "----------------------------------------"))))

      (println "Failed to get events:" (:error events-result)))))

(main)