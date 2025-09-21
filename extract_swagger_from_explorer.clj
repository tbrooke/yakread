#!/usr/bin/env bb

;; Extract Swagger configuration from API Explorer HTML

(require '[babashka.curl :as curl]
         '[clojure.string :as str])

(def alfresco-host "http://admin.mtzcg.com")
(def alfresco-user "admin")
(def alfresco-pass "admin")

(println "=== Extracting Swagger Info from API Explorer ===")

(try
  (let [resp (curl/get (str alfresco-host "/api-explorer/")
                       {:basic-auth [alfresco-user alfresco-pass]})]
    (if (= 200 (:status resp))
      (let [html (:body resp)]
        (println "✅ API Explorer HTML retrieved")

        ;; Look for various Swagger/OpenAPI references
        (println "\n=== Searching for Swagger References ===")

        ;; Look for swagger URLs
        (when-let [swagger-urls (re-seq #"['\"]([^'\"]*swagger[^'\"]*\.json)['\"]" html)]
          (println "Swagger URLs found:")
          (doseq [[_ url] swagger-urls]
            (println " -" url)))

        ;; Look for openapi URLs
        (when-let [openapi-urls (re-seq #"['\"]([^'\"]*openapi[^'\"]*\.json)['\"]" html)]
          (println "OpenAPI URLs found:")
          (doseq [[_ url] openapi-urls]
            (println " -" url)))

        ;; Look for spec URLs
        (when-let [spec-urls (re-seq #"['\"]([^'\"]*spec[^'\"]*\.json)['\"]" html)]
          (println "Spec URLs found:")
          (doseq [[_ url] spec-urls]
            (println " -" url)))

        ;; Look for API configuration in JavaScript
        (println "\n=== Searching for API Configuration ===")

        ;; Look for url: or spec: configurations
        (when-let [api-configs (re-seq #"(?i)(?:url|spec):\s*['\"]([^'\"]+)['\"]" html)]
          (println "API configurations found:")
          (doseq [[_ config] api-configs]
            (println " -" config)))

        ;; Look for SwaggerUIBundle configurations
        (when-let [swagger-configs (re-seq #"SwaggerUIBundle\([^)]*url:\s*['\"]([^'\"]+)['\"]" html)]
          (println "SwaggerUI configurations found:")
          (doseq [[_ config] swagger-configs]
            (println " -" config)))

        ;; Save a snippet of the HTML for manual inspection
        (println "\n=== HTML Preview (first 1000 chars) ===")
        (println (subs html 0 (min 1000 (count html))))

        ;; Look for script tags that might load the spec
        (when-let [scripts (re-seq #"<script[^>]*src=['\"]([^'\"]*)['\"][^>]*>" html)]
          (println "\n=== Script sources ===")
          (doseq [[_ src] (take 10 scripts)]
            (println " -" src))))

      (println "❌ Failed to access API Explorer. Status:" (:status resp))))

  (catch Exception e
    (println "❌ Error accessing API Explorer:" (.getMessage e))))