#!/usr/bin/env bb

;; Test script for universal content extraction from Mount Zion UCC website folders
;; This demonstrates the new universal content extractor vs the specific feature1 sync

(require '[clojure.pprint :as pp])

;; Load the universal content extractor
(load-file "src/com/yakread/alfresco/content_extractor.clj")
(alias 'extractor 'com.yakread.alfresco.content-extractor)

;; Load existing client infrastructure  
(load-file "src/com/yakread/alfresco/client.clj")
(alias 'client 'com.yakread.alfresco.client)

;; Load configuration
(load-file "src/com/yakread/config/website_nodes.clj")
(alias 'nodes 'com.yakread.config.website-nodes)

;; --- CONFIGURATION ---

(def ctx
  "Context with Alfresco connection details"
  {:alfresco/base-url "http://localhost:8080"
   :alfresco/username "admin" 
   :alfresco/password "admin"})

;; --- TEST FUNCTIONS ---

(defn test-connection []
  (println "🔌 Testing Alfresco connection...")
  (if (client/test-connection ctx)
    (println "✅ Connection successful")
    (println "❌ Connection failed")))

(defn test-single-component-extraction []
  (println "\n🧪 Testing single component extraction (feature1)...")
  (let [content-items (extractor/extract-component-content ctx :feature1)]
    (println "📋 Results:")
    (println "   Found" (count content-items) "content items")
    (doseq [item content-items]
      (println "   📄" (:name item) 
               "(" (:mime-type item) ")"
               "status:" (:status item)))
    content-items))

(defn test-full-website-extraction []
  (println "\n🌐 Testing full website content extraction...")
  (let [results (extractor/extract-website-content-with-summary ctx 
                                                                 {:include-unpublished false
                                                                  :max-depth 3})]
    (println "📊 Summary:")
    (pp/pprint (:summary results))
    
    (println "\n📋 Content by page:")
    (doseq [[page items] (:by-page results)]
      (println "   " page ":" (count items) "items"))
    
    (println "\n🧩 Content by type:")
    (doseq [[type items] (:by-type results)]
      (println "   " type ":" (count items) "items"))
    
    results))

(defn test-published-content-only []
  (println "\n📢 Testing published content extraction...")
  (let [content-items (extractor/extract-published-content-only ctx)]
    (println "📋 Published content results:")
    (doseq [[component-key result] content-items]
      (if (:success result)
        (println "   ✅" component-key ":" (:count result) "items")
        (println "   ❌" component-key ": FAILED -" (:error result))))
    content-items))

(defn test-health-checks []
  (println "\n🏥 Testing folder accessibility...")
  (let [health-results (extractor/health-check-all-folders ctx)]
    (println "📋 Health check results:")
    (println "   All accessible:" (:all-accessible health-results))
    (doseq [status (:folder-statuses health-results)]
      (if (:accessible status)
        (println "   ✅" (:folder-key status) "- accessible," (:item-count status) "items")
        (println "   ❌" (:folder-key status) "- FAILED:" (:error status))))
    health-results))

(defn compare-with-sync-feature1 []
  (println "\n🔄 Comparing universal extractor vs sync_feature1...")
  
  ;; Extract using universal extractor
  (println "Using universal extractor:")
  (let [universal-items (extractor/extract-component-content ctx :feature1)]
    (println "   Found" (count universal-items) "items")
    
    ;; Show difference in approach
    (println "\n📊 Universal Extractor Benefits:")
    (println "   ✅ Uses existing client infrastructure")
    (println "   ✅ Works with any website folder")
    (println "   ✅ Handles multiple content types")
    (println "   ✅ Built-in publish filtering")
    (println "   ✅ Recursive folder traversal")
    (println "   ✅ Organized by page/component")
    
    (println "\n📊 sync_feature1.clj (specific test):")
    (println "   📝 Focused on Feature 1 only")
    (println "   📝 Uses babashka.curl")
    (println "   📝 Hardcoded configuration")
    (println "   📝 File-based simulation")
    (println "   📝 Good for testing specific workflows")
    
    universal-items))

;; --- MAIN EXECUTION ---

(defn run-all-tests []
  (println "🏗️ Mount Zion UCC Universal Content Extraction Test")
  (println "   Testing new universal content extractor")
  (println "   SSH tunnel to Alfresco should be running\n")
  
  (try
    ;; Test connection first
    (test-connection)
    
    ;; Test individual functions
    (test-health-checks)
    (test-single-component-extraction)
    (test-published-content-only)
    (test-full-website-extraction)
    (compare-with-sync-feature1)
    
    (println "\n✅ All tests completed!")
    (println "\n💡 Next steps:")
    (println "   1. Integrate with XTDB storage")
    (println "   2. Create content processing pipeline")
    (println "   3. Add to regular sync jobs")
    (println "   4. Keep sync_feature1.clj as specific test case")
    
    (catch Exception e
      (println "❌ Test failed:" (.getMessage e))
      (.printStackTrace e))))

;; Run the tests
(run-all-tests)