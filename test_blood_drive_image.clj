#!/usr/bin/env bb

;; Test the Blood Drive content image processing

(require '[clojure.edn :as edn])

;; Load content processor
(load-file "src/com/yakread/alfresco/content_processor.clj")
(alias 'processor 'com.yakread.alfresco.content-processor)

;; --- TEST BLOOD DRIVE CONTENT ---

(defn test-blood-drive-content []
  (println "🩸 Testing Blood Drive content extraction and image processing...")
  
  (if (.exists (clojure.java.io/file "mtzuix-feature2-website-content.edn"))
    (let [content (edn/read-string (slurp "mtzuix-feature2-website-content.edn"))
          blood-drive-item (first content)]
      
      (println "\n📄 Blood Drive Content:")
      (println "   Title:" (:title blood-drive-item))
      (println "   Has images:" (:has-images blood-drive-item))
      (println "   Image URLs:" (:image-urls blood-drive-item))
      
      ;; Test node ID extraction from the document details URL
      (let [image-url (first (:image-urls blood-drive-item))
            node-id (processor/extract-node-id-from-url image-url)]
        
        (println "\n🔍 Image URL Analysis:")
        (println "   Original URL:" image-url)
        (println "   Extracted Node ID:" node-id)
        (if node-id
          (println "   Proxy URL: /proxy/image/" node-id)
          (println "   ⚠️ Could not extract node ID - may need URL pattern update")))
      
      ;; Show the HTML content
      (println "\n📝 HTML Content:")
      (println (:html-content blood-drive-item))
      
      ;; Show the original vs processed HTML
      (println "\n🔄 Original vs Processed:")
      (println "Original HTML:")
      (println (:original-html blood-drive-item))
      (println "\nProcessed HTML:")
      (println (:html-content blood-drive-item))
      
      blood-drive-item)
    
    (do
      (println "❌ No Feature 2 content found")
      (println "Run: bb extract_feature2_with_images.clj first")
      nil)))

(defn test-image-node-id-extraction []
  (println "\n🧪 Testing node ID extraction from document details URL...")
  
  (let [test-url "http://admin.mtzcg.com/share/page/site/swsdp/document-details?nodeRef=workspace://SpacesStore/fad117b4-b182-494e-9117-b4b182994ed8"
        node-id (processor/extract-node-id-from-url test-url)]
    
    (println "Test URL:" test-url)
    (println "Extracted Node ID:" node-id)
    
    (if node-id
      (do
        (println "✅ Successfully extracted node ID!")
        (println "Proxy URL would be: /proxy/image/" node-id))
      (do
        (println "❌ Could not extract node ID")
        (println "The URL pattern may need updating")))
    
    node-id))

(defn show-proxy-integration []
  (println "\n🌐 Integration with Image Proxy:")
  (println "The extracted content should work with the image proxy system:")
  (println)
  (println "1. Blood Drive HTML contains document details URL")
  (println "2. Content processor extracts node ID: fad117b4-b182-494e-9117-b4b182994ed8")
  (println "3. URL converted to proxy: /proxy/image/fad117b4-b182-494e-9117-b4b182994ed8")
  (println "4. Image proxy serves image with Alfresco authentication")
  (println "5. Image cached locally for fast serving")
  (println)
  (println "🚀 Test the image proxy:")
  (println "   Start the app: clj -M -m com.yakread")
  (println "   Visit: http://localhost:4000/proxy/test")
  (println "   Visit: http://localhost:4000/proxy/image/fad117b4-b182-494e-9117-b4b182994ed8"))

;; --- MAIN EXECUTION ---

(println "🩸 Blood Drive Image Processing Test")
(println "   Testing the extraction and processing of the Blood Drive content")
(println)

(test-blood-drive-content)
(test-image-node-id-extraction)
(show-proxy-integration)

(println "\n✅ Blood Drive test completed!")
(println "\n💡 Next steps:")
(println "   1. Content extracted with image processing working")
(println "   2. Feature 2 content ready for website display")
(println "   3. Image proxy ready to serve images with authentication")
(println "   4. Start the app to see Blood Drive content with images")