(require '[com.yakread.app.routes-migration :as migration])
(require '[com.yakread.alfresco.dynamic-pages :as dynamic])
(require '[com.yakread.alfresco.route-resolver :as resolver])

(println "🎯 Dynamic Routes REPL Helper Loaded")
(println "====================================")

;; Check current status
(println "\n📊 Current Status:")
(println "Migration status:" (migration/get-migration-status))

;; Enable dynamic routes
(defn enable! []
  (println "\n🔄 Enabling dynamic routes...")
  (migration/enable-dynamic-routes!)
  (println "✅ Dynamic routes enabled!")
  (println "📋 Status:" (migration/get-migration-status)))

;; Disable dynamic routes (fallback)
(defn disable! []
  (println "\n⏪ Disabling dynamic routes (fallback)...")
  (migration/disable-dynamic-routes!)
  (println "✅ Fallen back to static routes")
  (println "📋 Status:" (migration/get-migration-status)))

;; Test a specific route
(defn test-route [route-path]
  (println (str "\n🧪 Testing route: " route-path))
  (let [result (dynamic/preview-route-content route-path)]
    (clojure.pprint/pprint result)))

;; Quick tests
(defn test-all []
  (println "\n🧪 Testing all main routes...")
  (doseq [route ["/about" "/worship" "/activities" "/events" "/contact"]]
    (test-route route)))

(println "\n📖 Available Commands:")
(println "  (enable!)           - Enable dynamic routes")
(println "  (disable!)          - Disable dynamic routes (fallback)")
(println "  (test-route \"/about\") - Test specific route")
(println "  (test-all)          - Test all main routes")
(println "\n⚡ Ready! Run (enable!) to activate dynamic routes")