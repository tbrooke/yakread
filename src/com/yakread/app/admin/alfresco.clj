(ns com.yakread.app.admin.alfresco
  "Alfresco integration admin interface for Mt Zion website content"
  (:require [clojure.string :as str]
            [com.biffweb :as biff]
            [com.yakread.lib.alfresco :as lib.alfresco]
            [clojure.tools.logging :as log]
            [com.yakread.lib.middleware :as lib.mid]
            [com.yakread.lib.ui :as lib.ui]
            [xtdb.api :as xt]))

;; Configuration management
(defn get-alfresco-config
  "Get Alfresco configuration from environment/secrets"
  [ctx]
  {:base-url (biff/lookup ctx :alfresco/base-url "http://generated-setup-alfresco-1:8080")
   :username (biff/lookup ctx :alfresco/username "admin")
   :password (biff/lookup ctx :alfresco/password "admin")
   :timeout 30000
   :connection-timeout 10000})

;; UI Components
(defn health-status-badge [healthy?]
  (if healthy?
    [:span.inline-flex.items-center.px-2.5.py-0.5.rounded-full.text-xs.font-medium.bg-green-100.text-green-800
     "Connected"]
    [:span.inline-flex.items-center.px-2.5.py-0.5.rounded-full.text-xs.font-medium.bg-red-100.text-red-800
     "Disconnected"]))

(defn folder-tree-item [folder]
  [:li.py-2.border-b.border-gray-200
   [:div.flex.items-center.justify-between
    [:div.flex.items-center.space-x-3
     [:svg.w-5.h-5.text-blue-500 {:fill "currentColor" :viewBox "0 0 20 20"}
      [:path {:d "M2 6a2 2 0 012-2h5l2 2h5a2 2 0 012 2v6a2 2 0 01-2 2H4a2 2 0 01-2-2V6z"}]]
     [:div
      [:p.text-sm.font-medium.text-gray-900 (:name folder)]
      [:p.text-xs.text-gray-500 (str "ID: " (:id folder))]]]
    [:div.flex.items-center.space-x-2
     [:span.text-xs.text-gray-500 (:type folder)]
     [:button.text-blue-600.hover:text-blue-800.text-sm
      {:hx-post (str "/admin/alfresco/sync-folder/" (:id folder))
       :hx-target "#sync-results"
       :hx-swap "innerHTML"}
      "Sync"]]]])

(defn alfresco-dashboard [ctx health-status website-structure]
  (lib.ui/base-page
   ctx
   [:div.max-w-7xl.mx-auto.py-6.px-4
    [:div.mb-8
     [:h1.text-2xl.font-bold.text-gray-900 "Mt Zion Alfresco Integration"]
     [:p.text-gray-600 "Monitor and sync content from the Mt Zion Alfresco repository"]]
    
    ;; Health Status Card
    [:div.bg-white.shadow.rounded-lg.p-6.mb-6
     [:div.flex.items-center.justify-between.mb-4
      [:h2.text-lg.font-medium.text-gray-900 "Connection Status"]
      (health-status-badge (:healthy health-status))]
     
     (if (:healthy health-status)
       [:div.space-y-2
        [:p [:span.font-semibold "Repository ID:"] " " (:repository-id health-status)]
        [:p [:span.font-semibold "Version:"] " " (:version health-status)]
        [:p [:span.font-semibold "Edition:"] " " (:edition health-status)]
        (when (:mtzion-site-accessible health-status)
          [:div
           [:p.text-green-600 "✓ Mt Zion site accessible"]
           [:p [:span.font-semibold "Website folders:"] " " (:website-folders-count health-status)]])]
       [:div.space-y-2
        [:p.text-red-600 (str "Error: " (:message health-status))]
        [:button.bg-blue-600.text-white.px-4.py-2.rounded.hover:bg-blue-700
         {:hx-get "/admin/alfresco"
          :hx-target "body"
          :hx-swap "innerHTML"}
         "Retry Connection"]])]
    
    ;; Website Structure Card
    (when (and (:healthy health-status) website-structure (not (:error website-structure)))
      [:div.bg-white.shadow.rounded-lg.p-6.mb-6
       [:div.flex.items-center.justify-between.mb-4
        [:h2.text-lg.font-medium.text-gray-900 "Mt Zion Website Folders"]
        [:button.bg-green-600.text-white.px-4.py-2.rounded.hover:bg-green-700
         {:hx-post "/admin/alfresco/sync-all"
          :hx-target "#sync-results"
          :hx-swap "innerHTML"}
         "Sync All Folders"]]
       
       [:div.mb-4
        [:p [:span.font-semibold "Site:"] " " (:site-name website-structure)]
        [:p [:span.font-semibold "Website Folder ID:"] " " (:website-folder-id website-structure)]]
       
       [:ul.space-y-1
        (for [folder (:website-folders website-structure)]
          (folder-tree-item folder))]])
    
    ;; Sync Results
    [:div.bg-white.shadow.rounded-lg.p-6
     [:h2.text-lg.font-medium.text-gray-900.mb-4 "Sync Results"]
     [:div#sync-results.text-gray-500
      "Click 'Sync' on a folder or 'Sync All' to see results here."]]
    
    ;; Configuration
    [:details.mt-8
     [:summary.text-lg.font-medium.text-gray-900.cursor-pointer.mb-4 "Configuration"]
     [:div.bg-gray-50.p-4.rounded-lg
      [:pre.text-sm.text-gray-700
       (with-out-str (biff/pprint (get-alfresco-config ctx)))]]]]))

;; Route handlers
(defn dashboard-handler
  "Main Alfresco dashboard"
  [{:keys [biff/db] :as ctx}]
  (let [config (get-alfresco-config ctx)
        health-status (lib.alfresco/test-mtzion-connectivity config)
        website-structure (when (:healthy health-status)
                            (lib.alfresco/get-mtzion-website-structure config))]
    {:status 200
     :headers {"content-type" "text/html"}
     :body (alfresco-dashboard ctx health-status website-structure)}))

(defn sync-folder-handler
  "Sync a specific folder to Yakread items"
  [{:keys [path-params session biff/db] :as ctx}]
  (let [folder-id (:folder-id path-params)
        user-id (:uid session)
        config (get-alfresco-config ctx)
        result (lib.alfresco/sync-folder-to-yakread config folder-id user-id)]
    (if (:error result)
      {:status 200
       :headers {"content-type" "text/html"}
       :body [:div.text-red-600
              [:p "Sync failed: " (:message result)]]}
      (let [items (:created-items result)]
        ;; Save items to database
        (when (seq items)
          (xt/await-tx db (xt/submit-tx db (for [item items]
                                            [::xt/put item]))))
        {:status 200
         :headers {"content-type" "text/html"}
         :body [:div.text-green-600
                [:p (str "Successfully synced " (:synced-folders result) " folders and " 
                        (:synced-documents result) " documents")]
                [:p (str "Created " (count items) " new items in Yakread")]]}))))

(defn sync-all-handler
  "Sync all Mt Zion website folders"
  [{:keys [session biff/db] :as ctx}]
  (let [user-id (:uid session)
        config (get-alfresco-config ctx)
        website-structure (lib.alfresco/get-mtzion-website-structure config)]
    (if (:error website-structure)
      {:status 200
       :headers {"content-type" "text/html"}
       :body [:div.text-red-600
              [:p "Failed to get website structure: " (:message website-structure)]]}
      (let [folder-ids (map :id (:website-folders website-structure))
            results (map #(lib.alfresco/sync-folder-to-yakread config % user-id) folder-ids)
            successful-syncs (remove :error results)
            failed-syncs (filter :error results)
            all-items (mapcat :created-items successful-syncs)]
        ;; Save all items to database
        (when (seq all-items)
          (xt/await-tx db (xt/submit-tx db (for [item all-items]
                                            [::xt/put item]))))
        {:status 200
         :headers {"content-type" "text/html"}
         :body [:div
                (if (seq successful-syncs)
                  [:div.text-green-600.mb-2
                   [:p (str "Successfully synced " (count successful-syncs) " folders")]
                   [:p (str "Created " (count all-items) " new items in Yakread")]])
                (when (seq failed-syncs)
                  [:div.text-red-600
                   [:p (str (count failed-syncs) " folders failed to sync")]
                   [:ul
                    (for [failure failed-syncs]
                      [:li "• " (:message failure)])]])]}))))

(defn create-subscription-handler
  "Create a subscription to monitor Alfresco folder changes"
  [{:keys [path-params session biff/db] :as ctx}]
  (let [folder-id (:folder-id path-params)
        user-id (:uid session)
        config (get-alfresco-config ctx)
        folder-info (lib.alfresco/get-node config folder-id)]
    (if (:error folder-info)
      {:status 400
       :headers {"content-type" "application/json"}
       :body {:error "Failed to get folder info"}}
      (let [folder-name (get-in folder-info [:body :entry :name])
            subscription (lib.alfresco/create-alfresco-subscription user-id config folder-id folder-name)]
        (xt/await-tx db (xt/submit-tx db [[::xt/put subscription]]))
        {:status 200
         :headers {"content-type" "application/json"}
         :body {:success true
                :subscription-id (:xt/id subscription)
                :message (str "Created subscription for " folder-name)}}))))

(defn test-connection-handler
  "Test Alfresco connection endpoint"
  [ctx]
  (let [config (get-alfresco-config ctx)
        health-status (lib.alfresco/health-check config)]
    {:status 200
     :headers {"content-type" "application/json"}
     :body health-status}))

;; Routes configuration
(def alfresco-routes
  [["/admin/alfresco" {:get dashboard-handler}]
   ["/admin/alfresco/sync-folder/:folder-id" {:post sync-folder-handler}] 
   ["/admin/alfresco/sync-all" {:post sync-all-handler}]
   ["/admin/alfresco/subscribe/:folder-id" {:post create-subscription-handler}]
   ["/admin/alfresco/test-connection" {:get test-connection-handler}]])

(def module
  {:routes ["" {:middleware [lib.mid/wrap-admin]}
            alfresco-routes]})

;; Background sync jobs (for future implementation)
(defn sync-alfresco-subscriptions
  "Background job to sync all active Alfresco subscriptions"
  [ctx]
  (let [db (:biff/db ctx)
        subscriptions (xt/q db
                           '{:find [sub]
                             :where [[sub :subscription/type :subscription.type/alfresco-folder]
                                    [sub :subscription/is-active true]]})]
    (doseq [[sub-id] subscriptions]
      (let [sub (xt/entity db sub-id)
            config (merge (get-alfresco-config ctx) 
                         (:subscription/alfresco-config sub))
            folder-id (:subscription/alfresco-folder-id sub)
            user-id (:subscription/user-id sub)]
        (try
          (let [result (lib.alfresco/sync-folder-to-yakread config folder-id user-id)]
            (when-not (:error result)
              (let [items (:created-items result)]
                (when (seq items)
                  (xt/await-tx db (xt/submit-tx db (for [item items]
                                                    [::xt/put item]))))
                ;; Update last sync timestamp
                (xt/await-tx db (xt/submit-tx db 
                                              [[::xt/put (assoc sub :subscription/last-sync-at (biff/now))]])))))
          (catch Exception e
            (log/error e (str "Failed to sync Alfresco subscription: " sub-id))))))))

(comment
  ;; Development testing
  (def test-ctx
    {:alfresco/base-url "http://generated-setup-alfresco-1:8080"
     :alfresco/username "admin" 
     :alfresco/password "admin"})
  
  ;; Test configuration
  (get-alfresco-config test-ctx)
  
  ;; Test handlers with mock requests
  (dashboard-handler test-ctx)
  )
