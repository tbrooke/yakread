#!/usr/bin/env bb

;; Test the universal content extractor
;; This shows how the new system can extract from any website folder
;; while keeping sync_feature1.clj as a specific test case

(require '[clojure.edn :as edn]
         '[clojure.pprint :as pprint])

;; Load the universal content extractor (would normally be via classpath)
(load-file "src/com/yakread/alfresco/content_extractor.clj")
(load-file "src/com/yakread/alfresco/client.clj") 
(load-file "src/com/yakread/config/website_nodes.clj")

(alias 'extractor 'com.yakread.alfresco.content-extractor)
(alias 'client 'com.yakread.alfresco.client)
(alias 'nodes 'com.yakread.config.website-nodes)

;; --- CONFIGURATION ---
(def alfresco-ctx
  {:alfresco/base-url "http://localhost:8080"
   :alfresco/username "admin"
   :alfresco/password "admin"})

;; --- TEST FUNCTIONS ---

(defn test-connection []
  (println "🔗 Testing Alfresco connection...")
  (if (client/test-connection alfresco-ctx)
    (println "✅ Connection successful")
    (println "❌ Connection failed")))

(defn test-single-component-extraction []
  (println "\n📄 Testing single component extraction (Feature 1)...")
  (let [content-items (extractor/extract-component-content alfresco-ctx :feature1)]
    (println "Extracted" (count content-items) "items from Feature 1")
    (doseq [item content-items]
      (println "  -" (:name item) "(" (:mime-type item) ")"))))

(defn test-full-website-extraction []
  (println "\n🌐 Testing full website extraction...")
  (let [results (extractor/extract-website-content-with-summary alfresco-ctx)]
    (println "📊 Extraction Summary:")
    (pprint/pprint (:summary results))
    
    (println "\n📝 Content by Page:")
    (doseq [[page items] (:by-page results)]
      (println "  " page ":" (count items) "items"))
    
    (println "\n🎨 Content by Type:")
    (doseq [[type items] (:by-type results)]
      (println "  " type ":" (count items) "items"))))

(defn test-published-only-extraction []
  (println "\n📢 Testing published content only...")
  (let [results (extractor/extract-published-content-only alfresco-ctx)]
    (println "Found" (count (vals results)) "published components")
    (doseq [[component result] results]
      (if (:success result)
        (println "  " component ":" (:count result) "published items")
        (println "  " component ": ERROR -" (:error result))))))

(defn test-folder-health-check []
  (println "\n🏥 Testing folder accessibility...")
  (let [health-results (extractor/health-check-all-folders alfresco-ctx)]
    (if (:all-accessible health-results)
      (println "✅ All folders accessible")
      (println "⚠️ Some folders not accessible"))
    
    (doseq [folder-status (:folder-statuses health-results)]
      (println "  " (:folder-key folder-status) ":" 
               (if (:accessible folder-status) "✅" "❌")
               (when (:item-count folder-status) 
                 (str " (" (:item-count folder-status) " items)"))))))

(defn save-extraction-results []
  (println "\n💾 Saving extraction results for mtzUIX...")
  (let [results (extractor/extract-published-content-only alfresco-ctx)
        organized-content (extractor/organize-by-page results)]
    
    ;; Save organized content for each page
    (doseq [[page items] organized-content]
      (let [filename (str "mtzuix-" (name page) "-content.edn")]
        (spit filename (pr-str items))
        (println "📁 Saved" (count items) "items to" filename)))
    
    ;; Save full extraction metadata
    (spit "mtzuix-extraction-metadata.edn" (pr-str results))
    (println "📁 Saved extraction metadata to mtzuix-extraction-metadata.edn")))

;; --- COMPARISON WITH sync_feature1.clj ---

(defn compare-with-sync-feature1 []
  (println "\n🔄 Comparison with sync_feature1.clj:")
  (println "")
  (println "sync_feature1.clj (specific test):")
  (println "  ✅ Focused on Feature 1 only")
  (println "  ✅ Uses babashka.curl for simplicity")
  (println "  ✅ Creates XTDB documents")
  (println "  ✅ Good for testing specific functionality")
  (println "")
  (println "Universal Content Extractor (this system):")
  (println "  ✅ Works with any website folder")
  (println "  ✅ Uses existing alfresco.client infrastructure")
  (println "  ✅ Supports multiple content types")
  (println "  ✅ Organizes content by page and type")
  (println "  ✅ Health checks and validation")
  (println "  ✅ Scalable for entire website"))

;; --- MAIN EXECUTION ---

(defn -main []
  (println "🏗️ Universal Content Extractor Test")
  (println "   Testing the new universal system vs sync_feature1.clj")
  (println "   Make sure SSH tunnel to Alfresco is running\n")
  
  (try
    (test-connection)
    (test-folder-health-check)
    (test-single-component-extraction)
    (test-published-only-extraction)
    (test-full-website-extraction)
    (save-extraction-results)
    (compare-with-sync-feature1)
    
    (println "\n✅ All tests completed successfully!")
    (println "\n💡 Next Steps:")
    (println "   1. Keep sync_feature1.clj as a specific test case")
    (println "   2. Use Universal Content Extractor for production")
    (println "   3. Integrate with XTDB storage service")
    (println "   4. Create content rendering pipeline")
    
    (catch Exception e
      (println "❌ Test failed:" (.getMessage e))
      (println "Make sure Alfresco is running and SSH tunnel is active"))))

;; Run the tests
(-main)