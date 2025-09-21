#!/usr/bin/env bb

;; Find Swagger API explorer and REST API endpoints

(require '[babashka.curl :as curl])

(def alfresco-host "http://admin.mtzcg.com")
(def alfresco-user "admin")
(def alfresco-pass "admin")

(defn test-endpoint [url description]
  (println (str "\n=== " description " ==="))
  (println "URL:" url)
  (try
    (let [resp (curl/get url {:basic-auth [alfresco-user alfresco-pass]})]
      (cond
        (= 200 (:status resp))
        (do
          (println "✅ FOUND!")
          (println "Content preview:" (subs (:body resp) 0 (min 300 (count (:body resp)))))
          true)

        (= 404 (:status resp))
        (println "❌ Not found")

        (= 401 (:status resp))
        (println "🔒 Authentication required")

        (= 403 (:status resp))
        (println "🚫 Forbidden")

        :else
        (println "⚠️  Status:" (:status resp))))
    (catch Exception e
      (println "❌ Error:" (.getMessage e))))
  false)

;; Test various API explorer paths
(def api-explorer-paths
  ["/alfresco/api-explorer/"
   "/alfresco/api-explorer/index.html"
   "/alfresco/swagger/"
   "/alfresco/swagger-ui/"
   "/alfresco/swagger-ui/index.html"
   "/api-explorer/"
   "/swagger/"
   "/swagger-ui/"
   "/alfresco/s/api/"
   "/alfresco/service/api/"
   "/alfresco/wcservice/api/"
   "/alfresco/wcs/api/"
   "/alfresco/rest/"
   "/alfresco/restapi/"
   "/alfresco/api/v1/"
   "/alfresco/api/-default-/"
   "/alfresco/public/"
   "/alfresco/api/swagger.json"
   "/alfresco/swagger.json"])

(println "Searching for Swagger API Explorer and REST API endpoints...")

(doseq [path api-explorer-paths]
  (test-endpoint (str alfresco-host path) path))

;; Test with port 8080 if needed
(println "\n=== Testing with port 8080 ===")
(test-endpoint "http://admin.mtzcg.com:8080/alfresco/api-explorer/" "API Explorer on 8080")
(test-endpoint "http://admin.mtzcg.com:8080/alfresco/swagger-ui/" "Swagger UI on 8080")

;; Test Community Edition v1 API
(println "\n=== Testing V1 API endpoints ===")
(test-endpoint (str alfresco-host "/alfresco/api/-default-/public/alfresco/versions/1/nodes/-my-") "V1 API - My Files")
(test-endpoint (str alfresco-host "/alfresco/api/-default-/public/alfresco/versions/1/nodes/-root-") "V1 API - Root")

(println "\n=== Summary ===")
(println "If any ✅ FOUND! results appear above, those are your working API endpoints")
(println "Copy the successful URLs to use in your model sync script")