(ns com.yakread.layouts.core
  "Core layout registry and rendering functions"
  (:require
   [com.yakread.components.core :as components]))

(defn hero-page-layout
  "Layout with prominent hero section followed by content cards"
  [components-data]
  (let [hero-components (filter #(= (:component-type %) "hero") components-data)
        other-components (filter #(not= (:component-type %) "hero") components-data)]
    [:div
     ;; Hero section first
     (for [hero hero-components]
       (components/render-component (:component-type hero) hero))
     ;; Other components below
     (for [component other-components]
       (components/render-component (:component-type component) component))]))

(defn text-page-layout
  "Simple layout for text-heavy pages without hero"
  [components-data]
  [:div
   (for [component components-data]
     (components/render-component (:component-type component) component))])

(defn listing-page-layout
  "Layout optimized for chronological content (blogs, events)"
  [components-data]
  (let [sorted-components (sort-by #(or (:published-date %) (:event-date %) (:display-order %))
                                   #(compare %2 %1) ; reverse chronological
                                   components-data)]
    [:div {:class "mtz-listing-page"}
     (for [component sorted-components]
       [:div {:class "mtz-listing-item"}
        (components/render-component (:component-type component) component)])]))

(def layout-registry
  "Registry of available layout functions"
  {"hero-page" hero-page-layout
   "text-page" text-page-layout
   "listing-page" listing-page-layout})

(defn validate-component-layout-compatibility
  "Validate that components are compatible with the specified layout"
  [layout-type components-data]
  ;; Component-layout compatibility rules
  (let [layout-rules {"hero-page" #{"hero" "text-card" "html-card" "announcement"}
                      "text-page" #{"text-card" "html-card" "announcement"}
                      "listing-page" #{"blog-post" "event" "alfresco-event" "text-card" "html-card"}}
        allowed-components (get layout-rules layout-type #{})]
    (filter #(contains? allowed-components (:component-type %)) components-data)))

(defn render-with-layout
  "Render components using the specified layout"
  [layout-type components-data]
  (let [validated-components (validate-component-layout-compatibility layout-type components-data)
        layout-fn (get layout-registry layout-type hero-page-layout)]
    (if (seq validated-components)
      (layout-fn validated-components)
      [:div {:class "mtz-content-card"}
       [:h2 "No Compatible Content"]
       [:p "No components found that are compatible with layout: " layout-type]])))