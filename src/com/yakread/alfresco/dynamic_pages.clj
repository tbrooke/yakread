(ns com.yakread.alfresco.dynamic-pages
  "Dynamic page system using route-driven Alfresco folder resolution"
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [com.yakread.alfresco.route-resolver :as resolver]
   [com.yakread.alfresco.content-processor :as processor]
   [com.yakread.alfresco.client :as alfresco]
   [com.yakread.lib.ui :as ui]
   [com.yakread.util.biff-staging :as biffs]))

;; --- CONFIGURATION ---

(def alfresco-context
  "Default Alfresco context - should be injected from request context in production"
  {:alfresco/base-url "http://localhost:8080"
   :alfresco/username "admin"
   :alfresco/password "admin"})

;; --- CONTENT PROCESSING ---

(defn process-folder-content
  "Process content from an Alfresco folder for web display
   Returns: {:success? boolean :processed-content map :error string}"
  [folder-info]
  (try
    (let [children (:children folder-info [])
          html-files (filter #(and (= "text/html" (:mime-type %))
                                   (= "cm:content" (:type %))) children)
          image-files (filter #(str/starts-with? (str (:mime-type %)) "image/") children)]
      
      (log/info "Processing folder content:"
                "HTML files:" (count html-files)
                "Image files:" (count image-files))
      
      (if (seq html-files)
        ;; Process HTML content
        (let [processed-html-items
              (for [html-file html-files]
                (try
                  (let [content-result (alfresco/get-node-content alfresco-context (:node-id html-file))]
                    (if (:success content-result)
                      (let [html-content (:data content-result)
                            processed-html (processor/process-html-content html-content)]
                        {:success? true
                         :title (:name html-file)
                         :html-content processed-html
                         :original-html html-content
                         :node-id (:node-id html-file)
                         :modified-at (:modified-at html-file)
                         :has-images (processor/has-images? html-content)})
                      {:success? false
                       :error (:error content-result)
                       :node-id (:node-id html-file)}))
                  (catch Exception e
                    {:success? false
                     :error (.getMessage e)
                     :node-id (:node-id html-file)})))
              successful-items (filter :success? processed-html-items)]
          
          {:success? true
           :processed-content {:type :html-collection
                              :items successful-items
                              :folder-name (:name folder-info)
                              :folder-path (:path folder-info)
                              :total-html-files (count html-files)
                              :processed-files (count successful-items)
                              :has-images (some :has-images successful-items)
                              :last-modified (when (seq successful-items)
                                              (apply max (map #(or (:modified-at %) 0) successful-items)))}})
        
        ;; No HTML files, provide folder info
        {:success? true
         :processed-content {:type :folder-info
                            :folder-name (:name folder-info)
                            :folder-path (:path folder-info)
                            :children-count (count children)
                            :has-content false}}))
    
    (catch Exception e
      (log/error "Error processing folder content:" (.getMessage e))
      {:success? false
       :error (.getMessage e)})))

;; --- PAGE RENDERING ---

(defn render-html-collection
  "Render processed HTML content collection"
  [content-data]
  (let [{:keys [items folder-name has-images]} content-data]
    [:div {:class "dynamic-page-content"}
     [:div {:class "content-metadata" 
            :style {:font-size "0.875rem" :color "#6b7280" :margin-bottom "1rem"}}
      (str "📁 " folder-name)
      (when has-images " 📷 Contains images")]
     
     ;; Render each HTML item
     (for [item items]
       [:div {:key (:node-id item) :class "mtz-content-card" :style {:margin-bottom "1.5rem"}}
        (when (> (count items) 1)
          [:h3 {:style {:margin-bottom "1rem"}} (:title item)])
        [:div (biffs/unsafe (:html-content item))]
        
        ;; Show metadata for multi-item collections
        (when (> (count items) 1)
          [:div {:class "item-metadata"
                 :style {:font-size "0.75rem" :color "#9ca3af" :margin-top "1rem" :padding-top "0.5rem" :border-top "1px solid #e5e7eb"}}
           (when (:modified-at item)
             [:span "Last updated: " (:modified-at item)])
           (when (:has-images item)
             [:span {:style {:margin-left "1rem"}} "📷 Images"])])])]))

(defn render-folder-info
  "Render folder information when no HTML content available"
  [content-data]
  (let [{:keys [folder-name folder-path children-count]} content-data]
    [:div {:class "mtz-content-card"}
     [:h3 (str "📁 " folder-name)]
     [:p "This folder is ready for content."]
     [:div {:style {:font-size "0.875rem" :color "#6b7280" :margin-top "1rem"}}
      [:p "Folder location: " folder-path]
      [:p "Total items: " children-count]
      [:p "To add content, upload HTML files to this Alfresco folder."]]]))

(defn render-page-content
  "Render processed page content based on type"
  [processed-content]
  (case (:type processed-content)
    :html-collection (render-html-collection processed-content)
    :folder-info (render-folder-info processed-content)
    [:div {:class "mtz-content-card"}
     [:p "Unknown content type: " (:type processed-content)]]))

;; --- DYNAMIC PAGE HANDLER ---

(defn dynamic-page-handler
  "Generic handler for any route - resolves content dynamically from Alfresco
   This replaces the need for individual page handlers"
  [ctx]
  (let [route-path (:uri ctx)
        page-title (or (get-in ctx [:reitit.core/match :data :page-title])
                      (str/replace (str/replace route-path #"^/" "") #"/" " > ")
                      "Page")]
    
    (log/info "Dynamic page handler for route:" route-path)
    
    (try
      ;; Resolve content using route-driven system
      (let [content-result (resolver/resolve-route-content alfresco-context route-path)]
        (if (:success? content-result)
          ;; Process folder content
          (let [processing-result (process-folder-content (:folder-info content-result))]
            (if (:success? processing-result)
              ;; Render successful content
              (ui/base-page
               (assoc ctx :base/title (str page-title " | Mount Zion UCC"))
               [:div {:class "mtz-app"}
                ;; Header and navigation (to be extracted to shared layout)
                [:header {:class "mtz-header"}
                 [:div {:class "mtz-header-container"}
                  [:h1 {:class "mtz-logo-title"} "Mount Zion United Church of Christ"]
                  [:p {:style {:color "#ffffff" :font-size "1.125rem" :margin-top "0.5rem"}} 
                   "A welcoming faith community since 1979"]]]
                
                [:nav {:class "mtz-nav-menu"}
                 [:div {:style {:display "flex" :justify-content "center" :padding "12px 0" :gap "24px"}}
                  [:a {:href "/" :class "mtz-nav-link"} "Home"]
                  [:a {:href "/about" :class "mtz-nav-link"} "About"]
                  [:a {:href "/worship" :class "mtz-nav-link"} "Worship"]
                  [:a {:href "/activities" :class "mtz-nav-link"} "Activities"]
                  [:a {:href "/events" :class "mtz-nav-link"} "Events"]
                  [:a {:href "/contact" :class "mtz-nav-link"} "Contact"]]]
                
                [:div {:style {:padding "2rem"}}
                 [:h2 {:style {:font-size "1.875rem" :font-weight "bold" :text-align "center" 
                              :margin-bottom "1.5rem" :color "#1f2937"}} 
                  page-title]
                 
                 ;; Dynamic content rendering
                 (render-page-content (:processed-content processing-result))
                 
                 ;; Debug info in development
                 (when (= "dev" (System/getProperty "biff.env.BIFF_PROFILE"))
                   [:div {:class "debug-info" 
                          :style {:margin-top "2rem" :padding "1rem" :background-color "#f3f4f6" 
                                 :border-radius "0.5rem" :font-size "0.875rem" :color "#4b5563"}}
                    [:h4 "🔧 Debug Info"]
                    [:p "Route: " route-path]
                    [:p "Alfresco path: " (:alfresco-path content-result)]
                    [:p "Content type: " (get-in processing-result [:processed-content :type])]])]])
              
              ;; Processing failed
              (ui/base-page
               (assoc ctx :base/title (str page-title " | Mount Zion UCC - Error"))
               [:div {:class "error-page"}
                [:h2 page-title]
                [:p "Error processing content: " (:error processing-result)]])))
          
          ;; Content resolution failed
          (ui/base-page
           (assoc ctx :base/title (str page-title " | Mount Zion UCC - Not Found"))
           [:div {:class "error-page"}
            [:h2 (str page-title " - Page Not Found")]
            [:p "The page you're looking for doesn't exist in Alfresco."]
            [:p "Expected folder location: " (:alfresco-path content-result)]
            [:p "Error: " (:error content-result)]
            [:div {:style {:margin-top "2rem"}}
             [:a {:href "/" :class "mtz-nav-link"} "← Back to Home"]]])))
      
      (catch Exception e
        (log/error "Error in dynamic page handler:" (.getMessage e))
        (ui/base-page
         (assoc ctx :base/title (str page-title " | Mount Zion UCC - Error"))
         [:div {:class "error-page"}
          [:h2 "Technical Error"]
          [:p "We encountered a technical problem loading this page."]
          [:p "Please try again later or contact support if the problem persists."]
          (when (= "dev" (System/getProperty "biff.env.BIFF_PROFILE"))
            [:div {:class "debug-error" 
                   :style {:margin-top "1rem" :padding "1rem" :background-color "#fef2f2" 
                          :border-radius "0.5rem" :font-size "0.875rem" :color "#dc2626"}}
             [:p "Error: " (.getMessage e)]])])))))

;; --- ROUTE GENERATION ---

(defn create-dynamic-route
  "Create a route definition that uses dynamic page handler
   This can replace individual route definitions"
  [route-path & {:keys [page-title route-name]
                 :or {page-title (str/replace (str/replace route-path #"^/" "") #"/" " > ")
                      route-name (keyword (str "dynamic" (str/replace route-path #"[^a-zA-Z0-9]" "-")))}}]
  [route-path
   {:name route-name
    :page-title page-title
    :get dynamic-page-handler}])

;; --- UTILITIES ---

(defn preview-route-content
  "Preview what content would be loaded for a route - useful for debugging"
  [route-path]
  (let [content-result (resolver/resolve-route-content alfresco-context route-path)]
    (if (:success? content-result)
      (let [processing-result (process-folder-content (:folder-info content-result))]
        {:route route-path
         :alfresco-path (:alfresco-path content-result)
         :content-available (:success? processing-result)
         :content-summary (when (:success? processing-result)
                           (select-keys (:processed-content processing-result)
                                       [:type :folder-name :total-html-files :processed-files :has-images]))
         :error (when-not (:success? processing-result)
                  (:error processing-result))})
      {:route route-path
       :alfresco-path (:alfresco-path content-result)
       :content-available false
       :error (:error content-result)})))

(comment
  ;; Test dynamic route resolution
  (preview-route-content "/about")
  (preview-route-content "/worship")
  (preview-route-content "/nonexistent")
  
  ;; Create dynamic routes
  (create-dynamic-route "/about" :page-title "About Us")
  (create-dynamic-route "/worship/services" :page-title "Worship Services")
)