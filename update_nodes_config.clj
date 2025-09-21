#!/usr/bin/env bb

;; Node Configuration Updater - Automatically updates website_nodes.clj when new pages/components are found

(require '[babashka.curl :as curl]
         '[cheshire.core :as json]
         '[clojure.edn :as edn]
         '[clojure.string :as str]
         '[clojure.java.io :as io])

;; --- CONFIGURATION ---

(def alfresco-host "http://localhost:8080")
(def alfresco-user "admin")
(def alfresco-pass "admin")
(def api-base (str alfresco-host "/alfresco/api/-default-/public/alfresco/versions/1"))

;; Load existing configuration
(load-file "src/com/yakread/config/website_nodes.clj")
(alias 'nodes 'com.yakread.config.website-nodes)

;; --- HTTP CLIENT ---

(defn get-node-children [node-id]
  "Get children of a node"
  (let [resp (curl/get (str api-base "/nodes/" node-id "/children")
                       {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      {:success true :data (json/parse-string (:body resp) true)}
      {:success false :error (:body resp)})))

;; --- DISCOVERY FUNCTIONS ---

(defn discover-all-pages []
  "Discover all page folders in Web Site"
  (let [result (get-node-children nodes/website-root-node-id)]
    (if (:success result)
      (let [entries (get-in result [:data :list :entries])
            folders (filter #(get-in % [:entry :isFolder]) entries)]

        (for [folder folders]
          (let [folder-data (:entry folder)]
            {:name (:name folder-data)
             :node-id (:id folder-data)
             :keyword (-> (:name folder-data)
                          str/lower-case
                          (str/replace #"\\s+" "")
                          (str/replace #"[^a-z0-9]" "")
                          keyword)})))
      [])))

(defn discover-homepage-components []
  "Discover all component folders in Home Page"
  (let [homepage-node-id (nodes/get-page-node-id :homepage)
        result (get-node-children homepage-node-id)]
    (if (:success result)
      (let [entries (get-in result [:data :list :entries])
            folders (filter #(get-in % [:entry :isFolder]) entries)]

        (for [folder folders]
          (let [folder-data (:entry folder)]
            {:name (:name folder-data)
             :node-id (:id folder-data)
             :keyword (-> (:name folder-data)
                          str/lower-case
                          (str/replace #"\\s+" "")
                          (str/replace #"[^a-z0-9]" "")
                          keyword)})))
      [])))

;; --- CONFIGURATION GENERATION ---

(defn generate-page-node-ids-map [discovered-pages]
  "Generate the page-node-ids map code"
  (let [entries (for [page discovered-pages]
                  (str "   " (:keyword page) " \"" (:node-id page) "\"  ; " (:name page) " folder"))]
    (str "(def page-node-ids\n"
         "  \"Mapping of page names to Alfresco folder node IDs\"\n"
         "  {" (str/join "\n" entries) "})")))

(defn generate-homepage-components-map [discovered-components]
  "Generate the homepage-component-node-ids map code"
  (let [entries (for [comp discovered-components]
                  (str "   " (:keyword comp) " \"" (:node-id comp) "\"  ; " (:name comp) " folder"))]
    (str "(def homepage-component-node-ids\n"
         "  \"Mapping of homepage component names to Alfresco folder node IDs\"\n"
         "  {" (str/join "\n" entries) "})")))

(defn generate-updated-config-file [discovered-pages discovered-components]
  "Generate complete updated configuration file"
  (let [timestamp (java.time.LocalDateTime/now)]
    (str "(ns com.yakread.config.website-nodes\n"
         "  \"Hardcoded Alfresco node IDs for Mt Zion Web Site content structure\n"
         "   These node IDs point directly to folders in Alfresco, eliminating the need for searching\n"
         "   Auto-generated on " timestamp "\")\n\n"

         ";; --- ROOT WEB SITE STRUCTURE ---\n\n"

         "(def website-root-node-id\n"
         "  \"Root Web Site folder in Alfresco swsdp site\"\n"
         "  \"" nodes/website-root-node-id "\")\n\n"

         ";; --- PAGE FOLDER NODE IDS ---\n"
         ";; Top-level folders under Web Site - these map to pages in mtzUIX\n\n"

         (generate-page-node-ids-map discovered-pages) "\n\n"

         ";; --- HOMEPAGE COMPONENT FOLDER NODE IDS ---\n"
         ";; Subfolders under Home Page - these map to components on the homepage\n\n"

         (generate-homepage-components-map discovered-components) "\n\n"

         ";; --- EXAMPLE DOCUMENT REFERENCE ---\n"
         ";; For testing - the Welcome document in Feature 1\n\n"

         "(def sample-document-node-id\n"
         "  \"Sample HTML document for testing\"\n"
         "  \"" nodes/sample-document-node-id "\")  ; Welcome document in Feature 1\n\n"

         ";; --- UTILITY FUNCTIONS ---\n\n"

         "(defn get-page-node-id\n"
         "  \"Get node ID for a specific page\"\n"
         "  [page-keyword]\n"
         "  (get page-node-ids page-keyword))\n\n"

         "(defn get-homepage-component-node-id\n"
         "  \"Get node ID for a specific homepage component\"\n"
         "  [component-keyword]\n"
         "  (get homepage-component-node-ids component-keyword))\n\n"

         "(defn get-all-page-node-ids\n"
         "  \"Get all page node IDs for bulk operations\"\n"
         "  []\n"
         "  (vals page-node-ids))\n\n"

         "(defn get-all-homepage-component-node-ids\n"
         "  \"Get all homepage component node IDs\"\n"
         "  []\n"
         "  (vals homepage-component-node-ids))\n\n"

         ";; --- REVERSE MAPPING ---\n"
         ";; For when we have a node ID and need to know what page/component it represents\n\n"

         "(def node-id-to-page\n"
         "  \"Reverse mapping: node ID -> page keyword\"\n"
         "  (into {} (map (fn [[k v]] [v k]) page-node-ids)))\n\n"

         "(def node-id-to-homepage-component\n"
         "  \"Reverse mapping: node ID -> homepage component keyword\"\n"
         "  (into {} (map (fn [[k v]] [v k]) homepage-component-node-ids)))\n\n"

         "(defn identify-node\n"
         "  \"Given a node ID, identify what page or component it represents\"\n"
         "  [node-id]\n"
         "  (cond\n"
         "    (= node-id website-root-node-id) {:type :website-root}\n"
         "    (contains? node-id-to-page node-id) {:type :page\n"
         "                                         :page (get node-id-to-page node-id)}\n"
         "    (contains? node-id-to-homepage-component node-id) {:type :homepage-component\n"
         "                                                        :component (get node-id-to-homepage-component node-id)}\n"
         "    :else {:type :unknown :node-id node-id}))\n")))

;; --- COMPARISON AND UPDATE LOGIC ---

(defn compare-with-current [discovered-pages discovered-components]
  "Compare discovered structure with current configuration"
  (let [current-pages (set (keys nodes/page-node-ids))
        current-components (set (keys nodes/homepage-component-node-ids))
        discovered-page-keywords (set (map :keyword discovered-pages))
        discovered-component-keywords (set (map :keyword discovered-components))

        new-pages (clojure.set/difference discovered-page-keywords current-pages)
        removed-pages (clojure.set/difference current-pages discovered-page-keywords)
        new-components (clojure.set/difference discovered-component-keywords current-components)
        removed-components (clojure.set/difference current-components discovered-component-keywords)]

    {:changes-detected (or (seq new-pages) (seq removed-pages) (seq new-components) (seq removed-components))
     :new-pages new-pages
     :removed-pages removed-pages
     :new-components new-components
     :removed-components removed-components
     :total-pages (count discovered-pages)
     :total-components (count discovered-components)}))

;; --- MAIN FUNCTION ---

(defn -main []
  (println "🔄 Mt Zion Node Configuration Updater")
  (println "   Checking for new pages and components...\n")

  (try
    ;; Discover current structure
    (let [discovered-pages (discover-all-pages)
          discovered-components (discover-homepage-components)]

      (println "📊 Current structure:")
      (println "   Pages found:" (count discovered-pages))
      (println "   Homepage components found:" (count discovered-components))

      ;; Compare with current config
      (let [comparison (compare-with-current discovered-pages discovered-components)]

        (if (:changes-detected comparison)
          (do
            (println "\n🔄 Changes detected!")

            (when (seq (:new-pages comparison))
              (println "   🆕 New pages:" (str/join ", " (map name (:new-pages comparison)))))

            (when (seq (:removed-pages comparison))
              (println "   ❌ Removed pages:" (str/join ", " (map name (:removed-pages comparison)))))

            (when (seq (:new-components comparison))
              (println "   🆕 New components:" (str/join ", " (map name (:new-components comparison)))))

            (when (seq (:removed-components comparison))
              (println "   ❌ Removed components:" (str/join ", " (map name (:removed-components comparison)))))

            ;; Generate updated config
            (let [updated-config (generate-updated-config-file discovered-pages discovered-components)
                  backup-file (str "src/com/yakread/config/website_nodes.clj.backup." (.toEpochMilli (java.time.Instant/now)))
                  config-file "src/com/yakread/config/website_nodes.clj"]

              ;; Backup current config
              (io/copy (io/file config-file) (io/file backup-file))
              (println "\n💾 Backed up current config to:" backup-file)

              ;; Write updated config
              (spit config-file updated-config)
              (println "✅ Updated configuration written to:" config-file)

              (println "\n📝 Updated configuration includes:")
              (println "   📁" (:total-pages comparison) "page mappings")
              (println "   🧩" (:total-components comparison) "component mappings")

              {:success true :changes true :comparison comparison}))

          (do
            (println "✅ No changes detected - configuration is up to date")
            {:success true :changes false}))))

    (catch Exception e
      (println "❌ Update failed:" (.getMessage e))
      {:success false :error (.getMessage e)})))

;; Run the updater
(-main)