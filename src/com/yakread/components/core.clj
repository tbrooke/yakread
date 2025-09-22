(ns com.yakread.components.core
  "Core component registry and rendering functions"
  (:require
   [clojure.string :as str]))

;; Image processing functions
(defn extract-node-id-from-share-url
  "Extract node ID from Alfresco share URL"
  [share-url]
  (when share-url
    (let [node-ref-match (re-find #"nodeRef=workspace://SpacesStore/([a-f0-9\-]+)" share-url)]
      (second node-ref-match))))

(defn extract-node-id-from-placeholder
  "Extract node ID from alfresco-node: placeholder"
  [placeholder]
  (when placeholder
    (second (re-find #"alfresco-node:([a-f0-9\-]+)" placeholder))))

(defn get-rendition-url
  "Generate yakread proxy URL for Alfresco images"
  [node-id size base-domain]
  (let [rendition-type (case size
                         "small" "doclib"
                         "medium" "imgpreview"
                         "large" "imgpreview"
                         "original" nil
                         "doclib")]
    (if (= size "original")
      (str "/proxy/image/" node-id)
      (str "/proxy/image/" node-id "/" rendition-type))))

(defn process-alfresco-images
  "Process HTML content to convert Alfresco references to rendition URLs"
  [html-content & {:keys [default-size base-domain]
                   :or {default-size "medium"
                        base-domain "http://admin.mtzcg.com"}}]
  (when html-content
    (-> html-content
        ;; Handle share URLs with size specification
        (str/replace
         #"src=\"([^\"]*document-details\?nodeRef=workspace://SpacesStore/[a-f0-9\-]+)\""
         (fn [[full-match share-url]]
           (when-let [node-id (extract-node-id-from-share-url share-url)]
             (str "src=\"" (get-rendition-url node-id default-size base-domain) "\""))))
        ;; Handle alfresco-node: placeholders
        (str/replace
         #"src=\"alfresco-node:([a-f0-9\-]+)(?:\?size=([^\"]+))?\""
         (fn [[full-match node-id size]]
           (let [img-size (or size default-size)]
             (str "src=\"" (get-rendition-url node-id img-size base-domain) "\""))))
        ;; Handle alfresco-node: placeholders without quotes (for testing)
        (str/replace
         #"alfresco-node:([a-f0-9\-]+)(?:\?size=([^\s]+))?"
         (fn [[full-match node-id size]]
           (let [img-size (or size default-size)]
             (get-rendition-url node-id img-size base-domain)))))))

(defn content-card
  "Reusable content card component for Mount Zion content"
  [title content & {:keys [card-class title-class content-class]
                    :or {card-class "mtz-content-card"
                         title-class "mtz-content-title"
                         content-class "mtz-content-body"}}]
  [:div {:class card-class}
   (when title
     [:h1 {:class title-class} title])
   [:div {:class content-class}
    (if (string? content)
      [:div {:dangerouslySetInnerHTML {:__html (process-alfresco-images content)}}]
      content)]])

(defn hero-component
  "Large hero banner component"
  [data]
  [:div {:class "mtz-hero"}
   [:div {:class "mtz-hero-content"}
    [:h1 {:class "mtz-hero-title"} (:title data)]
    (when (:subtitle data)
      [:p {:class "mtz-hero-subtitle"} (:subtitle data)])
    (when (:call-to-action data)
      [:a {:class "mtz-hero-button" :href (:cta-link data "/contact")}
       (:call-to-action data)])]])

(defn text-card-component
  "Standard text card (same as content-card but component-system compatible)"
  [data]
  (content-card (:title data) (:content data)))

(defn announcement-component
  "Eye-catching announcement card"
  [data]
  (content-card (:title data) (:content data)
                :card-class "mtz-content-card mtz-announcement"))

(defn html-card-component
  "Rich HTML content card"
  [data]
  (content-card (:title data) (:html-content data)))

(defn event-component
  "Event listing with date, time, location and description"
  [data]
  [:article {:class "mtz-event"}
   [:header {:class "mtz-event-header"}
    [:h3 {:class "mtz-event-title"} (:title data)]
    [:div {:class "mtz-event-meta"}
     (when (:event-date data)
       [:time {:class "mtz-event-date"} (:event-date data)])
     (when (:event-time data)
       [:span {:class "mtz-event-time"} (:event-time data)])
     (when (:location data)
       [:span {:class "mtz-event-location"} (:location data)])]]
   [:div {:class "mtz-event-content"}
    (if (:html-content data)
      [:div {:dangerouslySetInnerHTML {:__html (process-alfresco-images (:html-content data))}}]
      [:p (:description data)])]])

(defn blog-post-component
  "Blog post with date, author, and content"
  [data]
  [:article {:class "mtz-blog-post"}
   [:header {:class "mtz-blog-header"}
    [:h2 {:class "mtz-blog-title"} (:title data)]
    [:div {:class "mtz-blog-meta"}
     (when (:published-date data)
       [:time {:class "mtz-blog-date"} (:published-date data)])
     (when (:author data)
       [:span {:class "mtz-blog-author"} "by " (:author data)])]]
   [:div {:class "mtz-blog-content"}
    (if (:html-content data)
      [:div {:dangerouslySetInnerHTML {:__html (process-alfresco-images (:html-content data))}}]
      [:p (:content data)])]])

(defn alfresco-event-component
  "Event component specifically for Alfresco calendar events"
  [data]
  [:article {:class "mtz-event mtz-alfresco-event"}
   [:header {:class "mtz-event-header"}
    [:h3 {:class "mtz-event-title"} (:title data)]
    [:div {:class "mtz-event-meta"}
     (when (:event-date data)
       [:time {:class "mtz-event-date" :datetime (:event-date data)}
        (try
          (let [date (java.time.LocalDate/parse (:event-date data))]
            (.format date (java.time.format.DateTimeFormatter/ofPattern "MMMM d, yyyy")))
          (catch Exception e (:event-date data)))])
     (when (:event-time data)
       [:span {:class "mtz-event-time"}
        (try
          (let [time (java.time.LocalTime/parse (:event-time data))]
            (.format time (java.time.format.DateTimeFormatter/ofPattern "h:mm a")))
          (catch Exception e (:event-time data)))])
     (when (:location data)
       [:span {:class "mtz-event-location"} (:location data)])]]
   [:div {:class "mtz-event-content"}
    (cond
      (:html-content data)
      [:div {:dangerouslySetInnerHTML {:__html (process-alfresco-images (:html-content data))}}]

      (:description data)
      [:p (:description data)]

      :else
      [:p "Details coming soon."])]])

(def component-registry
  "Registry of available component types"
  {"hero" hero-component
   "text-card" text-card-component
   "html-card" html-card-component
   "announcement" announcement-component
   "event" event-component
   "blog-post" blog-post-component
   "alfresco-event" alfresco-event-component})

(defn render-component
  "Render a component based on its type and data"
  [component-type data]
  (if-let [component-fn (get component-registry component-type)]
    (component-fn data)
    [:div {:class "mtz-content-card"}
     [:h2 "Unknown Component"]
     [:p "Component type '" component-type "' not found"]]))