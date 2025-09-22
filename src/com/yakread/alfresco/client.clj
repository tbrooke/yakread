(ns com.yakread.alfresco.client
  "Alfresco REST API client for yakread integration"
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [com.biffweb :as biff]
            [clojure.tools.logging :as log]))

;; --- API SPECIFICATIONS ---

(def ^:private api-specs-cache (atom {}))

(defn load-api-spec
  "Load API specification from EDN file"
  [spec-name version]
  (let [cache-key [spec-name version]]
    (if-let [cached-spec (get @api-specs-cache cache-key)]
      cached-spec
      (try
        (let [spec-path (str "api-specs/" version "/" spec-name ".edn")
              spec-data (edn/read-string (slurp spec-path))]
          (swap! api-specs-cache assoc cache-key spec-data)
          spec-data)
        (catch Exception e
          (log/error "Failed to load API spec" spec-name version (.getMessage e))
          nil)))))

(defn get-latest-api-version
  "Get the latest API version from metadata"
  []
  (try
    (let [metadata (edn/read-string (slurp "api-specs/update-metadata.edn"))]
      (:current-version metadata))
    (catch Exception e
      (log/warn "Could not load API metadata, using default version")
      "2025.09.19-0830"))) ; fallback to known version

;; --- HTTP CLIENT ---

(defn make-request
  "Make authenticated HTTP request to Alfresco API"
  [ctx method path & [options]]
  (let [base-url (get ctx :alfresco/base-url)
        username (get ctx :alfresco/username)
        password (get ctx :alfresco/password)
        api-base (str base-url "/alfresco/api/-default-/public/alfresco/versions/1")
        full-url (str api-base path)

        request-options (merge
                         {:basic-auth [username password]
                          :headers {"Accept" "application/json"
                                    "Content-Type" "application/json"}
                          :throw-exceptions false
                          :as :json}
                         options)]

    (log/debug "Alfresco API request:" method full-url)

    (try
      (let [response (case method
                       :get (http/get full-url request-options)
                       :post (http/post full-url request-options)
                       :put (http/put full-url request-options)
                       :delete (http/delete full-url request-options))]

        (if (< (:status response) 400)
          {:success true
           :status (:status response)
           :data (:body response)}
          {:success false
           :status (:status response)
           :error (:body response)}))

      (catch Exception e
        (log/error "Alfresco API request failed:" (.getMessage e))
        {:success false
         :error (.getMessage e)}))))

;; --- NODE OPERATIONS ---

(defn get-root-node
  "Get the root Company Home node"
  [ctx]
  (make-request ctx :get "/nodes/-root-"))

(defn get-node
  "Get a specific node by ID"
  [ctx node-id]
  (make-request ctx :get (str "/nodes/" node-id)))

(defn get-node-children
  "Get children of a node with optional filtering"
  [ctx node-id & [options]]
  (let [query-params (when options
                       (str "?" (str/join "&"
                                          (for [[k v] options]
                                            (str (name k) "=" (java.net.URLEncoder/encode (str v) "UTF-8"))))))]
    (make-request ctx :get (str "/nodes/" node-id "/children" (or query-params "")))))

(defn get-folders
  "Get all folders under a node"
  [ctx node-id]
  (get-node-children ctx node-id {:where "(isFolder=true)"}))

(defn get-files
  "Get all files under a node"
  [ctx node-id]
  (get-node-children ctx node-id {:where "(isFile=true)"}))

(defn get-node-content
  "Download content of a file node"
  [ctx node-id]
  (let [base-url (get ctx :alfresco/base-url)
        username (get ctx :alfresco/username)
        password (get ctx :alfresco/password)
        api-base (str base-url "/alfresco/api/-default-/public/alfresco/versions/1")
        full-url (str api-base "/nodes/" node-id "/content")]

    (log/debug "Fetching content for node:" node-id)

    (try
      (let [response (http/get full-url
                               {:basic-auth [username password]
                                :throw-exceptions false
                                :as :string})] ; Get as string for text content

        (if (< (:status response) 400)
          {:success true
           :status (:status response)
           :data (:body response)}
          {:success false
           :status (:status response)
           :error (:body response)}))

      (catch Exception e
        (log/error "Failed to fetch node content:" (.getMessage e))
        {:success false
         :error (.getMessage e)}))))

;; --- CONTENT EXTRACTION ---

(defn extract-node-metadata
  "Extract useful metadata from a node entry"
  [node-entry]
  (let [entry (:entry node-entry)]
    {:id (:id entry)
     :name (:name entry)
     :type (if (:isFolder entry) "folder" "file")
     :mime-type (:mimeType entry)
     :size (:sizeInBytes entry)
     :created-at (:createdAt entry)
     :modified-at (:modifiedAt entry)
     :created-by (get-in entry [:createdByUser :displayName])
     :modified-by (get-in entry [:modifiedByUser :displayName])
     :path (:path entry)
     :parent-id (:parentId entry)
     :aspect-names (:aspectNames entry)
     :properties (:properties entry)}))

(defn list-content-recursive
  "Recursively list all content starting from a node"
  [ctx node-id & [max-depth current-depth]]
  (let [max-depth (or max-depth 3)
        current-depth (or current-depth 0)]

    (when (< current-depth max-depth)
      (let [children-response (get-node-children ctx node-id)
            children (if (:success children-response)
                       (get-in children-response [:data :list :entries])
                       [])]

        (mapcat
         (fn [child]
           (let [metadata (extract-node-metadata child)
                 child-id (:id metadata)]
             (if (= "folder" (:type metadata))
               ;; For folders, recursively get children
               (cons metadata
                     (list-content-recursive ctx child-id max-depth (inc current-depth)))
               ;; For files, just return the metadata
               [metadata])))
         children)))))

;; --- SITE OPERATIONS ---

(defn get-sites
  "Get all sites accessible to the user"
  [ctx]
  (make-request ctx :get "/sites"))

(defn get-site-content
  "Get content from a specific site"
  [ctx site-id]
  (make-request ctx :get (str "/sites/" site-id "/containers")))

;; --- SEARCH OPERATIONS ---

(defn search-nodes
  "Search for nodes using Alfresco search API"
  [ctx query & [options]]
  (let [search-base (str (get ctx :alfresco/base-url) "/alfresco/api/-default-/public/search/versions/1")
        search-body {:query {:query query
                             :language "afts"} ; Alfresco Full Text Search
                     :paging (merge {:maxItems 100 :skipCount 0} (:paging options))
                     :include (:include options ["path" "properties"])
                     :fields (:fields options ["*"])}]

    (try
      (let [response (http/post (str search-base "/search")
                                {:basic-auth [(get ctx :alfresco/username) (get ctx :alfresco/password)]
                                 :headers {"Accept" "application/json"
                                           "Content-Type" "application/json"}
                                 :body (json/write-str search-body)
                                 :throw-exceptions false
                                 :as :json})]

        (if (< (:status response) 400)
          {:success true
           :status (:status response)
           :data (:body response)}
          {:success false
           :status (:status response)
           :error (:body response)}))

      (catch Exception e
        (log/error "Alfresco search failed:" (.getMessage e))
        {:success false
         :error (.getMessage e)}))))

;; --- HEALTH CHECK ---

(defn health-check
  "Check if Alfresco API is accessible and responding"
  [ctx]
  (let [start-time (System/currentTimeMillis)
        root-response (get-root-node ctx)
        end-time (System/currentTimeMillis)
        response-time (- end-time start-time)]

    (merge root-response
           {:response-time-ms response-time
            :timestamp (java.time.Instant/now)})))

;; --- YAKREAD INTEGRATION ---

(defn format-for-yakread
  "Convert Alfresco content metadata to yakread format"
  [alfresco-metadata]
  {:id (str "alfresco-" (:id alfresco-metadata))
   :type (case (:mime-type alfresco-metadata)
           "text/plain" "text"
           "text/html" "html"
           "application/pdf" "pdf"
           "text")
   :target-component (case (:mime-type alfresco-metadata)
                       "text/plain" "textBlock"
                       "text/html" "htmlBlock"
                       "application/pdf" "documentViewer"
                       "textBlock")
   :display-order 1
   :status "published"
   :alfresco {:id (:id alfresco-metadata)
              :name (:name alfresco-metadata)
              :path (:path alfresco-metadata)
              :type (:type alfresco-metadata)
              :mime-type (:mime-type alfresco-metadata)
              :size (:size alfresco-metadata)}
   :created-at (:created-at alfresco-metadata)
   :modified-at (:modified-at alfresco-metadata)
   :content nil}) ; Will be populated when content is fetched

;; --- TAG OPERATIONS ---

(defn get-node-tags
  "Get tags for a specific node"
  [ctx node-id]
  (make-request ctx :get (str "/nodes/" node-id "/tags")))

(defn has-tag?
  "Check if a node has a specific tag"
  [ctx node-id tag-name]
  (let [tags-response (get-node-tags ctx node-id)]
    (if (:success tags-response)
      (let [tags (get-in tags-response [:data :list :entries])]
        (some #(= tag-name (get-in % [:entry :tag])) tags))
      false)))

;; --- CONVENIENCE FUNCTIONS ---

(defn get-yakread-content
  "Get content formatted for yakread from a specific Alfresco path"
  [ctx path-or-query & [options]]
  (let [search-results (if (str/starts-with? path-or-query "/")
                         ;; Path-based lookup
                         (search-nodes ctx (str "PATH:\"" path-or-query "\"") options)
                         ;; Query-based search
                         (search-nodes ctx path-or-query options))]

    (if (:success search-results)
      (let [entries (get-in search-results [:data :list :entries])]
        (map (comp format-for-yakread extract-node-metadata) entries))
      [])))

(defn test-connection
  "Test the Alfresco connection and return status"
  [ctx]
  (let [health-result (health-check ctx)]
    (if (:success health-result)
      (do
        (log/info "Alfresco connection successful"
                  "Root node:" (get-in health-result [:data :entry :name])
                  "Response time:" (:response-time-ms health-result) "ms")
        true)
      (do
        (log/error "Alfresco connection failed:" (:error health-result))
        false))))