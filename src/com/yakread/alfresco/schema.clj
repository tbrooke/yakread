(ns com.yakread.alfresco.schema
  "Malli schemas derived from Alfresco API specifications"
  (:require [malli.core :as m]
            [malli.registry :as mr]
            [malli.transform :as mt]
            [malli.util :as mu]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.tools.logging :as log]))

;; --- ALFRESCO API SPEC TO MALLI CONVERSION ---

(defn openapi-type->malli
  "Convert OpenAPI/Swagger type definitions to Malli schemas"
  [type-def]
  (let [{:keys [type format items properties required enum $ref allOf oneOf]} type-def]
    (cond
      ;; Reference to another schema
      $ref (keyword (last (str/split $ref #"/")))

      ;; Enum values
      enum [:enum enum]

      ;; Array type
      (= type "array")
      [:vector (if items (openapi-type->malli items) :any)]

      ;; Object type with properties
      (= type "object")
      (let [req-set (set required)
            props (for [[prop-name prop-def] properties]
                    (let [prop-key (keyword prop-name)
                          prop-schema (openapi-type->malli prop-def)]
                      [prop-key (if (req-set prop-name)
                                  prop-schema
                                  [:maybe prop-schema])]))]
        (into [:map] props))

      ;; Composition schemas
      allOf [:and (map openapi-type->malli allOf)]
      oneOf [:or (map openapi-type->malli oneOf)]

      ;; Primitive types
      (= type "string")
      (case format
        "date-time" [:re #"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}"]
        "date" [:re #"^\d{4}-\d{2}-\d{2}"]
        "uri" [:re #"^https?://"]
        "email" [:re #"^[^@]+@[^@]+\.[^@]+$"]
        :string)

      (= type "integer") :int
      (= type "number") :double
      (= type "boolean") :boolean

      ;; Default fallback
      :else :any)))

(defn load-alfresco-schemas
  "Load schemas from both API specs and live-generated schemas"
  [version]
  (try
    ;; Load API-based schemas (if available)
    (let [api-schemas (try
                        (let [core-spec (edn/read-string (slurp (str "api-specs/" version "/core.edn")))
                              model-spec (edn/read-string (slurp (str "api-specs/" version "/model.edn")))
                              search-spec (edn/read-string (slurp (str "api-specs/" version "/search.edn")))

                              all-definitions (merge (:definitions core-spec)
                                                     (:definitions model-spec)
                                                     (:definitions search-spec))]

                          (into {}
                                (for [[schema-name schema-def] all-definitions]
                                  [(keyword schema-name) (openapi-type->malli schema-def)])))
                        (catch Exception e
                          (log/warn "Could not load API specs, using live schemas only:" (.getMessage e))
                          {}))

          ;; Load live-generated schemas (from our working script)
          live-schemas (try
                         (edn/read-string (slurp "generated-model/live-schemas.edn"))
                         (catch Exception e
                           (log/warn "Could not load live schemas:" (.getMessage e))
                           {}))

          ;; Merge both schema sources (live schemas take precedence)
          combined-schemas (merge api-schemas live-schemas)]

      (log/info "Loaded schemas:"
                "API-based:" (count api-schemas)
                "Live-generated:" (count live-schemas)
                "Total:" (count combined-schemas))
      combined-schemas)

    (catch Exception e
      (log/error "Failed to load Alfresco schemas:" (.getMessage e))
      {})))

;; --- CORE ALFRESCO SCHEMAS ---

;; Base node schema for all Alfresco content
(def Node
  [:map
   [:id :string]
   [:name :string]
   [:nodeType :string]
   [:isFile :boolean]
   [:isFolder :boolean]
   [:createdAt [:re #"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}"]]
   [:modifiedAt [:re #"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}"]]
   [:createdByUser [:map
                    [:id :string]
                    [:displayName :string]]]
   [:modifiedByUser [:map
                     [:id :string]
                     [:displayName :string]]]
   [:parentId [:maybe :string]]
   [:aspectNames [:vector :string]]
   [:properties [:maybe :map]]
   [:content [:maybe [:map
                      [:mimeType [:maybe :string]]
                      [:sizeInBytes [:maybe :int]]
                      [:encoding [:maybe :string]]]]]
   [:path [:maybe [:map
                   [:name :string]
                   [:isComplete :boolean]
                   [:elements [:vector [:map
                                        [:id :string]
                                        [:name :string]
                                        [:nodeType :string]]]]]]]])

;; Response wrapper for API calls
(def NodeEntry
  [:map
   [:entry Node]])

(def NodeList
  [:map
   [:list [:map
           [:pagination [:map
                         [:count :int]
                         [:hasMoreItems :boolean]
                         [:totalItems [:maybe :int]]
                         [:skipCount :int]
                         [:maxItems :int]]]
           [:entries [:vector NodeEntry]]]]])

;; --- YAKREAD-SPECIFIC SCHEMAS ---

;; Schema for content stored in XTDB
(def YakreadContent
  [:map
   ;; XTDB document ID
   [:xt/id :uuid]

   ;; Yakread metadata
   [:yakread/type [:enum "alfresco-content"]]
   [:yakread/created-at inst?]
   [:yakread/updated-at inst?]
   [:yakread/status [:enum "draft" "published" "archived"]]

   ;; Content identification
   [:content/id :string]
   [:content/display-order [:maybe :int]]
   [:content/target-component [:enum "textBlock" "htmlBlock" "documentViewer" "imageViewer"]]

   ;; Alfresco source data
   [:alfresco/node-id :string]
   [:alfresco/name :string]
   [:alfresco/path [:maybe :string]]
   [:alfresco/type [:enum "file" "folder"]]
   [:alfresco/mime-type [:maybe :string]]
   [:alfresco/size [:maybe :int]]
   [:alfresco/created-at [:maybe :string]]
   [:alfresco/modified-at [:maybe :string]]
   [:alfresco/created-by [:maybe :string]]
   [:alfresco/modified-by [:maybe :string]]
   [:alfresco/properties [:maybe :map]]

   ;; Extracted content
   [:content/text [:maybe :string]]
   [:content/html [:maybe :string]]
   [:content/metadata [:maybe :map]]

   ;; Cache and sync info
   [:sync/last-checked inst?]
   [:sync/checksum [:maybe :string]]
   [:sync/version :int]])

;; Schema for content as served to UIX frontend
(def UIXContent
  [:map
   [:id :string]
   [:type [:enum "text" "html" "pdf" "image" "document"]]
   [:targetComponent [:enum "textBlock" "htmlBlock" "documentViewer" "imageViewer"]]
   [:displayOrder :int]
   [:status [:enum "draft" "published" "archived"]]
   [:alfresco [:map
               [:id :string]
               [:name :string]
               [:path [:maybe :string]]
               [:type [:enum "file" "folder"]]
               [:mimeType [:maybe :string]]
               [:size [:maybe :int]]]]
   [:createdAt :string]
   [:modifiedAt :string]
   [:content [:maybe :string]]])

;; Schema for page content response
(def UIXPageContent
  [:map
   [:page :string]
   [:content [:vector UIXContent]]
   [:source [:enum "alfresco" "fake" "cache"]]
   [:timestamp :string]])

;; --- RENDITION SCHEMAS ---

;; Schema for Alfresco rendition information
(def RenditionEntry
  [:map
   [:entry [:map
            [:id :string]
            [:content [:map
                       [:mimeType :string]
                       [:sizeInBytes [:maybe :int]]
                       [:encoding [:maybe :string]]]]
            [:status [:enum "CREATED" "NOT_CREATED"]]
            [:createdAt [:maybe [:re #"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}"]]]
            [:createdByUser [:maybe [:map
                                     [:id :string]
                                     [:displayName :string]]]]
            [:properties [:maybe :map]]]]])

(def RenditionList
  [:map
   [:list [:map
           [:entries [:vector RenditionEntry]]]]])

;; Schema for rendition creation request
(def RenditionBodyCreate
  [:map
   [:id [:enum "doclib" "imgpreview" "avatar" "medium" "pdf"]]])

;; Schema for image processing options
(def ImageProcessingOptions
  [:map
   [:thumbnail-size [:enum "small" "medium" "large" "original"]]
   [:format [:maybe [:enum "jpg" "png" "webp"]]]
   [:quality [:maybe [:int {:min 1 :max 100}]]]
   [:responsive [:maybe :boolean]]])

;; --- TRANSFORMATION SCHEMAS ---

;; Transform Alfresco Node to YakreadContent for XTDB storage
(def alfresco->yakread-transform
  (mt/transformer
   {:name :alfresco->yakread
    :map {:compile (fn [schema _]
                     (fn [node]
                       {:xt/id (random-uuid)
                        :yakread/type "alfresco-content"
                        :yakread/created-at (java.time.Instant/now)
                        :yakread/updated-at (java.time.Instant/now)
                        :yakread/status "published"

                        :content/id (str "alfresco-" (:id node))
                        :content/display-order 1
                        :content/target-component (case (:mimeType (:content node))
                                                    "text/plain" "textBlock"
                                                    "text/html" "htmlBlock"
                                                    "application/pdf" "documentViewer"
                                                    "textBlock")

                        :alfresco/node-id (:id node)
                        :alfresco/name (:name node)
                        :alfresco/path (get-in node [:path :name])
                        :alfresco/type (if (:isFolder node) "folder" "file")
                        :alfresco/mime-type (:mimeType (:content node))
                        :alfresco/size (:sizeInBytes (:content node))
                        :alfresco/created-at (:createdAt node)
                        :alfresco/modified-at (:modifiedAt node)
                        :alfresco/created-by (get-in node [:createdByUser :displayName])
                        :alfresco/modified-by (get-in node [:modifiedByUser :displayName])
                        :alfresco/properties (:properties node)

                        :content/text nil ; To be filled when content is fetched
                        :content/html nil
                        :content/metadata {}

                        :sync/last-checked (java.time.Instant/now)
                        :sync/checksum nil
                        :sync/version 1}))}}))

;; Transform YakreadContent to UIXContent for frontend
(def yakread->uix-transform
  (mt/transformer
   {:name :yakread->uix
    :map {:compile (fn [schema _]
                     (fn [yakread-content]
                       {:id (:content/id yakread-content)
                        :type (case (:alfresco/mime-type yakread-content)
                                "text/plain" "text"
                                "text/html" "html"
                                "application/pdf" "pdf"
                                "text")
                        :targetComponent (:content/target-component yakread-content)
                        :displayOrder (:content/display-order yakread-content)
                        :status (:yakread/status yakread-content)
                        :alfresco {:id (:alfresco/node-id yakread-content)
                                   :name (:alfresco/name yakread-content)
                                   :path (:alfresco/path yakread-content)
                                   :type (:alfresco/type yakread-content)
                                   :mimeType (:alfresco/mime-type yakread-content)
                                   :size (:alfresco/size yakread-content)}
                        :createdAt (:alfresco/created-at yakread-content)
                        :modifiedAt (:alfresco/modified-at yakread-content)
                        :content (or (:content/text yakread-content)
                                     (:content/html yakread-content))}))}}))

;; --- SCHEMA REGISTRY ---

(def yakread-alfresco-registry
  "Complete Malli registry for yakread-alfresco integration"
  (merge
   ;; Core schemas
   {:alfresco/Node Node
    :alfresco/NodeEntry NodeEntry
    :alfresco/NodeList NodeList
    :alfresco/RenditionEntry RenditionEntry
    :alfresco/RenditionList RenditionList
    :alfresco/RenditionBodyCreate RenditionBodyCreate
    :yakread/Content YakreadContent
    :uix/Content UIXContent
    :uix/PageContent UIXPageContent
    :image/ProcessingOptions ImageProcessingOptions}

   ;; Load API-derived schemas
   (load-alfresco-schemas "2025.09.19-0830")))

;; --- UTILITY FUNCTIONS ---

(defn validate-alfresco-node
  "Validate an Alfresco node against the schema"
  [node]
  (m/validate Node node))

(defn validate-yakread-content
  "Validate yakread content for XTDB storage"
  [content]
  (m/validate YakreadContent content))

(defn validate-uix-content
  "Validate content for UIX frontend"
  [content]
  (m/validate UIXContent content))

(defn transform-alfresco->yakread
  "Transform Alfresco node to yakread content format"
  [node]
  (m/decode YakreadContent node alfresco->yakread-transform))

(defn transform-yakread->uix
  "Transform yakread content to UIX format"
  [yakread-content]
  (m/decode UIXContent yakread-content yakread->uix-transform))

(defn explain-validation-error
  "Get human-readable validation error"
  [schema data]
  (m/explain schema data))

;; Initialize the registry
(mr/set-default-registry! yakread-alfresco-registry)

(log/info "Yakread Alfresco schema registry initialized with"
          (count yakread-alfresco-registry) "schemas")