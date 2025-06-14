(ns com.yakread.lib.form
  (:require [clojure.pprint :as pprint]
            [com.biffweb :as biff]
            [fast-edn.core :as fedn]
            [com.yakread.util.biff-staging :as biffs]
            [com.yakread.lib.core :as lib.core]
            [potemkin.collections :as potemkin.c]))

;; TODO handle multiple checkboxes with same name

(defn- memoize-latest
  "Like memoize but only cache the latest result"
  [f]
  (let [cache (atom {})]
    (fn [& args]
      (let [cache* @cache]
        (if (contains? cache* args)
          (get cache* args)
          (let [result (apply f args)]
            (reset! cache {args result})
            result))))))

(defn- parse-form [field->parser form-params]
  (reduce-kv (fn [params k v]
               (if-some [field-or-path (biff/catchall (fedn/read-string k))]
                 (let [path (if (vector? field-or-path) field-or-path [field-or-path])
                       field (last path)
                       parser (get field->parser field identity)
                       value (biff/catchall (some-> v not-empty parser))]
                   (cond-> params
                     value (assoc-in path value)))
                 params))
             {}
             form-params))

(defn- parsers [malli-opts]
  (into {}
        (for [[field props] (biffs/field-asts malli-opts)]
          [field (or (get-in props [:properties :biff.form/parser])
                     (get-in props [:value :properties :biff.form/parser])
                     (case (get-in props [:value :type])
                       :boolean #(= "on" %)
                       :int Long/parseLong
                       :uuid parse-uuid
                       :string (if-some [max-value (get-in props [:value :properties :max])]
                                 (fn [s]
                                   (cond-> s
                                     (< max-value (count s)) (subs 0 max-value)))
                                 identity)
                       identity))])))

(def ^:private memo-parsers (memoize-latest parsers))

(defn wrap-parse-form [handler]
  (fn [{:keys [biff/malli-opts biff.form/parser-overrides form-params] :as ctx}]
     (let [new-params (lib.core/->DerefMap
                       (delay (-> (memo-parsers @malli-opts)
                                  (merge parser-overrides)
                                  (parse-form form-params))))]
       (handler (cond-> ctx
                  (not-empty form-params)
                  (assoc :biff.form/params new-params))))))
