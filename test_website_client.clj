#!/usr/bin/env bb

;; Test script for the new website client with hardcoded node IDs

(require '[babashka.curl :as curl]
         '[cheshire.core :as json]
         '[clojure.edn :as edn]
         '[clojure.pprint :as pprint])

;; Load our configuration
(load-file "src/com/yakread/config/website_nodes.clj")
(alias 'nodes 'com.yakread.config.website-nodes)

;; --- CONFIGURATION ---
(def alfresco-host "http://localhost:8080")
(def alfresco-user "admin")
(def alfresco-pass "admin")
(def api-base (str alfresco-host "/alfresco/api/-default-/public/alfresco/versions/1"))

;; --- SIMPLIFIED HTTP CLIENT ---

(defn get-node [node-id]
  "Get node info by ID"
  (let [resp (curl/get (str api-base "/nodes/" node-id)
                       {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      {:success true :data (json/parse-string (:body resp) true)}
      {:success false :error (:body resp)})))

(defn get-node-children [node-id]
  "Get children of a node"
  (let [resp (curl/get (str api-base "/nodes/" node-id "/children")
                       {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      {:success true :data (json/parse-string (:body resp) true)}
      {:success false :error (:body resp)})))

(defn get-node-content [node-id]
  "Get actual content of a file node"
  (let [resp (curl/get (str api-base "/nodes/" node-id "/content")
                       {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      {:success true :content (:body resp)}
      {:success false :error (:body resp)})))

;; --- TEST FUNCTIONS ---

(defn test-node-access []
  "Test access to all our hardcoded node IDs"
  (println "=== Testing Hardcoded Node Access ===\n")

  ;; Test Web Site root
  (println "1️⃣ Testing Web Site root folder...")
  (let [result (get-node nodes/website-root-node-id)]
    (if (:success result)
      (println "   ✅ Web Site root accessible:" (get-in result [:data :entry :name]))
      (println "   ❌ Web Site root failed:" (:error result))))

  ;; Test page folders
  (println "\n2️⃣ Testing page folders...")
  (doseq [[page-name node-id] nodes/page-node-ids]
    (let [result (get-node node-id)]
      (if (:success result)
        (println (str "   ✅ " (name page-name) ": " (get-in result [:data :entry :name])))
        (println (str "   ❌ " (name page-name) " failed: " (:error result))))))

  ;; Test homepage components
  (println "\n3️⃣ Testing homepage component folders...")
  (doseq [[component-name node-id] nodes/homepage-component-node-ids]
    (let [result (get-node node-id)]
      (if (:success result)
        (println (str "   ✅ " (name component-name) ": " (get-in result [:data :entry :name])))
        (println (str "   ❌ " (name component-name) " failed: " (:error result))))))

  ;; Test sample document
  (println "\n4️⃣ Testing sample document...")
  (let [result (get-node nodes/sample-document-node-id)]
    (if (:success result)
      (println "   ✅ Sample document accessible:" (get-in result [:data :entry :name]))
      (println "   ❌ Sample document failed:" (:error result)))))

(defn test-content-retrieval []
  "Test retrieving actual content from feature1"
  (println "\n=== Testing Content Retrieval ===\n")

  ;; Get feature1 folder contents
  (println "1️⃣ Getting Feature 1 folder contents...")
  (let [feature1-node-id (nodes/get-homepage-component-node-id :feature1)
        result (get-node-children feature1-node-id)]
    (if (:success result)
      (let [entries (get-in result [:data :list :entries])]
        (println "   ✅ Found" (count entries) "items in Feature 1:")
        (doseq [entry entries]
          (let [node-entry (:entry entry)]
            (println "     -" (:name node-entry)
                     (if (:isFolder node-entry) "(folder)" "(file)")
                     (when-let [mime (get-in node-entry [:content :mimeType])]
                       (str "(" mime ")")))))

        ;; Get content of the Welcome document
        (when-let [welcome-entry (first (filter #(= "Welcome" (get-in % [:entry :name])) entries))]
          (println "\n2️⃣ Getting Welcome document content...")
          (let [welcome-node-id (get-in welcome-entry [:entry :id])
                content-result (get-node-content welcome-node-id)]
            (if (:success content-result)
              (do
                (println "   ✅ Successfully retrieved HTML content:")
                (println "   📄 Content preview:")
                (println (str "       " (subs (:content content-result) 0 100) "...")))
              (println "   ❌ Failed to get content:" (:error content-result))))))

      (println "   ❌ Failed to get Feature 1 contents:" (:error result)))))

(defn test-page-content-structure []
  "Test getting content structure for homepage"
  (println "\n=== Testing Page Content Structure ===\n")

  (println "Getting homepage structure...")
  (let [homepage-node-id (nodes/get-page-node-id :homepage)
        result (get-node-children homepage-node-id)]
    (if (:success result)
      (let [entries (get-in result [:data :list :entries])]
        (println "✅ Homepage has" (count entries) "components:")

        ;; Analyze each component
        (doseq [entry entries]
          (let [node-entry (:entry entry)
                component-name (:name node-entry)
                node-id (:id node-entry)]

            (println "\n🔍 Component:" component-name)
            (println "   Node ID:" node-id)
            (println "   Type:" (if (:isFolder node-entry) "folder" "file"))

            ;; If it's a folder, get its contents
            (when (:isFolder node-entry)
              (let [component-result (get-node-children node-id)]
                (if (:success component-result)
                  (let [component-entries (get-in component-result [:data :list :entries])]
                    (println "   Contents:" (count component-entries) "items")
                    (doseq [item component-entries]
                      (let [item-entry (:entry item)]
                        (println "     -" (:name item-entry)
                                 (when-let [mime (get-in item-entry [:content :mimeType])]
                                   (str "(" mime ")"))))))
                  (println "   ❌ Could not access component contents")))))))

      (println "❌ Failed to get homepage structure:" (:error result)))))

;; --- MAIN TEST RUNNER ---

(defn -main []
  (println "Testing Mt Zion Web Site Client with Hardcoded Node IDs")
  (println "Make sure SSH tunnel is running: ssh -L 8080:localhost:8080 tmb@trust\n")

  (test-node-access)
  (test-content-retrieval)
  (test-page-content-structure)

  (println "\n🎉 Testing completed!")
  (println "\n💡 Next steps:")
  (println "   - Integrate this with yakread XTDB storage")
  (println "   - Add to yakread admin panel")
  (println "   - Set up periodic sync via cron"))

;; Run tests
(-main)