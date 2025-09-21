#!/usr/bin/env bb

;; Mt Zion Web Site Dashboard - Comprehensive status and content overview

(require '[babashka.curl :as curl]
         '[cheshire.core :as json]
         '[clojure.string :as str])

;; --- CONFIGURATION ---

(def alfresco-host "http://localhost:8080")
(def alfresco-user "admin")
(def alfresco-pass "admin")
(def api-base (str alfresco-host "/alfresco/api/-default-/public/alfresco/versions/1"))

;; Load existing configuration
(load-file "src/com/yakread/config/website_nodes.clj")
(alias 'nodes 'com.yakread.config.website-nodes)

;; --- HTTP CLIENT ---

(defn get-node [node-id]
  (let [resp (curl/get (str api-base "/nodes/" node-id)
                       {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      {:success true :data (json/parse-string (:body resp) true)}
      {:success false :error (:body resp)})))

(defn get-node-children [node-id]
  (let [resp (curl/get (str api-base "/nodes/" node-id "/children")
                       {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      {:success true :data (json/parse-string (:body resp) true)}
      {:success false :error (:body resp)})))

;; --- CONTENT ANALYSIS ---

(defn analyze-content-item [entry]
  "Analyze a content item and classify it"
  (let [item (:entry entry)
        name (:name item)
        mime-type (get-in item [:content :mimeType])
        size (get-in item [:content :sizeInBytes])]

    {:name name
     :type (cond
             (:isFolder item) :folder
             (= mime-type "text/html") :html
             (str/starts-with? (or mime-type "") "image/") :image
             (= mime-type "text/plain") :text
             :else :other)
     :mime-type mime-type
     :size size
     :modified-at (:modifiedAt item)}))

(defn analyze-folder-contents [node-id folder-name]
  "Get detailed analysis of folder contents"
  (let [result (get-node-children node-id)]
    (if (:success result)
      (let [entries (get-in result [:data :list :entries])
            analyzed (map analyze-content-item entries)
            by-type (group-by :type analyzed)]

        {:folder-name folder-name
         :node-id node-id
         :total-items (count analyzed)
         :folders (count (:folder by-type))
         :html-files (count (:html by-type))
         :images (count (:image by-type))
         :text-files (count (:text by-type))
         :other-files (count (:other by-type))
         :items analyzed
         :success true})

      {:folder-name folder-name
       :node-id node-id
       :error (:error result)
       :success false})))

;; --- DASHBOARD FUNCTIONS ---

(defn generate-website-overview []
  "Generate overview of entire Web Site structure"
  (println "🏠 MT ZION WEB SITE DASHBOARD")
  (println "   " (java.time.LocalDateTime/now))
  (println (str (repeat 60 "=")))

  ;; Web Site root status
  (let [root-result (get-node nodes/website-root-node-id)]
    (if (:success root-result)
      (let [root-data (get-in root-result [:data :entry])]
        (println "\n📁 WEB SITE ROOT STATUS:")
        (println "   ✅ Accessible")
        (println "   📝 Name:" (:name root-data))
        (println "   🕒 Modified:" (:modifiedAt root-data)))
      (println "\n❌ WEB SITE ROOT: Inaccessible"))))

(defn analyze-all-pages []
  "Analyze content in all page folders"
  (println "\n📄 PAGE ANALYSIS:")

  (let [page-analyses (for [[page-name node-id] nodes/page-node-ids]
                        [page-name (analyze-folder-contents node-id (name page-name))])
        successful (filter #(get-in % [1 :success]) page-analyses)
        failed (filter #(not (get-in % [1 :success])) page-analyses)]

    (println "   📊 Pages analyzed:" (count page-analyses))
    (println "   ✅ Successful:" (count successful))
    (println "   ❌ Failed:" (count failed))

    ;; Summary table
    (println "\n   📋 CONTENT SUMMARY:")
    (println "   " (format "%-12s %5s %4s %4s %4s %4s" "PAGE" "TOTAL" "HTML" "IMG" "TXT" "FLDR"))
    (println "   " (str (repeat 45 "-")))

    (doseq [[page-name analysis] successful]
      (println "   " (format "%-12s %5d %4d %4d %4d %4d"
                               (str/capitalize (name page-name))
                               (:total-items analysis)
                               (:html-files analysis)
                               (:images analysis)
                               (:text-files analysis)
                               (:folders analysis))))

    ;; Failed pages
    (when (seq failed)
      (println "\n   ❌ FAILED TO ANALYZE:")
      (doseq [[page-name analysis] failed]
        (println "     -" (name page-name) ":" (:error analysis))))

    successful))

(defn analyze-homepage-components []
  "Detailed analysis of homepage components"
  (println "\n🧩 HOMEPAGE COMPONENTS:")

  (let [component-analyses (for [[comp-name node-id] nodes/homepage-component-node-ids]
                             [comp-name (analyze-folder-contents node-id (name comp-name))])
        successful (filter #(get-in % [1 :success]) component-analyses)]

    (doseq [[comp-name analysis] successful]
      (println "\n   📁" (str/upper-case (name comp-name)) "component:")
      (println "     📊 Total items:" (:total-items analysis))

      (when (pos? (:html-files analysis))
        (println "     📄 HTML files:" (:html-files analysis))
        (doseq [item (filter #(= :html (:type %)) (:items analysis))]
          (println "       -" (:name item) "(" (:size item) "bytes)")))

      (when (pos? (:images analysis))
        (println "     🖼️  Images:" (:images analysis))
        (doseq [item (filter #(= :image (:type %)) (:items analysis))]
          (println "       -" (:name item) "(" (:mime-type item) ")")))

      (when (zero? (:total-items analysis))
        (println "     📭 Empty component - ready for content")))

    successful))

(defn generate_content_recommendations []
  "Generate recommendations based on content analysis"
  (println "\n💡 RECOMMENDATIONS:")

  ;; Check for empty components
  (let [empty-components (for [[comp-name node-id] nodes/homepage-component-node-ids
                               :let [analysis (analyze-folder-contents node-id (name comp-name))]
                               :when (and (:success analysis) (zero? (:total-items analysis)))]
                           comp-name)]

    (when (seq empty-components)
      (println "   📭 Empty components ready for content:")
      (doseq [comp empty-components]
        (println "     -" (name comp) "component"))))

  ;; Check for missing HTML content
  (let [html-needed (for [[comp-name node-id] nodes/homepage-component-node-ids
                          :let [analysis (analyze-folder-contents node-id (name comp-name))]
                          :when (and (:success analysis) (zero? (:html-files analysis)) (pos? (:total-items analysis)))]
                      comp-name)]

    (when (seq html-needed)
      (println "   📄 Components with content but no HTML:")
      (doseq [comp html-needed]
        (println "     -" (name comp) "component (has files but no HTML content)"))))

  (println "   🔄 Regular monitoring: Run this dashboard daily to track changes")
  (println "   🛠️  Update config: Run update_nodes_config.clj if structure changes"))

;; --- MAIN DASHBOARD ---

(defn -main []
  (println "Generating Mt Zion Web Site Dashboard...")
  (println "Make sure SSH tunnel is running: ssh -L 8080:localhost:8080 tmb@trust\n")

  (try
    ;; Generate overview
    (generate-website-overview)

    ;; Analyze pages
    (let [page-analyses (analyze-all-pages)]

      ;; Analyze homepage components
      (analyze-homepage-components)

      ;; Generate recommendations
      (generate_content_recommendations)

      (println "\n" (str (repeat 60 "=")))
      (println "✅ Dashboard generated successfully!")
      (println "📊 Total pages configured:" (count nodes/page-node-ids))
      (println "🧩 Total homepage components:" (count nodes/homepage-component-node-ids))

      {:success true
       :pages-analyzed (count page-analyses)
       :timestamp (java.time.Instant/now)})

    (catch Exception e
      (println "❌ Dashboard generation failed:" (.getMessage e))
      {:success false :error (.getMessage e)})))

;; Run dashboard
(-main)