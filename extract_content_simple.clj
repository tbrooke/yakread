#!/usr/bin/env bb

;; Simple content extraction test for Mount Zion UCC Feature 1
;; Extract once to file cache, then test serving to HTMX routes

(require '[clojure.edn :as edn]
         '[clojure.pprint :as pprint])

;; Load the content service and extractor
(load-file "src/com/yakread/alfresco/content_service.clj")
(load-file "src/com/yakread/alfresco/content_extractor.clj") 
(load-file "src/com/yakread/alfresco/client.clj")
(load-file "src/com/yakread/config/website_nodes.clj")

(alias 'service 'com.yakread.alfresco.content-service)
(alias 'client 'com.yakread.alfresco.client)

;; --- CONFIGURATION ---

(def alfresco-ctx
  "Simple Alfresco context for testing"
  {:alfresco/base-url "http://localhost:8080"
   :alfresco/username "admin"
   :alfresco/password "admin"})

;; --- TEST FUNCTIONS ---

(defn test-alfresco-connection []
  (println "🔗 Testing Alfresco connection...")
  (if (client/test-connection alfresco-ctx)
    (println "✅ Alfresco connection successful")
    (println "❌ Alfresco connection failed")))

(defn extract-feature1-content []
  (println "\n📡 Extracting Feature 1 content from Alfresco...")
  
  (let [result (service/test-feature1-extraction alfresco-ctx)]
    (if (:success result)
      (do
        (println "✅ Feature 1 content extracted successfully!")
        (println "📄 Content Details:")
        (println "   Title:" (get-in result [:content :title]))
        (println "   Content Length:" (count (get-in result [:content :html-content])) "characters")
        (println "   Last Updated:" (get-in result [:content :last-updated]))
        (println "   Source:" (:source result))
        
        ;; Show a preview of the HTML content
        (let [html-content (get-in result [:content :html-content])
              preview (if (> (count html-content) 200)
                        (str (subs html-content 0 200) "...")
                        html-content)]
          (println "\n📄 Content Preview:")
          (println preview))
        
        result)
      (do
        (println "❌ Feature 1 extraction failed:")
        (println "   Error:" (:error result))
        result))))

(defn test-file-cache ()
  (println "\n💾 Testing file cache...")
  
  ;; Try to load existing content
  (let [cached-result (service/get-homepage-feature1-for-display alfresco-ctx false)] ; don't force refresh
    (if (:success cached-result)
      (do
        (println "✅ Content loaded from cache")
        (println "   Source:" (:source cached-result))
        (println "   Title:" (get-in cached-result [:content :title])))
      (println "⚠️ No cached content found"))))

(defn test-content-for-routes []
  (println "\n🌐 Testing content ready for HTMX routes...")
  
  (let [homepage-content (service/load-homepage-content {})]
    (println "📋 Homepage Content Ready:")
    (pprint/pprint homepage-content)
    
    ;; This is what would be available to the routes
    (if-let [feature1-content (:feature1 homepage-content)]
      (do
        (println "\n✅ Feature 1 content ready for HTMX rendering")
        (println "   Title:" (:title feature1-content))
        (println "   Has HTML:" (boolean (:html-content feature1-content)))
        (when (:error feature1-content)
          (println "   Error:" (:error feature1-content))))
      (println "❌ No Feature 1 content available for routes"))))

(defn show-extracted-files []
  (println "\n📁 Checking extracted content files...")
  
  (let [files ["mtzuix-feature1-content.edn"
               "extracted-content-feature1.edn"]]
    (doseq [filename files]
      (if (.exists (clojure.java.io/file filename))
        (do
          (println "✅" filename "exists")
          (try
            (let [content (edn/read-string (slurp filename))]
              (println "   Items:" (count content)))
            (catch Exception e
              (println "   Error reading file:" (.getMessage e)))))
        (println "❌" filename "not found")))))

;; --- MAIN EXECUTION ---

(defn run-content-extraction-test []
  (println "🏗️ Mount Zion UCC Content Extraction Test")
  (println "   Testing the file cache approach for HTMX server-side rendering")
  (println "   Make sure SSH tunnel to Alfresco is running\n")
  
  (try
    ;; Test connection
    (test-alfresco-connection)
    
    ;; Show current file state
    (show-extracted-files)
    
    ;; Test file cache (existing content)
    (test-file-cache)
    
    ;; Extract fresh content
    (extract-feature1-content)
    
    ;; Test what's available for routes
    (test-content-for-routes)
    
    ;; Show final file state
    (show-extracted-files)
    
    (println "\n✅ Content extraction test completed!")
    (println "\n💡 Next Steps:")
    (println "   1. Content is now cached in files")
    (println "   2. HTMX routes can load content instantly")
    (println "   3. Run extraction periodically to refresh content")
    (println "   4. Site loads fast with pre-cached content")
    
    (catch Exception e
      (println "❌ Test failed:" (.getMessage e))
      (println "Make sure Alfresco is running and SSH tunnel is active"))))

;; Run the test
(run-content-extraction-test)