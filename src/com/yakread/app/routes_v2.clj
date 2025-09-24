(ns com.yakread.app.routes-v2
  "Route-driven dynamic website system - Evolution of Mount Zion UCC routing
   Single source of truth: Reitit routes drive Alfresco folder resolution"
  (:require
   [com.yakread.alfresco.dynamic-pages :as dynamic-pages]
   [com.yakread.alfresco.route-resolver :as resolver]
   [com.yakread.app.image-proxy :as image-proxy]
   [clojure.tools.logging :as log]))

;; --- ROUTE-DRIVEN DYNAMIC PAGES ---
;; These routes automatically resolve to Alfresco folders using the pattern:
;; /route-path → Sites/swsdp/documentLibrary/Web Site/route-path

(def dynamic-content-routes
  "Main content routes that use dynamic Alfresco folder resolution"
  [(dynamic-pages/create-dynamic-route "/" :page-title "Home" :route-name ::home)
   (dynamic-pages/create-dynamic-route "/about" :page-title "About Us" :route-name ::about)
   (dynamic-pages/create-dynamic-route "/worship" :page-title "Worship" :route-name ::worship)
   (dynamic-pages/create-dynamic-route "/activities" :page-title "Activities" :route-name ::activities)
   (dynamic-pages/create-dynamic-route "/events" :page-title "Events" :route-name ::events)
   (dynamic-pages/create-dynamic-route "/contact" :page-title "Contact Us" :route-name ::contact)])

;; --- UTILITY ROUTES ---
;; These can also be made dynamic, or kept static for legal/policy pages

(def utility-routes
  "Utility routes (privacy, terms, etc.) - can be static or dynamic"
  [(dynamic-pages/create-dynamic-route "/privacy" :page-title "Privacy Policy" :route-name ::privacy)
   (dynamic-pages/create-dynamic-route "/terms" :page-title "Terms of Service" :route-name ::terms)])

;; --- NESTED/HIERARCHICAL ROUTES ---
;; Examples of how the system handles nested paths

(def nested-route-examples
  "Examples of nested routes that map to nested Alfresco folders"
  [(dynamic-pages/create-dynamic-route "/worship/services" 
                                      :page-title "Worship Services" 
                                      :route-name ::worship-services)
   (dynamic-pages/create-dynamic-route "/worship/music" 
                                      :page-title "Music Ministry" 
                                      :route-name ::worship-music)
   (dynamic-pages/create-dynamic-route "/events/calendar" 
                                      :page-title "Event Calendar" 
                                      :route-name ::events-calendar)
   (dynamic-pages/create-dynamic-route "/activities/youth" 
                                      :page-title "Youth Programs" 
                                      :route-name ::activities-youth)
   (dynamic-pages/create-dynamic-route "/outreach/community" 
                                      :page-title "Community Outreach" 
                                      :route-name ::outreach-community)])

;; --- CATCH-ALL DYNAMIC ROUTE ---
;; This handles any route that wasn't explicitly defined above

(def catch-all-route
  "Catch-all route for any path - attempts dynamic resolution"
  ["*path"
   {:name ::catch-all-dynamic
    :get (fn [ctx]
           (let [requested-path (:uri ctx)]
             (log/info "Catch-all dynamic route handling:" requested-path)
             ;; Use the dynamic page handler with the requested path
             (dynamic-pages/dynamic-page-handler ctx)))}])

;; --- ALL ROUTES ---

(def all-routes
  "Complete route collection using dynamic system"
  (concat dynamic-content-routes
          utility-routes
          ;; Optionally include nested examples (comment out if not needed)
          ;; nested-route-examples
          ;; Image proxy routes (essential for image functionality)
          (:routes image-proxy/module)
          ;; Catch-all must be last
          [catch-all-route]))

;; --- MODULE DEFINITION ---

(def module
  "Dynamic routes module - replaces the static routes system"
  {:routes all-routes})

;; --- ROUTE DISCOVERY AND MANAGEMENT ---

(defn discover-available-routes
  "Discover what routes are available by scanning Alfresco folder structure"
  []
  (log/info "Discovering available routes from Alfresco...")
  (try
    (resolver/discover-routes-from-website-root
     {:base-url "http://localhost:8080" :username "admin" :password "admin"})
    (catch Exception e
      (log/warn "Could not discover routes from Alfresco:" (.getMessage e))
      [])))

(defn validate-route-content
  "Validate that a route has accessible content in Alfresco"
  [route-path]
  (try
    (let [result (resolver/resolve-route-content
                  {:base-url "http://localhost:8080" :username "admin" :password "admin"}
                  route-path)]
      {:route route-path
       :valid? (:success? result)
       :error (:error result)
       :alfresco-path (:alfresco-path result)})
    (catch Exception e
      {:route route-path
       :valid? false
       :error (.getMessage e)})))

(defn generate-navigation-menu
  "Generate navigation menu from route definitions - future enhancement"
  [routes]
  ;; Extract main routes (not nested, not utility)
  (let [main-routes (filter #(and (string? (first %))
                                 (not (str/includes? (first %) "/"))
                                 (not= (first %) "/")
                                 (not= (first %) "*path")) routes)]
    (map (fn [[route-path route-config]]
           {:path route-path
            :title (or (:page-title route-config)
                      (str/replace (str/replace route-path #"^/" "") #"-" " "))
            :name (:name route-config)}) 
         main-routes)))

;; --- DEVELOPMENT HELPERS ---

(defn preview-all-routes
  "Preview content availability for all defined routes - development tool"
  []
  (let [route-paths (map first (filter #(and (string? (first %))
                                            (not= (first %) "*path")) all-routes))]
    (map dynamic-pages/preview-route-content route-paths)))

(defn route-status-report
  "Generate a status report of all routes and their Alfresco content"
  []
  {:timestamp (java.time.Instant/now)
   :total-routes (count all-routes)
   :route-previews (preview-all-routes)
   :discovered-routes (discover-available-routes)})

;; --- MIGRATION HELPERS ---

(defn compare-with-legacy-routes
  "Compare new dynamic routes with legacy static routes for migration validation"
  [legacy-routes]
  (let [legacy-paths (set (map first (filter vector? legacy-routes)))
        dynamic-paths (set (map first (filter #(and (vector? %) (string? (first %))) all-routes)))]
    {:legacy-paths legacy-paths
     :dynamic-paths dynamic-paths
     :missing-in-dynamic (clojure.set/difference legacy-paths dynamic-paths)
     :new-in-dynamic (clojure.set/difference dynamic-paths legacy-paths)
     :common-paths (clojure.set/intersection legacy-paths dynamic-paths)}))

(comment
  ;; Development and testing
  
  ;; Preview what content is available
  (preview-all-routes)
  
  ;; Check specific route
  (dynamic-pages/preview-route-content "/about")
  (dynamic-pages/preview-route-content "/worship")
  
  ;; Discover what's actually in Alfresco
  (discover-available-routes)
  
  ;; Get full status report
  (route-status-report)
  
  ;; Test route validation
  (validate-route-content "/about")
  (validate-route-content "/nonexistent")
  
  ;; Navigation menu generation
  (generate-navigation-menu all-routes)
)