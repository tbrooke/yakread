(ns com.yakread.app.image-proxy
  "Image proxy for serving Alfresco images with authentication"
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [com.biffweb :as biff]
   [ring.util.response :as response]
   [clj-http.client :as http]
   [clojure.tools.logging :as log]))

(def alfresco-config
  "Alfresco connection configuration"
  {:base-url "http://admin.mtzcg.com"
   :username "admin"  ; Configure these in your config.edn
   :password "admin"  ; Configure these in your config.edn
   :cache-dir "cache/images"})

(defn- simple-hash
  "Generate simple hash for cache key"
  [input]
  (str (Math/abs (.hashCode input))))

(defn- ensure-cache-dir
  "Ensure cache directory exists"
  [cache-dir]
  (let [dir (io/file cache-dir)]
    (when-not (.exists dir)
      (.mkdirs dir))
    dir))

(defn- get-cache-path
  "Get cache file path for a given URL"
  [url]
  (let [hash (simple-hash url)
        cache-dir (:cache-dir alfresco-config)]
    (str cache-dir "/" hash ".jpg")))

(defn- cached-file-exists?
  "Check if cached file exists and is recent (less than 24 hours old)"
  [cache-path]
  (let [file (io/file cache-path)]
    (when (.exists file)
      (let [last-modified (.lastModified file)
            now (System/currentTimeMillis)
            age-hours (/ (- now last-modified) 1000 60 60)]
        (< age-hours 24)))))

(defn- fetch-from-alfresco
  "Fetch image from Alfresco with authentication"
  [url]
  (try
    (log/info "Fetching from Alfresco:" url)
    (let [response (http/get url
                             {:basic-auth [(:username alfresco-config)
                                           (:password alfresco-config)]
                              :as :byte-array
                              :headers {"User-Agent" "Mozilla/5.0 (compatible; yakread-proxy/1.0)"
                                        "Accept" "*/*"}
                              :throw-exceptions false})]
      (log/info "Alfresco response status:" (:status response))
      (if (= 200 (:status response))
        {:success true :data (:body response) :content-type (get-in response [:headers "content-type"])}
        {:success false :status (:status response) :error (str "HTTP " (:status response)) :headers (:headers response)}))
    (catch Exception e
      (log/error "Error fetching from Alfresco:" (.getMessage e))
      {:success false :error (.getMessage e)})))

(defn- cache-image
  "Cache image data to local file"
  [cache-path image-data]
  (try
    (ensure-cache-dir (:cache-dir alfresco-config))
    (with-open [out (io/output-stream cache-path)]
      (.write out image-data))
    (log/info "Cached image to:" cache-path)
    true
    (catch Exception e
      (log/error "Error caching image:" (.getMessage e))
      false)))

(defn- serve-cached-image
  "Serve image from cache"
  [cache-path content-type]
  (if (.exists (io/file cache-path))
    (-> (response/response (io/file cache-path))
        (response/content-type (or content-type "image/jpeg"))
        (response/header "Cache-Control" "public, max-age=86400")) ; 24 hour cache
    (-> (response/response "Image not found")
        (response/status 404)
        (response/content-type "text/plain"))))

(defn proxy-alfresco-image
  "Main proxy function - fetch, cache, and serve Alfresco images"
  [node-id rendition-type]
  (let [base-url (:base-url alfresco-config)
        ; Use only the working original content endpoint for now
        ; The renditions endpoint returns 500 errors
        alfresco-url (str base-url "/alfresco/service/api/node/content/workspace/SpacesStore/" node-id)
        cache-path (get-cache-path alfresco-url)]

    (log/info "Proxying image - Node ID:" node-id "Rendition:" (or rendition-type "original"))

    (if (cached-file-exists? cache-path)
      ; Serve from cache
      (do
        (log/info "Serving from cache:" cache-path)
        (serve-cached-image cache-path "image/jpeg"))

      ; Fetch from Alfresco and cache
      (let [fetch-result (fetch-from-alfresco alfresco-url)]
        (if (:success fetch-result)
          (do
            (cache-image cache-path (:data fetch-result))
            (serve-cached-image cache-path (:content-type fetch-result)))

          ; Return error response
          (do
            (log/warn "Failed to fetch image:" (:error fetch-result))
            (-> (response/response "Image not found")
                (response/status 404)
                (response/content-type "text/plain"))))))))

(defn get-proxy-url
  "Generate yakread proxy URL for an Alfresco image"
  [node-id rendition-type]
  (str "/proxy/image/" node-id
       (when rendition-type (str "/" rendition-type))))

;; Test route to verify the proxy is working
(def image-proxy-test-route
  ["/proxy/test"
   {:name ::image-proxy-test
    :get (fn [{:keys [path-params]}]
           {:status 200
            :headers {"content-type" "text/plain"}
            :body "Image proxy is working!"})}])

;; Route definitions
(def image-proxy-original-route
  ["/proxy/image/:node-id"
   {:name ::image-proxy-original
    :get (fn [{:keys [path-params]}]
           (log/info "Image proxy route hit for node-id:" (:node-id path-params))
           (proxy-alfresco-image (:node-id path-params) nil))}])

(def image-proxy-rendition-route
  ["/proxy/image/:node-id/:rendition-type"
   {:name ::image-proxy-rendition
    :get (fn [{:keys [path-params]}]
           (log/info "Image proxy route hit for node-id:" (:node-id path-params) "rendition:" (:rendition-type path-params))
           (proxy-alfresco-image (:node-id path-params) (:rendition-type path-params)))}])

(def module
  {:routes [image-proxy-test-route
            image-proxy-original-route
            image-proxy-rendition-route]})