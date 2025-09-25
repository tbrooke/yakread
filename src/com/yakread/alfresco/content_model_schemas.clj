(ns com.yakread.alfresco.content-model-schemas
  "Flexible Malli schemas for Alfresco content models
   Dynamically validates content based on type"
  (:require
   [malli.core :as m]
   [malli.error :as me]
   [malli.registry :as mr]
   [clojure.tools.logging :as log]))

;; --- BASE SCHEMAS ---

(def BaseNode
  "Common properties all Alfresco nodes have"
  [:map
   [:alfresco/node-id :string]
   [:alfresco/type :string]
   [:cm/name :string]
   [:cm/created inst?]
   [:cm/creator :string]
   [:cm/modified inst?]
   [:cm/modifier :string]])

(def ContentNode
  "Schema for cm:content nodes"
  [:and
   BaseNode
   [:map
    [:cm/title {:optional true} :string]
    [:cm/description {:optional true} :string]]])

(def FolderNode
  "Schema for cm:folder nodes"
  [:and
   BaseNode
   [:map
    [:alfresco/is-folder [:= true]]
    [:folder/child-count {:optional true} :int]]])

;; --- ASPECT SCHEMAS ---

(def TaggableAspect
  [:map
   [:tags/list {:optional true} [:vector :string]]])

(def VersionableAspect
  [:map
   [:version/number {:optional true} :string]
   [:version/label {:optional true} :string]])

(def GeographicAspect
  [:map
   [:geo/latitude {:optional true} :double]
   [:geo/longitude {:optional true} :double]
   [:geo/address {:optional true} :string]])

;; --- CUSTOM PROPERTY SCHEMAS ---

(def MtzWebContentProperties
  "Mt Zion web content properties"
  [:map
   [:mtz/target-page {:optional true} 
    [:enum "home" "about" "worship" "activities" "events" "contact"]]
   [:mtz/page-section {:optional true}
    [:enum "hero" "feature1" "feature2" "feature3" "sidebar" "footer"]]
   [:mtz/display-order {:optional true} :int]
   [:mtz/is-active {:optional true} :boolean]
   [:mtz/publish-date {:optional true} inst?]
   [:mtz/expiry-date {:optional true} inst?]])

(def MtzEventProperties
  "Mt Zion event properties"
  [:map
   [:mtz/event-date inst?]
   [:mtz/event-end-date {:optional true} inst?]
   [:mtz/event-location :string]
   [:mtz/event-capacity {:optional true} :int]
   [:mtz/registration-url {:optional true} :string]
   [:mtz/event-type {:optional true} 
    [:enum "worship" "community" "fundraiser" "meeting" "youth" "educational"]]])

(def MtzMinistryProperties
  "Mt Zion ministry properties"
  [:map
   [:mtz/ministry-name :string]
   [:mtz/ministry-leader {:optional true} :string]
   [:mtz/ministry-email {:optional true} :string]
   [:mtz/meeting-day {:optional true}
    [:enum "monday" "tuesday" "wednesday" "thursday" "friday" "saturday" "sunday"]]
   [:mtz/meeting-time {:optional true} :string]
   [:mtz/meeting-frequency {:optional true}
    [:enum "weekly" "biweekly" "monthly" "quarterly"]]])

;; --- DYNAMIC SCHEMA BUILDER ---

(defn build-schema-for-type
  "Build a schema based on content type and aspects"
  [content-type aspects]
  (let [base-schema (cond
                     (= content-type "cm:content") ContentNode
                     (= content-type "cm:folder") FolderNode
                     :else BaseNode)
        
        ;; Add aspect schemas
        aspect-schemas (cond-> []
                        (some #{"cm:taggable"} aspects)
                        (conj TaggableAspect)
                        
                        (some #{"cm:versionable"} aspects)
                        (conj VersionableAspect)
                        
                        (some #{"mtz:geographic"} aspects)
                        (conj GeographicAspect))
        
        ;; Add custom type schemas based on type prefix
        custom-schemas (cond-> []
                        (str/starts-with? content-type "mtz:webContent")
                        (conj MtzWebContentProperties)
                        
                        (str/starts-with? content-type "mtz:event")
                        (conj MtzEventProperties)
                        
                        (str/starts-with? content-type "mtz:ministry")
                        (conj MtzMinistryProperties))]
    
    ;; Combine all schemas
    (if (empty? (concat aspect-schemas custom-schemas))
      base-schema
      (into [:and base-schema] (concat aspect-schemas custom-schemas)))))

;; --- PROPERTY DISCOVERY SCHEMA ---

(def PropertyNamespace
  "Schema for discovered property namespaces"
  [:map
   [:namespace :string]
   [:prefix :string]
   [:property-count :int]
   [:sample-properties {:optional true} [:vector :keyword]]])

(def DiscoveredModel
  "Schema for model introspection results"
  [:map
   [:model/type :string]
   [:model/aspects [:vector :string]]
   [:model/property-namespaces [:vector :string]]
   [:model/validation-schema {:optional true} any?]])

;; --- VALIDATION HELPERS ---

(defn validate-content
  "Validate content against its appropriate schema"
  [content]
  (let [content-type (:alfresco/type content)
        aspects (:alfresco/aspects content [])
        schema (build-schema-for-type content-type aspects)]
    (if (m/validate schema content)
      {:valid? true
       :content content}
      {:valid? false
       :content content
       :errors (me/humanize (m/explain schema content))
       :schema schema})))

(defn validate-with-custom-schema
  "Validate content with a custom schema"
  [content custom-schema]
  (if (m/validate custom-schema content)
    {:valid? true}
    {:valid? false
     :errors (me/humanize (m/explain custom-schema content))}))

;; --- SCHEMA REGISTRY ---

(def schema-registry
  "Registry of known content type schemas"
  (atom {}))

(defn register-content-schema!
  "Register a schema for a specific content type"
  [content-type schema]
  (swap! schema-registry assoc content-type schema))

(defn get-registered-schema
  "Get a registered schema for content type"
  [content-type]
  (get @schema-registry content-type))

;; --- FLEXIBLE VALIDATION ---

(defn validate-properties-by-namespace
  "Validate just the properties of a specific namespace"
  [properties namespace-prefix schema]
  (let [filtered-props (reduce-kv
                        (fn [acc k v]
                          (if (str/starts-with? (namespace k) namespace-prefix)
                            (assoc acc k v)
                            acc))
                        {}
                        properties)]
    (m/validate schema filtered-props)))

;; --- INITIALIZATION ---

(defn init-standard-schemas!
  "Initialize standard Mt Zion content schemas"
  []
  (register-content-schema! "cm:content" ContentNode)
  (register-content-schema! "cm:folder" FolderNode)
  (register-content-schema! "mtz:webContent" 
                           [:and ContentNode MtzWebContentProperties])
  (register-content-schema! "mtz:event"
                           [:and ContentNode MtzEventProperties])
  (register-content-schema! "mtz:ministry"
                           [:and ContentNode MtzMinistryProperties]))

;; --- USAGE EXAMPLES ---

(comment
  ;; Initialize schemas
  (init-standard-schemas!)
  
  ;; Validate a content node
  (validate-content
   {:alfresco/node-id "123"
    :alfresco/type "mtz:webContent"
    :alfresco/aspects ["cm:taggable" "cm:versionable"]
    :cm/name "Blood Drive"
    :cm/created #inst "2024-01-01"
    :cm/creator "Tom"
    :mtz/target-page "home"
    :mtz/page-section "feature2"
    :tags/list ["event" "community"]})
  
  ;; Register custom schema
  (register-content-schema! 
   "mtz:sermon"
   [:map
    [:mtz/sermon-date inst?]
    [:mtz/speaker :string]
    [:mtz/bible-references [:vector :string]]
    [:mtz/audio-url {:optional true} :string]
    [:mtz/transcript {:optional true} :string]])
)