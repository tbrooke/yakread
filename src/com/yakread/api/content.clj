;; Content API endpoints for UIX integration
;; Add this as a new file: src/com/yakread/api/content.clj

(ns com.yakread.api.content
  "API endpoints for serving content pointers to UIX frontend"
  (:require [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [com.biffweb :as biff]
            [com.yakread.lib.alfresco :as lib.alfresco]
            [xtdb.api :as xt]))

;; Query functions for content pointers
(defn get-content-pointers-for-page
  "Get all content pointers for a specific page"
  [db page-keyword]
  (xt/q db
        '{:find [content]
          :where [[content :content/target-page page]
                  [content :content/status status]
                  [(contains? #{:content.status/published :content.status/draft} status)]]
          :in [page]}
        page-keyword))

(defn get-content-pointer-details
  "Get full details for content pointers"
  [db content-ids]
  (map #(xt/entity db %) content-ids))

(defn content-pointer-to-api-format
  "Convert XTDB content pointer to API format for UIX"
  [content-pointer]
  {:id (str (:xt/id content-pointer))
   :type (name (:content/type content-pointer))
   :targetComponent (name (:content/target-component content-pointer))
   :displayOrder (:content/display-order content-pointer 1)
   :status (name (:content/status content-pointer))
   
   ;; Alfresco reference info
   :alfresco {:id (:content/alfresco-id content-pointer)
             :name (:content/alfresco-name content-pointer)
             :path (:content/alfresco-path content-pointer)
             :type (:content/alfresco-type content-pointer)
             :mimeType (:content/alfresco-mime-type content-pointer)
             :size (:content/alfresco-size content-pointer)}
   
   ;; Timestamps
   :createdAt (str (:content/alfresco-created-at content-pointer))
   :modifiedAt (str (:content/alfresco-modified-at content-pointer))
   :lastSyncAt (str (:content/last-sync-at content-pointer))})

;; API route handlers
(defn get-page-content-handler
  "GET /api/content/:page - Get content for a specific page"
  [{:keys [path-params biff/db] :as ctx}]
  (let [page-name (:page path-params)
        page-keyword (keyword "page" page-name)
        content-ids (map first (get-content-pointers-for-page db page-keyword))
        content-details (get-content-pointer-details db content-ids)
        api-format (map content-pointer-to-api-format content-details)]
    (log/info "Serving content for page:" page-name "(" (count api-format) "items)")
    {:status 200
     :headers {"content-type" "application/json"
               "access-control-allow-origin" "*"} ; For local dev
     :body (json/write-str {:page page-name
                           :content api-format
                           :timestamp (str (biff/now))})}))

(defn get-all-pages-handler
  "GET /api/content/pages - List all available pages"
  [{:keys [biff/db] :as ctx}]
  (let [pages (xt/q db
                   '{:find [page]
                     :where [[_ :content/target-page page]]})
        page-names (map (comp name first) pages)]
    {:status 200
     :headers {"content-type" "application/json"
               "access-control-allow-origin" "*"}
     :body (json/write-str {:pages page-names
                           :timestamp (str (biff/now))})}))

(defn proxy-alfresco-content-handler
  "GET /api/alfresco/:asset-id - Proxy content from Alfresco"
  [{:keys [path-params] :as ctx}]
  (let [asset-id (:asset-id path-params)
        config (get-alfresco-config ctx)]
    ;; TODO: Implement actual Alfresco content proxying
    ;; For now, return a placeholder
    {:status 200
     :headers {"content-type" "text/plain"}
     :body (str "Content for Alfresco asset: " asset-id)}))

;; Fake data endpoints for testing
(defn create-fake-content-handler
  "POST /api/content/fake - Create fake content for testing"
  [{:keys [biff/db session] :as ctx}]
  (let [user-id (:uid session "test-user")
        fake-pointers (lib.alfresco/create-fake-content-pointers :homepage user-id)]
    ;; Save fake pointers to XTDB
    (xt/await-tx db (xt/submit-tx db (for [pointer fake-pointers]
                                      [::xt/put pointer])))
    (log/info "Created" (count fake-pointers) "fake content pointers")
    {:status 201
     :headers {"content-type" "application/json"}
     :body (json/write-str {:success true
                           :created (count fake-pointers)
                           :message "Fake content created for testing"})}))

(defn get-fake-homepage-content
  "GET /api/content/fake/homepage - Return fake homepage content for UIX testing"
  [ctx]
  {:status 200
   :headers {"content-type" "application/json"
             "access-control-allow-origin" "*"}
   :body (json/write-str 
          {:page "homepage"
           :content [{:id "fake-hero-123"
                     :type "image"
                     :targetComponent "heroImage"
                     :displayOrder 1
                     :status "published"
                     :alfresco {:id "fake-hero-img-123"
                               :name "hero-banner.jpg"
                               :path "/Web Site/Home Page/hero-banner.jpg"
                               :type "file"
                               :mimeType "image/jpeg"
                               :size 245760}
                     :createdAt "2025-09-17T10:00:00.000Z"
                     :modifiedAt "2025-09-17T15:30:00.000Z"}
                    
                    {:id "fake-hello-456"
                     :type "text"
                     :targetComponent "textBlock"
                     :displayOrder 2
                     :status "published"
                     :alfresco {:id "fake-hello-world-456"
                               :name "hello-world.txt"
                               :path "/Web Site/Home Page/hello-world.txt"
                               :type "file"
                               :mimeType "text/plain"
                               :size 13}
                     :createdAt "2025-09-17T09:15:00.000Z"
                     :modifiedAt "2025-09-17T09:15:00.000Z"
                     :content "Hello World from Mt Zion!"}]
           :timestamp (str (biff/now))})})

;; Configuration function (add to existing alfresco.clj)
(defn get-alfresco-config
  "Get Alfresco configuration from environment/secrets"
  [ctx]
  {:base-url (biff/lookup ctx :alfresco/base-url "http://generated-setup-alfresco-1:8080")
   :username (biff/lookup ctx :alfresco/username "admin")
   :password (biff/lookup ctx :alfresco/password "admin")})

;; Routes
(def content-api-routes
  [["/api/content/pages" {:get get-all-pages-handler}]
   ["/api/content/:page" {:get get-page-content-handler}]
   ["/api/alfresco/:asset-id" {:get proxy-alfresco-content-handler}]
   ["/api/content/fake" {:post create-fake-content-handler}]
   ["/api/content/fake/homepage" {:get get-fake-homepage-content}]])

(def module
  {:api-routes content-api-routes})

(comment
  ;; Test the API format conversion
  (def sample-pointer 
    {:xt/id #uuid "123e4567-e89b-12d3-a456-426614174000"
     :content/type :content.type/image
     :content/target-component :uix.component/hero-image
     :content/alfresco-id "fake-hero-img-123"
     :content/alfresco-name "hero-banner.jpg"})
  
  (content-pointer-to-api-format sample-pointer)
  )
