(ns com.yakread.lib.htmx
  (:require [cheshire.core :as cheshire]))

(defn edn-hx-vals [params]
  ;; TODO use tonsky pr-str replacement
  (cheshire/generate-string {:edn (pr-str params)}))
