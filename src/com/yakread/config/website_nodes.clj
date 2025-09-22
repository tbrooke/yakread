(ns com.yakread.config.website-nodes
  "Hardcoded Alfresco node IDs for Mt Zion Web Site content structure
   These node IDs point directly to folders in Alfresco, eliminating the need for searching")

;; --- ROOT WEB SITE STRUCTURE ---

(def website-root-node-id
  "Root Web Site folder in Alfresco swsdp site"
  "21f2687f-7b6c-403a-b268-7f7b6c803a85")

;; --- PAGE FOLDER NODE IDS ---
;; Top-level folders under Web Site - these map to pages in mtzUIX

(def page-node-ids
  "Mapping of page names to Alfresco folder node IDs"
  {;; Main website pages
   :homepage    "9faac48b-6c77-4266-aac4-8b6c7752668a"  ; Home Page folder
   :about       "8158a6aa-dbd7-4f5b-98a6-aadbd72f5b3b"  ; About folder
   :activities  "bb44a590-1c61-416b-84a5-901c61716b5e"  ; Activities folder
   :contact     "acfd9bd1-1e61-4c3b-bd9b-d11e611c3bc0"  ; Contact folder
   :news        "fd02c48b-3d27-4df7-82c4-8b3d27adf701"  ; News folder
   :outreach    "b0774f12-4ea4-4851-b74f-124ea4f851a7"  ; Outreach folder
   :preschool   "915ea06b-4d65-4d5c-9ea0-6b4d65bd5cba"  ; Preschool folder
   :worship     "2cf1aac5-8577-499e-b1aa-c58577a99ea0"  ; Worship folder
   :events      "4f6972f5-9d50-4ff3-a972-f59d500ff3f4"  ; Mt Zion Calendar folder in SWSDP site
   })

;; --- HOMEPAGE COMPONENT FOLDER NODE IDS ---
;; Subfolders under Home Page - these map to components on the homepage

(def homepage-component-node-ids
  "Mapping of homepage component names to Alfresco folder node IDs"
  {:feature1  "264ab06c-984e-4f64-8ab0-6c984eaf6440"  ; Feature 1 folder
   :feature2  "fe3c64bf-bb1b-456f-bc64-bfbb1b656f89"  ; Feature 2 folder
   :feature3  "6737d1b1-5465-4625-b7d1-b15465b62530"  ; Feature 3 folder
   :hero      "39985c5c-201a-42f6-985c-5c201a62f6d8"  ; Hero folder
   })

;; --- EXAMPLE DOCUMENT REFERENCE ---
;; For testing - the Welcome document in Feature 1

(def sample-document-node-id
  "Sample HTML document for testing"
  "96840559-ce06-43d9-8405-59ce06c3d9bf")  ; Welcome document in Feature 1

;; --- UTILITY FUNCTIONS ---

(defn get-page-node-id
  "Get node ID for a specific page"
  [page-keyword]
  (get page-node-ids page-keyword))

(defn get-homepage-component-node-id
  "Get node ID for a specific homepage component"
  [component-keyword]
  (get homepage-component-node-ids component-keyword))

(defn get-all-page-node-ids
  "Get all page node IDs for bulk operations"
  []
  (vals page-node-ids))

(defn get-all-homepage-component-node-ids
  "Get all homepage component node IDs"
  []
  (vals homepage-component-node-ids))

(defn get-all-component-nodes
  "Get all component nodes (pages + homepage components) as keyword->node-id map"
  []
  (merge page-node-ids homepage-component-node-ids))

;; --- REVERSE MAPPING ---
;; For when we have a node ID and need to know what page/component it represents

(def node-id-to-page
  "Reverse mapping: node ID -> page keyword"
  (into {} (map (fn [[k v]] [v k]) page-node-ids)))

(def node-id-to-homepage-component
  "Reverse mapping: node ID -> homepage component keyword"
  (into {} (map (fn [[k v]] [v k]) homepage-component-node-ids)))

(defn identify-node
  "Given a node ID, identify what page or component it represents"
  [node-id]
  (cond
    (= node-id website-root-node-id) {:type :website-root}
    (contains? node-id-to-page node-id) {:type :page
                                         :page (get node-id-to-page node-id)}
    (contains? node-id-to-homepage-component node-id) {:type :homepage-component
                                                        :component (get node-id-to-homepage-component node-id)}
    :else {:type :unknown :node-id node-id}))

;; --- CONFIGURATION SUMMARY ---
(comment
  ;; Usage examples:

  ;; Get homepage folder node ID
  (get-page-node-id :homepage)
  ;; => "9faac48b-6c77-4266-aac4-8b6c7752668a"

  ;; Get feature1 component node ID
  (get-homepage-component-node-id :feature1)
  ;; => "264ab06c-984e-4f64-8ab0-6c984eaf6440"

  ;; Identify what a node ID represents
  (identify-node "264ab06c-984e-4f64-8ab0-6c984eaf6440")
  ;; => {:type :homepage-component, :component :feature1}

  ;; Get all page folders for bulk sync
  (get-all-page-node-ids)
  ;; => ["9faac48b-6c77-4266-aac4-8b6c7752668a" "8158a6aa-dbd7-4f5b-98a6-aadbd72f5b3b" ...]
)