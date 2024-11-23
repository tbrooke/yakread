(ns com.yakread.lib.rss
  (:require [com.biffweb :as biff]
            [clj-http.client :as http]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [remus]
            [clj-xpath.core :as xpath])
  (:import [org.jsoup Jsoup]
           [org.xml.sax SAXParseException]))

(defn fix-url [url]
  (if (and (not-empty url)
           (not (str/starts-with? url "http")))
    (str "https://" url)
    url))

(defn- remus-parse [response]
  (biff/catchall
   (remus/parse-http-resp (cond-> response
                            true
                            (update :body #(io/input-stream (.getBytes %)))

                            (contains? (:headers response) "Content-Type")
                            (assoc-in [:headers "Content-Type"] "application/xml")))))

(defn parse-urls [{:keys [url body] :as http-response}]
  (let [doc (delay (Jsoup/parse body url))]
    (if (not-empty (:entries (remus-parse http-response)))
      [{:url url}]
      (for [element (.select @doc (str "link[type=application/rss+xml], "
                                       "link[type=application/atom+xml]"))]
        {:url (.attr element "abs:href")
         :title (.attr element "title")}))))

(defn extract-opml-urls [body]
  (some->> (try (xpath/xml->doc body) (catch SAXParseException _))
           (xpath/$x:attrs* "//outline")
           (keep :xmlUrl)
           distinct))
