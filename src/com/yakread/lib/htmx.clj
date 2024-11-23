(ns com.yakread.lib.htmx
  (:require [cheshire.core :as cheshire]))

(defn edn-hx-vals [params]
  (cheshire/generate-string {:edn (pr-str params)}))
