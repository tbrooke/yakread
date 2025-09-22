(ns com.yakread.alfresco.content-service
  "Service for extracting and processing content from Alfresco folders for Mount Zion UCC"
  (:require
   [clojure.tools.logging :as log]
   [com.yakread.alfresco.client :as alfresco]
   [com.yakread.config.website-nodes :as nodes]))

;; --- CONTENT EXTRACTION ---

(defn extract-folder-content
  "Extract all content from a specific Alfresco folder"
  [ctx folder-key]
  (try
    (log/info "Extracting content from folder:" folder-key)
    
    (let [node-id (nodes/get-homepage-component-node-id folder-key)
          children-response (alfresco/get-node-children ctx node-id)]
      
      (if (:success children-response)
        (let [entries (get-in children-response [:data :list :entries])
              html-files (filter #(= "text/html" (get-in % [:entry :content :mimeType])) entries)]
          
          (log/info "Found" (count entries) "items in" folder-key "folder")
          (log/info "HTML files:" (count html-files))
          
          ;; Process each HTML file
          (for [html-file html-files]
            (let [file-entry (:entry html-file)
                  file-node-id (:id file-entry)
                  file-name (:name file-entry)
                  content-response (alfresco/get-node-content ctx file-node-id)]
              
              (log/info "Processing file:" file-name)
              
              (if (:success content-response)
                {:success true
                 :alfresco-node-id file-node-id
                 :alfresco-name file-name
                 :alfresco-modified-at (:modifiedAt file-entry)
                 :alfresco-created-at (:createdAt file-entry)
                 :alfresco-size (get-in file-entry [:content :sizeInBytes])
                 :alfresco-mime-type (get-in file-entry [:content :mimeType])
                 :html-content (:data content-response) ; Updated to use :data from new client
                 :extracted-at (java.time.Instant/now)}
                
                {:success false
                 :alfresco-node-id file-node-id
                 :alfresco-name file-name
                 :error (:error content-response)}))))
        
        {:error "Could not access folder" 
         :folder-key folder-key
         :details (:error children-response)}))
    
    (catch Exception e
      (log/error "Error extracting folder content:" (.getMessage e))
      {:error (.getMessage e)})))

(defn extract-feature1-content
  "Extract content from Feature 1 folder - convenience function"
  [ctx]
  (extract-folder-content ctx :feature1))

;; --- CONTENT FILTERING ---

(defn filter-html-content
  "Filter content results to only include successful HTML extractions"
  [content-results]
  (filter #(and (:success %) (:html-content %)) content-results))

(defn filter-by-mime-type
  "Filter content by MIME type"
  [content-results mime-type]
  (filter #(= mime-type (:alfresco-mime-type %)) content-results))

;; --- CONTENT VALIDATION ---

(defn validate-content
  "Validate that extracted content meets requirements"
  [content-item]
  (and (:success content-item)
       (:alfresco-node-id content-item)
       (:alfresco-name content-item)
       (:html-content content-item)
       (not (empty? (:html-content content-item)))))

(defn validate-all-content
  "Validate a collection of content items"
  [content-items]
  (let [valid-items (filter validate-content content-items)
        total-count (count content-items)
        valid-count (count valid-items)]
    
    (log/info "Content validation:" valid-count "/" total-count "items valid")
    
    {:valid-items valid-items
     :total-count total-count
     :valid-count valid-count
     :validation-success (> valid-count 0)}))

;; --- CONVENIENCE FUNCTIONS ---

(defn get-folder-html-content
  "Get all valid HTML content from a folder"
  [ctx folder-key]
  (let [extraction-result (extract-folder-content ctx folder-key)]
    (if (sequential? extraction-result)
      (:valid-items (validate-all-content (filter-html-content extraction-result)))
      [])))

;; --- HEALTH CHECKS ---

(defn check-folder-access
  "Check if a folder is accessible and has content"
  [ctx folder-key]
  (try
    (let [node-id (nodes/get-homepage-component-node-id folder-key)
          children-response (alfresco/get-node-children ctx node-id)]
      
      (if (:success children-response)
        (let [entries (get-in children-response [:data :list :entries])]
          {:accessible true
           :folder-key folder-key
           :node-id node-id
           :item-count (count entries)
           :timestamp (java.time.Instant/now)})
        
        {:accessible false
         :folder-key folder-key
         :node-id node-id
         :error (:error children-response)
         :timestamp (java.time.Instant/now)}))
    
    (catch Exception e
      {:accessible false
       :folder-key folder-key
       :error (.getMessage e)
       :timestamp (java.time.Instant/now)})))

(defn health-check-all-folders
  "Check accessibility of all configured folders"
  [ctx]
  (let [folder-keys [:feature1] ; Add more folder keys as needed
        results (map #(check-folder-access ctx %) folder-keys)]
    
    {:all-accessible (every? :accessible results)
     :folder-statuses results
     :timestamp (java.time.Instant/now)}))