#!/usr/bin/env bb

;; Check all website folders for content

(require '[babashka.curl :as curl]
         '[cheshire.core :as json])

;; Load configuration
(load-file "src/com/yakread/config/website_nodes.clj")
(alias 'nodes 'com.yakread.config.website-nodes)

;; --- CONFIGURATION ---
(def alfresco-host "http://localhost:8080")
(def alfresco-user "admin")
(def alfresco-pass "admin")
(def api-base (str alfresco-host "/alfresco/api/-default-/public/alfresco/versions/1"))

;; --- HTTP CLIENT ---
(defn get-node-children [node-id]
  (let [resp (curl/get (str api-base "/nodes/" node-id "/children")
                       {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      {:success true :data (json/parse-string (:body resp) true)}
      {:success false :error (:body resp)})))

;; --- CHECK ALL FOLDERS ---
(println "📁 Checking all website folders for content...")
(println)

(let [all-components (nodes/get-all-component-nodes)]
  (doseq [[component-key node-id] all-components]
    (println "📂" component-key)
    (println "   Node ID:" node-id)
    
    (let [children-result (get-node-children node-id)]
      (if (:success children-result)
        (let [entries (get-in children-result [:data :list :entries])
              html-files (filter #(= "text/html" (get-in % [:entry :content :mimeType])) entries)]
          
          (println "   Items:" (count entries))
          (println "   HTML files:" (count html-files))
          
          (when (seq entries)
            (doseq [entry entries]
              (let [file-entry (:entry entry)]
                (println "     📄" (:name file-entry) 
                        "(" (get-in file-entry [:content :mimeType]) ")")))))
        
        (println "   ❌ Could not access folder")))
    
    (println)))

(println "💡 Components with content can be extracted using the image processing system!")