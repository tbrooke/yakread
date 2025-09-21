#!/usr/bin/env bb

;; Try to find WebScript registry/index

(require '[babashka.curl :as curl])

(def alfresco-host "http://admin.mtzcg.com")
(def alfresco-user "admin")
(def alfresco-pass "admin")

(defn try-index [url]
  (println "\n=== Trying:" url "===")
  (try
    (let [resp (curl/get url {:basic-auth [alfresco-user alfresco-pass]})]
      (if (= 200 (:status resp))
        (do
          (println "SUCCESS!")
          (println (:body resp)))
        (println "Status:" (:status resp))))
    (catch Exception e
      (println "Failed:" (.getMessage e)))))

;; Try various WebScript index endpoints
(try-index (str alfresco-host "/alfresco/service/index"))
(try-index (str alfresco-host "/alfresco/s/index"))
(try-index (str alfresco-host "/alfresco/service/api"))
(try-index (str alfresco-host "/alfresco/service/"))
(try-index (str alfresco-host "/alfresco/service/script/index"))
(try-index (str alfresco-host "/alfresco/service/registry"))

;; Check Share admin console if available
(try-index (str alfresco-host "/share/service/"))

println "\nIf any index is found, it will list available WebScript endpoints"