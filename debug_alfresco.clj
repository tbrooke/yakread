#!/usr/bin/env bb

;; Debug script to test Alfresco endpoints

(require '[babashka.curl :as curl])

(def alfresco-host "http://admin.mtzcg.com")
(def alfresco-user "admin")
(def alfresco-pass "admin")

(defn test-url [url description]
  (println (str "\n=== Testing " description " ==="))
  (println "URL:" url)
  (try
    (let [resp (curl/get url {:basic-auth [alfresco-user alfresco-pass]})]
      (println "Status:" (:status resp))
      (when (= 200 (:status resp))
        (println "Response preview:" (subs (:body resp) 0 (min 500 (count (:body resp))))))
      (when (not= 200 (:status resp))
        (println "Error body:" (:body resp))))
    (catch Exception e
      (println "Exception:" (.getMessage e)))))

;; Test different possible endpoints
(test-url (str alfresco-host "/alfresco/") "Base Alfresco")
(test-url (str alfresco-host "/alfresco/service/api/server") "Server Info")
(test-url (str alfresco-host "/alfresco/api/") "API Root")
(test-url (str alfresco-host "/alfresco/api/-default-/public/") "Public API Root")
(test-url (str alfresco-host "/alfresco/api/-default-/public/discovery/versions/1/discovery") "API Discovery")

;; Try different OpenAPI paths
(test-url (str alfresco-host "/alfresco/api/-default-/public/openapi/versions/1/openapi.json") "Original OpenAPI")
(test-url (str alfresco-host "/alfresco/api/discovery/openapi.json") "Alternative OpenAPI 1")
(test-url (str alfresco-host "/alfresco/openapi.json") "Alternative OpenAPI 2")

;; Try CMM endpoint
(test-url (str alfresco-host "/alfresco/api/-default-/public/cmm/versions/1/cmm") "CMM Models")