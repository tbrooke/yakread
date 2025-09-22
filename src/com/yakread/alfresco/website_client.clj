(ns com.yakread.alfresco.website-client
  "Specialized Alfresco client for Mt Zion Web Site content using hardcoded node IDs"
  (:require [com.yakread.alfresco.client :as alfresco]
            [com.yakread.alfresco.schema :as schema]
            [com.yakread.config.website-nodes :as nodes]
            [clojure.tools.logging :as log]
            [clojure.string :as str]))

;; --- DIRECT NODE ACCESS FUNCTIONS ---

(defn get-page-content
  "Get all content from a specific page folder using hardcoded node ID"
  [ctx page-keyword & [options]]
  (if-let [node-id (nodes/get-page-node-id page-keyword)]
    (do
      (log/info "Fetching content for page:" page-keyword "node-id:" node-id)
      (alfresco/get-node-children ctx node-id options))
    (do
      (log/error "Unknown page:" page-keyword)
      {:success false :error "Unknown page"})))

(defn get-homepage-component-content
  "Get content from a specific homepage component folder"
  [ctx component-keyword & [options]]
  (if-let [node-id (nodes/get-homepage-component-node-id component-keyword)]
    (do
      (log/info "Fetching homepage component content:" component-keyword "node-id:" node-id)
      (alfresco/get-node-children ctx node-id options))
    (do
      (log/error "Unknown homepage component:" component-keyword)
      {:success false :error "Unknown homepage component"})))

(defn get-document-content
  "Get the actual content (text/HTML) of a document by node ID"
  [ctx node-id]
  (log/info "Fetching document content for node:" node-id)
  (alfresco/get-node-content ctx node-id))

;; --- WEB SITE STRUCTURE EXPLORATION ---

(defn get-all-website-content
  "Get content from all Web Site page folders - useful for bulk sync"
  [ctx & [options]]
  (log/info "Fetching content from all Web Site pages")
  (let [page-results (for [[page-name node-id] nodes/page-node-ids]
                       (let [result (alfresco/get-node-children ctx node-id options)]
                         [page-name {:node-id node-id
                                     :success (:success result)
                                     :content (when (:success result)
                                                (get-in result [:data :list :entries]))
                                     :error (when-not (:success result)
                                              (:error result))}]))]
    (into {} page-results)))

(defn get-homepage-components-content
  "Get content from all homepage component folders"
  [ctx & [options]]
  (log/info "Fetching content from all homepage components")
  (let [component-results (for [[component-name node-id] nodes/homepage-component-node-ids]
                            (let [result (alfresco/get-node-children ctx node-id options)]
                              [component-name {:node-id node-id
                                               :success (:success result)
                                               :content (when (:success result)
                                                          (get-in result [:data :list :entries]))
                                               :error (when-not (:success result)
                                                        (:error result))}]))]
    (into {} component-results)))

;; --- CONTENT PROCESSING FOR MTZUIX ---

(defn extract-content-metadata
  "Extract useful metadata from Alfresco node for mtzUIX"
  [node-entry]
  (let [entry (:entry node-entry)]
    {:node-id (:id entry)
     :name (:name entry)
     :type (if (:isFolder entry) :folder :file)
     :mime-type (get-in entry [:content :mimeType])
     :size (get-in entry [:content :sizeInBytes])
     :created-at (:createdAt entry)
     :modified-at (:modifiedAt entry)
     :is-html? (= "text/html" (get-in entry [:content :mimeType]))
     :is-image? (str/starts-with? (get-in entry [:content :mimeType] "") "image/")
     :is-text? (str/starts-with? (get-in entry [:content :mimeType] "") "text/")}))

(defn process-page-content-for-mtzuix
  "Process page content specifically for mtzUIX frontend consumption"
  [ctx page-keyword]
  (log/info "Processing page content for mtzUIX:" page-keyword)
  (let [page-result (get-page-content ctx page-keyword)]
    (if (:success page-result)
      (let [entries (get-in page-result [:data :list :entries])
            processed-content (map extract-content-metadata entries)

            ;; Separate content by type
            folders (filter #(= :folder (:type %)) processed-content)
            files (filter #(= :file (:type %)) processed-content)
            html-files (filter :is-html? files)
            images (filter :is-image? files)]

        {:success true
         :page page-keyword
         :node-id (nodes/get-page-node-id page-keyword)
         :summary {:total-items (count processed-content)
                   :folders (count folders)
                   :files (count files)
                   :html-files (count html-files)
                   :images (count images)}
         :content {:folders folders
                   :files files
                   :html-files html-files
                   :images images}})

      {:success false
       :page page-keyword
       :error (:error page-result)})))

;; --- CALENDAR/EVENTS OPERATIONS ---

(defn get-calendar-events
  "Get all calendar events from the events folder with properties"
  [ctx & [options]]
  (if-let [events-node-id (nodes/get-page-node-id :events)]
    (do
      (log/info "Fetching calendar events from node:" events-node-id)
      ;; Include properties in the response to get calendar metadata
      (let [options-with-props (merge {:include "properties,aspectNames"} options)]
        (alfresco/get-node-children ctx events-node-id options-with-props)))
    (do
      (log/error "Events node ID not configured")
      {:success false :error "Events node ID not configured"})))

(defn extract-event-metadata
  "Extract event metadata from Alfresco calendar content with tag checking"
  [ctx node-entry]
  (let [entry (:entry node-entry)
        properties (:properties entry)
        node-id (:id entry)

        ;; Check for publish tag
        tags-response (alfresco/get-node-tags ctx node-id)
        has-publish-tag (if (:success tags-response)
                          (let [tags (get-in tags-response [:data :list :entries])]
                            (some #(= "publish" (get-in % [:entry :tag])) tags))
                          false)

        ;; Check if event is upcoming
        event-date-str (get properties :ia:fromDate)
        is-upcoming (if event-date-str
                      (try
                        (let [event-date (java.time.LocalDate/parse (subs event-date-str 0 10))] ; Take date part only
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

(defn process-calendar-events-for-website
  "Process calendar events for website display with publish tag filtering"
  [ctx & [options]]
  (log/info "Processing calendar events for website display")
  (let [events-result (get-calendar-events ctx options)]
    (log/info "Raw events result success:" (:success events-result))
    (when (:success events-result)
      (log/info "Raw events count:" (count (get-in events-result [:data :list :entries]))))
    (if (:success events-result)
      (let [entries (get-in events-result [:data :list :entries])
            processed-events (map (partial extract-event-metadata ctx) entries)

            ;; Filter for valid calendar events only (not folders)
            event-docs (filter #(not (:isFolder (get (:entry %) {}))) entries)

            ;; Extract metadata for event documents only
            event-metadata (map (partial extract-event-metadata ctx) event-docs)

            ;; Filter for published events only (with publish tag and upcoming)
            published-events (filter #(and (:has-publish-tag %)
                                           (:is-upcoming %))
                                    event-metadata)

            ;; Validate events against schema
            valid-published-events (filter #(schema/validate-published-calendar-event %)
                                          published-events)

            ;; Sort by event date
            sorted-events (sort-by :event-date valid-published-events)]

        (log/info "Calendar events processed:"
                  "Total items:" (count entries)
                  "Event documents:" (count event-docs)
                  "Published events:" (count published-events)
                  "Valid published events:" (count valid-published-events))

        {:success true
         :events-node-id (nodes/get-page-node-id :events)
         :summary {:total-items (count entries)
                   :event-documents (count event-docs)
                   :published-events (count published-events)
                   :valid-published-events (count valid-published-events)}
         :events sorted-events})

      {:success false
       :error (:error events-result)})))

;; --- CONTENT RETRIEVAL WITH AUTOMATIC COMPONENT MAPPING ---

(defn get-homepage-content-with-mapping
  "Get homepage content with automatic component mapping based on folder structure"
  [ctx]
  (log/info "Getting homepage content with component mapping")
  (let [components-result (get-homepage-components-content ctx)]
    (into {}
          (for [[component-name component-data] components-result]
            [component-name
             (if (:success component-data)
               (let [processed-content (map extract-content-metadata (:content component-data))]
                 (assoc component-data :processed-content processed-content))
               component-data)]))))

;; --- TESTING AND VALIDATION ---

(defn test-website-access
  "Test access to all configured Web Site folders"
  [ctx]
  (log/info "Testing access to all Web Site folders")
  (let [test-results (atom {:pages {} :components {} :sample-document nil})]

    ;; Test page access
    (doseq [[page-name node-id] nodes/page-node-ids]
      (let [result (alfresco/get-node ctx node-id)]
        (swap! test-results assoc-in [:pages page-name]
               {:node-id node-id
                :accessible? (:success result)
                :name (when (:success result) (get-in result [:data :entry :name]))
                :error (when-not (:success result) (:error result))})))

    ;; Test homepage component access
    (doseq [[component-name node-id] nodes/homepage-component-node-ids]
      (let [result (alfresco/get-node ctx node-id)]
        (swap! test-results assoc-in [:components component-name]
               {:node-id node-id
                :accessible? (:success result)
                :name (when (:success result) (get-in result [:data :entry :name]))
                :error (when-not (:success result) (:error result))})))

    ;; Test sample document access
    (let [doc-result (alfresco/get-node ctx nodes/sample-document-node-id)]
      (swap! test-results assoc :sample-document
             {:node-id nodes/sample-document-node-id
              :accessible? (:success doc-result)
              :name (when (:success doc-result) (get-in doc-result [:data :entry :name]))
              :mime-type (when (:success doc-result) (get-in doc-result [:data :entry :content :mimeType]))
              :error (when-not (:success doc-result) (:error doc-result))}))

    @test-results))

(comment
  ;; Usage examples:

  ;; Get content from homepage
  (get-page-content ctx :homepage)

  ;; Get content from feature1 component
  (get-homepage-component-content ctx :feature1)

  ;; Get content from all Web Site pages for bulk sync
  (get-all-website-content ctx)

  ;; Process homepage for mtzUIX consumption
  (process-page-content-for-mtzuix ctx :homepage)

  ;; Get homepage with component mapping
  (get-homepage-content-with-mapping ctx)

  ;; Test all configured node access
  (test-website-access ctx)
)