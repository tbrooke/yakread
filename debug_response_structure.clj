#!/usr/bin/env bb

;; Debug the actual response structure from your Alfresco API

(require '[babashka.curl :as curl]
         '[cheshire.core :as json]
         '[clojure.pprint :as pprint])

(def alfresco-host "http://admin.mtzcg.com")
(def alfresco-user "admin")
(def alfresco-pass "admin")
(def api-base (str alfresco-host "/alfresco/api/-default-/public/alfresco/versions/1"))

(defn debug-endpoint [url description]
  (println (str "\n=== " description " ==="))
  (println "URL:" url)
  (try
    (let [resp (curl/get url {:basic-auth [alfresco-user alfresco-pass] :as :json})]
      (println "Status:" (:status resp))
      (if (= 200 (:status resp))
        (do
          (println "Raw response structure:")
          (pprint/pprint (:body resp))

          ;; Try different ways to access the data
          (let [body (:body resp)]
            (println "\nAnalyzing response:")
            (println "Type:" (type body))
            (println "Keys:" (when (map? body) (keys body)))

            ;; Check for entry
            (when-let [entry (get body "entry")]
              (println "Entry keys:" (keys entry))
              (println "Entry name:" (get entry "name")))

            ;; Check for list structure
            (when-let [list-data (get body "list")]
              (println "List keys:" (keys list-data))
              (when-let [pagination (get list-data "pagination")]
                (println "Pagination:" pagination))
              (when-let [entries (get list-data "entries")]
                (println "Entries count:" (count entries))
                (when (seq entries)
                  (println "First entry:" (first entries)))))))
        (println "Error response:" (:body resp))))
    (catch Exception e
      (println "Exception:" (.getMessage e)))))

;; Debug the endpoints that returned nil values
(debug-endpoint (str api-base "/nodes/-root-") "Root Node")
(debug-endpoint (str api-base "/nodes/-root-/children") "Root Children")
(debug-endpoint (str api-base "/nodes/-root-/children?where=(isFolder=true)") "Root Folders")

;; Test the swagger endpoint with different approaches
(println "\n=== Testing Swagger Endpoint Authentication ===")
(let [swagger-url (str alfresco-host "/alfresco/api/swagger.json")]
  (try
    ;; Try without authentication first
    (let [resp (curl/get swagger-url)]
      (println "Without auth - Status:" (:status resp)))

    ;; Try with authentication
    (let [resp (curl/get swagger-url {:basic-auth [alfresco-user alfresco-pass]})]
      (println "With auth - Status:" (:status resp))
      (when (not= 200 (:status resp))
        (println "Response headers:" (:headers resp))))

    (catch Exception e
      (println "Swagger test failed:" (.getMessage e))))

;; Test if API explorer shows the OpenAPI spec location
(println "\n=== Checking API Explorer for OpenAPI location ===")
(try
  (let [resp (curl/get (str alfresco-host "/api-explorer/") {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      (let [body (:body resp)]
        (println "Looking for OpenAPI URL in API Explorer HTML...")
        (when (re-find #"swagger.*\.json|openapi.*\.json" body)
          (println "Found OpenAPI reference in HTML!")
          (doseq [match (re-seq #"[\"']([^\"']*(?:swagger|openapi)[^\"']*\.json)[\"']" body)]
            (println "Possible OpenAPI URL:" (second match)))))
      (println "API Explorer not accessible")))
  (catch Exception e
    (println "API Explorer check failed:" (.getMessage e))))