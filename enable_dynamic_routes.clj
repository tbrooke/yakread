#!/usr/bin/env bb

;; Script to safely enable the dynamic routes system
;; This implements the migration path described in ROUTE_DRIVEN_IMPLEMENTATION.md

(require '[clojure.java.io :as io])

(println "🚀 Enabling Dynamic Routes System")
(println "=================================")

(println "\n1. Current Routes Configuration:")
(let [routes-file "src/com/yakread/app/routes.clj"
      content (slurp routes-file)]
  (if (clojure.string/includes? content ";; [com.yakread.app.routes-migration")
    (println "   ❌ Migration imports are commented out")
    (println "   ✅ Migration imports are active")))

(println "\n2. Activating Migration System...")

;; Step 1: Uncomment the migration import in routes.clj
(let [routes-file "src/com/yakread/app/routes.clj"
      content (slurp routes-file)
      updated-content (-> content
                         (clojure.string/replace 
                           ";; Migration system imports (temporarily disabled)\n   ;; [com.yakread.app.routes-migration :as migration]"
                           "Migration system imports (now active)\n   [com.yakread.app.routes-migration :as migration]"))]
  (if (not= content updated-content)
    (do
      (spit routes-file updated-content)
      (println "   ✅ Uncommented migration import in routes.clj"))
    (println "   ✅ Migration import already active")))

;; Step 2: Update the module definition to use migration
(let [routes-file "src/com/yakread/app/routes.clj"
      content (slurp routes-file)
      updated-content (-> content
                         (clojure.string/replace 
                           "(def module\n  \"Routes module - using static routes for now\"\n  static-routes-module)"
                           "(def module\n  \"Routes module - dynamically switches between static and dynamic\"\n  (migration/get-active-routes-module))"))]
  (if (not= content updated-content)
    (do
      (spit routes-file updated-content)
      (println "   ✅ Updated module definition to use migration system"))
    (println "   ✅ Module definition already using migration system")))

(println "\n3. Testing Route Conversion...")
(def alfresco-website-root "Sites/swsdp/documentLibrary/Web Site")
(defn route->alfresco-path [route-path]
  (let [normalized-path (-> route-path
                           (clojure.string/replace #"^/" "")
                           (clojure.string/replace #"/$" ""))
        path-segments (if (empty? normalized-path)
                       []
                       (clojure.string/split normalized-path #"/"))]
    (str alfresco-website-root 
         (when (seq path-segments)
           (str "/" (clojure.string/join "/" path-segments))))))

(let [test-routes ["/about" "/worship" "/activities" "/events" "/contact"]]
  (doseq [route test-routes]
    (println (str "   " route " → " (route->alfresco-path route)))))

(println "\n4. Creating REPL Helper Script...")
(spit "enable_dynamic_routes_repl.clj"
      "(require '[com.yakread.app.routes-migration :as migration])
(require '[com.yakread.alfresco.dynamic-pages :as dynamic])
(require '[com.yakread.alfresco.route-resolver :as resolver])

(println \"🎯 Dynamic Routes REPL Helper Loaded\")
(println \"====================================\")

;; Check current status
(println \"\\n📊 Current Status:\")
(println \"Migration status:\" (migration/get-migration-status))

;; Enable dynamic routes
(defn enable! []
  (println \"\\n🔄 Enabling dynamic routes...\")
  (migration/enable-dynamic-routes!)
  (println \"✅ Dynamic routes enabled!\")
  (println \"📋 Status:\" (migration/get-migration-status)))

;; Disable dynamic routes (fallback)
(defn disable! []
  (println \"\\n⏪ Disabling dynamic routes (fallback)...\")
  (migration/disable-dynamic-routes!)
  (println \"✅ Fallen back to static routes\")
  (println \"📋 Status:\" (migration/get-migration-status)))

;; Test a specific route
(defn test-route [route-path]
  (println (str \"\\n🧪 Testing route: \" route-path))
  (let [result (dynamic/preview-route-content route-path)]
    (clojure.pprint/pprint result)))

;; Quick tests
(defn test-all []
  (println \"\\n🧪 Testing all main routes...\")
  (doseq [route [\"/about\" \"/worship\" \"/activities\" \"/events\" \"/contact\"]]
    (test-route route)))

(println \"\\n📖 Available Commands:\")
(println \"  (enable!)           - Enable dynamic routes\")
(println \"  (disable!)          - Disable dynamic routes (fallback)\")
(println \"  (test-route \\\"/about\\\") - Test specific route\")
(println \"  (test-all)          - Test all main routes\")
(println \"\\n⚡ Ready! Run (enable!) to activate dynamic routes\")")

(println "   ✅ Created enable_dynamic_routes_repl.clj")

(println "\n✅ Dynamic Routes System Prepared!")
(println "\n🎯 Next Steps:")
(println "   1. Restart your application to load the migration system")
(println "   2. In a REPL, run: (load-file \"enable_dynamic_routes_repl.clj\")")
(println "   3. Test routes: (test-route \"/about\")")
(println "   4. Enable dynamic routes: (enable!)")
(println "   5. If issues occur, quickly fallback: (disable!)")

(println "\n🏗️  Alfresco Content Requirements:")
(println "   • Create folder: Sites/swsdp/documentLibrary/Web Site/about/")
(println "   • Create folder: Sites/swsdp/documentLibrary/Web Site/worship/")
(println "   • Create folder: Sites/swsdp/documentLibrary/Web Site/activities/")
(println "   • Create folder: Sites/swsdp/documentLibrary/Web Site/events/")
(println "   • Create folder: Sites/swsdp/documentLibrary/Web Site/contact/")
(println "   • Upload HTML files to these folders")
(println "   • Pages will automatically be available!")

(println "\n🎊 Content Team Empowerment:")
(println "   Once enabled, content teams can create new pages by just:")
(println "   • Creating folders in Sites/swsdp/documentLibrary/Web Site/")
(println "   • Uploading HTML content")
(println "   • NO developer involvement needed!")