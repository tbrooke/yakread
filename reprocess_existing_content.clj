(require '[com.yakread.alfresco.content-processor :as processor]
         '[clojure.edn :as edn])

(println "🔧 Reprocessing existing content files with image proxy URLs...")

;; Process Feature 2 content that has images
(let [content-file "mtzuix-feature2-website-content.edn"
      content (edn/read-string (slurp content-file))
      processed-content (map (fn [item]
                               (if (:html-content item)
                                 (let [processed-html (processor/process-html-content (:html-content item))
                                       image-urls (processor/find-image-urls (:html-content item))
                                       proxy-urls (map #(str "/proxy/image/" (processor/extract-node-id-from-url %)) image-urls)]
                                   (assoc item 
                                          :html-content processed-html
                                          :original-html (:html-content item)
                                          :image-urls image-urls
                                          :proxy-urls proxy-urls
                                          :has-images (seq image-urls)
                                          :processed-at (str (java.time.Instant/now))))
                                 item))
                             content)]
  
  (println "📁 Original content file:" content-file)
  (println "📊 Processing" (count content) "items...")
  
  (doseq [item processed-content]
    (when (:has-images item)
      (println "🖼️  Item:" (:title item))
      (println "   Original URLs:" (:image-urls item))
      (println "   Proxy URLs:" (:proxy-urls item))))
  
  ;; Save processed version
  (let [processed-file "mtzuix-feature2-processed-content.edn"]
    (spit processed-file (pr-str processed-content))
    (println "✅ Saved processed content to:" processed-file)))