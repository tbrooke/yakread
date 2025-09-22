(ns test-calendar
  (:require [com.yakread.alfresco.calendar :as calendar]
            [clojure.tools.logging :as log]))

(defn test-calendar-config []
  "Test calendar configuration setup"
  (let [ctx {:alfresco/base-url "http://admin.mtzcg.com"
             :alfresco/username "admin"
             :alfresco/password "admin"}
        config (calendar/get-alfresco-config ctx)]
    (println "Alfresco config:")
    (println config)
    config))

(defn test-calendar-events []
  "Test calendar events retrieval"
  (let [ctx {:alfresco/base-url "http://admin.mtzcg.com"
             :alfresco/username "admin"
             :alfresco/password "admin"}]
    (println "Testing calendar events retrieval...")
    (try
      (let [events (calendar/get-published-calendar-events ctx)]
        (println "Found" (count events) "events:")
        (doseq [event events]
          (println "Event:" (:title event) "Date:" (:event-date event) "Has publish tag:" (:has-publish-tag event)))
        events)
      (catch Exception e
        (println "Error:" (.getMessage e))
        (.printStackTrace e)
        []))))

(defn -main []
  (println "=== Testing Calendar Configuration ===")
  (test-calendar-config)

  (println "\n=== Testing Calendar Events ===")
  (test-calendar-events)

  (println "\nTest complete."))

(when (= *file* (System/getProperty "babashka.file"))
  (-main))