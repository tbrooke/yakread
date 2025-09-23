(ns com.yakread.app.routes
  "Clean, focused routes for Mount Zion UCC website powered by Alfresco"
  (:require
   [com.biffweb :as biff]
   [com.yakread.lib.ui :as ui]
   [com.yakread.alfresco.website-client :as alfresco-client]
   [com.yakread.alfresco.calendar :as calendar]
   [clojure.tools.logging :as log]
   [clojure.java.io :as io]
   [clojure.edn :as edn]
   [com.yakread.util.biff-staging :as biffs]))

;; --- SHARED LAYOUT COMPONENTS ---

(defn site-header []
  [:header {:class "mtz-header"}
   [:div {:class "mtz-header-container"}
    [:h1 {:class "mtz-logo-title"} "Mount Zion United Church of Christ"]
    [:p {:style {:color "#ffffff" :font-size "1.125rem" :margin-top "0.5rem"}} 
     "A welcoming faith community since 1979"]]])

(defn site-navigation []
  [:nav {:class "mtz-nav-menu"}
   [:div {:style {:display "flex" :justify-content "center" :padding "12px 0" :gap "24px"}}
    [:a {:href "/" :class "mtz-nav-link"} "Home"]
    [:a {:href "/about" :class "mtz-nav-link"} "About"]
    [:a {:href "/worship" :class "mtz-nav-link"} "Worship"]
    [:a {:href "/activities" :class "mtz-nav-link"} "Activities"]
    [:a {:href "/events" :class "mtz-nav-link"} "Events"]
    [:a {:href "/contact" :class "mtz-nav-link"} "Contact"]]])

(defn site-layout [ctx title & content]
  (ui/base-page
   (assoc ctx :base/title title)
   [:div {:class "mtz-app"}
    (site-header)
    (site-navigation)
    [:div {:style {:padding "2rem"}}
     content]]))

;; --- CONTENT LOADING ---

(defn load-feature1-content
  "Load Feature 1 content from extracted file"
  []
  (try
    (when (.exists (clojure.java.io/file "mtzuix-feature1-content.edn"))
      (let [content (clojure.edn/read-string (slurp "mtzuix-feature1-content.edn"))
            first-item (first content)]
        (when first-item
          {:title (:title first-item)
           :html-content (:html-content first-item)
           :last-updated (:last-updated first-item)})))
    (catch Exception e
      (log/error "Error loading Feature 1 content:" (.getMessage e))
      nil)))

(defn load-feature2-content
  "Load Feature 2 content from extracted file"
  []
  (try
    (when (.exists (clojure.java.io/file "mtzuix-feature2-website-content.edn"))
      (let [content (clojure.edn/read-string (slurp "mtzuix-feature2-website-content.edn"))
            first-item (first content)]
        (when first-item
          {:title (:title first-item)
           :html-content (:html-content first-item)
           :has-images (:has-images first-item)
           :last-updated (:last-updated first-item)})))
    (catch Exception e
      (log/error "Error loading Feature 2 content:" (.getMessage e))
      nil)))

;; --- ROUTE HANDLERS ---

(defn home-handler
  "Homepage handler - loads real content from Alfresco extractions"
  [ctx]
  (log/info "Loading homepage content from extracted Alfresco files")
  (try
    (let [feature1-content (load-feature1-content)
          feature2-content (load-feature2-content)]
      (site-layout ctx "Mount Zion UCC - Home"
        [:h2 {:style {:font-size "1.875rem" :font-weight "bold" :text-align "center" 
                      :margin-bottom "1.5rem" :color "#1f2937"}} 
         "Welcome to Mount Zion UCC"]
        [:p {:style {:font-size "1.125rem" :text-align "center" 
                     :margin-bottom "1.5rem" :color "#374151"}} 
         "A United Church of Christ congregation"]
        
        ;; Real Alfresco content from Feature 1
        (if feature1-content
          [:div {:class "mtz-content-card"}
           [:h3 (:title feature1-content)]
           [:div (biffs/unsafe (:html-content feature1-content))]
           (when (:last-updated feature1-content)
             [:p {:style {:font-size "0.875rem" :color "#6b7280" :margin-top "1rem"}}
              "Last updated: " (:last-updated feature1-content)])]
          
          ;; Fallback if no content
          [:div {:class "mtz-content-card"}
           [:h3 "Welcome"]
           [:p "Content is being updated. Please check back soon."]])
        
        ;; Real Alfresco content from Feature 2
        (when feature2-content
          [:div {:class "mtz-content-card" :style {:margin-top "2rem"}}
           [:h3 (:title feature2-content)]
           [:div (biffs/unsafe (:html-content feature2-content))]
           (when (:has-images feature2-content)
             [:p {:style {:font-size "0.875rem" :color "#6b7280" :margin-top "1rem"}}
              "📷 Contains images served via proxy"])
           (when (:last-updated feature2-content)
             [:p {:style {:font-size "0.875rem" :color "#6b7280" :margin-top "0.5rem"}}
              "Last updated: " (:last-updated feature2-content)])])))
    
    (catch Exception e
      (log/error "Error loading homepage:" (.getMessage e))
      (site-layout ctx "Mount Zion UCC - Home"
        [:div {:class "error-message"}
         [:h2 "Welcome to Mount Zion UCC"]
         [:p "We're experiencing technical difficulties. Please check back soon."]]))))

(defn page-handler
  "Generic page handler for Alfresco folder-based pages"
  [page-key page-title]
  (fn [ctx]
    (log/info "Loading page content for:" page-key)
    (try
      ;; TODO: Load content from Alfresco using page-key
      ;; (alfresco-client/get-page-content ctx page-key)
      
      (site-layout ctx (str page-title " | Mount Zion UCC")
        [:h2 {:style {:font-size "1.875rem" :font-weight "bold" :text-align "center" 
                      :margin-bottom "1.5rem" :color "#1f2937"}} 
         page-title]
        
        ;; Placeholder content - will be replaced with Alfresco content
        [:div {:class "mtz-content-card"}
         [:h3 (str page-title " Content")]
         [:p (str "Content for " page-title " will be loaded from Alfresco folder: " (name page-key))]])
      
      (catch Exception e
        (log/error "Error loading page" page-key ":" (.getMessage e))
        (site-layout ctx (str page-title " | Mount Zion UCC")
          [:div {:class "error-message"}
           [:h2 page-title]
           [:p "Content temporarily unavailable. Please check back soon."]])))))

(defn events-handler
  "Events page handler - loads calendar events from Alfresco"
  [ctx]
  (log/info "Loading events from Alfresco calendar")
  (try
    ;; TODO: Use actual calendar loading from Alfresco
    ;; (let [events (calendar/get-published-calendar-events ctx)]
    
    (site-layout ctx "Events | Mount Zion UCC"
      [:h2 {:style {:font-size "1.875rem" :font-weight "bold" :text-align "center" 
                    :margin-bottom "1.5rem" :color "#1f2937"}} "Events"]
      
      ;; Placeholder - will be replaced with actual calendar events
      [:div {:class "mtz-content-card"}
       [:h3 "Upcoming Events"]
       [:p "Calendar events will be loaded from Alfresco calendar system."]])
    
    (catch Exception e
      (log/error "Error loading events:" (.getMessage e))
      (site-layout ctx "Events | Mount Zion UCC"
        [:div {:class "error-message"}
         [:h2 "Events"]
         [:p "Events calendar temporarily unavailable. Please check back soon."]]))))

;; --- ROUTE DEFINITIONS ---

(def home-route
  ["/"
   {:name ::home
    :get home-handler}])

(def about-route
  ["/about"
   {:name ::about
    :get (page-handler :about "About Us")}])

(def worship-route
  ["/worship"
   {:name ::worship
    :get (page-handler :worship "Worship")}])

(def activities-route
  ["/activities"
   {:name ::activities
    :get (page-handler :activities "Activities")}])

(def events-route
  ["/events"
   {:name ::events
    :get events-handler}])

(def contact-route
  ["/contact"
   {:name ::contact
    :get (page-handler :contact "Contact Us")}])

;; --- ROUTE COLLECTIONS ---

(def content-routes
  "Main content routes for the site"
  [home-route
   about-route
   worship-route
   activities-route
   events-route
   contact-route])

(def utility-routes
  "Utility routes (privacy, terms, etc.)"
  [["/privacy"
    {:name ::privacy
     :get (page-handler :privacy "Privacy Policy")}]
   
   ["/terms"
    {:name ::terms
     :get (page-handler :terms "Terms of Service")}]])

;; --- MODULE DEFINITION ---

(def module
  "Routes module for Mount Zion UCC website"
  {:routes (concat content-routes utility-routes)})

;; --- ROUTE EVOLUTION HELPERS ---

(defn add-page-route
  "Helper to dynamically add new page routes"
  [path page-key page-title]
  [path
   {:name (keyword (str "mtz-" (name page-key)))
    :get (page-handler page-key page-title)}])

(comment
  ;; Examples of how to evolve routes:
  
  ;; Add a new page
  (add-page-route "/preschool" :preschool "Preschool")
  
  ;; Add a new outreach page
  (add-page-route "/outreach" :outreach "Community Outreach")
  
  ;; This flexibility allows easy addition of new Alfresco folders
  )