(ns com.yakread.alfresco.calendar
  "Alfresco Calendar API integration for Mt Zion UCC website"
  (:require [com.yakread.alfresco.client :as client]
            [com.yakread.alfresco.schema :as schema]
            [com.biffweb :as biff]
            [clojure.tools.logging :as log]))

(defn get-alfresco-config
  "Get Alfresco configuration from environment variables using biff/lookup"
  [ctx]
  {:alfresco/base-url (biff/lookup ctx :alfresco/base-url "http://localhost:8080")
   :alfresco/username (biff/lookup ctx :alfresco/username "admin")
   :alfresco/password (biff/lookup ctx :alfresco/password "admin")
   :timeout 30000
   :connection-timeout 10000})

(defn get-published-calendar-events
  "Get calendar events that have publish tags and are upcoming"
  [ctx]
  (try
    (let [alfresco-config (get-alfresco-config ctx)
          ;; Create a context that has the alfresco config for the client
          client-ctx (merge ctx alfresco-config)]
      (log/info "Fetching calendar events from Alfresco"
                "URL:" (:alfresco/base-url alfresco-config))

      ;; Use the basic calendar client functions with proper context
      (let [events-node-id "4f6972f5-9d50-4ff3-a972-f59d500ff3f4" ; Mt Zion Calendar folder
            events-result (client/get-node-children client-ctx events-node-id
                                                   {:include "properties,aspectNames"})]
        (if (:success events-result)
          (let [entries (get-in events-result [:data :list :entries])
                ;; Filter only event documents (not folders)
                event-docs (filter #(not (:isFolder (:entry %))) entries)
                ;; Process events with proper tag checking using basic client
                event-metadata (map (fn [node-entry]
                                      (let [entry (:entry node-entry)
                                            properties (:properties entry)
                                            node-id (:id entry)

                                            ;; Check for publish tag using basic client
                                            tags-response (client/get-node-tags ctx node-id)
                                            has-publish-tag (if (:success tags-response)
                                                              (let [tags (get-in tags-response [:data :list :entries])]
                                                                (some #(= "publish" (get-in % [:entry :tag])) tags))
                                                              false)

                                            ;; Check if event is upcoming
                                            event-date-str (get properties :ia:fromDate)
                                            is-upcoming (if event-date-str
                                                          (try
                                                            (let [event-date (java.time.LocalDate/parse (subs event-date-str 0 10))]
                                                              (not (.isBefore event-date (java.time.LocalDate/now))))
                                                            (catch Exception e false))
                                                          false)]

                                        {:node-id node-id
                                         :title (or (get properties :ia:whatEvent) (:name entry))
                                         :description (get properties :ia:descriptionEvent)
                                         :event-date event-date-str
                                         :event-time (get properties :ia:toDate)
                                         :location (get properties :ia:whereEvent)
                                         :has-publish-tag has-publish-tag
                                         :is-upcoming is-upcoming
                                         :created-at (:createdAt entry)
                                         :modified-at (:modifiedAt entry)}))
                                    event-docs)
                ;; Filter for published and upcoming events
                published-events (filter #(and (:has-publish-tag %)
                                               (:is-upcoming %))
                                        event-metadata)]

            (log/info "Successfully retrieved calendar events"
                      "Total entries:" (count entries)
                      "Event documents:" (count event-docs)
                      "Published events:" (count published-events))
            published-events)
          (do
            (log/warn "Failed to retrieve calendar events:" (:error events-result))
            []))))
    (catch Exception e
      (log/error "Exception retrieving calendar events:" (.getMessage e))
      [])))

(defn format-events-for-website
  "Convert Alfresco calendar events to website component format"
  [events]
  (map (fn [event]
         {:component-type "alfresco-event"
          :page "events"
          :layout "listing-page"
          :display-order 1
          :title (:title event)
          :event-date (:event-date event)
          :event-time (:event-time event)
          :location (:location event)
          :description (:description event)
          :node-id (:node-id event)
          :has-publish-tag (:has-publish-tag event)
          :is-upcoming (:is-upcoming event)})
       events))

(defn test-calendar-connection
  "Test the calendar connection and return sample events"
  [ctx]
  (try
    (let [alfresco-config (get-alfresco-config ctx)
          ;; Create a context that has the alfresco config for the client
          client-ctx (merge ctx alfresco-config)]
      (log/info "Testing calendar connection...")

      ;; Test basic connection first using context with alfresco config
      (let [health-result (client/health-check client-ctx)]
        (if (:success health-result)
          (do
            (log/info "Alfresco connection successful")
            ;; Now try to get calendar events
            (let [events (get-published-calendar-events client-ctx)]
              {:success true
               :connection-healthy true
               :events-count (count events)
               :sample-events (take 3 events)}))
          (do
            (log/error "Alfresco connection failed:" (:error health-result))
            {:success false
             :connection-healthy false
             :error (:error health-result)}))))
    (catch Exception e
      (log/error "Calendar connection test failed:" (.getMessage e))
      {:success false
       :connection-healthy false
       :error (.getMessage e)})))