(ns com.yakread.alfresco.pathom-content
  "Basic Pathom resolvers for Alfresco content models
   Starting simple with cm:content before adding complexity"
  (:require
   [com.wsscode.pathom3.connect.operation :as pco]
   [clojure.tools.logging :as log]
   [com.yakread.alfresco.client :as alfresco]))

;; --- BASIC CONTENT RESOLVERS ---

(pco/defresolver content-properties
  "Resolve basic properties for any Alfresco content node"
  [{:keys [alfresco]} {:content/keys [node-id]}]
  {::pco/input  [:content/node-id]
   ::pco/output [:content/name
                 :content/type
                 :content/mime-type
                 :content/size
                 :content/created
                 :content/modified
                 :content/creator
                 :content/modifier]}
  (when-let [node (alfresco/get-node alfresco node-id)]
    {:content/name      (:name node)
     :content/type      (:type node)
     :content/mime-type (get-in node [:content :mimeType])
     :content/size      (get-in node [:content :sizeInBytes])
     :content/created   (:createdAt node)
     :content/modified  (:modifiedAt node)
     :content/creator   (get-in node [:createdByUser :displayName])
     :content/modifier  (get-in node [:modifiedByUser :displayName])}))

(pco/defresolver content-metadata
  "Resolve cm:titled properties (title, description)"
  [{:keys [alfresco]} {:content/keys [node-id]}]
  {::pco/input  [:content/node-id]
   ::pco/output [:content/title
                 :content/description
                 :content/author]}
  (when-let [node (alfresco/get-node alfresco node-id)]
    {:content/title       (get-in node [:properties :cm:title])
     :content/description (get-in node [:properties :cm:description])
     :content/author      (get-in node [:properties :cm:author])}))

(pco/defresolver content-body
  "Resolve the actual content (HTML, text, etc.)"
  [{:keys [alfresco]} {:content/keys [node-id]}]
  {::pco/input  [:content/node-id]
   ::pco/output [:content/body
                 :content/encoding]}
  (when-let [content-result (alfresco/get-node-content alfresco node-id)]
    (when (:success content-result)
      {:content/body     (:data content-result)
       :content/encoding (or (:encoding content-result) "UTF-8")})))

(pco/defresolver content-location
  "Resolve where this content lives in the repository"
  [{:keys [alfresco]} {:content/keys [node-id]}]
  {::pco/input  [:content/node-id]
   ::pco/output [:content/path
                 :content/parent-id
                 :content/site
                 :content/folder-path]}
  (when-let [node (alfresco/get-node alfresco node-id)]
    (let [path-elements (get-in node [:path :elements])
          site-name (some #(when (= "st:site" (:type %)) (:name %)) path-elements)]
      {:content/path        (get-in node [:path :name])
       :content/parent-id   (:parentId node)
       :content/site        site-name
       :content/folder-path (str/join "/" (map :name path-elements))})))

;; --- CONTENT TYPE DETECTION ---

(pco/defresolver content-classification
  "Classify content based on location and type"
  [{:content/keys [path mime-type name]}]
  {::pco/input  [:content/path :content/mime-type :content/name]
   ::pco/output [:content/classification
                 :content/is-blog-post
                 :content/is-article
                 :content/is-feature
                 :content/page-section]}
  (let [path-lower (str/lower-case path)
        is-html    (= "text/html" mime-type)]
    {:content/classification (cond
                              (str/includes? path-lower "blog")     :blog-post
                              (str/includes? path-lower "article")  :article
                              (str/includes? path-lower "news")     :news
                              (str/includes? path-lower "feature")  :feature
                              (str/includes? path-lower "hero")     :hero
                              :else                                 :general)
     :content/is-blog-post   (and is-html (str/includes? path-lower "blog"))
     :content/is-article     (and is-html (or (str/includes? path-lower "article")
                                               (str/includes? path-lower "news")))
     :content/is-feature     (and is-html (str/includes? path-lower "feature"))
     :content/page-section   (cond
                              (str/includes? path-lower "feature 1") :feature1
                              (str/includes? path-lower "feature 2") :feature2
                              (str/includes? path-lower "feature 3") :feature3
                              (str/includes? path-lower "hero")      :hero
                              :else nil)}))

;; --- FOLDER-BASED CONTENT DISCOVERY ---

(pco/defresolver folder-contents
  "List all content in a folder"
  [{:keys [alfresco]} {:folder/keys [node-id]}]
  {::pco/input  [:folder/node-id]
   ::pco/output [{:folder/children [:content/node-id
                                    :content/name
                                    :content/type
                                    :content/mime-type]}]}
  (when-let [children-result (alfresco/get-folder-children alfresco node-id)]
    (when (:success children-result)
      {:folder/children (mapv (fn [child]
                               {:content/node-id  (:node-id child)
                                :content/name     (:name child)
                                :content/type     (:type child)
                                :content/mime-type (:mime-type child)})
                             (:children children-result))})))

;; --- SITE STRUCTURE NAVIGATION ---

(pco/defresolver site-structure
  "Navigate Mt Zion site structure"
  [{:keys [alfresco]} {:site/keys [name]}]
  {::pco/input  [:site/name]
   ::pco/output [:site/document-library-id
                 :site/web-site-folder-id
                 {:site/main-folders [:folder/name
                                      :folder/node-id]}]}
  ;; This would navigate to find key folders
  ;; For now, return known structure
  (when (= "swsdp" name)
    {:site/document-library-id "8f2105b4-daaf-4874-9e8a-2152569d109b"
     :site/web-site-folder-id  "21f2687f-7b6c-403a-b268-7f7b6c803a85"
     :site/main-folders [{:folder/name "Home Page" 
                         :folder/node-id "9faac48b-6c77-4266-aac4-8b6c7752668a"}
                        {:folder/name "About"
                         :folder/node-id "8158a6aa-dbd7-4f5b-98a6-aadbd72f5b3b"}
                        {:folder/name "Worship"
                         :folder/node-id "2cf1aac5-8577-499e-b1aa-c58577a99ea0"}]}))

;; --- REGISTER ALL RESOLVERS ---

(def all-resolvers
  "All content model resolvers"
  [content-properties
   content-metadata
   content-body
   content-location
   content-classification
   folder-contents
   site-structure])

;; --- EXAMPLE QUERIES ---

(comment
  ;; Basic content query
  (pathom-parser ctx
    [{[:content/node-id "7d6d4b12-a339-4f10-ad4b-12a339bf1080"]
      [:content/name
       :content/title
       :content/description
       :content/mime-type
       :content/body]}])
  
  ;; Richer query with classification
  (pathom-parser ctx
    [{[:content/node-id "7d6d4b12-a339-4f10-ad4b-12a339bf1080"]
      [:content/name
       :content/title
       :content/path
       :content/classification
       :content/page-section
       :content/body]}])
  
  ;; Browse folder structure
  (pathom-parser ctx
    [{[:folder/node-id "fe3c64bf-bb1b-456f-bc64-bfbb1b656f89"]
      [:folder/name
       {:folder/children [:content/name
                         :content/title
                         :content/mime-type]}]}])
  
  ;; Navigate from site
  (pathom-parser ctx
    [{[:site/name "swsdp"]
      [{:site/main-folders [:folder/name
                           {:folder/children [:content/name]}]}]}])
)