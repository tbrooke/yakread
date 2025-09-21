#!/usr/bin/env bb

;; Simple Mt Zion Web Site Monitor - Test version

(require '[babashka.curl :as curl]
         '[cheshire.core :as json]
         '[clojure.edn :as edn])

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
  "Get node information by ID"
  (let [resp (curl/get (str api-base "/nodes/" node-id)
                       {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      {:success true :data (json/parse-string (:body resp) true)}
      {:success false :status (:status resp) :error (:body resp)})))

(defn get-node-children [node-id]
  "Get children of a node"
  (let [resp (curl/get (str api-base "/nodes/" node-id "/children")
                       {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      {:success true :data (json/parse-string (:body resp) true)}
      {:success false :status (:status resp) :error (:body resp)})))

;; --- STATUS CHECKING ---

(defn check-node-status [node-id description]
  "Check if a node is accessible"
  (let [result (get-node node-id)]
    (if (:success result)
      (let [node-data (get-in result [:data :entry])]
        {:node-id node-id
         :description description
         :status :accessible
         :name (:name node-data)
         :modified-at (:modifiedAt node-data)})
      {:node-id node-id
       :description description
       :status :inaccessible
       :error (:error result)})))

;; --- DISCOVERY ---

(defn discover-website-structure []
  "Discover current Web Site structure"
  (println "🔍 Discovering Web Site structure...")

  (let [root-result (get-node-children nodes/website-root-node-id)]
    (if (:success root-result)
      (let [entries (get-in root-result [:data :list :entries])
            folders (filter #(get-in % [:entry :isFolder]) entries)]

        (println "📁 Found" (count folders) "page folders in Web Site:")

        (for [folder folders]
          (let [folder-data (:entry folder)
                folder-name (:name folder-data)
                node-id (:id folder-data)
                is-known (contains? (set (vals nodes/page-node-ids)) node-id)]

            (println "  " (if is-known "✅" "🆕") folder-name "(" node-id ")")

            {:name folder-name
             :node-id node-id
             :is-known is-known
             :modified-at (:modifiedAt folder-data)})))

      (do
        (println "❌ Could not access Web Site root")
        []))))

;; --- MAIN FUNCTION ---

(defn -main []
  (println "🔍 Mt Zion Web Site Simple Monitor")
  (println "   Make sure SSH tunnel is running\n")

  ;; Check all configured nodes
  (println "1️⃣ Checking configured nodes...")

  (let [checks [(check-node-status nodes/website-root-node-id "Web Site Root")]]

    ;; Add page checks
    (let [page-checks (for [[page-name node-id] nodes/page-node-ids]
                        (check-node-status node-id (str "Page: " (name page-name))))

          all-checks (concat checks page-checks)
          accessible (filter #(= :accessible (:status %)) all-checks)
          inaccessible (filter #(= :inaccessible (:status %)) all-checks)]

      (println "   ✅ Accessible:" (count accessible))
      (println "   ❌ Inaccessible:" (count inaccessible))

      (when (seq inaccessible)
        (println "\n❌ Inaccessible nodes:")
        (doseq [node inaccessible]
          (println "   -" (:description node))))

      ;; Discover structure
      (println "\n2️⃣ Discovering current structure...")
      (let [discovered (discover-website-structure)
            new-items (filter #(not (:is-known %)) discovered)]

        (when (seq new-items)
          (println "\n🆕 New items found:")
          (doseq [item new-items]
            (println "   📁" (:name item) "→" (:node-id item))))

        (println "\n✅ Monitoring complete!")
        {:accessible (count accessible)
         :inaccessible (count inaccessible)
         :new-items (count new-items)}))))

;; Run the monitor
(-main)