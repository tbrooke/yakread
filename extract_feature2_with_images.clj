#!/usr/bin/env bb

;; Extract Feature 2 content with embedded image processing
;; This handles HTML files that contain Alfresco Share image links

(require '[babashka.curl :as curl]
         '[cheshire.core :as json]
         '[clojure.edn :as edn]
         '[clojure.string :as str])

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

;; --- HTTP CLIENT ---

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

;; --- FEATURE 2 EXTRACTION ---

(defn extract-feature2-content []
  (println "📡 Extracting Feature 2 content from Alfresco...")
  (let [feature2-node-id (nodes/get-homepage-component-node-id :feature2)]
    
    (if feature2-node-id
      (let [children-result (get-node-children feature2-node-id)]
        (if (:success children-result)
          (let [entries (get-in children-result [:data :list :entries])
                html-files (filter #(= "text/html" (get-in % [:entry :content :mimeType])) entries)]
            
            (println "✅ Found" (count entries) "items in Feature 2 folder")
            (println "   HTML files:" (count html-files))
            
            ;; Extract content from HTML files
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
                   :content (:content content-result)
                   :extracted-at (java.time.Instant/now)}
                  
                  {:success false
                   :alfresco-node-id file-node-id
                   :alfresco-name file-name
                   :error (:error content-result)}))))
          
          (do
            (println "❌ Could not access Feature 2 folder:" (:error children-result))
            [])))
      
      (do
        (println "❌ No Feature 2 node ID configured")
        []))))

(defn process-content-for-images [content-items]
  (println "\n🖼️ Processing content for embedded images...")
  
  (let [successful-items (filter :success content-items)]
    (if (seq successful-items)
      (do
        (println "Found" (count successful-items) "items to process")
        
        (for [item successful-items]
          (let [html-content (:content item)
                
                ;; Find image URLs in the content
                image-urls (processor/find-image-urls html-content)
                
                ;; Process the HTML to replace Alfresco URLs with proxy URLs
                processed-html (processor/process-html-content html-content)
                
                ;; Extract Alfresco node IDs from image URLs
                alfresco-node-ids (map processor/extract-node-id-from-url image-urls)]
            
            (println "\n📄 Processing item:" (:alfresco-name item))
            (println "   Original content length:" (count html-content))
            (println "   Found images:" (count image-urls))
            
            (when (seq image-urls)
              (println "   Image URLs found:")
              (doseq [[i url] (map-indexed vector image-urls)]
                (let [node-id (processor/extract-node-id-from-url url)]
                  (println "     " (inc i) ":" url)
                  (when node-id
                    (println "         Node ID:" node-id)
                    (println "         Proxy URL: /proxy/image/" node-id)))))
            
            ;; Return processed item
            (merge item
                   {:processed-html processed-html
                    :image-urls image-urls
                    :alfresco-image-nodes (filter identity alfresco-node-ids)
                    :has-images (seq image-urls)
                    :processed-at (java.time.Instant/now)}))))
      
      (do
        (println "No successful content items to process")
        []))))

(defn save-processed-content [processed-items]
  (println "\n💾 Saving processed content...")
  
  (if (seq processed-items)
    (let [website-content (map (fn [item]
                                {:title (:alfresco-name item)
                                 :html-content (:processed-html item)
                                 :original-html (:content item)
                                 :image-urls (:image-urls item)
                                 :has-images (:has-images item)
                                 :last-updated (:alfresco-modified-at item)
                                 :source-node-id (:alfresco-node-id item)})
                              processed-items)]
      
      ;; Save raw extracted content
      (spit "mtzuix-feature2-content.edn" (pr-str processed-items))
      
      ;; Save website-ready content
      (spit "mtzuix-feature2-website-content.edn" (pr-str website-content))
      
      (println "✅ Saved content files:")
      (println "   mtzuix-feature2-content.edn (full extraction data)")
      (println "   mtzuix-feature2-website-content.edn (website-ready)")
      
      ;; Show preview
      (when-let [first-item (first website-content)]
        (println "\n📄 Content Preview:")
        (println "   Title:" (:title first-item))
        (println "   Has images:" (:has-images first-item))
        (when (:has-images first-item)
          (println "   Image URLs:" (:image-urls first-item)))
        (println "   Content length:" (count (:html-content first-item)))
        (println "   Processed HTML preview:")
        (println "   " (subs (:html-content first-item) 0 (min 200 (count (:html-content first-item)))) "..."))
      
      website-content)
    
    (do
      (println "❌ No content to save")
      [])))

;; --- MAIN EXECUTION ---

(println "🖼️ Feature 2 Content Extraction with Image Processing")
(println "   Extracting HTML content with embedded Alfresco Share images")
(println "   Converting image URLs to local proxy URLs")
(println)

(try
  ;; Test connection
  (let [test-resp (curl/get (str api-base "/nodes/-root-")
                           {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status test-resp))
      (do
        (println "✅ Alfresco connection successful")
        
        ;; Extract Feature 2 content
        (let [content-items (extract-feature2-content)]
          (if (seq content-items)
            (do
              ;; Process for images
              (let [processed-items (process-content-for-images content-items)]
                (if (seq processed-items)
                  (do
                    ;; Save processed content
                    (save-processed-content processed-items)
                    
                    (println "\n✅ Feature 2 extraction with image processing completed!")
                    (println "\n💡 Next steps:")
                    (println "   1. Content extracted with image URL processing")
                    (println "   2. Alfresco Share URLs converted to proxy URLs")
                    (println "   3. Content ready for website display")
                    (println "   4. Images will be served via /proxy/image/<node-id>"))
                  
                  (println "❌ No content could be processed"))))
            
            (println "❌ No content extracted from Feature 2 folder"))))
      
      (println "❌ Alfresco connection failed. Status:" (:status test-resp))))
  
  (catch Exception e
    (println "❌ Extraction failed:" (.getMessage e))
    (println "Make sure:")
    (println "   - Alfresco is running")
    (println "   - SSH tunnel is active")
    (println "   - Feature 2 folder exists with HTML content")))

(println "\n💡 Test the image proxy:")
(println "   Visit: http://localhost:4000/proxy/test")
(println "   Visit: http://localhost:4000/proxy/image/<node-id>")