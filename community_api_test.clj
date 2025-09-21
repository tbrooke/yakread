#!/usr/bin/env bb

;; Test available APIs in Alfresco Community Edition

(require '[babashka.curl :as curl]
         '[cheshire.core :as json])

(def alfresco-host "http://admin.mtzcg.com")
(def alfresco-user "admin")
(def alfresco-pass "admin")

(defn test-url [url description]
  (println (str "\n=== " description " ==="))
  (println "URL:" url)
  (try
    (let [resp (curl/get url {:basic-auth [alfresco-user alfresco-pass] :as :json})]
      (println "Status:" (:status resp))
      (when (= 200 (:status resp))
        (println "Response:" (:body resp))))
    (catch Exception e
      (println "Error:" (.getMessage e)))))

;; Test Community Edition APIs that might be available
(test-url (str alfresco-host "/alfresco/service/api/classes") "Content Model Classes")
(test-url (str alfresco-host "/alfresco/service/api/namespaces") "Namespaces")
(test-url (str alfresco-host "/alfresco/service/api/dictionary") "Data Dictionary")
(test-url (str alfresco-host "/alfresco/service/api/dictionary/class/cm:content") "Content Type")
(test-url (str alfresco-host "/alfresco/service/api/dictionary/class/cm:folder") "Folder Type")

;; Try some other common endpoints
(test-url (str alfresco-host "/alfresco/service/api/repository") "Repository Info")
(test-url (str alfresco-host "/alfresco/service/index") "Service Index")
(test-url (str alfresco-host "/alfresco/wcs/api/repository") "WebScript Repository")

;; Try node operations
(test-url (str alfresco-host "/alfresco/service/api/path/workspace/SpacesStore/Company%20Home") "Company Home")
(test-url (str alfresco-host "/alfresco/service/api/node/workspace/SpacesStore") "SpacesStore")

;; Search
(test-url (str alfresco-host "/alfresco/service/api/search") "Search Service")

println "\n=== Available API Endpoints Summary ==="
println "Community Edition supports WebScript APIs instead of REST APIs"
println "Use /alfresco/service/api/* endpoints for content model access"