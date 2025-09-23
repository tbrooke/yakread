(ns com.yakread.alfresco.content-service
  "Live content service for Mount Zion UCC website
   Connects extracted Alfresco content to the running application"
  (:require
   [clojure.tools.logging :as log]
   [clojure.java.io :as io]
   [clojure.edn :as edn]
   [com.yakread.alfresco.content-extractor :as extractor]
   [com.yakread.config.website-nodes :as nodes]))

;; --- CONTENT LOADING ---

(defn load-extracted-content
  "Load previously extracted content from file"
  [filename]
  (try
    (if (.exists (io/file filename))
      (let [content (edn/read-string (slurp filename))]
        (log/info "Loaded content from" filename ":" (count content) "items")
        content)
      (do
        (log/warn "Content file not found:" filename)
        []))
    (catch Exception e
      (log/error "Failed to load content from" filename ":" (.getMessage e))
      [])))

(defn extract-and-save-content
  "Extract content from Alfresco and save to file for the live site"
  [ctx component-key]
  (log/info "Extracting content for component:" component-key)
  
  (try
    (let [content-items (extractor/extract-component-content ctx component-key)
          valid-items (filter #(= :extracted (:status %)) content-items)
          filename (str "mtzuix-" (name component-key) "-content.edn")]
      
      (if (seq valid-items)
        (do
          (spit filename (pr-str valid-items))
          (log/info "✅ Saved" (count valid-items) "items to" filename)
          {:success true
           :items valid-items
           :filename filename
           :count (count valid-items)})
        (do
          (log/warn "❌ No valid content found for" component-key)
          {:success false
           :error "No valid content found"
           :component component-key})))
    
    (catch Exception e
      (log/error "❌ Failed to extract content for" component-key ":" (.getMessage e))
      {:success false
       :error (.getMessage e)
       :component component-key})))

;; --- LIVE CONTENT RETRIEVAL ---

(defn get-component-content
  "Get content for a component, extracting fresh if needed"
  [ctx component-key & [force-refresh]]
  (let [filename (str "mtzuix-" (name component-key) "-content.edn")
        existing-content (when-not force-refresh (load-extracted-content filename))]
    
    (if (and (not force-refresh) (seq existing-content))
      {:success true
       :items existing-content
       :source :cached}
      
      ;; Extract fresh content
      (let [extraction-result (extract-and-save-content ctx component-key)]
        (if (:success extraction-result)
          {:success true
           :items (:items extraction-result)
           :source :fresh}
          extraction-result)))))

(defn get-feature1-content
  "Get Feature 1 content specifically"
  [ctx & [force-refresh]]
  (get-component-content ctx :feature1 force-refresh))

;; --- CONTENT FORMATTING FOR UI ---

(defn format-content-for-display
  "Format extracted content for display in UI components"
  [content-item]
  (let [html-content (:content content-item)
        title (or (:name content-item) "Feature Content")
        last-updated (:alfresco-modified-at content-item)]
    
    {:title title
     :html-content html-content
     :last-updated last-updated
     :source-info {:alfresco-node-id (:alfresco-node-id content-item)
                   :alfresco-name (:alfresco-name content-item)}}))

(defn get-homepage-feature1-for-display
  "Get Feature 1 content formatted for homepage display"
  [ctx & [force-refresh]]
  (let [content-result (get-feature1-content ctx force-refresh)]
    (if (:success content-result)
      (let [items (:items content-result)
            first-item (first items)]
        (if first-item
          {:success true
           :content (format-content-for-display first-item)
           :source (:source content-result)}
          {:success false
           :error "No content items found"}))
      content-result)))

;; --- HEALTH AND STATUS ---

(defn check-content-availability
  "Check what content is available locally and in Alfresco"
  [ctx]
  (let [components [:feature1 :feature2 :feature3 :hero] ; Add more as needed
        results (atom {})]
    
    (doseq [component components]
      (let [filename (str "mtzuix-" (name component) "-content.edn")
            has-local (and (.exists (io/file filename))
                          (seq (load-extracted-content filename)))
            alfresco-check (try
                             (extractor/check-folder-access ctx component)
                             (catch Exception e
                               {:accessible false :error (.getMessage e)}))]
        
        (swap! results assoc component
               {:local-content has-local
                :alfresco-accessible (:accessible alfresco-check)
                :alfresco-item-count (:item-count alfresco-check)
                :filename filename})))
    
    @results))

;; --- SIMPLE TEST FUNCTIONS ---

(defn test-feature1-extraction
  "Simple test function to extract Feature 1 content"
  [ctx]
  (log/info "🧪 Testing Feature 1 content extraction...")
  
  (let [result (get-homepage-feature1-for-display ctx true)] ; force refresh
    (if (:success result)
      (do
        (log/info "✅ Feature 1 extraction successful!")
        (log/info "   Title:" (get-in result [:content :title]))
        (log/info "   Content length:" (count (get-in result [:content :html-content])))
        (log/info "   Last updated:" (get-in result [:content :last-updated]))
        (log/info "   Source:" (:source result))
        result)
      (do
        (log/error "❌ Feature 1 extraction failed:" (:error result))
        result))))

;; --- ALFRESCO CONTEXT CREATION ---

(defn create-alfresco-context
  "Create Alfresco context from application config or environment"
  [& [config]]
  (let [base-config {:alfresco/base-url "http://localhost:8080"
                     :alfresco/username "admin"
                     :alfresco/password "admin"}]
    (merge base-config config)))

;; --- MAIN CONTENT FUNCTIONS FOR ROUTES ---

(defn load-homepage-content
  "Main function to load content for homepage - used by routes"
  [ctx]
  (log/info "Loading homepage content...")
  
  (let [alfresco-ctx (create-alfresco-context)
        feature1-result (get-homepage-feature1-for-display alfresco-ctx)]
    
    (if (:success feature1-result)
      {:feature1 (:content feature1-result)
       :loaded-from (:source feature1-result)}
      {:feature1 {:title "Feature 1"
                  :html-content "<p>Content temporarily unavailable.</p>"
                  :error (:error feature1-result)}})))