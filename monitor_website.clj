#!/usr/bin/env bb

;; Mt Zion Web Site Monitor - Babashka app for monitoring Alfresco Web Site structure
;; Checks node status, detects changes, discovers new pages/components, updates configuration

(require '[babashka.curl :as curl]
         '[cheshire.core :as json]
         '[clojure.edn :as edn]
         '[clojure.string :as str]
         '[clojure.java.io :as io]
         '[clojure.pprint :as pprint]
         '[clojure.set :as set])

;; --- CONFIGURATION ---

(def alfresco-host "http://localhost:8080")
(def alfresco-user "admin")
(def alfresco-pass "admin")
(def api-base (str alfresco-host "/alfresco/api/-default-/public/alfresco/versions/1"))

;; Load existing configuration
(def current-config-file "src/com/yakread/config/website_nodes.clj")
(def monitoring-log-file "website_monitor.log")
(def status-cache-file "website_status_cache.edn")

;; --- HTTP CLIENT ---

(defn get-json [url]
  "Make HTTP request and parse JSON"
  (let [resp (curl/get url {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      {:success true :data (json/parse-string (:body resp) true)}
      {:success false :status (:status resp) :error (:body resp)})))

(defn get-node [node-id]
  "Get node information by ID"
  (get-json (str api-base "/nodes/" node-id)))

(defn get-node-children [node-id]
  "Get children of a node"
  (get-json (str api-base "/nodes/" node-id "/children")))

;; --- CURRENT CONFIG PARSING ---

(defn parse-current-config []
  "Parse the current website_nodes.clj to extract node mappings"
  (try
    (let [config-content (slurp current-config-file)
          ;; Extract page-node-ids map
          page-ids-match (re-find #"\(def page-node-ids[^}]+\}" config-content)
          ;; Extract homepage-component-node-ids map
          component-ids-match (re-find #"\(def homepage-component-node-ids[^}]+\}" config-content)
          ;; Extract website-root-node-id
          root-id-match (re-find #"\(def website-root-node-id[^\"]*\"([^\"]+)\"" config-content)]

      {:success true
       :website-root-node-id (when root-id-match (second root-id-match))
       :config-content config-content
       :has-page-ids (boolean page-ids-match)
       :has-component-ids (boolean component-ids-match)})

    (catch Exception e
      {:success false :error (.getMessage e)})))

;; Load the actual configuration by requiring the namespace
(load-file current-config-file)
(alias 'nodes 'com.yakread.config.website-nodes)

;; --- NODE STATUS CHECKING ---

(defn check-node-status [node-id description]
  "Check if a node is accessible and get its current state"
  (let [result (get-node node-id)
        timestamp (java.time.Instant/now)]
    (if (:success result)
      (let [node-data (get-in result [:data :entry])]
        {:node-id node-id
         :description description
         :status :accessible
         :name (:name node-data)
         :modified-at (:modifiedAt node-data)
         :is-folder (:isFolder node-data)
         :timestamp timestamp
         :success true})
      {:node-id node-id
       :description description
       :status :inaccessible
       :error (:error result)
       :http-status (:status result)
       :timestamp timestamp
       :success false})))

(defn check-all-configured-nodes []
  "Check status of all nodes in our current configuration"
  (println "🔍 Checking status of all configured nodes...")

  (let [results (atom [])]

    ;; Check website root
    (println "  Checking Web Site root...")
    (swap! results conj (check-node-status nodes/website-root-node-id "Web Site Root"))

    ;; Check page nodes
    (println "  Checking page folders...")
    (doseq [[page-name node-id] nodes/page-node-ids]
      (swap! results conj (check-node-status node-id (str "Page: " (name page-name)))))

    ;; Check homepage component nodes
    (println "  Checking homepage component folders...")
    (doseq [[component-name node-id] nodes/homepage-component-node-ids]
      (swap! results conj (check-node-status node-id (str "Homepage Component: " (name component-name)))))

    ;; Check sample document
    (println "  Checking sample document...")
    (swap! results conj (check-node-status nodes/sample-document-node-id "Sample Document"))

    @results))

;; --- CHANGE DETECTION ---

(defn load-previous-status []
  "Load previous node status from cache file"
  (try
    (if (.exists (io/file status-cache-file))
      (edn/read-string (slurp status-cache-file))
      {})
    (catch Exception e
      (println "⚠️  Could not load previous status:" (.getMessage e))
      {})))

(defn save-current-status [status-results]
  "Save current status to cache file"
  (let [status-map (into {} (map (fn [result]
                                   [(:node-id result)
                                    (select-keys result [:name :modified-at :status :timestamp])])
                                 status-results))]
    (spit status-cache-file (pr-str status-map))
    status-map))

(defn detect-changes [current-results previous-status]
  "Compare current results with previous status to detect changes"
  (let [changes (atom [])]

    (doseq [result current-results]
      (let [node-id (:node-id result)
            previous (get previous-status node-id)]

        (cond
          ;; New node (not in previous status)
          (nil? previous)
          (swap! changes conj {:type :new-node
                               :node-id node-id
                               :description (:description result)
                               :current-status (:status result)})

          ;; Node became inaccessible
          (and (= :accessible (:status previous))
               (= :inaccessible (:status result)))
          (swap! changes conj {:type :node-became-inaccessible
                               :node-id node-id
                               :description (:description result)
                               :error (:error result)})

          ;; Node became accessible again
          (and (= :inaccessible (:status previous))
               (= :accessible (:status result)))
          (swap! changes conj {:type :node-became-accessible
                               :node-id node-id
                               :description (:description result)
                               :name (:name result)})

          ;; Content modified
          (and (= :accessible (:status result))
               (not= (:modified-at previous) (:modified-at result)))
          (swap! changes conj {:type :content-modified
                               :node-id node-id
                               :description (:description result)
                               :name (:name result)
                               :previous-modified (:modified-at previous)
                               :current-modified (:modified-at result)})

          ;; Name changed
          (and (= :accessible (:status result))
               (not= (:name previous) (:name result)))
          (swap! changes conj {:type :name-changed
                               :node-id node-id
                               :description (:description result)
                               :previous-name (:name previous)
                               :current-name (:name result)}))))

    @changes))

;; --- NEW PAGE/COMPONENT DISCOVERY ---

(defn discover-current-website-structure []
  "Discover the current Web Site folder structure from Alfresco"
  (println "🔍 Discovering current Web Site structure...")

  (let [discovery-results (atom {:pages {} :homepage-components {} :errors []})]

    ;; Get Web Site root contents
    (let [root-result (get-node-children nodes/website-root-node-id)]
      (if (:success root-result)
        (let [root-entries (get-in root-result [:data :list :entries])]
          (println "  Found" (count root-entries) "items in Web Site root")

          ;; Analyze each top-level folder (these are pages)
          (doseq [entry root-entries]
            (let [entry-data (:entry entry)
                  folder-name (:name entry-data)
                  node-id (:id entry-data)]

              (when (:isFolder entry-data)
                (println "    Analyzing page folder:" folder-name)
                (swap! discovery-results assoc-in [:pages folder-name]
                       {:node-id node-id
                        :name folder-name
                        :modified-at (:modifiedAt entry-data)
                        :is-known (contains? (set (vals nodes/page-node-ids)) node-id)})

                ;; If this is the homepage, also analyze its components
                (when (= folder-name "Home Page")
                  (let [homepage-result (get-node-children node-id)]
                    (if (:success homepage-result)
                      (let [homepage-entries (get-in homepage-result [:data :list :entries])]
                        (doseq [comp-entry homepage-entries]
                          (let [comp-data (:entry comp-entry)
                                comp-name (:name comp-data)
                                comp-node-id (:id comp-data)]
                            (when (:isFolder comp-data)
                              (println "      Found homepage component:" comp-name)
                              (swap! discovery-results assoc-in [:homepage-components comp-name]
                                     {:node-id comp-node-id
                                      :name comp-name
                                      :modified-at (:modifiedAt comp-data)
                                      :is-known (contains? (set (vals nodes/homepage-component-node-ids)) comp-node-id)})))))
                      (swap! discovery-results update :errors conj
                             {:type :homepage-analysis-failed
                              :folder folder-name
                              :error (:error homepage-result)})))))))

        (swap! discovery-results update :errors conj
               {:type :root-discovery-failed
                :error (:error root-result)})))

    @discovery-results))

(defn find-new-items [discovery-results]
  "Find new pages and components not in our current configuration"
  (let [new-items (atom [])]

    ;; Check for new pages
    (doseq [[page-name page-info] (:pages discovery-results)]
      (when-not (:is-known page-info)
        (swap! new-items conj {:type :new-page
                               :name page-name
                               :node-id (:node-id page-info)
                               :suggested-keyword (keyword (str/lower-case (str/replace page-name #"\\s+" "")))
                               :modified-at (:modified-at page-info)})))

    ;; Check for new homepage components
    (doseq [[comp-name comp-info] (:homepage-components discovery-results)]
      (when-not (:is-known comp-info)
        (swap! new-items conj {:type :new-homepage-component
                               :name comp-name
                               :node-id (:node-id comp-info)
                               :suggested-keyword (keyword (str/lower-case (str/replace comp-name #"\\s+" "")))
                               :modified-at (:modified-at comp-info)})))

    @new-items))

;; --- CONFIGURATION GENERATION ---

(defn generate-updated-config [discovery-results new-items]
  "Generate updated configuration with new items added"
  (when (seq new-items)
    (println "\\n📝 Generating updated configuration...")

    (let [timestamp (java.time.Instant/now)
          backup-file (str current-config-file ".backup." (.toEpochMilli timestamp))]

      ;; Backup current config
      (io/copy (io/file current-config-file) (io/file backup-file))
      (println "  💾 Backed up current config to:" backup-file)

      ;; Generate new page entries
      (let [new-pages (filter #(= :new-page (:type %)) new-items)
            new-components (filter #(= :new-homepage-component (:type %)) new-items)]

        (when (seq new-pages)
          (println "  📄 New pages to add:")
          (doseq [page new-pages]
            (println "    " (:suggested-keyword page) "=>" (:node-id page) "(" (:name page) ")")))

        (when (seq new-components)
          (println "  🧩 New homepage components to add:")
          (doseq [comp new-components]
            (println "    " (:suggested-keyword comp) "=>" (:node-id comp) "(" (:name comp) ")")))

        ;; Generate the additions (user can manually add these)
        (let [additions-file "website_nodes_additions.edn"]
          (spit additions-file
                (pr-str {:new-pages (map #(select-keys % [:suggested-keyword :node-id :name]) new-pages)
                         :new-homepage-components (map #(select-keys % [:suggested-keyword :node-id :name]) new-components)
                         :generated-at timestamp}))
          (println "  📝 New items written to:" additions-file)
          (println "  💡 Review and manually add these to" current-config-file))))))

;; --- LOGGING ---

(defn log-monitoring-results [status-results changes new-items]
  "Log monitoring results to file"
  (let [timestamp (java.time.Instant/now)
        log-entry {:timestamp timestamp
                   :total-nodes-checked (count status-results)
                   :accessible-nodes (count (filter #(= :accessible (:status %)) status-results))
                   :inaccessible-nodes (count (filter #(= :inaccessible (:status %)) status-results))
                   :changes-detected (count changes)
                   :new-items-found (count new-items)
                   :changes changes
                   :new-items new-items}]

    ;; Append to log file
    (spit monitoring-log-file
          (str (pr-str log-entry) "\\n")
          :append true)

    log-entry))

;; --- REPORTING ---

(defn print-monitoring-report [status-results changes new-items]
  "Print a comprehensive monitoring report"
  (println "\\n" (str (repeat 60 "=")) "\\n")
  (println "📊 MT ZION WEB SITE MONITORING REPORT")
  (println "   " (java.time.LocalDateTime/now))
  (println (str (repeat 60 "=")))

  ;; Node status summary
  (let [accessible (filter #(= :accessible (:status %)) status-results)
        inaccessible (filter #(= :inaccessible (:status %)) status-results)]

    (println "\\n🔍 NODE STATUS SUMMARY:")
    (println "   Total nodes checked:" (count status-results))
    (println "   ✅ Accessible:" (count accessible))
    (println "   ❌ Inaccessible:" (count inaccessible)))

  ;; Inaccessible nodes details
  (when (seq (filter #(= :inaccessible (:status %)) status-results))
    (println "\\n❌ INACCESSIBLE NODES:")
    (doseq [result (filter #(= :inaccessible (:status %)) status-results)]
      (println "   -" (:description result)
               "(ID:" (:node-id result) ")"
               "Status:" (:http-status result))))

  ;; Changes detected
  (if (seq changes)
    (do
      (println "\\n🔄 CHANGES DETECTED:")
      (doseq [change changes]
        (case (:type change)
          :content-modified
          (println "   📝" (:description change) "- Content modified"
                   "\\n      Previous:" (:previous-modified change)
                   "\\n      Current: " (:current-modified change))

          :name-changed
          (println "   📛" (:description change) "- Name changed"
                   "\\n      From:" (:previous-name change)
                   "\\n      To:  " (:current-name change))

          :node-became-inaccessible
          (println "   ❌" (:description change) "- Became inaccessible"
                   "\\n      Error:" (:error change))

          :node-became-accessible
          (println "   ✅" (:description change) "- Became accessible again"
                   "\\n      Name:" (:name change))

          :new-node
          (println "   🆕" (:description change) "- New node detected"))))
    (println "\\n✅ NO CHANGES DETECTED"))

  ;; New items found
  (if (seq new-items)
    (do
      (println "\\n🆕 NEW ITEMS DISCOVERED:")
      (doseq [item new-items]
        (println "   📁" (:name item)
                 "(" (name (:type item)) ")"
                 "\\n      Suggested keyword:" (:suggested-keyword item)
                 "\\n      Node ID:" (:node-id item))))
    (println "\\n✅ NO NEW ITEMS DISCOVERED"))

  (println "\\n" (str (repeat 60 "="))))

;; --- MAIN MONITORING FUNCTION ---

(defn -main []
  (println "🔍 Mt Zion Web Site Structure Monitor")
  (println "   Checking node status, detecting changes, discovering new content...")
  (println "   Make sure SSH tunnel is running: ssh -L 8080:localhost:8080 tmb@trust\\n")

  (try
    ;; Load previous status
    (let [previous-status (load-previous-status)]
      (println "📋 Loaded previous status for" (count previous-status) "nodes")

      ;; Check all configured nodes
      (let [status-results (check-all-configured-nodes)]

        ;; Save current status
        (save-current-status status-results)

        ;; Detect changes
        (let [changes (detect-changes status-results previous-status)]
          (when (seq changes)
            (println "🔄 Detected" (count changes) "changes"))

          ;; Discover current structure
          (let [discovery (discover-current-website-structure)
                new-items (find-new-items discovery)]

            (when (seq new-items)
              (println "🆕 Found" (count new-items) "new items"))

            ;; Generate updated config if needed
            (when (seq new-items)
              (generate-updated-config discovery new-items))

            ;; Log results
            (log-monitoring-results status-results changes new-items)

            ;; Print report
            (print-monitoring-report status-results changes new-items)

            ;; Return summary
            {:success true
             :nodes-checked (count status-results)
             :changes-detected (count changes)
             :new-items-found (count new-items)}))))

    (catch Exception e
      (println "❌ Monitoring failed:" (.getMessage e))
      {:success false :error (.getMessage e)})))

;; Run monitoring
(-main)