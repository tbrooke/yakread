#!/usr/bin/env bb

;; Simple test to extract your one Feature 1 item

(require '[babashka.curl :as curl]
         '[cheshire.core :as json]
         '[clojure.edn :as edn])

;; Load configuration
(load-file "src/com/yakread/config/website_nodes.clj")
(alias 'nodes 'com.yakread.config.website-nodes)

;; --- CONFIGURATION ---
(def alfresco-host "http://localhost:8080")
(def alfresco-user "admin")
(def alfresco-pass "admin")
(def api-base (str alfresco-host "/alfresco/api/-default-/public/alfresco/versions/1"))

;; --- HTTP CLIENT ---

(defn test-connection []
  (println "🔗 Testing Alfresco connection...")
  (try
    (let [resp (curl/get (str api-base "/nodes/-root-")
                         {:basic-auth [alfresco-user alfresco-pass]})]
      (if (= 200 (:status resp))
        (do
          (println "✅ Alfresco connection successful")
          true)
        (do
          (println "❌ Alfresco connection failed. Status:" (:status resp))
          false)))
    (catch Exception e
      (println "❌ Connection error:" (.getMessage e))
      false)))

(defn get-node-children [node-id]
  (println "📡 Getting children for node:" node-id)
  (let [resp (curl/get (str api-base "/nodes/" node-id "/children")
                       {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      {:success true :data (json/parse-string (:body resp) true)}
      {:success false :error (:body resp)})))

(defn get-node-content [node-id]
  (println "📄 Getting content for node:" node-id)
  (let [resp (curl/get (str api-base "/nodes/" node-id "/content")
                       {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      {:success true :content (:body resp)}
      {:success false :error (:body resp)})))

;; --- MAIN TEST ---

(println "🧪 Simple Feature 1 Content Test")
(println "   Testing your one item in Feature 1 folder")
(println)

;; Test connection
(when (test-connection)
  ;; Get Feature 1 node ID
  (let [feature1-node-id (nodes/get-homepage-component-node-id :feature1)]
    (println "Feature 1 node ID:" feature1-node-id)
    
    ;; Get children
    (let [children-result (get-node-children feature1-node-id)]
      (if (:success children-result)
        (let [entries (get-in children-result [:data :list :entries])]
          (println "✅ Found" (count entries) "items in Feature 1 folder")
          
          ;; Show what we found
          (doseq [entry entries]
            (let [file-entry (:entry entry)]
              (println "   📄" (:name file-entry) 
                      "(" (get-in file-entry [:content :mimeType]) ")")))
          
          ;; Extract HTML content
          (let [html-files (filter #(= "text/html" (get-in % [:entry :content :mimeType])) entries)]
            (if (seq html-files)
              (let [first-html (first html-files)
                    file-entry (:entry first-html)
                    content-result (get-node-content (:id file-entry))]
                
                (if (:success content-result)
                  (let [content-data {:title (:name file-entry)
                                     :html-content (:content content-result)
                                     :last-updated (:modifiedAt file-entry)}]
                    
                    ;; Save for website
                    (spit "mtzuix-feature1-content.edn" (pr-str [content-data]))
                    
                    (println "✅ Content extracted and saved!")
                    (println "   Title:" (:title content-data))
                    (println "   Content length:" (count (:html-content content-data)))
                    (println "   Preview:" (subs (:html-content content-data) 0 100) "...")
                    (println "   Saved to: mtzuix-feature1-content.edn"))
                  
                  (println "❌ Failed to get content:" (:error content-result))))
              
              (println "⚠️ No HTML files found"))))
        
        (println "❌ Could not access Feature 1 folder:" (:error children-result)))))

(println "\n💡 If successful, your content is now cached for the website!")