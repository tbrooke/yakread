(ns com.yakread.app.routes-migration
  "Migration helper to switch between static and dynamic routing systems"
  (:require
   [com.yakread.app.routes :as routes-v1]
   [com.yakread.app.routes-v2 :as routes-v2]
   [clojure.tools.logging :as log]))

;; --- CONFIGURATION ---

(def ^:dynamic *use-dynamic-routes* 
  "Feature flag to enable dynamic routes system"
  ;; Start with false for safety, switch to true when ready to test
  false)

;; --- ROUTE SELECTION ---

(defn get-active-routes-module
  "Get the active routes module based on configuration"
  []
  (if *use-dynamic-routes*
    (do
      (log/info "Using dynamic routes system (v2)")
      routes-v2/module)
    (do
      (log/info "Using static routes system (v1)")
      routes-v1/module)))

;; --- MIGRATION HELPERS ---

(defn compare-route-coverage
  "Compare route coverage between v1 and v2 systems"
  []
  (let [v1-routes (set (map first (filter vector? (:routes routes-v1/module))))
        v2-routes (set (map first (filter vector? (:routes routes-v2/module))))]
    {:v1-routes v1-routes
     :v2-routes v2-routes
     :missing-in-v2 (clojure.set/difference v1-routes v2-routes)
     :new-in-v2 (clojure.set/difference v2-routes v1-routes)
     :common-routes (clojure.set/intersection v1-routes v2-routes)}))

(defn enable-dynamic-routes!
  "Enable dynamic routes system"
  []
  (log/info "Enabling dynamic routes system")
  (alter-var-root #'*use-dynamic-routes* (constantly true)))

(defn disable-dynamic-routes!
  "Disable dynamic routes system (fall back to static)"
  []
  (log/info "Disabling dynamic routes system (using static routes)")
  (alter-var-root #'*use-dynamic-routes* (constantly false)))

(defn get-migration-status
  "Get current migration status"
  []
  {:dynamic-routes-enabled *use-dynamic-routes*
   :active-system (if *use-dynamic-routes* :dynamic :static)
   :route-comparison (compare-route-coverage)})

;; --- TESTING HELPERS ---

(defn test-dynamic-routes
  "Test dynamic routes system without switching over permanently"
  []
  (log/info "Testing dynamic routes system...")
  (try
    ;; Temporarily enable dynamic routes
    (binding [*use-dynamic-routes* true]
      (let [test-routes ["/about" "/worship" "/activities" "/events" "/contact"]
            test-results (map (fn [route]
                               (try
                                 {:route route
                                  :available true
                                  :preview "Preview not available in migration test"}
                                 (catch Exception e
                                   {:route route
                                    :available false
                                    :error (.getMessage e)}))) test-routes)]
        {:test-completed true
         :routes-tested (count test-routes)
         :results test-results}))
    (catch Exception e
      {:test-completed false
       :error (.getMessage e)})))

;; --- GRADUAL MIGRATION ---

(defn create-hybrid-routes
  "Create a hybrid routing system - some routes dynamic, some static"
  [dynamic-route-paths]
  (let [dynamic-routes (filter #(contains? (set dynamic-route-paths) (first %))
                              (:routes routes-v2/module))
        static-routes (filter #(not (contains? (set dynamic-route-paths) (first %)))
                             (:routes routes-v1/module))]
    {:routes (concat dynamic-routes static-routes)}))

(comment
  ;; Migration workflow:
  
  ;; 1. Check current status
  (get-migration-status)
  
  ;; 2. Test dynamic routes without switching
  (test-dynamic-routes)
  
  ;; 3. Enable dynamic routes when ready
  (enable-dynamic-routes!)
  
  ;; 4. If issues, fall back quickly
  (disable-dynamic-routes!)
  
  ;; 5. Gradual migration - enable specific routes
  (create-hybrid-routes ["/about" "/worship"])
)