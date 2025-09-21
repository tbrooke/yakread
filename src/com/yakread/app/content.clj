(ns com.yakread.app.content
  "Minimal content API for testing Feature1 pipeline"
  (:require [clojure.data.json :as json]
            [com.biffweb :as biff]
            [clojure.tools.logging :as log]))

(defn get-feature1-content
  "GET /api/mtzuix/feature1 - Get Feature 1 content for mtzUIX homepage"
  [ctx]
  (try
    (log/info "Serving Feature 1 content for mtzUIX")

    ;; For now, read from our generated content file
    ;; Later this will query XTDB directly
    (let [content-file "mtzuix-feature1-content.edn"]
      (if (.exists (clojure.java.io/file content-file))
        (let [feature1-content (clojure.edn/read-string (slurp content-file))
              first-content (first feature1-content)]

          {:status 200
           :headers {"content-type" "application/json"
                     "access-control-allow-origin" "*"}  ; For development
           :body (json/write-str
                  {:success true
                   :component "feature1"
                   :data {:title (:title first-content)
                          :html (:html-content first-content)
                          :text (:text-content first-content)
                          :lastUpdated (:last-updated first-content)
                          :source (:source-info first-content)
                          :componentId (:component-id first-content)}
                   :timestamp (str (biff/now))})})

        ;; Fallback when no content file exists
        {:status 200
         :headers {"content-type" "application/json"
                   "access-control-allow-origin" "*"}
         :body (json/write-str
                {:success true
                 :component "feature1"
                 :data {:title "Feature 1"
                        :html "<p>No content available yet. Run sync_feature1.clj to populate.</p>"
                        :text "No content available yet."
                        :lastUpdated nil
                        :source nil
                        :componentId "homepage-feature1"}
                 :timestamp (str (biff/now))})}))

    (catch Exception e
      (log/error "Error serving Feature 1 content:" (.getMessage e))
      {:status 500
       :headers {"content-type" "application/json"
                 "access-control-allow-origin" "*"}
       :body (json/write-str
              {:success false
               :error "Failed to load Feature 1 content"
               :message (.getMessage e)
               :timestamp (str (biff/now))})})))

;; --- MODULE DEFINITION ---

(def module
  {:api-routes [;; mtzUIX content endpoints
                ["/api/mtzuix/feature1" {:get get-feature1-content}]]})