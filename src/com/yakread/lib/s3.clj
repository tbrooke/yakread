(ns com.yakread.lib.s3
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [com.biffweb :as biff]))

;; use the filesystem instead of S3
(defn mock-request [_ input]
  (let [file (io/file "storage/mock-s3" (:key input))]
    (case (:method input)
      "PUT" (spit (doto file io/make-parents)
                  (pr-str (select-keys input [:headers :body])))
      "GET" (edn/read-string (slurp file))
      "DELETE" (do (.delete file) nil))))

(defn translate-config [{:keys [biff.s3/config-ns biff/secret] :as ctx}]
  (cond-> ctx
    config-ns (merge (biff/select-ns-as ctx config-ns 'biff.s3)
                     {:biff/secret
                      (fn [k]
                        (or (when (= k :biff.s3/secret-key)
                              (secret (keyword (str config-ns) "secret-key")))
                            (secret k)))})))

(defn request [ctx input]
  (biff/s3-request (translate-config (merge ctx (biff/select-ns-as input nil 'biff.s3)))))
