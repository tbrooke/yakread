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

(defn lang [html]
  (try
    (some-> html
            (Jsoup/parse)
            (.text)
            cld/detect
            first
            not-empty)
    (catch Exception _
      "en")))

(defn excerpt [html]
  (some-> html
          (str/trim)
          (str/replace #"\s+" " ")
          (truncate 500)))
