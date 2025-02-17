(ns com.yakread.lib.content
  (:require [cld.core :as cld]
            [clojure.string :as str])
  (:import [org.jsoup Jsoup]))

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
