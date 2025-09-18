(ns com.yakread.lib.alfresco
  "Alfresco REST API and CMIS client for Mt Zion website integration"
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [com.biffweb :as biff]
            [xtdb.api :as xt]))

;; Configuration and constants
(def default-config
  {:base-url "http://generated-setup-alfresco-1:8080"
   :username "admin"
   :password "admin"
   :timeout 30000
   :connection-timeout 10000})

(def mtzion-site-id "swsdp")
(def website-folder-name "Web Site")

;; Utility functions
(defn- build-auth [config]
  [(:username config) (:password config)])

(defn- build-url [config & path-segments]
  (str (:base-url config)
       (str/join "/" (cons "" path-segments))))

(defn- rest-api-url [config & path-segments]
  (apply build-url config "alfresco" "api" "-default-" "public" "alfresco" "versions" "1" path-segments))

(defn- cmis-browser-url [config & path-segments]
  (apply build-url config "alfresco" "api" "-default-" "public" "cmis" "versions" "1.1" "browser" path-segments))

(defn- make-request
  "Make HTTP request with standard error handling and authentication"
  [method url config & [opts]]
  (try
    (let [request-opts (merge {:method method
                              :url url
                              :basic-auth (build-auth config)
                              :accept :json
                              :content-type :json
                              :socket-timeout (:timeout config)
                              :connection-timeout (:connection-timeout config)
                              :throw-exceptions false}
                             opts)
          response (http/request request-opts)]
      (log/debug "Alfresco request:" method url "Status:" (:status response))
      (cond
        (< (:status response) 400)
        {:status (:status response)
         :body (if (string? (:body response))
                (json/read-str (:body response) :key-fn keyword)
                (:body response))}
        
        (= (:status response) 401)
        {:error :unauthorized
         :message "Authentication failed - check credentials"}
        
        (= (:status response) 404)
        {:error :not-found
         :message "Resource not found"}
        
        (>= (:status response) 500)
        {:error :server-error
         :message "Alfresco server error"}
        
        :else
        {:error :http-error
         :status (:status response)
         :message (str "HTTP " (:status response) " error")}))
    (catch Exception e
      (log/error e "Alfresco request failed")
      {:error :connection-error
       :message (.getMessage e)})))

;; REST API functions
(defn get-repository-info
  "Get repository information"
  [config]
  (make-request :get (rest-api-url config "repositories" "-default-") config))

(defn list-sites
  "List all sites"
  [config & [opts]]
  (let [query-params (select-keys opts [:skipCount :maxItems :where :orderBy])]
    (make-request :get 
                  (rest-api-url config "sites") 
                  config
                  (when (seq query-params)
                    {:query-params query-params}))))

(defn get-site
  "Get site information by site ID"
  [config site-id]
  (make-request :get (rest-api-url config "sites" site-id) config))

(defn get-site-containers
  "Get site containers (like documentLibrary)"
  [config site-id]
  (make-request :get (rest-api-url config "sites" site-id "containers") config))

(defn list-folder-children
  "List children of a folder by node ID"
  [config node-id & [opts]]
  (let [query-params (merge {:skipCount 0 :maxItems 100} opts)]
    (make-request :get 
                  (rest-api-url config "nodes" node-id "children") 
                  config
                  {:query-params query-params})))

(defn get-node
  "Get node information by ID"
  [config node-id]
  (make-request :get (rest-api-url config "nodes" node-id) config))

(defn search-nodes
  "Search for nodes using AFTS query"
  [config query & [opts]]
  (let [search-body {:query {:query query
                            :language "afts"}
                    :paging (merge {:maxItems 100 :skipCount 0} (:paging opts))
                    :include (:include opts ["path" "properties"])
                    :sort (:sort opts)}]
    (make-request :post 
                  (rest-api-url config "search") 
                  config
                  {:body (json/write-str search-body)})))

;; CMIS API functions
(defn cmis-get-repository-info
  "Get CMIS repository information"
  [config]
  (make-request :get (cmis-browser-url config) config))

(defn cmis-query
  "Execute CMIS SQL query"
  [config cmis-query & [opts]]
  (let [query-params (merge {:q cmis-query
                            :maxItems 100
                            :skipCount 0}
                           opts)]
    (make-request :get 
                  (cmis-browser-url config "query") 
                  config
                  {:query-params query-params})))

(defn cmis-get-object
  "Get CMIS object by ID"
  [config object-id]
  (make-request :get 
                (cmis-browser-url config "root")
                config
                {:query-params {:objectId object-id}}))

(defn cmis-get-children
  "Get CMIS object children"
  [config object-id & [opts]]
  (let [query-params (merge {:maxItems 100 :skipCount 0} opts)]
    (make-request :get 
                  (cmis-browser-url config "root")
                  config
                  {:query-params (merge query-params {:objectId object-id :cmisselector "children"})})))

;; High-level integration functions
(defn find-website-folder
  "Find the Web Site folder in Mt Zion site document library"
  [config]
  (let [site-containers (get-site-containers config mtzion-site-id)]
    (if (:error site-containers)
      site-containers
      (let [doc-library (->> (get-in site-containers [:body :list :entries])
                           (filter #(= "documentLibrary" (get-in % [:entry :folderId])))
                           first
                           :entry
                           :id)]
        (if doc-library
          (let [library-contents (list-folder-children config doc-library)]
            (if (:error library-contents)
              library-contents
              (->> (get-in library-contents [:body :list :entries])
                   (filter #(= website-folder-name (get-in % [:entry :name])))
                   first
                   :entry)))
          {:error :not-found :message "Document library not found"})))))

(defn get-mtzion-website-structure
  "Get complete Mt Zion website folder structure"
  [config]
  (let [site-info (get-site config mtzion-site-id)]
    (if (:error site-info)
      site-info
      (let [website-folder (find-website-folder config)]
        (if (:error website-folder)
          website-folder
          (let [folder-contents (list-folder-children config (:id website-folder))]
            (if (:error folder-contents)
              folder-contents
              {:site-id mtzion-site-id
               :site-name (get-in site-info [:body :entry :title])
               :document-library-id (:parentId website-folder)
               :website-folder-id (:id website-folder)
               :website-folders (->> (get-in folder-contents [:body :list :entries])
                                   (map (fn [entry]
                                          (let [node (:entry entry)]
                                            {:id (:id node)
                                             :name (:name node)
                                             :type (if (:isFolder node) "folder" "file")
                                             :path (str "/Sites/" mtzion-site-id "/documentLibrary/" 
                                                       website-folder-name "/" (:name node))
                                             :created-at (:createdAt node)
                                             :modified-at (:modifiedAt node)
                                             :created-by (get-in node [:createdByUser :displayName])
                                             :modified-by (get-in node [:modifiedByUser :displayName])}))))})))))))

(defn list-folder-contents-recursive
  "Recursively list all contents of a folder"
  [config folder-id max-depth]
  (when (pos? max-depth)
    (let [contents (list-folder-children config folder-id)]
      (if (:error contents)
        contents
        (let [entries (get-in contents [:body :list :entries])]
          (concat
           (map :entry entries)
           (mapcat #(when (get-in % [:entry :isFolder])
                      (list-folder-contents-recursive config 
                                                     (get-in % [:entry :id]) 
                                                     (dec max-depth)))
                   entries)))))))

;; Yakread integration functions
(defn folder-to-yakread-item
  "Convert an Alfresco folder to a Yakread item structure"
  [folder site-path]
  (let [now (biff/now)]
    {:xt/id (random-uuid)
     :item/type :item.type/alfresco-folder
     :item/title (:name folder)
     :item/url (str "alfresco://" (:id folder))
     :item/description (str "Alfresco folder: " site-path "/" (:name folder))
     :item/source "Mt Zion Alfresco"
     :item/alfresco-id (:id folder)
     :item/alfresco-path (:path folder)
     :item/alfresco-type (:type folder)

     :item/created-at (if-let [date-str (:created-at folder)]
                        (try (java.time.Instant/parse date-str)
                             (catch Exception _ now))
                        now)
     :item/updated-at (if-let [date-str (:modified-at folder)]
                        (try (java.time.Instant/parse date-str)
                             (catch Exception _ now))
                        now)
     :item/sync-timestamp now}))

(defn document-to-yakread-item
  "Convert an Alfresco document to a Yakread item structure"
  [document site-path]
  (let [now (biff/now)]
    {:xt/id (random-uuid)
     :item/type :item.type/alfresco-document
     :item/title (:name document)
     :item/url (str "alfresco://" (:id document))
     :item/description (str "Alfresco document: " site-path "/" (:name document))
     :item/source "Mt Zion Alfresco"
     :item/alfresco-id (:id document)
     :item/alfresco-path (:path document)
     :item/alfresco-type (:type document)
     :item/content-type (:content-type document)
     :item/file-size (:size document)
     :item/created-at (if-let [date-str (:created-at document)]
                        (try (java.time.Instant/parse date-str)
                             (catch Exception _ now))
                        now)
     :item/updated-at (if-let [date-str (:modified-at document)]
                        (try (java.time.Instant/parse date-str)
                             (catch Exception _ now))
                        now)
     :item/sync-timestamp now}))

(defn sync-folder-to-yakread
  "Sync Alfresco folder contents to Yakread items"
  [config folder-id user-id & [opts]]
  (let [max-depth (get opts :max-depth 2)
        contents (list-folder-contents-recursive config folder-id max-depth)]
    (if (:error contents)
      contents
      (let [folders (filter #(= "folder" (:type %)) contents)
            documents (filter #(= "file" (:type %)) contents)
            folder-items (map #(folder-to-yakread-item % "Mt Zion Website") folders)
            document-items (map #(document-to-yakread-item % "Mt Zion Website") documents)]
        {:synced-folders (count folders)
         :synced-documents (count documents)
         :created-items (concat folder-items document-items)
         :updated-items []}))))

(defn create-alfresco-subscription
  "Create a subscription to monitor Alfresco folder changes"
  [user-id config folder-id folder-name]
  (let [now (biff/now)]
    {:xt/id (random-uuid)
     :subscription/type :subscription.type/alfresco-folder
     :subscription/user-id user-id
     :subscription/title (str "Mt Zion: " folder-name)
     :subscription/url (str "alfresco://" folder-id)
     :subscription/alfresco-config (select-keys config [:base-url :username])
     :subscription/alfresco-folder-id folder-id
     :subscription/alfresco-folder-name folder-name
     :subscription/last-sync-at now
     :subscription/created-at now
     :subscription/is-active true}))

;; Health check and connectivity functions
(defn health-check
  "Check if Alfresco is accessible and authentication works"
  [config]
  (let [repo-info (get-repository-info config)]
    (if (:error repo-info)
      {:healthy false
       :error (:error repo-info)
       :message (:message repo-info)}
      {:healthy true
       :repository-id (get-in repo-info [:body :entry :id])
       :version (get-in repo-info [:body :entry :version :display])
       :edition (get-in repo-info [:body :entry :edition])})))

(defn test-mtzion-connectivity
  "Test connectivity specifically to Mt Zion site and Web Site folder"
  [config]
  (let [health (health-check config)]
    (if-not (:healthy health)
      health
      (let [website-structure (get-mtzion-website-structure config)]
        (if (:error website-structure)
          {:healthy false
           :error (:error website-structure)
           :message (:message website-structure)}
          (assoc health
                 :mtzion-site-accessible true
                 :website-folder-id (:website-folder-id website-structure)
                 :website-folders-count (count (:website-folders website-structure))))))))

;; Configuration validation
(defn validate-config
  "Validate Alfresco configuration"
  [config]
  (let [required-keys [:base-url :username :password]
        missing-keys (remove #(contains? config %) required-keys)]
    (if (empty? missing-keys)
      {:valid true}
      {:valid false
       :missing-keys missing-keys
       :message (str "Missing required configuration keys: " (str/join ", " missing-keys))})))

(comment
  ;; Development and testing
  (def test-config
    {:base-url "http://generated-setup-alfresco-1:8080"
     :username "admin"
     :password "admin"})

  ;; Test basic connectivity
  (health-check test-config)
  (test-mtzion-connectivity test-config)
  
  ;; Test site structure
  (get-mtzion-website-structure test-config)
  
  ;; Test CMIS queries
  (cmis-query test-config "SELECT * FROM cmis:folder WHERE IN_FOLDER('21f2687f-7b6c-403a-b268-7f7b6c803a85')")
  
  ;; Test sync
  (sync-folder-to-yakread test-config "21f2687f-7b6c-403a-b268-7f7b6c803a85" "user123")


  ;; Enhanced Alfresco sync functions for content pointer system
;; Add these functions to your existing com.yakread.lib.alfresco namespace

;; Content type detection
(defn detect-content-type
  "Detect content type based on file extension and folder context"
  [alfresco-item folder-name]
  (let [name (:name alfresco-item)
        is-folder (:isFolder alfresco-item)]
    (cond
      is-folder
      (cond
        (re-find #"(?i)hero" name) :content.type/hero-folder
        (re-find #"(?i)feature" name) :content.type/feature-folder
        (re-find #"(?i)gallery" name) :content.type/gallery-folder
        :else :content.type/folder)
      
      ;; File types
      (re-find #"(?i)\.(jpg|jpeg|png|gif|webp)$" name) :content.type/image
      (re-find #"(?i)\.(txt|md|markdown)$" name) :content.type/text
      (re-find #"(?i)\.json$" name) :content.type/config
      (re-find #"(?i)\.(pdf|doc|docx)$" name) :content.type/document
      :else :content.type/unknown)))

(defn determine-target-component
  "Map content type and context to UIX component"
  [content-type folder-context file-name]
  (case content-type
    :content.type/hero-folder :uix.component/hero-section
    :content.type/feature-folder :uix.component/feature-section
    :content.type/gallery-folder :uix.component/image-gallery
    :content.type/image 
    (cond
      (re-find #"(?i)hero" folder-context) :uix.component/hero-image
      (re-find #"(?i)feature" folder-context) :uix.component/feature-image
      :else :uix.component/content-image)
    :content.type/text :uix.component/text-block
    :content.type/config :uix.component/page-config
    :content.type/document :uix.component/document-link
    :uix.component/generic))

(defn create-content-pointer
  "Create XTDB content pointer from Alfresco item"
  [alfresco-item parent-folder target-page user-id]
  (let [now (biff/now)
        content-type (detect-content-type alfresco-item (:name parent-folder))
        target-component (determine-target-component content-type 
                                                    (:name parent-folder) 
                                                    (:name alfresco-item))]
    {:xt/id (random-uuid)
     ;; Content identification
     :content/type content-type
     :content/target-page target-page
     :content/target-component target-component
     :content/display-order 1 ; Default, can be enhanced later
     
     ;; Alfresco references
     :content/alfresco-id (:id alfresco-item)
     :content/alfresco-name (:name alfresco-item)
     :content/alfresco-path (str "/Web Site/" (:name parent-folder) "/" (:name alfresco-item))
     :content/alfresco-type (if (:isFolder alfresco-item) "folder" "file")
     :content/alfresco-size (:size alfresco-item)
     :content/alfresco-mime-type (:mimeType alfresco-item)
     
     ;; Metadata
     :content/created-by user-id
     :content/status :content.status/draft ; Start as draft, can be published later
     :content/last-sync-at now
     
     ;; Temporal tracking
     :content/alfresco-created-at (if-let [date-str (:createdAt alfresco-item)]
                                    (try (java.time.Instant/parse date-str)
                                         (catch Exception _ now))
                                    now)
     :content/alfresco-modified-at (if-let [date-str (:modifiedAt alfresco-item)]
                                     (try (java.time.Instant/parse date-str)
                                          (catch Exception _ now))
                                     now)
     :xt/valid-from now
     :xt/valid-to #inst "9999-12-31"}))

(defn sync-folder-contents-to-pointers
  "Enhanced sync that creates content pointers instead of generic items"
  [config folder-id folder-name target-page user-id]
  (let [contents (list-folder-children config folder-id)]
    (if (:error contents)
      contents
      (let [items (get-in contents [:body :list :entries])
            parent-folder {:name folder-name :id folder-id}
            content-pointers (map #(create-content-pointer (:entry %) 
                                                          parent-folder 
                                                          target-page 
                                                          user-id) 
                                items)]
        {:success true
         :folder-id folder-id
         :folder-name folder-name
         :items-found (count items)
         :content-pointers content-pointers
         :created-at (biff/now)}))))

;; Fake data for testing
(defn create-fake-alfresco-content
  "Create fake Alfresco content for testing the pipeline"
  []
  [{:id "fake-hero-img-123"
    :name "hero-banner.jpg"
    :isFolder false
    :size 245760
    :mimeType "image/jpeg"
    :createdAt "2025-09-17T10:00:00.000+0000"
    :modifiedAt "2025-09-17T15:30:00.000+0000"}
   
   {:id "fake-hello-world-456"
    :name "hello-world.txt" 
    :isFolder false
    :size 13
    :mimeType "text/plain"
    :createdAt "2025-09-17T09:15:00.000+0000"
    :modifiedAt "2025-09-17T09:15:00.000+0000"}
   
   {:id "fake-welcome-text-789"
    :name "welcome-message.md"
    :isFolder false
    :size 420
    :mimeType "text/markdown"
    :createdAt "2025-09-17T08:45:00.000+0000"
    :modifiedAt "2025-09-17T14:20:00.000+0000"}
   
   {:id "fake-hero-folder-abc"
    :name "Hero"
    :isFolder true
    :createdAt "2025-09-16T12:00:00.000+0000"
    :modifiedAt "2025-09-17T15:30:00.000+0000"}])

(defn create-fake-content-pointers
  "Create fake content pointers for testing UIX integration"
  [target-page user-id]
  (let [fake-items (create-fake-alfresco-content)
        parent-folder {:name "Home Page" :id "fake-homepage-folder"}]
    (map #(create-content-pointer % parent-folder target-page user-id) fake-items)))

;; Testing functions
(defn test-content-pointer-creation
  "Test the content pointer creation with fake data"
  []
  (let [fake-pointers (create-fake-content-pointers :homepage "test-user-123")]
    (println "Created" (count fake-pointers) "fake content pointers:")
    (doseq [pointer fake-pointers]
      (println "- " (:content/alfresco-name pointer) 
               "→" (:content/target-component pointer)
               "(" (:content/type pointer) ")"))
    fake-pointers))

;; Enhanced sync function for real Alfresco
(defn sync-mtzion-website-to-pointers
  "Sync Mt Zion website folders to content pointer system"
  [config user-id]
  (let [website-structure (get-mtzion-website-structure config)]
    (if (:error website-structure)
      website-structure
      (let [folders (:website-folders website-structure)
            sync-results (map #(sync-folder-contents-to-pointers 
                               config 
                               (:id %) 
                               (:name %) 
                               (keyword (str "page/" (clojure.string/lower-case (:name %))))
                               user-id) 
                             folders)]
        {:success true
         :website-structure website-structure
         :sync-results sync-results
         :total-content-pointers (reduce + (map #(count (:content-pointers %)) sync-results))
         :synced-at (biff/now)}))))

(comment
  ;; Test with fake data
  (test-content-pointer-creation)
  
  ;; Test with real Alfresco (when connected)
  (sync-mtzion-website-to-pointers test-config "user123")
  
  ;; Create fake data for UIX testing
  (def fake-pointers (create-fake-content-pointers :homepage "test-user"))
  )
  )
