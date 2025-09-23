#!/usr/bin/env bb

;; Test extraction of single item from Feature 1 folder
;; This tests the universal content system with real Alfresco data

(require '[clojure.pprint :as pprint])

;; Load the content service and dependencies
(load-file "src/com/yakread/alfresco/content_service.clj")
(load-file "src/com/yakread/alfresco/content_extractor.clj")
(load-file "src/com/yakread/alfresco/client.clj")
(load-file "src/com/yakread/config/website_nodes.clj")

(alias 'content-service 'com.yakread.alfresco.content-service)
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
  (try
    (if (client/test-connection alfresco-ctx)
      (println "✅ Alfresco connection successful")
      (println "❌ Alfresco connection failed"))
    (catch Exception e
      (println "❌ Connection error:" (.getMessage e)))))

(defn test-folder-structure []
  (println "\n📁 Testing folder structure...")
  (let [homepage-node-id (nodes/get-page-node-id :homepage)
        feature1-node-id (nodes/get-homepage-component-node-id :feature1)]
    
    (println "   Homepage node ID:" homepage-node-id)
    (println "   Feature1 node ID:" feature1-node-id)
    
    ;; Test folder access
    (let [feature1-access (extractor/check-folder-access alfresco-ctx :feature1)]
      (if (:accessible feature1-access)
        (println "✅ Feature1 folder accessible with" (:item-count feature1-access) "items")
        (println "❌ Feature1 folder not accessible:" (:error feature1-access))))))

(defn test-content-extraction []
  (println "\n📄 Testing content extraction...")
  (let [result (content-service/test-feature1-extraction alfresco-ctx)]
    (if (:success result)
      (do
        (println "✅ Content extraction successful!")
        (println "\n📋 Extracted Content:")
        (pprint/pprint (:content result)))
      (println "❌ Content extraction failed:" (:error result)))))

(defn test-content-for-homepage []
  (println "\n🏠 Testing content for homepage display...")
  (let [homepage-content (content-service/load-homepage-content {})]
    (println "📋 Homepage Content Result:")
    (pprint/pprint homepage-content)
    
    (when-let [feature1 (:feature1 homepage-content)]
      (println "\n📄 Feature 1 Content Preview:")
      (println "   Title:" (:title feature1))
      (println "   Content length:" (count (:html-content feature1)))
      (println "   HTML preview:" (subs (:html-content feature1) 0 (min 100 (count (:html-content feature1)))) "..."))))

(defn test-content-availability []
  (println "\n🏥 Checking content availability...")
  (let [availability (content-service/check-content-availability alfresco-ctx)]
    (println "📊 Content Availability:")
    (doseq [[component status] availability]
      (println "   " component ":")
      (println "     Local file:" (if (:local-content status) "✅" "❌"))
      (println "     Alfresco access:" (if (:alfresco-accessible status) "✅" "❌"))
      (when (:alfresco-item-count status)
        (println "     Items in Alfresco:" (:alfresco-item-count status))))))

(defn show-generated-files []
  (println "\n📁 Generated content files:")
  (let [files (filter #(.exists %) 
                      (map clojure.java.io/file 
                           ["mtzuix-feature1-content.edn"
                            "mtzuix-feature2-content.edn"
                            "mtzuix-feature3-content.edn"
                            "mtzuix-hero-content.edn"]))]
    (if (seq files)
      (doseq [file files]
        (println "   📄" (.getName file) "(" (.length file) "bytes)"))
      (println "   No content files found yet."))))

;; --- MAIN EXECUTION ---

(defn run-single-item-test []
  (println "🧪 Testing Single Item Content Extraction")
  (println "   Testing universal content system with your one Feature 1 item")
  (println "   Make sure SSH tunnel to Alfresco is running\n")
  
  (try
    ;; Test connection and setup
    (test-connection)
    (test-folder-structure)
    (test-content-availability)
    
    ;; Test extraction
    (test-content-extraction)
    (test-content-for-homepage)
    
    ;; Show results
    (show-generated-files)
    
    (println "\n✅ Single item test completed!")
    (println "\n💡 Next steps:")
    (println "   1. If extraction worked, integrate with routes.clj")
    (println "   2. Test the live website with real Alfresco content")
    (println "   3. Add more content items to Feature 1 folder")
    (println "   4. Expand to other components (Feature 2, Hero, etc.)")
    
    (catch Exception e
      (println "❌ Test failed:" (.getMessage e))
      (println "Make sure:")
      (println "   - Alfresco is running")
      (println "   - SSH tunnel is active")
      (println "   - Feature 1 folder has content")
      (.printStackTrace e))))

;; Run the test
(run-single-item-test)