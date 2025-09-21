#!/usr/bin/env bb

;; Community Edition compatible model sync script
;; Uses WebScript APIs instead of REST APIs

(require '[babashka.curl :as curl]
         '[cheshire.core :as json]
         '[clojure.edn :as edn]
         '[clojure.string :as str]
         '[clojure.java.io :as io])

;; --- CONFIGURATION ---

(def alfresco-host "http://admin.mtzcg.com")
(def alfresco-user "admin")
(def alfresco-pass "admin")

;; Community Edition uses WebScript APIs
(def dictionary-url (str alfresco-host "/alfresco/service/api/dictionary"))
(def classes-url (str alfresco-host "/alfresco/service/api/classes"))
(def namespaces-url (str alfresco-host "/alfresco/service/api/namespaces"))

(def output-dir "generated-model")
(def model-edn (str output-dir "/community-model.edn"))

;; --- UTILS ---

(defn get-json [url]
  (println "Fetching:" url)
  (let [resp (curl/get url {:basic-auth [alfresco-user alfresco-pass] :as :json})]
    (if (= 200 (:status resp))
      (:body resp)
      (do
        (println "Failed to fetch" url "- Status:" (:status resp))
        (println "Response:" (:body resp))
        nil))))

(defn ensure-dir [dir]
  (.mkdirs (io/file dir)))

(defn write-edn [data path]
  (spit path (prn-str data)))

;; --- Community Edition Model Extraction ---

(defn extract-property-type [prop-data]
  "Extract Malli type from Alfresco Community Edition property data"
  (let [data-type (get prop-data "dataType")]
    (case data-type
      "d:text" :string
      "d:mltext" :string
      "d:int" :int
      "d:long" :int
      "d:float" :double
      "d:double" :double
      "d:date" :inst
      "d:datetime" :inst
      "d:boolean" :boolean
      "d:noderef" :string
      "d:qname" :string
      "d:content" :any
      :any)))

(defn process-class-definition [class-data]
  "Convert Community Edition class definition to Malli schema"
  (let [class-name (get class-data "name")
        properties (get class-data "properties" [])]
    [(keyword class-name)
     (into [:map]
           (for [prop properties
                 :let [prop-name (get prop "name")
                       mandatory (get prop "mandatory" false)
                       multi-valued (get prop "multiValued" false)
                       base-type (extract-property-type prop)
                       final-type (if multi-valued [:sequential base-type] base-type)]]
             [(keyword prop-name)
              (if mandatory final-type [:maybe final-type])]))]))

;; --- MAIN ---

(defn -main []
  (ensure-dir output-dir)

  ;; Try to get basic dictionary info
  (println "Fetching data dictionary...")
  (when-let [dictionary (get-json dictionary-url)]
    (println "Dictionary response:" dictionary))

  ;; Try to get content classes
  (println "Fetching content classes...")
  (when-let [classes (get-json classes-url)]
    (println "Classes response:" classes))

  ;; Try to get namespaces
  (println "Fetching namespaces...")
  (when-let [namespaces (get-json namespaces-url)]
    (println "Namespaces response:" namespaces))

  ;; Try specific content model classes
  (println "Fetching specific content types...")
  (doseq [class-name ["cm:content" "cm:folder" "cm:person" "sys:base"]]
    (when-let [class-def (get-json (str dictionary-url "/class/" class-name))]
      (println (str "=== " class-name " ==="))
      (println class-def)))

  (println "\nCommunity Edition limitations:")
  (println "- No OpenAPI specification available")
  (println "- No CMM (Custom Metadata Model) REST APIs")
  (println "- Limited to WebScript APIs")
  (println "- Manual content model extraction required")

  (println "\nTo extract your content model:")
  (println "1. Use WebScript APIs: /alfresco/service/api/dictionary/class/{className}")
  (println "2. Or export content model XML from Alfresco admin tools")
  (println "3. Or check filesystem: <alfresco>/tomcat/shared/classes/alfresco/extension/"))

(-main)