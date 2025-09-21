;; filename: model_sync.clj

#!/usr/bin/env bb

;; Babashka script to fetch Alfresco OpenAPI and CMM model, generate Malli schemas, and write EDN registry

(require '[babashka.curl :as curl]
         '[cheshire.core :as json]
         '[clojure.edn :as edn]
         '[clojure.string :as str]
         '[clojure.java.io :as io])

;; --- CONFIGURATION ---

(def alfresco-host "http://localhost:8080")
(def alfresco-user "admin")
(def alfresco-pass "admin")
(def openapi-url (str alfresco-host "/alfresco/api/-default-/public/openapi/versions/1/openapi.json"))
(def cmm-url (str alfresco-host "/alfresco/api/-default-/public/cmm/versions/1/cmm"))

(def output-dir "generated-model")
(def openapi-edn (str output-dir "/openapi-malli.edn"))
(def cmm-edn (str output-dir "/cmm-malli.edn"))

;; --- UTILS ---

(defn get-json [url]
  (let [resp (curl/get url {:basic-auth [alfresco-user alfresco-pass] :as :json})]
    (if (= 200 (:status resp))
      (:body resp)
      (throw (ex-info (str "Failed to fetch " url) resp)))))

(defn ensure-dir [dir]
  (.mkdirs (io/file dir)))

(defn write-edn [data path]
  (spit path (prn-str data)))

;; --- OPENAPI → Malli ---

(defn oapi-type->malli [{:keys [type format nullable enum items properties required oneOf anyOf $ref]}]
  (cond
    $ref (keyword (last (str/split $ref #"/")))
    enum [:enum enum]
    oneOf [:orn (into [:orn] (map-indexed (fn [i s] [(keyword (str "alt" i)) (oapi-type->malli s)]) oneOf))]
    anyOf [:orn (into [:orn] (map-indexed (fn [i s] [(keyword (str "alt" i)) (oapi-type->malli s)]) anyOf))]
    (= type "array") [:sequential (oapi-type->malli items)]
    (= type "object")
    (let [req (set required)]
      (into [:map]
            (for [[k v] properties]
              [(keyword k) (if (and (not (req k)) (not (:mandatory v)))
                             [:maybe (oapi-type->malli v)]
                             (oapi-type->malli v))])))
    (= type "string")
    (case format
      "date-time" :inst
      "date" :string
      :string)
    (= type "integer") :int
    (= type "number") :double
    (= type "boolean") :boolean
    :else :any))

(defn openapi->malli-registry [openapi]
  (let [schemas (get-in openapi ["components" "schemas"])]
    (into {}
          (for [[k v] schemas]
            [(keyword k) (oapi-type->malli v)]))))

;; --- CMM → Malli ---

(defn alfresco-prop->malli [{:keys [dataType mandatory multiValued constraint]}]
  (let [base (case dataType
               "d:text" :string
               "d:mltext" :string
               "d:int" :int
               "d:float" :double
               "d:double" :double
               "d:date" :inst
               "d:datetime" :inst
               "d:boolean" :boolean
               "d:noderef" :string
               "d:qname" :string
               "d:content" :any
               :any)
        base (if multiValued [:sequential base] base)
        base (if constraint
               (cond
                 (:allowedValues constraint) [:enum (:allowedValues constraint)]
                 (:regex constraint) [:and base [:re (:regex constraint)]]
                 (:minLength constraint) [:and base [:min (:minLength constraint)] [:max (:maxLength constraint)]]
                 (:minValue constraint) [:and base [:>= (:minValue constraint)] [:<= (:maxValue constraint)]]
                 :else base)
               base)]
    (if (true? mandatory) base [:maybe base])))

(defn alfresco-type->malli [{:keys [name properties]}]
  (into [:map]
        (for [{:keys [name] :as p} properties]
          [(keyword name) (alfresco-prop->malli p)])))

(defn cmm->malli-registry [cmm]
  (let [models (get-in cmm ["list" "entries"])]
    (into {}
          (for [model models
                :let [model-name (get-in model ["entry" "name"])
                      types (get-json (str cmm-url "/" model-name "/types"))]]
            (for [type (get-in types ["list" "entries"])]
              [(keyword (get-in type ["entry" "name"]))
               (alfresco-type->malli (get-in type ["entry"]))])))))

;; --- MAIN ---

(defn -main []
  (ensure-dir output-dir)
  (println "Fetching OpenAPI spec...")
  (let [openapi (get-json openapi-url)
        oapi-reg (openapi->malli-registry openapi)]
    (write-edn oapi-reg openapi-edn)
    (println "Wrote OpenAPI Malli registry to" openapi-edn))
  (println "Fetching CMM models...")
  (let [cmm (get-json cmm-url)
        cmm-reg (apply merge (cmm->malli-registry cmm))]
    (write-edn cmm-reg cmm-edn)
    (println "Wrote CMM Malli registry to" cmm-edn))
  (println "Done."))

(-main)

