(ns com.yakread.alfresco.content-processor
  "Processes extracted Alfresco content, handling embedded images and links"
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [clojure.java.io :as io]))

;; --- IMAGE URL PATTERNS ---

(def alfresco-share-patterns
  "Regex patterns to match Alfresco Share image URLs"
  [;; Standard Share URL pattern
   #"http[s]?://[^/]+/share/proxy/alfresco/api/node/content/workspace/SpacesStore/([a-f0-9\-]+)/([^?\s\"]+)"
   
   ;; Direct node content URL  
   #"http[s]?://[^/]+/alfresco/service/api/node/content/workspace/SpacesStore/([a-f0-9\-]+)"
   
   ;; Share direct download
   #"http[s]?://[^/]+/share/proxy/alfresco-noauth/api/node/content/workspace/SpacesStore/([a-f0-9\-]+)"
   
   ;; Thumbnail/rendition URLs
   #"http[s]?://[^/]+/share/proxy/alfresco/api/node/([a-f0-9\-]+)/content/thumbnails/([^?\s\"]+)"
   
   ;; Document details page URLs (extract node ID from nodeRef parameter)
   #"http[s]?://[^/]+/share/page/[^?]*\?nodeRef=workspace://SpacesStore/([a-f0-9\-]+)"])

(defn extract-node-id-from-url
  "Extract Alfresco node ID from various URL formats"
  [url]
  (when url
    (some (fn [pattern]
            (when-let [match (re-find pattern url)]
              ;; Return the first capture group (node ID)
              (second match)))
          alfresco-share-patterns)))

;; --- HTML PROCESSING ---

(defn find-image-urls
  "Find all image URLs in HTML content"
  [html-content]
  (when html-content
    (let [img-pattern #"<img[^>]+src=[\"']([^\"']+)[\"'][^>]*>"
          matches (re-seq img-pattern html-content)]
      (map second matches))))

(defn process-image-url
  "Convert Alfresco Share URL to local proxy URL"
  [original-url]
  (if-let [node-id (extract-node-id-from-url original-url)]
    (do
      (log/debug "Converting Alfresco URL to proxy:" original-url "→" node-id)
      (str "/proxy/image/" node-id))
    (do
      (log/debug "Non-Alfresco image URL, keeping as-is:" original-url)
      original-url)))

(defn replace-image-urls
  "Replace Alfresco Share URLs with local proxy URLs in HTML"
  [html-content]
  (if (str/blank? html-content)
    html-content
    (let [image-urls (find-image-urls html-content)]
      (reduce (fn [html url]
                (let [proxy-url (process-image-url url)]
                  (if (not= url proxy-url)
                    (do
                      (log/info "Replacing image URL:" url "→" proxy-url)
                      (str/replace html url proxy-url))
                    html)))
              html-content
              image-urls))))

;; --- LINK PROCESSING ---

(defn find-alfresco-links
  "Find Alfresco document links in HTML content"
  [html-content]
  (when html-content
    (let [link-pattern #"<a[^>]+href=[\"']([^\"']*alfresco[^\"']*)[\"'][^>]*>"
          matches (re-seq link-pattern html-content)]
      (map second matches))))

(defn process-alfresco-link
  "Process Alfresco document links (for now, keep as-is)"
  [link-url]
  ;; TODO: Could proxy these too, or convert to direct downloads
  link-url)

(defn replace-alfresco-links
  "Replace Alfresco links (placeholder for future enhancement)"
  [html-content]
  ;; For now, just return as-is
  ;; Future: Could proxy document downloads too
  html-content)

;; --- CONTENT ENHANCEMENT ---

(defn add-image-lazy-loading
  "Add lazy loading attributes to images"
  [html-content]
  (when html-content
    (-> html-content
        (str/replace #"<img\s+([^>]*?)src=" "<img loading=\"lazy\" $1src=")
        (str/replace #"<img\s+loading=\"lazy\"\s+loading=\"lazy\"" "<img loading=\"lazy\""))))

(defn add-image-error-handling
  "Add error handling for broken images"
  [html-content]
  (when html-content
    ;; Add onerror handler to show placeholder for broken images
    (str/replace html-content 
                 #"<img\s+([^>]*?)>" 
                 "<img $1 onerror=\"this.src='/img/placeholder.jpg'; this.onerror=null;\">")))

;; --- HTML ANALYSIS FUNCTIONS ---

(defn has-images?
  "Check if HTML content contains any images"
  [html-content]
  (when html-content
    (boolean (seq (find-image-urls html-content)))))

;; --- MAIN PROCESSING FUNCTION ---

(defn process-html-content
  "Main function to process HTML content with embedded Alfresco images"
  [html-content & [options]]
  (let [{:keys [proxy-images add-lazy-loading add-error-handling]
         :or {proxy-images true add-lazy-loading true add-error-handling false}} options]
    
    (log/debug "Processing HTML content:" (count html-content) "characters")
    
    (cond-> html-content
      ;; Replace Alfresco image URLs with proxy URLs
      proxy-images
      (replace-image-urls)
      
      ;; Replace Alfresco document links
      proxy-images
      (replace-alfresco-links)
      
      ;; Add lazy loading
      add-lazy-loading
      (add-image-lazy-loading)
      
      ;; Add error handling
      add-error-handling
      (add-image-error-handling))))

;; --- CONTENT ITEM PROCESSING ---

(defn process-content-item
  "Process a complete content item (from extraction results)"
  [content-item & [options]]
  (log/debug "Processing content item:" (:alfresco-name content-item))
  
  (let [processed-html (when (:content content-item)
                         (process-html-content (:content content-item) options))]
    
    (assoc content-item
           :processed-html processed-html
           :has-images (and processed-html 
                           (seq (find-image-urls processed-html)))
           :image-urls (when processed-html 
                        (find-image-urls processed-html))
           :processed-at (java.time.Instant/now))))

(defn process-extracted-content
  "Process a collection of extracted content items"
  [content-items & [options]]
  (log/info "Processing" (count content-items) "content items")
  
  (map #(process-content-item % options) content-items))

;; --- FEATURE 2 SPECIFIC PROCESSING ---

(defn extract-feature2-content
  "Extract and process Feature 2 content with images"
  [ctx]
  (log/info "Extracting Feature 2 content with image processing...")
  
  ;; This would use the content extractor to get Feature 2 content
  ;; then process it for images
  ;; For now, let's create a test version that processes existing content
  
  (try
    (if (.exists (io/file "mtzuix-feature2-content.edn"))
      (let [raw-content (clojure.edn/read-string (slurp "mtzuix-feature2-content.edn"))
            processed-content (process-extracted-content raw-content)]
        
        ;; Save processed version
        (spit "mtzuix-feature2-processed-content.edn" (pr-str processed-content))
        
        (log/info "Feature 2 content processed and saved")
        processed-content)
      
      (do
        (log/warn "No Feature 2 content file found")
        []))
    
    (catch Exception e
      (log/error "Error processing Feature 2 content:" (.getMessage e))
      [])))

;; --- TESTING FUNCTIONS ---

(defn test-html-processing
  "Test HTML processing with sample content"
  []
  (let [sample-html "<h1>Test Content</h1>
                     <p>This is a test with an image:</p>
                     <img src=\"http://admin.mtzcg.com/share/proxy/alfresco/api/node/content/workspace/SpacesStore/12345678-1234-1234-1234-123456789012/image.jpg\" alt=\"Test Image\">
                     <p>More content here.</p>"
        
        processed-html (process-html-content sample-html)]
    
    (println "Original HTML:")
    (println sample-html)
    (println "\nProcessed HTML:")
    (println processed-html)
    
    {:original sample-html
     :processed processed-html
     :image-urls (find-image-urls processed-html)}))

(comment
  ;; Test the processing
  (test-html-processing)
  
  ;; Extract node ID from URL
  (extract-node-id-from-url "http://admin.mtzcg.com/share/proxy/alfresco/api/node/content/workspace/SpacesStore/12345678-1234-1234-1234-123456789012/image.jpg")
  
  ;; Process Feature 2 content
  (extract-feature2-content {}))