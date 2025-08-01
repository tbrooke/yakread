(ns com.yakread.lib.content
  (:require
   [cld.core :as cld]
   [clojure.string :as str]
   [com.biffweb :as biff]
   [pantomime.extract :as pantomime])
  (:import
   [org.jsoup Jsoup]))

(defn truncate
  "Truncates a string s to be at most n characters long, appending an ellipsis if any characters were removed."
  [s n]
  (if (<= (count s) n)
    s
    (str (subs s 0 (dec n)) "…")))

(defn html->text [html]
  (some-> html (Jsoup/parse) (.text)))

(defn lang [html]
  (try
    (some-> (html->text html)
            cld/detect
            first
            not-empty)
    (catch Exception _
      "en")))

(defn excerpt [text]
  (some-> text
          (str/trim)
          (str/replace #"\s+" " ")
          (truncate 500)))

(defn pantomime-parse [html]
  (-> html
      (.getBytes "UTF-8")
      (java.io.ByteArrayInputStream.)
      pantomime/parse
      (try (catch Exception _))))

(defn normalize [html]
  (let [doc (Jsoup/parse html)]
    (-> doc
        (.select "a[href]")
        (.attr "target" "_blank"))
    (doseq [img (.select doc "img[src^=http://]")]
      (.attr img "src" (str/replace (.attr img "src")
                                    #"^http://"
                                    "https://")))
    (.outerHtml doc)))

(defn parse-instant [d]
  (some->> [biff/rfc3339
            "yyyy-MM-dd'T'HH:mm:ssXXX"]
           (keep (fn [fmt]
                   (biff/catchall (biff/parse-date d fmt))))
           first
           (.toInstant)))

(defn clean-string [s]
  (str/replace (apply str (remove #{\newline
                                    (char 65279)
                                    (char 847)
                                    (char 8199)}
                                  s))
               #"\s+"
               " "))

(defn add-protocol [url]
  (if (and (not-empty url)
           (not (str/starts-with? url "http")))
    (str "https://" url)
    url))
