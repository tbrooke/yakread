(ns com.yakread.alfresco.content-extractor
  "Universal content extraction service for Mount Zion UCC website
   Traverses any Alfresco folder structure and extracts content for XTDB storage"
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [com.yakread.alfresco.client :as alfresco]
   [com.yakread.config.website-nodes :as nodes]))

;; --- CONTENT FILTERING ---

(def supported-content-types
  "MIME types we can extract and display on the website"
  #{"text/html"
    "text/plain"
    "text/markdown"
    "application/pdf"
    "image/jpeg"
    "image/png"
    "image/gif"})

(defn extractable-content?
  "Check if a file node contains content we can extract and display"
  [node-metadata]
  (and (:mime-type node-metadata)
       (contains? supported-content-types (:mime-type node-metadata))
       (= "file" (:type node-metadata))))

(defn published-content?
  "Check if content should be published (has publish tag or is in published folder)"
  [ctx node-metadata]
  ;; For now, simple check - can be enhanced with tag checking
  (or 
    ;; Check if in a 'published' or 'public' folder
    (some-> (:path node-metadata) 
            str/lower-case 
            (str/includes? "published"))
    ;; Check for publish tag (requires API call)
    (try
      (alfresco/has-tag? ctx (:id node-metadata) "publish")
      (catch Exception e
        (log/debug "Could not check tags for node" (:id node-metadata) ":" (.getMessage e))
        false))))

;; --- CONTENT EXTRACTION ---

(defn extract-file-content
  "Extract content from a file node"
  [ctx node-metadata]
  (log/debug "Extracting content from file:" (:name node-metadata))
  
  (let [content-response (alfresco/get-node-content ctx (:id node-metadata))]
    (if (:success content-response)
      (let [content-stream (:data content-response)]
        (try
          (with-open [reader (clojure.java.io/reader content-stream)]
            {:success true
             :content (slurp reader)
             :content-type (:mime-type node-metadata)
             :size (:size node-metadata)})
          (catch Exception e
            (log/error "Failed to read content from stream:" (.getMessage e))
            {:success false
             :error (.getMessage e)})))
      {:success false
       :error (:error content-response)})))

(defn process-extractable-file
  "Process a single extractable file node"
  [ctx node-metadata]
  (let [content-result (extract-file-content ctx node-metadata)]
    (if (:success content-result)
      (merge node-metadata content-result
             {:extracted-at (java.time.Instant/now)
              :status :extracted})
      (merge node-metadata content-result
             {:extracted-at (java.time.Instant/now)
              :status :extraction-failed}))))

;; --- FOLDER TRAVERSAL ---

(defn traverse-folder
  "Recursively traverse a folder and extract all publishable content"
  [ctx node-id & [options]]
  (let [{:keys [max-depth include-unpublished filter-fn]
         :or {max-depth 5 include-unpublished false}} options]
    
    (log/info "Traversing folder:" node-id "max-depth:" max-depth)
    
    (letfn [(traverse-recursive [current-node-id depth path]
              (when (< depth max-depth)
                (let [children-response (alfresco/get-node-children ctx current-node-id)
                      children (if (:success children-response)
                                 (get-in children-response [:data :list :entries])
                                 [])]
                  
                  (log/debug "Found" (count children) "children in node" current-node-id)
                  
                  (mapcat
                   (fn [child-entry]
                     (let [child-metadata (alfresco/extract-node-metadata child-entry)
                           child-path (str path "/" (:name child-metadata))]
                       
                       (cond
                         ;; If it's a folder, recurse into it
                         (= "folder" (:type child-metadata))
                         (do
                           (log/debug "Recursing into folder:" (:name child-metadata))
                           (traverse-recursive (:id child-metadata) (inc depth) child-path))
                         
                         ;; If it's an extractable file, process it
                         (extractable-content? child-metadata)
                         (do
                           (log/debug "Processing extractable file:" (:name child-metadata))
                           (let [should-publish (or include-unpublished 
                                                    (published-content? ctx child-metadata))
                                 passes-filter (if filter-fn
                                                 (filter-fn child-metadata)
                                                 true)]
                             (if (and should-publish passes-filter)
                               [(process-extractable-file ctx (assoc child-metadata :path child-path))]
                               (do
                                 (log/debug "Skipping file:" (:name child-metadata) 
                                           "published:" should-publish "passes-filter:" passes-filter)
                                 []))))
                         
                         ;; Otherwise, skip
                         :else
                         (do
                           (log/debug "Skipping unsupported file:" (:name child-metadata) 
                                     "type:" (:type child-metadata) "mime:" (:mime-type child-metadata))
                           []))))
                   children))))]
      
      (traverse-recursive node-id 0 ""))))

;; --- WEBSITE STRUCTURE EXTRACTION ---

(defn extract-website-content
  "Extract content from known website structure folders"
  [ctx & [options]]
  (log/info "Starting website content extraction")
  
  (let [website-folders (nodes/get-all-component-nodes)
        extraction-results (atom {})]
    
    (doseq [[component-key node-id] website-folders]
      (log/info "Extracting content for component:" component-key "node:" node-id)
      
      (try
        (let [content-items (traverse-folder ctx node-id options)]
          (swap! extraction-results assoc component-key
                 {:success true
                  :component component-key
                  :node-id node-id
                  :items content-items
                  :count (count content-items)
                  :extracted-at (java.time.Instant/now)}))
        
        (catch Exception e
          (log/error "Failed to extract content for component" component-key ":" (.getMessage e))
          (swap! extraction-results assoc component-key
                 {:success false
                  :component component-key
                  :node-id node-id
                  :error (.getMessage e)
                  :extracted-at (java.time.Instant/now)}))))
    
    @extraction-results))

;; --- CONTENT ORGANIZATION ---

(defn organize-by-page
  "Organize extracted content by target page"
  [extraction-results]
  (let [all-items (mapcat :items (vals extraction-results))
        successful-items (filter #(= :extracted (:status %)) all-items)]
    
    (group-by
     (fn [item]
       ;; Determine target page from path or component
       (cond
         (str/includes? (:path item) "homepage") :homepage
         (str/includes? (:path item) "about") :about
         (str/includes? (:path item) "worship") :worship
         (str/includes? (:path item) "events") :events
         (str/includes? (:path item) "contact") :contact
         :else :homepage)) ; default to homepage
     successful-items)))

(defn organize-by-component-type
  "Organize extracted content by component type for rendering"
  [extraction-results]
  (let [all-items (mapcat :items (vals extraction-results))
        successful-items (filter #(= :extracted (:status %)) all-items)]
    
    (group-by
     (fn [item]
       ;; Determine component type from MIME type
       (case (:mime-type item)
         "text/html" :html-content
         "text/plain" :text-content
         "text/markdown" :markdown-content
         "image/jpeg" :image-content
         "image/png" :image-content
         "image/gif" :image-content
         "application/pdf" :document-content
         :unknown-content))
     successful-items)))

;; --- CONVENIENCE FUNCTIONS ---

(defn extract-component-content
  "Extract content from a specific component folder"
  [ctx component-key & [options]]
  (if-let [node-id (nodes/get-homepage-component-node-id component-key)]
    (do
      (log/info "Extracting content for component:" component-key)
      (traverse-folder ctx node-id options))
    (do
      (log/warn "No node ID found for component:" component-key)
      [])))

(defn extract-published-content-only
  "Extract only published content from entire website"
  [ctx]
  (extract-website-content ctx {:include-unpublished false :max-depth 3}))

(defn extract-all-content
  "Extract all content (including unpublished) for admin review"
  [ctx]
  (extract-website-content ctx {:include-unpublished true :max-depth 5}))

;; --- CONTENT SUMMARY ---

(defn summarize-extraction
  "Create a summary of extraction results"
  [extraction-results]
  (let [total-components (count extraction-results)
        successful-components (count (filter :success (vals extraction-results)))
        total-items (apply + (map :count (vals extraction-results)))
        by-type (frequencies (map :mime-type (mapcat :items (vals extraction-results))))]
    
    {:total-components total-components
     :successful-components successful-components
     :failed-components (- total-components successful-components)
     :total-content-items total-items
     :content-by-type by-type
     :extraction-time (java.time.Instant/now)}))

;; --- MAIN EXTRACTION FUNCTION ---

(defn extract-website-content-with-summary
  "Extract website content and return organized results with summary"
  [ctx & [options]]
  (log/info "🏗️ Starting universal website content extraction")
  
  (let [start-time (System/currentTimeMillis)
        extraction-results (extract-website-content ctx options)
        organized-by-page (organize-by-page extraction-results)
        organized-by-type (organize-by-component-type extraction-results)
        summary (summarize-extraction extraction-results)
        end-time (System/currentTimeMillis)
        total-time (- end-time start-time)]
    
    (log/info "✅ Website content extraction completed in" total-time "ms")
    (log/info "📊 Summary:" (:total-content-items summary) "items from" 
             (:successful-components summary) "components")
    
    {:raw-results extraction-results
     :by-page organized-by-page
     :by-type organized-by-type
     :summary (assoc summary :extraction-time-ms total-time)
     :extracted-at (java.time.Instant/now)}))