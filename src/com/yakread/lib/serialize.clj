(ns com.yakread.lib.serialize
  (:require [com.biffweb :as biff]
            [clojure.edn :as edn]))

(defn edn->base64 [edn]
  (biff/base64-encode (.getBytes (pr-str edn))))

(defn base64->edn [base64]
  (edn/read-string (String. (biff/base64-decode base64))))

(defn uuid->url [uuid]
  (biff/catchall
   (let [ba (.. (java.nio.ByteBuffer/allocate 16)
                (putLong (.getMostSignificantBits uuid))
                (putLong (.getLeastSignificantBits uuid))
                array)]
     (subs (.encodeToString (java.util.Base64/getUrlEncoder) ba) 0 22))))

(defn url->uuid [s]
  (biff/catchall
   (let [buffer (-> (java.util.Base64/getUrlDecoder)
                    (.decode (str s "=="))
                    (java.nio.ByteBuffer/wrap))]
     (java.util.UUID. (.getLong buffer) (.getLong buffer)))))
