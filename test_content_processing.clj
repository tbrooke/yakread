(require '[com.yakread.alfresco.content-processor :as processor])

;; Test the current Feature 2 content
(def test-html "<p style=\"text-align: center;\">&nbsp;</p>
<h2 style=\"text-align: center; font-family: sans-serif; color: black;\">Blood Drive</h2>
<p>&nbsp;</p>
<p><img src=\"http://admin.mtzcg.com/share/page/site/swsdp/document-details?nodeRef=workspace://SpacesStore/fad117b4-b182-494e-9117-b4b182994ed8\" alt=\"Blood Drive\" width=\"600\" height=\"400\" /></p>")

(println "=== ORIGINAL HTML ===")
(println test-html)

(println "\n=== PROCESSED HTML ===")
(def processed (processor/process-html-content test-html))
(println processed)

(println "\n=== IMAGE URLs FOUND ===")
(def image-urls (processor/find-image-urls test-html))
(println image-urls)

(println "\n=== NODE ID EXTRACTION ===")
(doseq [url image-urls]
  (println url "→" (processor/extract-node-id-from-url url)))