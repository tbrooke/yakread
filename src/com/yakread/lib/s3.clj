(ns com.yakread.lib.s3
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]))

;; use the filesystem instead of S3
(defn mock-request [_ input]
  (let [file (io/file "storage/mock-s3" (:key input))]
    (case (:method input)
      "PUT" (spit (doto file io/make-parents)
                  (pr-str (select-keys input [:headers :body])))
      "GET" (edn/read-string (slurp file))
      "DELETE" (do (.delete file) nil))))
