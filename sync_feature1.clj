#!/usr/bin/env bb

;; Sync Feature 1 content from Alfresco to XTDB for mtzUIX display
;; Creates bitemporal records linking Alfresco source to displayed content

(require '[babashka.curl :as curl]
         '[cheshire.core :as json]
         '[clojure.edn :as edn])

;; Load our configuration
(load-file "src/com/yakread/config/website_nodes.clj")
(alias 'nodes 'com.yakread.config.website-nodes)

;; --- CONFIGURATION ---
(def alfresco-host "http://localhost:8080")
(def alfresco-user "admin")
(def alfresco-pass "admin")
(def api-base (str alfresco-host "/alfresco/api/-default-/public/alfresco/versions/1"))

;; --- HTTP CLIENT ---

(defn get-node-children [node-id]
  (let [resp (curl/get (str api-base "/nodes/" node-id "/children")
                       {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      {:success true :data (json/parse-string (:body resp) true)}
      {:success false :error (:body resp)})))

(defn get-node-content [node-id]
  (let [resp (curl/get (str api-base "/nodes/" node-id "/content")
                       {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      {:success true :content (:body resp)}
      {:success false :error (:body resp)})))

;; --- FEATURE 1 CONTENT EXTRACTION ---

(defn extract-feature1-content []
  "Extract content from Feature 1 folder in Alfresco"
  (println "📡 Extracting Feature 1 content from Alfresco...")

  (let [feature1-node-id (nodes/get-homepage-component-node-id :feature1)
        children-result (get-node-children feature1-node-id)]

    (if (:success children-result)
      (let [entries (get-in children-result [:data :list :entries])
            html-files (filter #(= "text/html" (get-in % [:entry :content :mimeType])) entries)]

        (println "   Found" (count entries) "items in Feature 1 folder")
        (println "   HTML files:" (count html-files))

        ;; Process each HTML file
        (for [html-file html-files]
          (let [file-entry (:entry html-file)
                file-node-id (:id file-entry)
                file-name (:name file-entry)
                content-result (get-node-content file-node-id)]

            (println "   Processing:" file-name)

            (if (:success content-result)
              {:success true
               :alfresco-node-id file-node-id
               :alfresco-name file-name
               :alfresco-modified-at (:modifiedAt file-entry)
               :alfresco-created-at (:createdAt file-entry)
               :alfresco-size (get-in file-entry [:content :sizeInBytes])
               :alfresco-mime-type (get-in file-entry [:content :mimeType])
               :html-content (:content content-result)
               :extracted-at (java.time.Instant/now)}

              {:success false
               :alfresco-node-id file-node-id
               :alfresco-name file-name
               :error (:error content-result)}))))

      {:error "Could not access Feature 1 folder" :details (:error children-result)})))

;; --- XTDB DOCUMENT CREATION ---

(defn create-xtdb-document [alfresco-content]
  "Transform Alfresco content into XTDB document for bitemporal storage"
  (let [doc-id (random-uuid)
        now (java.time.Instant/now)]

    {:xt/id doc-id

     ;; Content classification
     :content/type :mtzuix-content
     :content/component :feature1
     :content/page :homepage
     :content/status :published
     :content/display-order 1

     ;; Alfresco source tracking (bitemporal linkage)
     :alfresco/source-node-id (:alfresco-node-id alfresco-content)
     :alfresco/source-name (:alfresco-name alfresco-content)
     :alfresco/source-modified-at (:alfresco-modified-at alfresco-content)
     :alfresco/source-created-at (:alfresco-created-at alfresco-content)
     :alfresco/source-size (:alfresco-size alfresco-content)
     :alfresco/source-mime-type (:alfresco-mime-type alfresco-content)

     ;; Content data
     :content/html (:html-content alfresco-content)
     :content/text (-> (:html-content alfresco-content)
                       (clojure.string/replace #"<[^>]*>" "")  ; Strip HTML tags for text version
                       clojure.string/trim)

     ;; Temporal tracking
     :sync/extracted-from-alfresco-at (:extracted-at alfresco-content)
     :sync/stored-in-xtdb-at now
     :sync/version 1
     :sync/checksum (str (hash (:html-content alfresco-content)))

     ;; mtzUIX serving metadata
     :mtzuix/component-props {:title "Feature 1"
                              :component-type :html-display
                              :source-attribution "Mt Zion Alfresco"}
     :mtzuix/last-served-at nil
     :mtzuix/serve-count 0}))

;; --- XTDB SIMULATION (for now, we'll save to files) ---

(defn save-to-xtdb-simulation [xtdb-documents]
  "Simulate saving to XTDB by writing to files"
  (println "💾 Saving to XTDB (simulation)...")

  (doseq [doc xtdb-documents]
    (let [doc-id (:xt/id doc)
          filename (str "xtdb-simulation-" doc-id ".edn")]

      (spit filename (pr-str doc))
      (println "   ✅ Saved document:" filename)
      (println "     Content preview:" (subs (:content/text doc) 0 50) "...")))

  (println "   📝 In real yakread, this would be:")
  (println "     (biff/submit-tx ctx (map #(vector :xtdb.api/put %) xtdb-documents))")

  xtdb-documents)

;; --- CONTENT SERVING PREPARATION ---

(defn prepare-content-for-mtzuix [xtdb-documents]
  "Prepare content in the format mtzUIX expects"
  (println "\n📤 Preparing content for mtzUIX serving...")

  (for [doc xtdb-documents]
    {:component-id "homepage-feature1"
     :component-type "feature1"
     :title (get-in doc [:mtzuix/component-props :title])
     :html-content (:content/html doc)
     :text-content (:content/text doc)
     :last-updated (:alfresco/source-modified-at doc)
     :source-info {:type "alfresco"
                   :node-id (:alfresco/source-node-id doc)
                   :name (:alfresco/source-name doc)}
     :xtdb-id (:xt/id doc)}))

;; --- MAIN SYNC FUNCTION ---

(defn sync-feature1-to-xtdb []
  "Complete sync of Feature 1 content from Alfresco to XTDB"
  (println "🔄 Starting Feature 1 → XTDB sync for mtzUIX pipeline\n")

  (try
    ;; Extract from Alfresco
    (let [alfresco-contents (extract-feature1-content)
          successful-extracts (filter :success alfresco-contents)]

      (if (seq successful-extracts)
        (do
          (println "\n📋 Successfully extracted" (count successful-extracts) "content items")

          ;; Transform to XTDB documents
          (let [xtdb-documents (map create-xtdb-document successful-extracts)]

            (println "\n🏗️  Created" (count xtdb-documents) "XTDB documents with bitemporal tracking")

            ;; Save to XTDB (simulated)
            (save-to-xtdb-simulation xtdb-documents)

            (println "\n✅ Sync completed successfully!")
            (println "📊 Results:")
            (println "   📡 Alfresco items extracted:" (count successful-extracts))
            (println "   💾 XTDB documents created:" (count xtdb-documents))
            (println "   🔗 Bitemporal links established:" (count xtdb-documents))

            {:success true
             :extracted (count successful-extracts)
             :stored (count xtdb-documents)
             :documents xtdb-documents}))

        (do
          (println "❌ No content could be extracted from Feature 1 folder")
          {:success false :error "No extractable content"})))

    (catch Exception e
      (println "❌ Sync failed:" (.getMessage e))
      {:success false :error (.getMessage e)})))

;; --- MAIN EXECUTION ---

(defn -main []
  (println "🏠 Mt Zion Feature 1 Content Pipeline")
  (println "   Alfresco → XTDB → mtzUIX preparation")
  (println "   Make sure SSH tunnel is running\n")

  (let [sync-result (sync-feature1-to-xtdb)]
    (when (:success sync-result)
      (let [mtzuix-content (prepare-content-for-mtzuix (:documents sync-result))]

        (println "\n🎯 Content ready for mtzUIX:")
        (doseq [content mtzuix-content]
          (println "   📄" (:title content))
          (println "     Component ID:" (:component-id content))
          (println "     XTDB ID:" (:xtdb-id content))
          (println "     Content length:" (count (:html-content content)) "characters"))

        (println "\n💡 Next steps:")
        (println "   1. Create Feature1 UIX component")
        (println "   2. Add API endpoint to serve this content")
        (println "   3. Display on homepage")

        ;; Save mtzUIX-ready content for next step
        (spit "mtzuix-feature1-content.edn" (pr-str mtzuix-content))
        (println "\n📁 mtzUIX content saved to: mtzuix-feature1-content.edn")

        sync-result))))

;; Run the sync
(-main)