(ns com.yakread.alfresco.website-client
  "Specialized Alfresco client for Mt Zion Web Site content using hardcoded node IDs"
  (:require [com.yakread.alfresco.client :as alfresco]
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