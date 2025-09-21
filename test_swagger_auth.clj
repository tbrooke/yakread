#!/usr/bin/env bb

;; Test different authentication methods for Swagger endpoint

(require '[babashka.curl :as curl]
         '[cheshire.core :as json])

(def alfresco-host "http://admin.mtzcg.com")
(def alfresco-user "admin")
(def alfresco-pass "admin")
(def swagger-url (str alfresco-host "/alfresco/api/swagger.json"))

(defn test-auth-method [description options]
  (println (str "\n=== " description " ==="))
  (try
    (let [resp (curl/get swagger-url options)]
      (println "Status:" (:status resp))
      (when (not= 200 (:status resp))
        (println "Headers:" (select-keys (:headers resp) ["www-authenticate" "content-type"]))
        (println "Response preview:" (subs (:body resp) 0 (min 200 (count (:body resp))))))
      (when (= 200 (:status resp))
        (println "✅ SUCCESS! Swagger JSON retrieved")
        (let [swagger (json/parse-string (:body resp) true)]
          (println "Swagger version:" (:swagger swagger))
          (println "API info:" (get-in swagger [:info :title])))))
    (catch Exception e
      (println "Error:" (.getMessage e)))))

;; Test different authentication approaches
(test-auth-method "No authentication" {})

(test-auth-method "Basic auth (current method)"
                  {:basic-auth [alfresco-user alfresco-pass]})

(test-auth-method "Basic auth with headers"
                  {:basic-auth [alfresco-user alfresco-pass]
                   :headers {"Accept" "application/json"
                             "Content-Type" "application/json"}})

(test-auth-method "Form auth simulation"
                  {:headers {"Authorization" (str "Basic "
                                                  (.encodeToString
                                                   (java.util.Base64/getEncoder)
                                                   (.getBytes (str alfresco-user ":" alfresco-pass))))}})

;; Try alternative swagger paths that might have different auth requirements
(println "\n=== Testing Alternative Swagger Paths ===")

(def alt-swagger-urls
  [(str alfresco-host "/alfresco/swagger.json")
   (str alfresco-host "/alfresco/api-explorer/swagger.json")
   (str alfresco-host "/alfresco/service/api/swagger.json")
   (str alfresco-host "/swagger.json")
   (str alfresco-host "/api/swagger.json")])

(doseq [url alt-swagger-urls]
  (println "\nTrying:" url)
  (try
    (let [resp (curl/get url {:basic-auth [alfresco-user alfresco-pass]})]
      (if (= 200 (:status resp))
        (println "✅ SUCCESS at" url)
        (println "Status:" (:status resp))))
    (catch Exception e
      (println "Failed:" (.getMessage e)))))

;; Check if we can access the API explorer web interface
(println "\n=== Testing API Explorer Web Interface ===")
(try
  (let [resp (curl/get (str alfresco-host "/api-explorer/")
                       {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      (do
        (println "✅ API Explorer accessible")
        (println "Looking for Swagger config...")
        (when-let [swagger-refs (re-seq #"['\"]([^'\"]*swagger[^'\"]*\.json)['\"]" (:body resp))]
          (doseq [[_ url] swagger-refs]
            (println "Found Swagger reference:" url))))
      (println "API Explorer status:" (:status resp))))
  (catch Exception e
    (println "API Explorer error:" (.getMessage e))))