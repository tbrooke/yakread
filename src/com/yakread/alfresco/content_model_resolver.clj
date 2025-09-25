(ns com.yakread.alfresco.content-model-resolver
  "Flexible Pathom resolvers for Alfresco content models
   Handles both standard and custom content types dynamically"
  (:require
   [clojure.tools.logging :as log]
   [com.yakread.lib.alfresco :as alfresco-lib]
   [com.wsscode.pathom3.connect.operation :as pco]
   [com.wsscode.pathom3.connect.indexes :as pci]
   [com.wsscode.pathom3.interface.eql :as p.eql]))

;; --- CONTENT MODEL DISCOVERY ---

(pco/defresolver node-type-info
  "Discover the content type of any Alfresco node"
  [{:keys [alfresco]} {:alfresco/keys [node-id]}]
  {::pco/input [:alfresco/node-id]
   ::pco/output [:alfresco/type
                 :alfresco/type-title
                 :alfresco/is-folder
                 :alfresco/is-document
                 :alfresco/aspects]}
  (log/info "Resolving type info for node:" node-id)
  (let [node-info (alfresco-lib/get-node alfresco node-id)]
    (when (:success node-info)
      (let [node (:body node-info)]
        {:alfresco/type (get-in node [:entry :nodeType])
         :alfresco/type-title (get-in node [:entry :nodeType]) ; Could be enhanced with type definitions
         :alfresco/is-folder (get-in node [:entry :isFolder])
         :alfresco/is-document (not (get-in node [:entry :isFolder]))
         :alfresco/aspects (get-in node [:entry :aspectNames] [])}))))

(pco/defresolver node-properties
  "Get all properties of a node, regardless of type"
  [{:keys [alfresco]} {:alfresco/keys [node-id]}]
  {::pco/input [:alfresco/node-id]
   ::pco/output [:alfresco/properties]}
  (log/info "Resolving properties for node:" node-id)
  (let [node-info (alfresco-lib/get-node alfresco node-id)]
    (when (:success node-info)
      {:alfresco/properties (get-in node-info [:body :entry :properties] {})})))

(pco/defresolver standard-metadata
  "Extract standard cm:content metadata"
  [{:keys [alfresco]} {:alfresco/keys [node-id]}]
  {::pco/input [:alfresco/node-id]
   ::pco/output [:cm/name
                 :cm/title
                 :cm/description
                 :cm/created
                 :cm/creator
                 :cm/modified
                 :cm/modifier]}
  (log/info "Resolving standard metadata for node:" node-id)
  (let [node-info (alfresco-lib/get-node alfresco node-id)]
    (when (:success node-info)
      (let [entry (:entry (:body node-info))
            props (:properties entry)]
        {:cm/name (:name entry)
         :cm/title (get props :cm:title)
         :cm/description (get props :cm:description)
         :cm/created (:createdAt entry)
         :cm/creator (get-in entry [:createdByUser :displayName])
         :cm/modified (:modifiedAt entry)
         :cm/modifier (get-in entry [:modifiedByUser :displayName])}))))

;; --- DYNAMIC PROPERTY RESOLVERS ---

(defn create-property-resolver
  "Create a resolver for a specific property namespace (e.g., 'mtz:', 'custom:')"
  [property-prefix resolver-name]
  (pco/resolver resolver-name
    {::pco/input [:alfresco/node-id :alfresco/properties]
     ::pco/output [::pco/output-attribute-wildcard]
     ::pco/dynamic-output? true}
    (fn [_env {:alfresco/keys [properties]}]
      (let [prefix-keyword (keyword property-prefix)
            filtered-props (reduce-kv
                           (fn [acc k v]
                             (if (str/starts-with? (name k) (str property-prefix ":"))
                               ;; Convert "mtz:title" to :mtz/title
                               (let [clean-name (subs (name k) (inc (count property-prefix)))
                                     ns-keyword (keyword property-prefix clean-name)]
                                 (assoc acc ns-keyword v))
                               acc))
                           {}
                           properties)]
        (log/debug "Resolved" (count filtered-props) "properties for prefix" property-prefix)
        filtered-props))))

;; Pre-defined resolvers for common namespaces
(def mtz-properties-resolver
  (create-property-resolver "mtz" `mtz-properties))

(def custom-properties-resolver
  (create-property-resolver "custom" `custom-properties))

;; --- CONTENT TYPE SPECIFIC RESOLVERS ---

(pco/defresolver html-content-data
  "Extract data specific to HTML content"
  [{:keys [alfresco]} {:alfresco/keys [node-id type]}]
  {::pco/input [:alfresco/node-id :alfresco/type]
   ::pco/output [:html/content
                 :html/mime-type
                 :html/size
                 :html/encoding]}
  (when (= type "cm:content")
    (let [node-info (alfresco-lib/get-node alfresco node-id)]
      (when (:success node-info)
        (let [content-info (get-in node-info [:body :entry :content])]
          (when (= "text/html" (:mimeType content-info))
            {:html/mime-type (:mimeType content-info)
             :html/size (:sizeInBytes content-info)
             :html/encoding (:encoding content-info)
             ;; Actual content would need separate API call
             :html/content nil}))))))

(pco/defresolver folder-children-summary
  "Get summary of folder contents"
  [{:keys [alfresco]} {:alfresco/keys [node-id is-folder]}]
  {::pco/input [:alfresco/node-id :alfresco/is-folder]
   ::pco/output [:folder/child-count
                 :folder/has-subfolders
                 :folder/has-documents]}
  (when is-folder
    (let [children-result (alfresco-lib/list-folder-children alfresco node-id)]
      (when (:success children-result)
        (let [entries (get-in children-result [:body :list :entries])
              folders (filter #(get-in % [:entry :isFolder]) entries)
              documents (remove #(get-in % [:entry :isFolder]) entries)]
          {:folder/child-count (count entries)
           :folder/has-subfolders (> (count folders) 0)
           :folder/has-documents (> (count documents) 0)})))))

;; --- ASPECT-BASED RESOLVERS ---

(pco/defresolver taggable-aspect-data
  "Extract data from cm:taggable aspect"
  [{:alfresco/keys [aspects properties]}]
  {::pco/input [:alfresco/aspects :alfresco/properties]
   ::pco/output [:tags/list]}
  (when (some #{"cm:taggable"} aspects)
    {:tags/list (get properties :cm:taggable [])}))

(pco/defresolver versionable-aspect-data
  "Extract version information"
  [{:alfresco/keys [aspects properties]}]
  {::pco/input [:alfresco/aspects :alfresco/properties]
   ::pco/output [:version/number :version/label]}
  (when (some #{"cm:versionable"} aspects)
    {:version/number (get properties :cm:versionNumber)
     :version/label (get properties :cm:versionLabel)}))

;; --- CONTENT MODEL INTROSPECTION ---

(pco/defresolver discover-node-model
  "Discover all available properties and aspects for a node"
  [{:keys [alfresco]} {:alfresco/keys [node-id]}]
  {::pco/input [:alfresco/node-id]
   ::pco/output [:model/type
                 :model/aspects
                 :model/all-properties
                 :model/property-namespaces]}
  (let [node-info (alfresco-lib/get-node alfresco node-id)]
    (when (:success node-info)
      (let [entry (:entry (:body node-info))
            properties (:properties entry)
            prop-namespaces (->> (keys properties)
                               (map name)
                               (map #(first (str/split % #":")))
                               (distinct)
                               (sort))]
        {:model/type (:nodeType entry)
         :model/aspects (:aspectNames entry)
         :model/all-properties properties
         :model/property-namespaces prop-namespaces}))))

;; --- FLEXIBLE CONTENT QUERY BUILDER ---

(defn build-content-graph
  "Build a complete content graph for any node type"
  [ctx node-id]
  (let [parser (p.eql/boundary-interface ctx)]
    (parser ctx
            [{[:alfresco/node-id node-id]
              [;; Always get basic info
               :alfresco/type
               :alfresco/aspects
               :alfresco/properties
               
               ;; Standard metadata
               :cm/name
               :cm/title
               :cm/description
               
               ;; Dynamic properties (will resolve based on what's there)
               :mtz/title          ; Will resolve if exists
               :mtz/target-page
               :mtz/page-section
               
               ;; Conditional data based on type
               :html/mime-type
               :folder/child-count
               
               ;; Model discovery
               :model/property-namespaces]}])))

;; --- SMART CONTENT ROUTER ---

(pco/defresolver route-by-content-type
  "Route to appropriate processing based on content type"
  [{:alfresco/keys [type properties]}]
  {::pco/input [:alfresco/type :alfresco/properties]
   ::pco/output [:content/processing-type
                 :content/suggested-template]}
  (cond
    ;; Custom Mt Zion types
    (str/starts-with? type "mtz:")
    {:content/processing-type :custom-mtz
     :content/suggested-template (keyword "template" (subs type 4))}
    
    ;; Standard Alfresco types
    (= type "cm:content")
    (let [mime-type (get-in properties [:content :mimeType])]
      (cond
        (= mime-type "text/html")
        {:content/processing-type :html-content
         :content/suggested-template :template/article}
        
        (str/starts-with? (str mime-type) "image/")
        {:content/processing-type :image
         :content/suggested-template :template/image-display}
        
        :else
        {:content/processing-type :generic-content
         :content/suggested-template :template/download}))
    
    (= type "cm:folder")
    {:content/processing-type :folder
     :content/suggested-template :template/listing}
    
    :else
    {:content/processing-type :unknown
     :content/suggested-template :template/debug}))

;; --- USAGE EXAMPLES ---

(comment
  ;; Basic usage - discover what's in a node
  (def ctx {:alfresco alfresco-config})
  
  ;; Get everything about a node
  (build-content-graph ctx "node-id-123")
  
  ;; Query specific Mt Zion properties
  (parser ctx
          [{[:alfresco/node-id "node-id-123"]
            [:mtz/title
             :mtz/target-page  
             :mtz/page-section
             :mtz/display-order]}])
  
  ;; Discover model for a node
  (parser ctx
          [{[:alfresco/node-id "node-id-123"]
            [:model/type
             :model/aspects
             :model/property-namespaces]}])
)