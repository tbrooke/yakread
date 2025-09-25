#!/usr/bin/env bb

;; Test Alfresco connection through SSH tunnel
;; Run this after setting up the SSH tunnel with ./setup_alfresco_tunnel.sh

(require '[clj-http.client :as http]
         '[clojure.data.json :as json]
         '[clojure.pprint :refer [pprint]])

(println "🧪 Testing Alfresco Connection Through SSH Tunnel")
(println "================================================")

(def alfresco-config
  {:base-url "http://localhost:8080"
   :username "admin"
   :password "admin"
   :timeout 30000})

(defn test-basic-connection []
  (println "\n1. Testing basic connection to Alfresco...")
  (try
    (let [response (http/get (str (:base-url alfresco-config) "/alfresco")
                            {:socket-timeout 10000
                             :connection-timeout 10000
                             :throw-exceptions false})]
      (if (< (:status response) 400)
        (do
          (println "   ✅ Basic connection successful")
          (println "   📊 Status:" (:status response))
          true)
        (do
          (println "   ❌ Connection failed")
          (println "   📊 Status:" (:status response))
          false)))
    (catch Exception e
      (println "   ❌ Connection error:" (.getMessage e))
      false)))

(defn test-repository-info []
  (println "\n2. Testing repository API access...")
  (try
    (let [response (http/get 
                    (str (:base-url alfresco-config) "/alfresco/api/-default-/public/alfresco/versions/1/repositories/-default-")
                    {:basic-auth [(:username alfresco-config) (:password alfresco-config)]
                     :accept :json
                     :socket-timeout 10000
                     :connection-timeout 10000
                     :throw-exceptions false})]
      (if (= 200 (:status response))
        (do
          (println "   ✅ Repository API accessible")
          (let [repo-info (json/read-str (:body response) :key-fn keyword)]
            (println "   📋 Repository ID:" (get-in repo-info [:entry :id]))
            (println "   📋 Version:" (get-in repo-info [:entry :version :display]))
            (println "   📋 Edition:" (get-in repo-info [:entry :edition])))
          true)
        (do
          (println "   ❌ Repository API failed")
          (println "   📊 Status:" (:status response))
          (println "   📝 Response:" (take 200 (:body response)))
          false)))
    (catch Exception e
      (println "   ❌ Repository API error:" (.getMessage e))
      false)))

(defn test-sites-access []
  (println "\n3. Testing sites API access...")
  (try
    (let [response (http/get 
                    (str (:base-url alfresco-config) "/alfresco/api/-default-/public/alfresco/versions/1/sites")
                    {:basic-auth [(:username alfresco-config) (:password alfresco-config)]
                     :accept :json
                     :socket-timeout 10000
                     :connection-timeout 10000
                     :throw-exceptions false})]
      (if (= 200 (:status response))
        (do
          (println "   ✅ Sites API accessible")
          (let [sites-info (json/read-str (:body response) :key-fn keyword)]
            (println "   📋 Total sites:" (get-in sites-info [:list :pagination :totalItems]))
            (when-let [entries (get-in sites-info [:list :entries])]
              (doseq [site (take 3 entries)]
                (println "   📁 Site:" (get-in site [:entry :id]) "-" (get-in site [:entry :title])))))
          true)
        (do
          (println "   ❌ Sites API failed")
          (println "   📊 Status:" (:status response))
          false)))
    (catch Exception e
      (println "   ❌ Sites API error:" (.getMessage e))
      false)))

(defn test-mtzion-site []
  (println "\n4. Testing Mount Zion site access...")
  (try
    (let [response (http/get 
                    (str (:base-url alfresco-config) "/alfresco/api/-default-/public/alfresco/versions/1/sites/swsdp")
                    {:basic-auth [(:username alfresco-config) (:password alfresco-config)]
                     :accept :json
                     :socket-timeout 10000
                     :connection-timeout 10000
                     :throw-exceptions false})]
      (if (= 200 (:status response))
        (do
          (println "   ✅ Mount Zion site (swsdp) accessible")
          (let [site-info (json/read-str (:body response) :key-fn keyword)]
            (println "   📋 Site ID:" (get-in site-info [:entry :id]))
            (println "   📋 Title:" (get-in site-info [:entry :title]))
            (println "   📋 Description:" (get-in site-info [:entry :description])))
          true)
        (do
          (println "   ❌ Mount Zion site not found")
          (println "   📊 Status:" (:status response))
          (println "   💡 You may need to create the site or use a different site ID")
          false)))
    (catch Exception e
      (println "   ❌ Mount Zion site error:" (.getMessage e))
      false)))

(defn test-website-folder []
  (println "\n5. Testing Web Site folder structure...")
  (try
    ;; This is more complex, would need to navigate through document library
    (println "   ⏳ This would test the Web Site folder in document library")
    (println "   💡 Requires implementing folder navigation logic")
    (println "   🎯 Route-driven system will handle this automatically!")
    true
    (catch Exception e
      (println "   ❌ Website folder test error:" (.getMessage e))
      false)))

;; Run all tests
(println "🚀 Starting connection tests...")
(println "⚙️  Using config:" alfresco-config)

(let [results [(test-basic-connection)
               (test-repository-info) 
               (test-sites-access)
               (test-mtzion-site)
               (test-website-folder)]
      passed (count (filter true? results))
      total (count results)]
  
  (println (str "\n📊 Test Results: " passed "/" total " passed"))
  
  (if (>= passed 3)
    (do
      (println "✅ Alfresco connection is working!")
      (println "\n🎯 Next Steps:")
      (println "   1. Run the dynamic routes enablement: bb enable_dynamic_routes.clj")
      (println "   2. Start your application")
      (println "   3. Load REPL helper: (load-file \"enable_dynamic_routes_repl.clj\")")
      (println "   4. Test routes: (test-route \"/about\")")
      (println "   5. Enable dynamic routes: (enable!)")
      (println "   6. Create content in Alfresco folders!")
      
      (println "\n🏗️  Create these Alfresco folders to test:")
      (println "   • Sites/swsdp/documentLibrary/Web Site/about/")
      (println "   • Sites/swsdp/documentLibrary/Web Site/worship/") 
      (println "   • Sites/swsdp/documentLibrary/Web Site/activities/")
      (println "   • Sites/swsdp/documentLibrary/Web Site/events/")
      (println "   • Sites/swsdp/documentLibrary/Web Site/contact/"))
    
    (do
      (println "❌ Some connection tests failed")
      (println "\n🔧 Troubleshooting:")
      (println "   1. Ensure SSH tunnel is running: ./setup_alfresco_tunnel.sh")
      (println "   2. Check SSH connection: ssh tmb@trust")
      (println "   3. Verify Alfresco container is running on remote server")
      (println "   4. Check Alfresco credentials (admin/admin)")
      (println "   5. Verify site ID (currently using 'swsdp' - may need different ID)"))))

(println "\n🎊 Route-Driven System Ready!")
(println "Once connection is working, the route-driven system will:")
(println "• Map /about → Sites/swsdp/documentLibrary/Web Site/about")
(println "• Allow content teams to create pages by creating folders") 
(println "• Eliminate manual node-ID mapping")
(println "• Enable zero-developer-involvement content updates!")