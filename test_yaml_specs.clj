#!/usr/bin/env bb

;; Test accessing the YAML specification files found in API Explorer

(require '[babashka.curl :as curl])

(def alfresco-host "http://admin.mtzcg.com")
(def alfresco-user "admin")
(def alfresco-pass "admin")

(def yaml-specs
  ["definitions/alfresco-core.yaml"
   "definitions/alfresco-search.yaml"
   "definitions/alfresco-search-sql.yaml"
   "definitions/alfresco-auth.yaml"
   "definitions/alfresco-discovery.yaml"
   "definitions/alfresco-workflow.yaml"
   "definitions/alfresco-model.yaml"])

(defn test-yaml-spec [spec-path]
  (let [full-url (str alfresco-host "/api-explorer/" spec-path)]
    (println (str "\n=== Testing: " spec-path " ==="))
    (println "URL:" full-url)
    (try
      (let [resp (curl/get full-url {:basic-auth [alfresco-user alfresco-pass]})]
        (cond
          (= 200 (:status resp))
          (do
            (println "✅ SUCCESS!")
            (println "Content preview:")
            (println (subs (:body resp) 0 (min 300 (count (:body resp))))))

          (= 404 (:status resp))
          (println "❌ Not found")

          :else
          (println "⚠️  Status:" (:status resp))))
      (catch Exception e
        (println "❌ Error:" (.getMessage e))))))

(println "Testing YAML specification files found in API Explorer...")

(doseq [spec yaml-specs]
  (test-yaml-spec spec))

;; Also try the base definitions directory
(println "\n=== Testing definitions directory ===")
(try
  (let [resp (curl/get (str alfresco-host "/api-explorer/definitions/")
                       {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      (do
        (println "✅ Definitions directory accessible")
        (println "Directory listing preview:")
        (println (subs (:body resp) 0 (min 500 (count (:body resp))))))
      (println "Status:" (:status resp))))
  (catch Exception e
    (println "Error:" (.getMessage e))))