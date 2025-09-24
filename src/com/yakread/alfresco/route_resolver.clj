(ns com.yakread.alfresco.route-resolver
  "Dynamic route-to-Alfresco-folder resolution system
   Converts Reitit routes to Alfresco folder paths dynamically"
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [com.yakread.lib.alfresco :as alfresco-lib]
   [malli.core :as m]))

;; --- ROUTE-TO-PATH CONVERSION ---

(def alfresco-website-root
  "Root path for website content in Alfresco"
  "Sites/swsdp/documentLibrary/Web Site")

(defn route->alfresco-path
  "Convert a route path to an Alfresco folder path
   Examples:
   /about → Sites/swsdp/documentLibrary/Web Site/about  
   /worship/services → Sites/swsdp/documentLibrary/Web Site/worship/services
   /events/calendar → Sites/swsdp/documentLibrary/Web Site/events/calendar"
  [route-path]
  (let [normalized-path (-> route-path
                           (str/replace #"^/" "")  ; Remove leading slash
                           (str/replace #"/$" "")) ; Remove trailing slash
        path-segments (if (empty? normalized-path)
                       []
                       (str/split normalized-path #"/"))]
    (str alfresco-website-root 
         (when (seq path-segments)
           (str "/" (str/join "/" path-segments))))))

(defn alfresco-path->route
  "Convert an Alfresco folder path back to a route path
   Example: Sites/swsdp/documentLibrary/Web Site/about → /about"
  [alfresco-path]
  (if (str/starts-with? alfresco-path alfresco-website-root)
    (let [suffix (subs alfresco-path (count alfresco-website-root))
          clean-suffix (str/replace suffix #"^/" "")]
      (if (empty? clean-suffix)
        "/"
        (str "/" clean-suffix)))
    nil)) ; Invalid path

;; --- FOLDER STRUCTURE VALIDATION ---

(def content-folder-schema
  "Malli schema for validating Alfresco content folder structure"
  [:map
   [:exists? :boolean]
   [:type [:= "cm:folder"]]
   [:name :string]
   [:path :string]
   [:node-id :string]
   [:children {:optional true}
    [:vector
     [:map
      [:type [:enum "cm:content" "cm:folder"]]
      [:name :string]
      [:mime-type {:optional true} :string]
      [:node-id :string]]]]])

(defn validate-folder-structure
  "Validate that an Alfresco folder matches expected content structure"
  [folder-info]
  (if (m/validate content-folder-schema folder-info)
    {:valid? true :folder-info folder-info}
    {:valid? false 
     :errors (m/explain content-folder-schema folder-info)
     :folder-info folder-info}))

;; --- DYNAMIC FOLDER RESOLUTION ---

(defn resolve-folder-by-path
  "Resolve Alfresco folder by path, with caching and validation
   Returns: {:success? boolean :folder-info map :error string}"
  ([alfresco-config alfresco-path]
   (resolve-folder-by-path alfresco-config alfresco-path {}))
  ([alfresco-config alfresco-path opts]
   (log/info "Resolving Alfresco folder:" alfresco-path)
   (try
     ;; First, try to find the folder by path
     (let [folder-result (alfresco-lib/find-folder-by-path-with-children alfresco-config alfresco-path)]
       (if (:success folder-result)
         (let [folder-info (:folder folder-result)
               validation (validate-folder-structure folder-info)]
           (if (:valid? validation)
             {:success? true 
              :folder-info folder-info
              :validation validation
              :alfresco-path alfresco-path}
             {:success? false
              :error "Folder structure validation failed"
              :validation validation
              :alfresco-path alfresco-path}))
         {:success? false
          :error (:error folder-result "Unknown error")
          :alfresco-path alfresco-path}))
     (catch Exception e
       (log/error "Error resolving folder by path:" alfresco-path "Error:" (.getMessage e))
       {:success? false
        :error (.getMessage e)
        :alfresco-path alfresco-path}))))

(defn resolve-route-content
  "Resolve content for a route by converting to Alfresco path and fetching folder
   Returns: {:success? boolean :route string :content map :error string}"
  [alfresco-config route-path]
  (log/info "Resolving content for route:" route-path)
  (let [alfresco-path (route->alfresco-path route-path)
        folder-result (resolve-folder-by-path alfresco-config alfresco-path)]
    (if (:success? folder-result)
      {:success? true
       :route route-path
       :alfresco-path alfresco-path
       :folder-info (:folder-info folder-result)
       :content (:folder-info folder-result)} ; For now, folder-info is the content
      {:success? false
       :route route-path
       :alfresco-path alfresco-path
       :error (:error folder-result)})))

;; --- ROUTE GENERATION HELPERS ---

(defn generate-route-from-folder
  "Generate a route definition from an Alfresco folder
   Useful for auto-discovering new routes from folder structure"
  [folder-info]
  (when-let [route-path (alfresco-path->route (:path folder-info))]
    {:route-path route-path
     :folder-info folder-info
     :route-name (keyword (str "dynamic-" (str/replace route-path #"[^a-zA-Z0-9]" "-")))
     :page-title (or (:title folder-info) (:name folder-info))}))

(defn discover-routes-from-website-root
  "Discover all possible routes by scanning the website root folder
   Returns vector of route definitions"
  [alfresco-config]
  (log/info "Discovering routes from Alfresco website root")
  (try
    (let [root-result (resolve-folder-by-path alfresco-config alfresco-website-root)]
      (if (:success? root-result)
        (let [root-children (get-in root-result [:folder-info :children] [])
              folder-children (filter #(= "cm:folder" (:type %)) root-children)]
          (keep generate-route-from-folder folder-children))
        (do
          (log/warn "Could not discover routes - website root not accessible:" (:error root-result))
          [])))
    (catch Exception e
      (log/error "Error discovering routes:" (.getMessage e))
      [])))

;; --- UTILITIES ---

(defn route-path-valid?
  "Check if a route path is valid for Alfresco folder resolution"
  [route-path]
  (and (string? route-path)
       (str/starts-with? route-path "/")
       (not (str/includes? route-path ".."))  ; Security: no path traversal
       (not (str/includes? route-path "//")))) ; No double slashes

(defn normalize-route-path
  "Normalize route path for consistent processing"
  [route-path]
  (when (route-path-valid? route-path)
    (-> route-path
        (str/replace #"/+$" "") ; Remove trailing slashes
        (str/replace #"/+" "/") ; Collapse multiple slashes
        (#(if (= % "") "/" %))))) ; Empty becomes root

;; --- DEVELOPMENT AND TESTING HELPERS ---

(comment
  ;; Testing route-to-path conversion
  (route->alfresco-path "/")
  ;; => "Sites/swsdp/documentLibrary/Web Site"
  
  (route->alfresco-path "/about")
  ;; => "Sites/swsdp/documentLibrary/Web Site/about"
  
  (route->alfresco-path "/worship/services")
  ;; => "Sites/swsdp/documentLibrary/Web Site/worship/services"
  
  ;; Testing path-to-route conversion
  (alfresco-path->route "Sites/swsdp/documentLibrary/Web Site/about")
  ;; => "/about"
  
  (alfresco-path->route "Sites/swsdp/documentLibrary/Web Site")
  ;; => "/"
  
  ;; Route validation
  (route-path-valid? "/about") ;; => true
  (route-path-valid? "/about/../admin") ;; => false
  (route-path-valid? "//about") ;; => false
  
  ;; Usage with actual Alfresco config
  #_(let [config {:base-url "http://localhost:8080" :username "admin" :password "admin"}]
      (resolve-route-content config "/about"))
)