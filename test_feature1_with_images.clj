#!/usr/bin/env bb

;; Test Feature 1 content but process it as if it had images
;; Demonstrates the image processing pipeline

(require '[clojure.edn :as edn])

;; Load content processor
(load-file "src/com/yakread/alfresco/content_processor.clj")
(alias 'processor 'com.yakread.alfresco.content-processor)

;; --- LOAD EXISTING FEATURE 1 CONTENT ---

(defn load-and-enhance-feature1 []
  (println "📄 Loading existing Feature 1 content...")
  
  (if (.exists (clojure.java.io/file "mtzuix-feature1-content.edn"))
    (let [feature1-content (edn/read-string (slurp "mtzuix-feature1-content.edn"))
          first-item (first feature1-content)]
      
      (println "✅ Loaded Feature 1 content:")
      (println "   Title:" (:title first-item))
      (println "   Content:" (:html-content first-item))
      
      ;; Create a version with an embedded image for testing
      (let [enhanced-html (str (:html-content first-item)
                               "\n<div style=\"text-align: center; margin-top: 20px;\">"
                               "\n<img src=\"http://admin.mtzcg.com/share/proxy/alfresco/api/node/content/workspace/SpacesStore/203303207-1234-5678-9012-image123456/welcome-image.jpg\" "
                               "alt=\"Welcome to Mt Zion\" style=\"max-width: 100%; height: auto; border-radius: 8px;\">"
                               "\n</div>")
            
            enhanced-item (assoc first-item :html-content enhanced-html)]
        
        (println "\n🖼️ Enhanced with sample image:")
        (println "   Original length:" (count (:html-content first-item)))
        (println "   Enhanced length:" (count enhanced-html))
        
        enhanced-item))
    
    (do
      (println "❌ No Feature 1 content found")
      nil)))

(defn test-image-processing-on-feature1 []
  (println "\n🧪 Testing image processing on Feature 1 content...")
  
  (when-let [enhanced-item (load-and-enhance-feature1)]
    (let [html-content (:html-content enhanced-item)
          image-urls (processor/find-image-urls html-content)
          processed-html (processor/process-html-content html-content)]
      
      (println "\n📋 Processing Results:")
      (println "   Images found:" (count image-urls))
      (when (seq image-urls)
        (doseq [url image-urls]
          (let [node-id (processor/extract-node-id-from-url url)]
            (println "     📷" url)
            (println "        → Node ID:" node-id)
            (println "        → Proxy URL: /proxy/image/" node-id))))
      
      (println "\n🔄 HTML Transformation:")
      (println "Original HTML:")
      (println html-content)
      (println "\n" (str/join "" (repeat 60 "-")))
      (println "Processed HTML:")
      (println processed-html)
      
      ;; Save processed version
      (let [processed-item (assoc enhanced-item :html-content processed-html
                                                :image-urls image-urls
                                                :has-images (seq image-urls))]
        (spit "mtzuix-feature1-with-images.edn" (pr-str [processed-item]))
        (println "\n💾 Saved enhanced Feature 1 with image processing to:")
        (println "   mtzuix-feature1-with-images.edn"))
      
      processed-html)))

(defn show-integration-example []
  (println "\n🌐 Website Integration Example:")
  (println "With the image processing working, you can now:")
  (println)
  (println "1. Add real images to Feature 2 folder in Alfresco")
  (println "2. Extract with: bb extract_feature2_with_images.clj") 
  (println "3. Images will be automatically converted to proxy URLs")
  (println "4. Display on website with working images")
  (println)
  (println "Image serving flow:")
  (println "   Browser requests: /proxy/image/203303207-1234-5678-9012-image123456")
  (println "   ↓")
  (println "   Image proxy fetches from Alfresco with authentication")
  (println "   ↓") 
  (println "   Image cached locally for fast serving")
  (println "   ↓")
  (println "   Served to browser"))

;; --- MAIN EXECUTION ---

(println "🧪 Feature 1 Content with Image Processing Test")
(println "   Testing image processing pipeline with existing content")
(println)

(test-image-processing-on-feature1)
(show-integration-example)

(println "\n✅ Image processing test completed!")
(println "\n💡 Ready for real Feature 2 content:")
(println "   1. Add HTML file with images to Feature 2 folder in Alfresco")
(println "   2. Run: bb extract_feature2_with_images.clj")
(println "   3. Images will be processed and ready for website")