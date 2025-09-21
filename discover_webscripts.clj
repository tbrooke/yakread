#!/usr/bin/env bb

;; Discover available WebScript APIs in Alfresco Community Edition

(require '[babashka.curl :as curl]
         '[cheshire.core :as json]
         '[clojure.string :as str])

(def alfresco-host "http://admin.mtzcg.com")
(def alfresco-user "admin")
(def alfresco-pass "admin")

(defn test-url-safe [url description]
  (println (str "\n=== " description " ==="))
  (println "URL:" url)
  (try
    (let [resp (curl/get url {:basic-auth [alfresco-user alfresco-pass]})]
      (cond
        (= 200 (:status resp))
        (do
          (println "✅ SUCCESS - Status:" (:status resp))
          (let [body (:body resp)]
            (if (str/starts-with? body "{")
              (try
                (let [parsed (json/parse-string body)]
                  (println "JSON Response keys:" (keys parsed)))
                (catch Exception e
                  (println "JSON parse failed, raw response preview:" (subs body 0 (min 200 (count body))))))
              (println "Response preview:" (subs body 0 (min 200 (count body)))))))

        (= 404 (:status resp))
        (println "❌ NOT FOUND - Endpoint doesn't exist")

        :else
        (println "⚠️  Status:" (:status resp) "- Response:" (subs (:body resp) 0 (min 200 (count (:body resp)))))))
    (catch Exception e
      (println "❌ ERROR:" (.getMessage e)))))

;; Test WebScript index/discovery endpoints
(test-url-safe (str alfresco-host "/alfresco/service/index") "WebScript Index")
(test-url-safe (str alfresco-host "/alfresco/service/") "Service Root")
(test-url-safe (str alfresco-host "/alfresco/s/index") "Short Service Index")

;; Test known working endpoints from earlier
(test-url-safe (str alfresco-host "/alfresco/service/api/server") "Server Info")
(test-url-safe (str alfresco-host "/alfresco/api/") "API Root")

;; Test dictionary/model endpoints that might exist
(test-url-safe (str alfresco-host "/alfresco/service/api/dictionary") "Dictionary Root")
(test-url-safe (str alfresco-host "/alfresco/service/api/classes") "Content Classes")
(test-url-safe (str alfresco-host "/alfresco/service/api/namespaces") "Namespaces")

;; Test specific content model queries
(test-url-safe (str alfresco-host "/alfresco/service/api/dictionary/class/cm:content") "CM Content Type")
(test-url-safe (str alfresco-host "/alfresco/service/api/dictionary/class/cm:folder") "CM Folder Type")

;; Test alternative paths
(test-url-safe (str alfresco-host "/alfresco/wcs/api/dictionary") "WCS Dictionary")
(test-url-safe (str alfresco-host "/alfresco/wcservice/api/dictionary") "WC Service Dictionary")

;; Test repository browsing
(test-url-safe (str alfresco-host "/alfresco/service/api/repository") "Repository Info")
(test-url-safe (str alfresco-host "/alfresco/service/api/path/workspace/SpacesStore") "SpacesStore Root")

println "\n=== Summary ==="
println "This script tests various WebScript endpoints to find what's available"
println "Look for ✅ SUCCESS entries to see working endpoints"
println "Use those endpoints to build your content model extraction"