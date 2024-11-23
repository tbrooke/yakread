(ns com.yakread.lib.serialize
  (:require [com.biffweb :as biff]
            [clojure.edn :as edn]))

(defn edn->base64 [edn]
  (biff/base64-encode (.getBytes (pr-str edn))))

(defn base64->edn [base64]
  (edn/read-string (String. (biff/base64-decode base64))))
