#!/usr/bin/env bb

;; Test SSH tunnel connectivity and extract Feature 2 content with images
;; Guides user through tunnel setup if needed

(require '[babashka.curl :as curl]
         '[cheshire.core :as json]
         '[clojure.edn :as edn])

;; Load configuration and content processor
(load-file "src/com/yakread/config/website_nodes.clj")
(load-file "src/com/yakread/alfresco/content_processor.clj")

(alias 'nodes 'com.yakread.config.website-nodes)
(alias 'processor 'com.yakread.alfresco.content-processor)

;; --- CONFIGURATION ---
(def alfresco-host "http://localhost:8080")
(def alfresco-user "admin")
(def alfresco-pass "admin")
(def api-base (str alfresco-host "/alfresco/api/-default-/public/alfresco/versions/1"))

;; --- CONNECTION TESTING ---

(defn test-tunnel-connection []
  (println "🔗 Testing SSH tunnel connection to Alfresco...")
  (try
    (let [resp (curl/get (str api-base "/nodes/-root-")
                         {:basic-auth [alfresco-user alfresco-pass]
                          :connect-timeout 5000})]
      (if (= 200 (:status resp))
        (do
          (println "✅ SSH tunnel and Alfresco connection successful!")
          (let [root-info (json/parse-string (:body resp) true)]
            (println "   Connected to:" (get-in root-info [:entry :name]))
            (println "   Node ID:" (get-in root-info [:entry :id])))
          true)
        (do
          (println "❌ Alfresco responded but with error. Status:" (:status resp))
          false)))
    (catch Exception e
      (println "❌ Connection failed:" (.getMessage e))
      (println "\n💡 SSH Tunnel Setup Guide:")
      (println "   1. Open a new terminal")
      (println "   2. Run: ssh -L 8080:localhost:8080 your-user@your-alfresco-server")
      (println "   3. Keep that terminal open")
      (println "   4. Come back here and run this script again")
      (println "\n   Alternative (background tunnel):")
      (println "   ssh -L 8080:localhost:8080 -N -f your-user@your-alfresco-server")
      false)))

(defn check-tunnel-status []
  (println "\n🔍 Checking for existing SSH tunnels...")
  (try
    (let [result (clojure.java.shell/sh "ps" "aux")]
      (if (= 0 (:exit result))
        (let [processes (:out result)
              ssh-tunnels (filter #(and (clojure.string/includes? % "ssh")
                                        (clojure.string/includes? % "8080"))
                                 (clojure.string/split-lines processes))]
          (if (seq ssh-tunnels)
            (do
              (println "✅ Found SSH tunnel processes:")
              (doseq [tunnel ssh-tunnels]
                (println "   " tunnel)))
            (println "⚠️ No SSH tunnel processes found for port 8080")))
        (println "Could not check process list")))
    (catch Exception e
      (println "Could not check SSH tunnel status"))))

;; --- FEATURE 2 EXTRACTION ---

(defn get-node-children [node-id]
  (let [resp (curl/get (str api-base "/nodes/" node-id "/children")
                       {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      {:success true :data (json/parse-string (:body resp) true)}
      {:success false :error (:body resp)})))

(defn get-node-content [node-id]
  (let [resp (curl/get (str api-base "/nodes/" node-id "/content")
                       {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      {:success true :content (:body resp)}
      {:success false :error (:body resp)})))

(defn extract-feature2-with-images []
  (println "\n📡 Extracting Feature 2 content with image processing...")
  
  (let [feature2-node-id (nodes/get-homepage-component-node-id :feature2)]
    (if feature2-node-id
      (do
        (println "   Feature 2 node ID:" feature2-node-id)
        (let [children-result (get-node-children feature2-node-id)]
          (if (:success children-result)
            (let [entries (get-in children-result [:data :list :entries])
                  html-files (filter #(= "text/html" (get-in % [:entry :content :mimeType])) entries)]
              
              (println "✅ Found" (count entries) "items in Feature 2 folder")
              (println "   HTML files:" (count html-files))
              
              ;; Show all items found
              (when (seq entries)
                (println "   Items in folder:")
                (doseq [entry entries]
                  (let [file-entry (:entry entry)]
                    (println "     📄" (:name file-entry) 
                            "(" (get-in file-entry [:content :mimeType]) ")"))))
              
              ;; Extract and process HTML content
              (if (seq html-files)
                (let [extraction-results
                      (for [html-file html-files]
                        (let [file-entry (:entry html-file)
                              file-node-id (:id file-entry)
                              file-name (:name file-entry)
                              content-result (get-node-content file-node-id)]
                          
                          (println "\n   📄 Processing:" file-name)
                          
                          (if (:success content-result)
                            (let [html-content (:content content-result)
                                  image-urls (processor/find-image-urls html-content)
                                  processed-html (processor/process-html-content html-content)]
                              
                              (println "      Content length:" (count html-content) "characters")
                              (println "      Images found:" (count image-urls))
                              
                              ;; Show image details
                              (when (seq image-urls)
                                (println "      Image URLs:")
                                (doseq [url image-urls]
                                  (let [node-id (processor/extract-node-id-from-url url)]
                                    (println "        📷" url)
                                    (when node-id
                                      (println "           → Node ID:" node-id)
                                      (println "           → Proxy URL: /proxy/image/" node-id)))))
                              
                              {:success true
                               :alfresco-node-id file-node-id
                               :alfresco-name file-name
                               :alfresco-modified-at (:modifiedAt file-entry)
                               :content html-content
                               :processed-html processed-html
                               :image-urls image-urls
                               :has-images (seq image-urls)
                               :extracted-at (java.time.Instant/now)})
                            
                            {:success false
                             :alfresco-node-id file-node-id
                             :alfresco-name file-name
                             :error (:error content-result)})))]
                  
                  ;; Filter successful extractions
                  (let [successful-items (filter :success extraction-results)]
                    (if (seq successful-items)
                      successful-items
                      (do
                        (println "❌ No content could be extracted")
                        []))))
                
                (do
                  (println "⚠️ No HTML files found in Feature 2 folder")
                  [])))
            
            (do
              (println "❌ Could not access Feature 2 folder:" (:error children-result))
              []))))
      
      (do
        (println "❌ No Feature 2 node ID configured")
        []))))

(defn save-feature2-content [content-items]
  (println "\n💾 Saving Feature 2 content with image processing...")
  
  (if (seq content-items)
    (let [website-content (map (fn [item]
                                {:title (:alfresco-name item)
                                 :html-content (:processed-html item)
                                 :original-html (:content item)
                                 :image-urls (:image-urls item)
                                 :has-images (:has-images item)
                                 :last-updated (:alfresco-modified-at item)
                                 :source-node-id (:alfresco-node-id item)})
                              content-items)]
      
      ;; Save raw extracted content
      (spit "mtzuix-feature2-content.edn" (pr-str content-items))
      
      ;; Save website-ready content  
      (spit "mtzuix-feature2-website-content.edn" (pr-str website-content))
      
      (println "✅ Saved Feature 2 content:")
      (println "   📄 mtzuix-feature2-content.edn (full data)")
      (println "   🌐 mtzuix-feature2-website-content.edn (website-ready)")
      
      ;; Show summary
      (let [total-images (apply + (map (comp count :image-urls) content-items))
            items-with-images (count (filter :has-images content-items))]
        (println "\n📊 Content Summary:")
        (println "   Items extracted:" (count content-items))
        (println "   Items with images:" items-with-images)
        (println "   Total images found:" total-images))
      
      ;; Show preview
      (when-let [first-item (first website-content)]
        (println "\n📄 Content Preview:")
        (println "   Title:" (:title first-item))
        (println "   Has images:" (:has-images first-item))
        (when (:has-images first-item)
          (println "   Image count:" (count (:image-urls first-item))))
        (println "   Content preview:" (subs (:html-content first-item) 0 (min 150 (count (:html-content first-item)))) "..."))
      
      website-content)
    
    (do
      (println "❌ No content to save")
      [])))

;; --- MAIN EXECUTION ---

(println "🔗 SSH Tunnel Test & Feature 2 Extraction")
(println "   Testing connection and extracting content with image processing")
(println)

(check-tunnel-status)

(if (test-tunnel-connection)
  (do
    (println "\n🚀 Connection successful! Proceeding with extraction...")
    
    (let [content-items (extract-feature2-with-images)]
      (if (seq content-items)
        (do
          (save-feature2-content content-items)
          
          (println "\n✅ Feature 2 extraction completed successfully!")
          (println "\n💡 Next steps:")
          (println "   1. Feature 2 content extracted with image processing")
          (println "   2. Alfresco Share URLs converted to proxy URLs") 
          (println "   3. Content ready for website integration")
          (println "   4. Images will be served via image proxy"))
        
        (println "\n❌ No Feature 2 content found or extracted"))))
  
  (println "\n❌ Cannot proceed without SSH tunnel connection"))

(println "\n🌐 To integrate with website:")
(println "   Add Feature 2 content loading to routes.clj")
(println "   Images will automatically use proxy URLs")
(println "   Test image proxy: http://localhost:4000/proxy/test")