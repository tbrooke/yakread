(ns com.yakread.alfresco.component-mapper
  "Maps Alfresco Web Site folder structure to mtzUIX components automatically"
  (:require [com.yakread.config.website-nodes :as nodes]
            [clojure.string :as str]
            [clojure.tools.logging :as log]))

;; --- FOLDER NAME TO COMPONENT MAPPING ---

(defn normalize-folder-name
  "Convert folder name to component-friendly keyword"
  [folder-name]
  (-> folder-name
      str/lower-case
      (str/replace #"\s+" "")     ; Remove spaces
      (str/replace #"[^a-z0-9]" "") ; Remove special chars
      keyword))

(defn folder-to-component-type
  "Determine component type based on folder name and content"
  [folder-name content-items]
  (let [normalized (normalize-folder-name folder-name)
        has-html? (some #(= "text/html" (get-in % [:content :mimeType])) content-items)
        has-images? (some #(str/starts-with? (get-in % [:content :mimeType] "") "image/") content-items)]

    (cond
      ;; Specific folder name patterns
      (str/includes? (str/lower-case folder-name) "hero") :hero
      (str/includes? (str/lower-case folder-name) "feature") :feature
      (str/includes? (str/lower-case folder-name) "banner") :banner
      (str/includes? (str/lower-case folder-name) "news") :news-feed
      (str/includes? (str/lower-case folder-name) "gallery") :image-gallery

      ;; Content-based detection
      (and has-html? has-images?) :rich-content
      has-html? :text-content
      has-images? :image-content

      ;; Default
      :else :generic-content)))

;; --- CONTENT CLASSIFICATION ---

(defn classify-content-item
  "Classify a content item for mtzUIX consumption"
  [item-entry]
  (let [name (:name item-entry)
        mime-type (get-in item-entry [:content :mimeType])
        size (get-in item-entry [:content :sizeInBytes])]

    {:node-id (:id item-entry)
     :name name
     :original-name name
     :display-name (str/replace name #"\.[^.]+$" "") ; Remove file extension
     :mime-type mime-type
     :size size
     :type (cond
             (:isFolder item-entry) :folder
             (= mime-type "text/html") :html-content
             (= mime-type "text/plain") :text-content
             (str/starts-with? mime-type "image/") :image
             (= mime-type "application/pdf") :pdf
             :else :file)
     :mtzuix-component (cond
                         (= mime-type "text/html") :html-block
                         (str/starts-with? mime-type "image/") :image-display
                         (= mime-type "text/plain") :text-block
                         :else :file-download)}))

;; --- COMPONENT MAPPING LOGIC ---

(defn map-folder-to-mtzuix-component
  "Map an Alfresco folder to an mtzUIX component structure"
  [folder-name folder-node-id content-items]
  (let [component-type (folder-to-component-type folder-name content-items)
        component-name (normalize-folder-name folder-name)
        classified-items (map (comp classify-content-item :entry) content-items)]

    {:component-name component-name
     :component-type component-type
     :original-folder-name folder-name
     :alfresco-node-id folder-node-id
     :content-count (count classified-items)

     ;; Organize content by type
     :content {:html-content (filter #(= :html-content (:type %)) classified-items)
               :images (filter #(= :image (:type %)) classified-items)
               :text-content (filter #(= :text-content (:type %)) classified-items)
               :files (filter #(= :file (:type %)) classified-items)
               :all classified-items}

     ;; mtzUIX-specific mappings
     :mtzuix {:target-page :homepage  ; Will be dynamic based on parent folder
              :component-id (str "homepage-" (name component-name))
              :display-order (case component-name
                               :hero 1
                               :feature1 2
                               :feature2 3
                               :feature3 4
                               99) ; Default to end
              :props {:title folder-name
                      :content-source :alfresco
                      :auto-refresh true}}}))

;; --- PAGE-LEVEL MAPPING ---

(defn map-page-to-mtzuix-structure
  "Map an entire page (folder + subfolders) to mtzUIX structure"
  [page-keyword folder-name content-structure]
  {:page page-keyword
   :original-folder-name folder-name
   :alfresco-node-id (nodes/get-page-node-id page-keyword)
   :components (for [[component-folder-name component-data] content-structure]
                 (map-folder-to-mtzuix-component
                   component-folder-name
                   (:node-id component-data)
                   (:content component-data)))
   :total-components (count content-structure)
   :last-updated (java.time.Instant/now)})

;; --- SPECIAL HOMEPAGE MAPPING ---

(defn map-homepage-with-known-components
  "Map homepage using our pre-known component structure"
  [homepage-content-structure]
  (let [component-mappings (for [[component-keyword component-data] homepage-content-structure]
                             (let [folder-name (case component-keyword
                                                 :feature1 "Feature 1"
                                                 :feature2 "Feature 2"
                                                 :feature3 "Feature 3"
                                                 :hero "Hero"
                                                 (name component-keyword))]
                               (map-folder-to-mtzuix-component
                                 folder-name
                                 (:node-id component-data)
                                 (:content component-data))))]

    {:page :homepage
     :original-folder-name "Home Page"
     :alfresco-node-id (nodes/get-page-node-id :homepage)
     :components component-mappings
     :total-components (count component-mappings)
     :component-map (into {} (map (fn [comp] [(:component-name comp) comp]) component-mappings))
     :last-updated (java.time.Instant/now)}))

;; --- CONTENT TARGETING FOR MTZUIX ---

(defn generate-mtzuix-content-targets
  "Generate content targets for mtzUIX frontend consumption"
  [mapped-page]
  (for [component (:components mapped-page)]
    (let [component-name (:component-name component)
          html-items (get-in component [:content :html-content])
          image-items (get-in component [:content :images])]

      {:target-page (:page mapped-page)
       :target-component component-name
       :component-type (:component-type component)
       :display-order (get-in component [:mtzuix :display-order])

       ;; Content items ready for mtzUIX
       :content-items (concat
                        ;; HTML content with component targeting
                        (map (fn [html-item]
                               {:type :html-block
                                :node-id (:node-id html-item)
                                :name (:display-name html-item)
                                :mime-type (:mime-type html-item)
                                :component-target (str (name (:page mapped-page)) "-" (name component-name))
                                :fetch-content? true}) html-items)

                        ;; Images with component targeting
                        (map (fn [img-item]
                               {:type :image-display
                                :node-id (:node-id img-item)
                                :name (:display-name img-item)
                                :mime-type (:mime-type img-item)
                                :component-target (str (name (:page mapped-page)) "-" (name component-name))
                                :fetch-content? false  ; Images fetched via URL
                                :image-url (str "/api/alfresco/content/" (:node-id img-item))}) image-items))})))

;; --- UTILITY FUNCTIONS ---

(defn summarize-mapping
  "Create a summary of the mapping for logging/debugging"
  [mapped-page]
  {:page (:page mapped-page)
   :total-components (:total-components mapped-page)
   :component-summary (map (fn [comp]
                             {:name (:component-name comp)
                              :type (:component-type comp)
                              :content-count (:content-count comp)
                              :has-html? (pos? (count (get-in comp [:content :html-content])))
                              :has-images? (pos? (count (get-in comp [:content :images])))})
                           (:components mapped-page))})

(comment
  ;; Usage examples:

  ;; Map a folder to component
  (map-folder-to-mtzuix-component "Feature 1" "node-id-123"
                                  [{:entry {:name "Welcome.html" :content {:mimeType "text/html"}}}])

  ;; Map homepage with known structure
  (map-homepage-with-known-components homepage-data)

  ;; Generate mtzUIX targets
  (generate-mtzuix-content-targets mapped-homepage)

  ;; Summarize mapping
  (summarize-mapping mapped-homepage)
)