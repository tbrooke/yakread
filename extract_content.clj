#!/usr/bin/env bb

;; Command to extract content from Alfresco for Mount Zion UCC website
;; Usage: bb extract_content.clj [component-name]
;; Example: bb extract_content.clj feature1
;; Example: bb extract_content.clj all

(require '[clojure.tools.logging :as log]
         '[clojure.pprint :as pprint])

;; Load the services
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
  "Alfresco connection context - update these settings"
  {:alfresco/base-url "http://localhost:8080"      ; Update if different
   :alfresco/username "admin"                       ; Update with your username
   :alfresco/password "admin"})                     ; Update with your password

;; --- COMMAND FUNCTIONS ---

(defn test-alfresco-connection []
  (println "🔗 Testing Alfresco connection...")
  (if (client/test-connection alfresco-ctx)
    (do
      (println "✅ Alfresco connection successful")
      true)
    (do
      (println "❌ Alfresco connection failed")
      (println "   Check that:")
      (println "   - Alfresco is running")
      (println "   - SSH tunnel is active (if using SSH)")
      (println "   - Credentials are correct")
      false)))

(defn extract-single-component [component-name]
  (let [component-key (keyword component-name)]
    (println (str "📄 Extracting content for component: " component-name))
    
    (if (contains? (nodes/get-all-component-nodes) component-key)
      (let [result (content-service/extract-and-store-component-content alfresco-ctx component-key)]
        (if (:success result)
          (do
            (println "✅ Successfully extracted" (:items-count result) "items")
            (println "📁 Content saved to:" (content-service/get-content-file-path component-key))
            
            ;; Show content summary
            (let [summary (content-service/get-content-summary component-key)]
              (println "📊 Content Summary:")
              (pprint/pprint summary))
            
            result)
          (do
            (println "❌ Extraction failed:" (:error result))
            result)))
      
      (do
        (println "❌ Unknown component:" component-name)
        (println "Available components:" (keys (nodes/get-all-component-nodes)))
        {:success false :error "Unknown component"}))))

(defn extract-all-components []
  (println "🌐 Extracting content for all website components...")
  
  (let [result (content-service/extract-and-store-all-content alfresco-ctx)]
    (println "📊 Extraction Results:")
    (println "   Total components:" (:total-components result))
    (println "   Successful:" (:successful-components result))
    
    (println "\n📋 Component Details:")
    (doseq [[component-key component-result] (:results result)]
      (if (:success component-result)
        (println "   ✅" component-key ":" (:items-count component-result) "items")
        (println "   ❌" component-key ": FAILED -" (:error component-result))))
    
    result))

(defn show-health-status []
  (println "🏥 Content Service Health Check:")
  (let [health (content-service/content-health-check)]
    (pprint/pprint health)))

(defn show-available-components []
  (println "📋 Available Components:")
  (let [all-components (nodes/get-all-component-nodes)]
    (doseq [[component-key node-id] all-components]
      (println "   " component-key "→" node-id))))

(defn show-usage []
  (println "📖 Usage:")
  (println "   bb extract_content.clj <command>")
  (println "")
  (println "Commands:")
  (println "   test           - Test Alfresco connection")
  (println "   list           - Show available components")  
  (println "   health         - Show content service health")
  (println "   feature1       - Extract Feature 1 content")
  (println "   homepage       - Extract homepage content")
  (println "   about          - Extract about page content")
  (println "   all            - Extract all content")
  (println "")
  (println "Examples:")
  (println "   bb extract_content.clj test")
  (println "   bb extract_content.clj feature1")
  (println "   bb extract_content.clj all"))

;; --- MAIN EXECUTION ---

(defn -main [& args]
  (println "🏗️ Mount Zion UCC Content Extraction")
  (println "   Extracting content from Alfresco for website")
  (println "")
  
  (let [command (first args)]
    (case command
      "test" 
      (test-alfresco-connection)
      
      "list"
      (show-available-components)
      
      "health"
      (show-health-status)
      
      "all"
      (if (test-alfresco-connection)
        (extract-all-components)
        (println "❌ Cannot extract - Alfresco connection failed"))
      
      ("feature1" "feature2" "feature3" "hero" "homepage" "about" "worship" "activities" "events" "contact")
      (if (test-alfresco-connection)
        (extract-single-component command)
        (println "❌ Cannot extract - Alfresco connection failed"))
      
      (nil "help")
      (show-usage)
      
      ;; Unknown command
      (do
        (println "❌ Unknown command:" command)
        (show-usage))))
  
  (println "\n💡 Next Steps:")
  (println "   1. Run 'bb extract_content.clj test' to verify Alfresco connection")
  (println "   2. Run 'bb extract_content.clj feature1' to test single component")
  (println "   3. Run 'bb extract_content.clj all' to extract all content")
  (println "   4. Check generated .edn files for extracted content"))

;; Run with command line arguments
(apply -main *command-line-args*)