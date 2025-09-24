#!/usr/bin/env bb

;; Test what folders exist in Alfresco for dynamic routing
;; This will show us what pages we can already create

(require '[babashka.curl :as curl]
         '[cheshire.core :as json])

;; Alfresco connection (using SSH tunnel)
(def alfresco-host "http://localhost:8080")
(def alfresco-user "admin")
(def alfresco-pass "admin")
(def api-base (str alfresco-host "/alfresco/api/-default-/public/alfresco/versions/1"))

(println "🔍 Testing Alfresco Folder Structure for Dynamic Routes")
(println "====================================================")

;; Check what folders exist under the Web Site root
(defn get-website-root-folders []
  (println "🔗 Connecting to Alfresco via SSH tunnel...")
  (try
    ;; First, let's find the Web Site folder
    (let [search-response (curl/get (str api-base "/queries/nodes")
                                   {:basic-auth [alfresco-user alfresco-pass]
                                    :query-params {:term "Web Site"
                                                   :nodeType "cm:folder"}})
          search-data (json/parse-string (:body search-response) true)]
      
      (if (= 200 (:status search-response))
        (let [entries (get-in search-data [:list :entries])
              web-site-folders (filter #(= "Web Site" (get-in % [:entry :name])) entries)]
          
          (if (seq web-site-folders)
            (let [web-site-folder (first web-site-folders)
                  folder-id (get-in web-site-folder [:entry :id])]
              
              (println "✅ Found Web Site folder with ID:" folder-id)
              
              ;; Now get its children
              (let [children-response (curl/get (str api-base "/nodes/" folder-id "/children")
                                               {:basic-auth [alfresco-user alfresco-pass]})
                    children-data (json/parse-string (:body children-response) true)]
                
                (if (= 200 (:status children-response))
                  (let [child-entries (get-in children-data [:list :entries])
                        folders (filter #(get-in % [:entry :isFolder]) child-entries)]
                    
                    (println "\n📁 Available folders for dynamic routes:")
                    (doseq [folder folders]
                      (let [folder-name (get-in folder [:entry :name])
                            folder-id (get-in folder [:entry :id])
                            route-path (str "/" (clojure.string/lower-case folder-name))]
                        (println (str "   " folder-name " → " route-path " (ID: " folder-id ")"))))
                    
                    (println "\n🚀 These routes should work with dynamic routing:")
                    (doseq [folder folders]
                      (let [folder-name (get-in folder [:entry :name])
                            route-path (str "/" (clojure.string/lower-case folder-name))]
                        (println (str "   http://localhost:4000" route-path))))
                    
                    folders)
                  
                  (do
                    (println "❌ Could not get children of Web Site folder")
                    (println "Status:" (:status children-response))
                    []))))
            
            (do
              (println "❌ Web Site folder not found in search results")
              (println "Available folders:")
              (doseq [entry entries]
                (println "   " (get-in entry [:entry :name])))
              [])))
        
        (do
          (println "❌ Search failed. Status:" (:status search-response))
          [])))
    
    (catch Exception e
      (println "❌ Error connecting to Alfresco:" (.getMessage e))
      [])))

;; Test specific folders that should exist
(defn test-expected-folders []
  (println "\n🧪 Testing expected folders...")
  (let [expected-routes ["/about" "/worship" "/activities" "/events" "/contact"]]
    (doseq [route expected-routes]
      (let [folder-path (str "Sites/swsdp/documentLibrary/Web Site" (subs route 1))]
        (println (str "   " route " → " folder-path))))))

(defn create-test-folder []
  (println "\n🛠️  Want to create a test folder?")
  (println "   You can create a folder called 'test-page' in:")
  (println "   Sites/swsdp/documentLibrary/Web Site/test-page")
  (println "   Then add an HTML file to it, and it will appear at:")
  (println "   http://localhost:4000/test-page"))

;; Run the tests
(get-website-root-folders)
(test-expected-folders) 
(create-test-folder)

(println "\n✅ Alfresco folder test complete!")
(println "Now restart your Yakread server to enable dynamic routes.")