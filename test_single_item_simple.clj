#!/usr/bin/env bb

;; Simple test to extract your one Feature 1 item using the HTTP client
;; This bypasses the complex universal extractor for now

(require '[babashka.curl :as curl]
         '[cheshire.core :as json]
         '[clojure.edn :as edn])

;; Load configuration
(load-file "src/com/yakread/config/website_nodes.clj")
(alias 'nodes 'com.yakread.config.website-nodes)

;; --- CONFIGURATION ---
(def alfresco-host "http://localhost:8080")
(def alfresco-user "admin")
(def alfresco-pass "admin")
(def api-base (str alfresco-host "/alfresco/api/-default-/public/alfresco/versions/1"))

;; --- SIMPLE HTTP CLIENT ---

(defn get-node-children [node-id]
  (println "📡 Getting children for node:" node-id)
  (let [resp (curl/get (str api-base "/nodes/" node-id "/children")
                       {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      {:success true :data (json/parse-string (:body resp) true)}
      {:success false :error (:body resp)})))

(defn get-node-content [node-id]
  (println "📄 Getting content for node:" node-id)
  (let [resp (curl/get (str api-base "/nodes/" node-id "/content")
                       {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      {:success true :content (:body resp)}
      {:success false :error (:body resp)})))

;; --- TEST FUNCTIONS ---

(defn test-connection []
  (println "🔗 Testing Alfresco connection...")
  (try
    (let [resp (curl/get (str api-base "/nodes/-root-")
                         {:basic-auth [alfresco-user alfresco-pass]})]
      (if (= 200 (:status resp))
        (do
          (println "✅ Alfresco connection successful")
          true)
        (do
          (println "❌ Alfresco connection failed. Status:" (:status resp))
          false)))
    (catch Exception e
      (println "❌ Connection error:" (.getMessage e))
      false)))

(defn test-feature1-access []
  (println "\n📁 Testing Feature 1 folder access...")
  (let [feature1-node-id (nodes/get-homepage-component-node-id :feature1)]
    (println "   Feature 1 node ID:" feature1-node-id)
    
    (let [children-result (get-node-children feature1-node-id)]
      (if (:success children-result)
        (let [entries (get-in children-result [:data :list :entries])]
          (println "✅ Feature 1 folder accessible")
          (println "   Found" (count entries) "items:")
          (doseq [entry entries]
            (let [file-entry (:entry entry)]
              (println "     📄" (:name file-entry) 
                      "(" (get-in file-entry [:content :mimeType]) ")")))
          entries)
        (do
          (println "❌ Feature 1 folder not accessible:" (:error children-result))
          [])))))

(defn extract-feature1-content []
  (println "\n📡 Extracting Feature 1 content...")
  (let [feature1-node-id (nodes/get-homepage-component-node-id :feature1)
        children-result (get-node-children feature1-node-id)]
    
    (if (:success children-result)
      (let [entries (get-in children-result [:data :list :entries])
            html-files (filter #(= "text/html" (get-in % [:entry :content :mimeType])) entries)]
        
        (println "   Found" (count html-files) "HTML files")
        
        (for [html-file html-files]
          (let [file-entry (:entry html-file)
                file-node-id (:id file-entry)
                file-name (:name file-entry)
                content-result (get-node-content file-node-id)]
            
            (println "   Processing:" file-name)
            
            (if (:success content-result)
              {:success true
               :alfresco-node-id file-node-id
               :alfresco-name file-name
               :alfresco-modified-at (:modifiedAt file-entry)
               :alfresco-created-at (:createdAt file-entry)
               :alfresco-size (get-in file-entry [:content :sizeInBytes])
               :alfresco-mime-type (get-in file-entry [:content :mimeType])
               :html-content (:content content-result)
               :extracted-at (java.time.Instant/now)}
              
              {:success false
               :alfresco-node-id file-node-id
               :alfresco-name file-name
               :error (:error content-result)}))))
      
      {:error "Could not access Feature 1 folder" 
       :details (:error children-result)})))

(defn save-content-for-website [content-items]
  (println "\n💾 Saving content for website...")
  (let [successful-items (filter :success content-items)
        website-content (map (fn [item]
                              {:title (:alfresco-name item)
                               :html-content (:html-content item)
                               :last-updated (:alfresco-modified-at item)
                               :source-node-id (:alfresco-node-id item)})
                            successful-items)]
    
    (if (seq website-content)
      (do
        (spit "mtzuix-feature1-content.edn" (pr-str website-content))
        (println "✅ Saved" (count website-content) "items to mtzuix-feature1-content.edn")
        
        ;; Show content preview
        (when-let [first-item (first website-content)]
          (println "\n📄 Content Preview:")
          (println "   Title:" (:title first-item))
          (println "   Content length:" (count (:html-content first-item)) "characters")
          (println "   Last updated:" (:last-updated first-item))
          (let [preview (subs (:html-content first-item) 0 (min 150 (count (:html-content first-item))))]
            (println "   HTML preview:" preview "...")))
        
        website-content)
      (do
        (println "❌ No content to save")
        []))))

(defn test-website-content-loading []
  (println "\n🌐 Testing website content loading...")
  (if (.exists (clojure.java.io/file "mtzuix-feature1-content.edn"))
    (try
      (let [content (edn/read-string (slurp "mtzuix-feature1-content.edn"))]
        (println "✅ Website content file loaded")
        (println "   Items:" (count content))
        (when-let [first-item (first content)]
          (println "   First item title:" (:title first-item))
          (println "   Has HTML content:" (boolean (:html-content first-item))))
        true)
      (catch Exception e
        (println "❌ Error loading website content:" (.getMessage e))
        false))
    (do
      (println "⚠️ No website content file found")
      false)))

;; --- MAIN EXECUTION ---

(defn run-single-item-test []
  (println "🧪 Testing Single Feature 1 Item Extraction")
  (println "   Simple test of the HTTP client with your one item")
  (println "   Make sure SSH tunnel to Alfresco is running\n")
  
  (try
    ;; Test connection
    (if (test-connection)
      (do
        ;; Test folder access
        (test-feature1-access)
        
        ;; Extract content
        (let [content-items (extract-feature1-content)]
          (if (seq content-items)
            (do
              ;; Save for website
              (save-content-for-website content-items)
              
              ;; Test loading
              (test-website-content-loading)
              
              (println "\n✅ Single item test completed successfully!")
              (println "\n💡 Next steps:")
              (println "   1. Content extracted from your Feature 1 folder")
              (println "   2. Saved to mtzuix-feature1-content.edn")
              (println "   3. Ready to connect to live website routes")
              (println "   4. Add more content to Feature 1 folder in Alfresco"))
            
            (println "❌ No content items extracted")))
      
      (println "❌ Cannot proceed - Alfresco connection failed"))
    
    (catch Exception e
      (println "❌ Test failed:" (.getMessage e))
      (.printStackTrace e))))

;; Run the test
(run-single-item-test)