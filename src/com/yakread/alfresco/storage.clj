(ns com.yakread.alfresco.storage
  "XTDB storage operations for Alfresco content with Malli validation"
  (:require [com.yakread.alfresco.schema :as schema]
            [com.biffweb :as biff]
            [malli.core :as m]
            [clojure.tools.logging :as log]
            [clojure.set :as set]))

;; --- XTDB OPERATIONS WITH SCHEMA VALIDATION ---

(defn store-alfresco-content!
  "Store Alfresco content in XTDB with schema validation"
  [ctx alfresco-node & [content-text]]
  (try
    ;; First validate the incoming Alfresco node
    (if (schema/validate-alfresco-node alfresco-node)
      (let [;; Transform to yakread format
            yakread-content (schema/transform-alfresco->yakread alfresco-node)

            ;; Add content text if provided
            yakread-content (if content-text
                              (assoc yakread-content :content/text content-text)
                              yakread-content)

            ;; Validate the transformed content
            _ (when-not (schema/validate-yakread-content yakread-content)
                (throw (ex-info "Invalid yakread content format"
                                {:errors (schema/explain-validation-error
                                          schema/YakreadContent yakread-content)})))

            ;; Store in XTDB
            doc-id (:xt/id yakread-content)]

        (biff/submit-tx ctx [yakread-content])

        (log/info "Stored Alfresco content in XTDB"
                  "node-id:" (:alfresco/node-id yakread-content)
                  "doc-id:" doc-id)

        doc-id)

      (do
        (log/error "Invalid Alfresco node format"
                   "errors:" (schema/explain-validation-error schema/Node alfresco-node))
        nil))

    (catch Exception e
      (log/error "Failed to store Alfresco content:" (.getMessage e))
      nil)))

(defn get-content-by-alfresco-id
  "Retrieve content by Alfresco node ID"
  [ctx alfresco-node-id]
  (let [query '{:find [(pull ?e [*])]
                :where [[?e :alfresco/node-id alfresco-node-id]]
                :in [alfresco-node-id]}
        result (biff/q (:biff/db ctx) query alfresco-node-id)]
    (when-let [content (ffirst result)]
      (if (schema/validate-yakread-content content)
        content
        (do
          (log/warn "Stored content failed validation, needs migration"
                    "doc-id:" (:xt/id content))
          content)))))

(defn get-content-for-page
  "Get all content for a specific page, validated and transformed for UIX"
  [ctx page-name & [limit]]
  (let [query '{:find [(pull ?e [*])]
                :where [[?e :yakread/type "alfresco-content"]
                        [?e :yakread/status "published"]]
                :limit limit}
        limit (or limit 50)
        results (biff/q (:biff/db ctx) query)
        yakread-contents (map first results)]

    ;; Transform to UIX format and validate
    (keep (fn [yakread-content]
            (try
              (let [uix-content (schema/transform-yakread->uix yakread-content)]
                (if (schema/validate-uix-content uix-content)
                  uix-content
                  (do
                    (log/warn "Content failed UIX validation"
                              "doc-id:" (:xt/id yakread-content)
                              "errors:" (schema/explain-validation-error
                                         schema/UIXContent uix-content))
                    nil)))
              (catch Exception e
                (log/error "Failed to transform content for UIX:" (.getMessage e)
                           "doc-id:" (:xt/id yakread-content))
                nil)))
          yakread-contents)))

(defn update-content-text!
  "Update the text content of a stored document"
  [ctx doc-id content-text]
  (try
    (let [existing (biff/lookup (:biff/db ctx) :xt/id doc-id)]
      (if existing
        (let [updated (-> existing
                          (assoc :content/text content-text)
                          (assoc :sync/last-checked (java.time.Instant/now))
                          (update :sync/version inc))]

          (if (schema/validate-yakread-content updated)
            (do
              (biff/submit-tx ctx [updated])
              (log/info "Updated content text for doc-id:" doc-id)
              true)
            (do
              (log/error "Updated content failed validation"
                         "errors:" (schema/explain-validation-error
                                    schema/YakreadContent updated))
              false)))
        (do
          (log/warn "Document not found for update:" doc-id)
          false)))

    (catch Exception e
      (log/error "Failed to update content text:" (.getMessage e))
      false)))

(defn sync-alfresco-content!
  "Sync content from Alfresco, updating existing or creating new"
  [ctx alfresco-nodes]
  (let [results (atom {:created 0 :updated 0 :errors 0})]

    (doseq [node alfresco-nodes]
      (try
        (let [alfresco-id (:id node)
              existing (get-content-by-alfresco-id ctx alfresco-id)]

          (if existing
            ;; Update existing content
            (let [updated-content (-> existing
                                      (merge (schema/transform-alfresco->yakread node))
                                      (assoc :sync/last-checked (java.time.Instant/now))
                                      (update :sync/version inc))]

              (if (schema/validate-yakread-content updated-content)
                (do
                  (biff/submit-tx ctx [updated-content])
                  (swap! results update :updated inc)
                  (log/debug "Updated content for Alfresco node:" alfresco-id))
                (do
                  (swap! results update :errors inc)
                  (log/error "Updated content failed validation for node:" alfresco-id))))

            ;; Create new content
            (if-let [doc-id (store-alfresco-content! ctx node)]
              (do
                (swap! results update :created inc)
                (log/debug "Created new content for Alfresco node:" alfresco-id))
              (swap! results update :errors inc))))

        (catch Exception e
          (swap! results update :errors inc)
          (log/error "Error syncing node:" (:id node) (.getMessage e)))))

    (let [final-results @results]
      (log/info "Alfresco content sync completed"
                "created:" (:created final-results)
                "updated:" (:updated final-results)
                "errors:" (:errors final-results))
      final-results)))

(defn get-content-stats
  "Get statistics about stored Alfresco content"
  [ctx]
  (try
    (let [total-query '{:find [(count ?e)]
                        :where [[?e :yakread/type "alfresco-content"]]}

          published-query '{:find [(count ?e)]
                            :where [[?e :yakread/type "alfresco-content"]
                                    [?e :yakread/status "published"]]}

          by-type-query '{:find [?type (count ?e)]
                          :where [[?e :yakread/type "alfresco-content"]
                                  [?e :alfresco/type ?type]]
                          :group-by [?type]}

          by-mime-type-query '{:find [?mime (count ?e)]
                               :where [[?e :yakread/type "alfresco-content"]
                                       [?e :alfresco/mime-type ?mime]]
                               :group-by [?mime]}

          total (ffirst (biff/q (:biff/db ctx) total-query))
          published (ffirst (biff/q (:biff/db ctx) published-query))
          by-type (into {} (biff/q (:biff/db ctx) by-type-query))
          by-mime-type (into {} (biff/q (:biff/db ctx) by-mime-type-query))]

      {:total-documents total
       :published-documents published
       :by-type by-type
       :by-mime-type by-mime-type
       :last-updated (java.time.Instant/now)})

    (catch Exception e
      (log/error "Failed to get content stats:" (.getMessage e))
      {:error (.getMessage e)})))

(defn cleanup-orphaned-content!
  "Remove content that no longer exists in Alfresco"
  [ctx valid-alfresco-ids]
  (try
    (let [query '{:find [?e ?alfresco-id]
                  :where [[?e :yakread/type "alfresco-content"]
                          [?e :alfresco/node-id ?alfresco-id]]}

          stored-content (biff/q (:biff/db ctx) query)
          stored-ids (set (map second stored-content))
          valid-ids (set valid-alfresco-ids)
          orphaned-ids (set/difference stored-ids valid-ids)

          ;; Find documents to delete
          orphaned-docs (filter #(orphaned-ids (second %)) stored-content)]

      (when (seq orphaned-docs)
        (let [delete-ops (map (fn [[doc-id _]]
                                [:xtdb.api/delete doc-id])
                              orphaned-docs)]

          (biff/submit-tx ctx delete-ops)

          (log/info "Cleaned up orphaned content"
                    "removed:" (count orphaned-docs)
                    "ids:" (map second orphaned-docs))))

      (count orphaned-docs))

    (catch Exception e
      (log/error "Failed to cleanup orphaned content:" (.getMessage e))
      0)))

;; --- HEALTH AND VALIDATION ---

(defn validate-stored-content
  "Validate all stored content against current schemas"
  [ctx & [fix-invalid]]
  (try
    (let [query '{:find [(pull ?e [*])]
                  :where [[?e :yakread/type "alfresco-content"]]}

          all-content (map first (biff/q (:biff/db ctx) query))
          validation-results (map (fn [content]
                                    {:doc-id (:xt/id content)
                                     :valid? (schema/validate-yakread-content content)
                                     :errors (when-not (schema/validate-yakread-content content)
                                               (schema/explain-validation-error
                                                schema/YakreadContent content))
                                     :content content})
                                  all-content)

          valid-count (count (filter :valid? validation-results))
          invalid-count (count (filter #(not (:valid? %)) validation-results))]

      (log/info "Content validation completed"
                "valid:" valid-count
                "invalid:" invalid-count)

      (when (and fix-invalid (pos? invalid-count))
        (log/info "Attempting to fix invalid content...")
        ;; TODO: Implement content migration/fixing logic
        )

      {:total (count validation-results)
       :valid valid-count
       :invalid invalid-count
       :invalid-docs (filter #(not (:valid? %)) validation-results)})

    (catch Exception e
      (log/error "Failed to validate stored content:" (.getMessage e))
      {:error (.getMessage e)})))