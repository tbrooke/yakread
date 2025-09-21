(ns com.yakread.app.home
  (:require
   [clojure.string :as str]
   [com.biffweb :as biff]
   [com.yakread.lib.icons :as lib.icons]
   [com.yakread.lib.route :refer [href]]
   [com.yakread.lib.ui :as ui]
   [com.yakread.routes :as routes]
   [com.yakread.ui.components :as ui-components]
   [hiccup.page]
   [xtdb.api :as xt]))

(def home-head
  [[:link {:rel "preconnect" :href "https://fonts.googleapis.com"}]
   [:link {:rel "preconnect" :href "https://fonts.gstatic.com" :crossorigin true}]
   [:link {:href "https://fonts.googleapis.com/css2?family=Montserrat:wght@700&display=swap"
           :rel "stylesheet"}]
   [:script {:src "https://www.google.com/recaptcha/api.js" :async "async" :defer "defer"}]])

(def signin-head
  [[:link {:rel "preconnect" :href "https://fonts.googleapis.com"}]
   [:link {:rel "preconnect" :href "https://fonts.gstatic.com" :crossorigin true}]
   [:link {:href "https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap"
           :rel "stylesheet"}]
   [:script {:src "https://www.google.com/recaptcha/api.js" :async "async" :defer "defer"}]])

(defn maybe-error [error-code code->message]
  (when error-code
    (ui/banner
     {:ui/kind :error}
     (get code->message error-code "There was an error."))))

(defn- signin-well [& content]
  [:div {:class '[sm:bg-white
                  "max-w-[26rem]"
                  mx-auto
                  p-8
                  pb-6
                  max-sm:p-4
                  sm:rounded-md
                  w-full]}
   [:img.mx-auto {:src "/img/logo-signin.svg"
                  :alt "Yakread logo"
                  :style {:max-width "200px"}}]
   [:.h-8]
   content])

(defn base-signin-page [{:keys [::errors params] :as ctx} & content]
  (ui/base-page
   (assoc ctx :base/head signin-head)
   (maybe-error (:error params) errors)
   [:div {:class (concat '[bg-neut-75
                           flex
                           flex-col
                           flex-grow
                           overflow-y-auto
                           sm:p-3
                           text-neut-900])}
    [:.h-8]
    [:.sm:flex-grow]
    (signin-well content)
    [:.flex-grow]
    [:.flex-grow]
    [:.sm:h-8.flex-shrink-0]
    (ui/footer {:show-recaptcha-message true})
    [:.h-4]]))

(def signin-button-classes
  '[g-recaptcha
    bg-tealv-500
    hover:bg-tealv-600
    text-center
    py-2
    px-4
    rounded-md
    text-white
    block
    w-full
    inter
    font-medium])

(def signin-page-route
  ["/signin"
   {:name ::signin-page-route
    :get
    (fn signin-page [{:keys [params recaptcha/site-key] :as ctx}]
      (base-signin-page
       (assoc ctx
              :base/title "Sign in | Yakread"
              ::errors {"recaptcha" (str "You failed the recaptcha test. Try again, "
                                         "and make sure you aren't blocking scripts from Google.")
                        "invalid-email" "Invalid email. Try again with a different address."
                        "send-failed" (str "We weren't able to send an email to that address. "
                                           "If the problem persists, try another address.")
                        "invalid-link" "Invalid or expired link. Sign in to get a new link."
                        "not-signed-in" "You must be signed in to view that page."})
       (biff/form
         {:id "signin-form"
          :action "/auth/send-code"
          :hidden {:on-error (href signin-page-route)
                   :redirect "/"
                   :ewt (:ewt params)}}
         (biff/recaptcha-callback "onSubscribe" "signin-form")
         [:.flex-grow (ui/text-input {:name "email"
                                      :placeholder "Enter your email address"
                                      :ui/size :large})]
         [:.h-3]
         [:button {:class signin-button-classes
                   :type "submit"
                   :data-sitekey site-key
                   :data-callback "onSubscribe"
                   :data-action "subscribe"}
          "Sign in"])
       [:.h-5]
       [:.text-center.leading-none "Don't have an account yet? " [:a.underline {:href "/"} "Sign up"] "."]))}])

(def verify-code-route
  ["/verify-code"
   {:name ::verify-code
    :get
    (fn verify-code [{:keys [biff/domain recaptcha/site-key params] :as ctx}]
      (base-signin-page
       (assoc ctx
              :base/title "Sign in | Yakread"
              ::errors {"invalid-code" "Invalid code."})
       (biff/form
         {:id "verify-code-form"
          :action "/auth/verify-code"
          :hidden {:email (:email params)}}
         (biff/recaptcha-callback "verifyCode" "verify-code-form")
         [:label.block {:for "code"} "Enter the 6-digit code we sent to "
          (if (some-> (:email params) str/lower-case (str/includes? (str "@" domain)))
            "your email address"
            (:email params)) ":"]
         [:.h-3]
         [:.flex-grow (ui/text-input {:name "code" :ui/size :large})]
         [:.h-3]
         [:button {:class signin-button-classes
                   :type "submit"
                   :data-sitekey site-key
                   :data-callback "verifyCode"}
          "Verify code"])
       [:.h-5]
       (biff/form
         {:action "/auth/send-code"
          :id "send-again"
          :hidden {:email (:email params)
                   :on-error (href signin-page-route)}}
         (biff/recaptcha-callback "sendAgain" "send-again")
         [:.flex.justify-center.items-center.text-neut-700
          [:button.text-center.underline.g-recaptcha
           {:type "submit"
            :data-sitekey site-key
            :data-callback "sendAgain"}
           "Send another code"]
          [:.px-2 ui/interpunct]
          [:a.underline {:href "/"} "Home"]])))}])

(def navbar
  [:.p-4.bg-neut-900.w-full
   [:.flex.mx-auto.max-w-screen-lg
    [:img {:class '["h-[27px]"
                    opacity-85]
           :src "/img/logo-navbar.svg"
           :height "27px"}]
    [:.grow]
    [:a.text-white.hover:underline {:href (href signin-page-route)} "Sign in"]]])

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

;; Layout Functions
(defn hero-page-layout
  "Layout with prominent hero section followed by content cards"
  [components]
  (let [hero-components (filter #(= (:component-type %) "hero") components)
        other-components (filter #(not= (:component-type %) "hero") components)]
    [:div
     ;; Hero section first
     (for [hero hero-components]
       (render-component (:component-type hero) hero))
     ;; Other components below
     (for [component other-components]
       (render-component (:component-type component) component))]))

(defn text-page-layout
  "Simple layout for text-heavy pages without hero"
  [components]
  [:div
   (for [component components]
     (render-component (:component-type component) component))])

(defn listing-page-layout
  "Layout optimized for chronological content (blogs, events)"
  [components]
  (let [sorted-components (sort-by #(or (:published-date %) (:event-date %) (:display-order %))
                                   #(compare %2 %1) ; reverse chronological
                                   components)]
    [:div {:class "mtz-listing-page"}
     (for [component sorted-components]
       [:div {:class "mtz-listing-item"}
        (render-component (:component-type component) component)])]))

(def layout-registry
  "Registry of available layout functions"
  {"hero-page" hero-page-layout
   "text-page" text-page-layout
   "listing-page" listing-page-layout})

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

(def component-registry
  "Registry of available component types"
  {"hero" hero-component
   "text-card" text-card-component
   "html-card" html-card-component
   "announcement" announcement-component
   "event" event-component
   "blog-post" blog-post-component})

(defn render-component
  "Render a component based on its type and data"
  [component-type data]
  (if-let [component-fn (get component-registry component-type)]
    (component-fn data)
    [:div {:class "mtz-content-card"}
     [:h2 "Unknown Component"]
     [:p "Component type '" component-type "' not found"]]))

(defn load-page-components
  "Load all components for a specific page from content file"
  [page-name content-file]
  (try
    (if (.exists (clojure.java.io/file content-file))
      (let [all-content (clojure.edn/read-string (slurp content-file))]
        (->> all-content
             (filter #(= (:page %) page-name))
             (sort-by :display-order)))
      [])
    (catch Exception e
      [])))

(defn load-page-layout
  "Load layout metadata for a specific page from content file"
  [page-name content-file]
  (try
    (if (.exists (clojure.java.io/file content-file))
      (let [all-content (clojure.edn/read-string (slurp content-file))
            page-components (filter #(= (:page %) page-name) all-content)
            first-component (first page-components)]
        (:layout first-component "hero-page")) ; Default to hero-page layout
      "hero-page")
    (catch Exception e
      "hero-page")))

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

(defn generate-responsive-image
  "Generate responsive image HTML with multiple rendition sizes"
  [node-id alt-text base-domain]
  (let [small-url (get-rendition-url node-id "small" base-domain)
        medium-url (get-rendition-url node-id "medium" base-domain)
        large-url (get-rendition-url node-id "large" base-domain)]
    [:img {:src medium-url
           :srcset (str small-url " 300w, "
                       medium-url " 600w, "
                       large-url " 1200w")
           :sizes "(max-width: 600px) 300px, (max-width: 1200px) 600px, 1200px"
           :alt alt-text
           :loading "lazy"}]))

(defn validate-component-layout-compatibility
  "Validate that components are compatible with the specified layout"
  [layout-type components]
  ;; Component-layout compatibility rules
  (let [layout-rules {"hero-page" #{"hero" "text-card" "html-card" "announcement"}
                      "text-page" #{"text-card" "html-card" "announcement"}
                      "listing-page" #{"blog-post" "event" "text-card" "html-card"}}
        allowed-components (get layout-rules layout-type #{})]
    (filter #(contains? allowed-components (:component-type %)) components)))

(defn render-page-components
  "Render all components for a specific page using layout system"
  [page-name content-file]
  (let [page-components (load-page-components page-name content-file)
        page-layout (load-page-layout page-name content-file)
        validated-components (validate-component-layout-compatibility page-layout page-components)
        layout-fn (get layout-registry page-layout hero-page-layout)]
    (if (seq validated-components)
      (layout-fn validated-components)
      [:div {:class "mtz-content-card"}
       [:h2 "No Compatible Content"]
       [:p "No components found that are compatible with layout: " page-layout]])))

(defn feature1-component
  "Simple Alfresco content display component"
  [ctx]
  (try
    (let [content-file "mtzuix-feature1-content.edn"]
      (if (.exists (clojure.java.io/file content-file))
        (let [feature1-content (clojure.edn/read-string (slurp content-file))
              content-data (first feature1-content)]
          (content-card
           (:title content-data "Feature 1")
           (or (:html-content content-data)
               (:text-content content-data "No content available."))))
        (content-card
         "Feature 1 Component"
         "No content available yet.")))
    (catch Exception e
      [:div {:class "bg-red-100 p-4 m-4 rounded-lg"}
       [:h2 "Error loading content"]
       [:p "Failed to load: " (.getMessage e)]])))

(def home-page-route
  ["/"
   {:name ::home-page-route
    :get
    (fn home-page [ctx]
      (ui/base-page
       (assoc ctx :base/title "Mount Zion UCC - Home")
       [:div {:class "mtz-app"}
        [:header {:class "mtz-header"}
         [:div {:class "mtz-header-container"}
          [:h1 {:class "mtz-logo-title"} "Mount Zion United Church of Christ"]
          [:p {:style {:color "#ffffff" :font-size "1.125rem" :margin-top "0.5rem"}} "A welcoming faith community since 1979"]]]

        [:nav {:class "mtz-nav-menu"}
         [:div {:style {:display "flex" :justify-content "center" :padding "12px 0" :gap "24px"}}
          [:a {:href "/" :class "mtz-nav-link"} "Home"]
          [:a {:href "/about" :class "mtz-nav-link"} "About"]
          [:a {:href "/worship" :class "mtz-nav-link"} "Worship"]
          [:a {:href "/ministries" :class "mtz-nav-link"} "Ministries"]
          [:a {:href "/events" :class "mtz-nav-link"} "Events"]
          [:a {:href "/contact" :class "mtz-nav-link"} "Contact"]]]

        [:div {:style {:padding "2rem"}}
         ;; Try component system first, fall back to old system
         (let [components (render-page-components "home" "mtz-components-test.edn")]
           (if (seq components)
             components
             [[:h2 {:style {:font-size "1.875rem" :font-weight "bold" :text-align "center" :margin-bottom "1.5rem" :color "#1f2937"}} "Welcome to Mount Zion UCC"]
              [:p {:style {:font-size "1.125rem" :text-align "center" :margin-bottom "1.5rem" :color "#374151"}} "A United Church of Christ congregation"]
              (feature1-component ctx)]))]]))}])

(def link-sent-route
  ["/link-sent"
   {:name ::link-sent-route
    :get
    (fn link-sent [{:keys [biff/domain params] :as ctx}]
      (ui/plain-page
       (assoc ctx :base/title "Sign up | Yakread")
       [:.text-2xl.font-bold "Check your inbox"]
       [:.h-3]
       [:.text-lg
        (if (and (:email params)
                 (not (str/includes? (str/lower-case (:email params)) (str "@" domain))))
          [:<> "We've sent a sign-in link to " (:email params) "."]
          "We've sent you a sign-in link.")]))}])

(defn simple-page-route [path title content]
  [path
   {:name (keyword (str "mtz-" (subs path 1)))
    :get (fn [ctx]
           (ui/base-page
            (assoc ctx :base/title (str title " | Mount Zion UCC"))
            [:div {:class "mtz-app"}
             [:header {:class "mtz-header"}
              [:div {:class "mtz-header-container"}
               [:h1 {:class "mtz-logo-title"} "Mount Zion United Church of Christ"]
               [:p {:style {:color "#ffffff" :font-size "1.125rem" :margin-top "0.5rem"}} "A welcoming faith community since 1979"]]]

             [:nav {:class "mtz-nav-menu"}
              [:div {:style {:display "flex" :justify-content "center" :padding "12px 0" :gap "24px"}}
               [:a {:href "/" :class "mtz-nav-link"} "Home"]
               [:a {:href "/about" :class "mtz-nav-link"} "About"]
               [:a {:href "/worship" :class "mtz-nav-link"} "Worship"]
               [:a {:href "/ministries" :class "mtz-nav-link"} "Ministries"]
               [:a {:href "/events" :class "mtz-nav-link"} "Events"]
               [:a {:href "/contact" :class "mtz-nav-link"} "Contact"]]]

             [:div {:style {:padding "2rem"}}
              [:h2 {:style {:font-size "1.875rem" :font-weight "bold" :text-align "center" :margin-bottom "1.5rem" :color "#1f2937"}} title]
              (content-card nil content)]]))}])

(def about-route (simple-page-route "/about" "About Us"
  "Welcome to Mount Zion United Church of Christ. We are a welcoming faith community that has been serving since 1979."))

(def worship-route (simple-page-route "/worship" "Worship"
  "Join us for worship every Sunday at 10:00 AM. All are welcome to experience our inclusive and Spirit-filled worship."))

(def ministries-route (simple-page-route "/ministries" "Ministries"
  "Our ministries serve the community through various programs including youth groups, adult education, and community outreach."))

(def events-route
  ["/events"
   {:name ::events-page
    :get (fn [ctx]
           (ui/base-page
            (assoc ctx :base/title "Events | Mount Zion UCC")
            [:div {:class "mtz-app"}
             [:header {:class "mtz-header"}
              [:div {:class "mtz-header-container"}
               [:h1 {:class "mtz-logo-title"} "Mount Zion United Church of Christ"]
               [:p {:style {:color "#ffffff" :font-size "1.125rem" :margin-top "0.5rem"}} "A welcoming faith community since 1979"]]]

             [:nav {:class "mtz-nav-menu"}
              [:div {:style {:display "flex" :justify-content "center" :padding "12px 0" :gap "24px"}}
               [:a {:href "/" :class "mtz-nav-link"} "Home"]
               [:a {:href "/about" :class "mtz-nav-link"} "About"]
               [:a {:href "/worship" :class "mtz-nav-link"} "Worship"]
               [:a {:href "/ministries" :class "mtz-nav-link"} "Ministries"]
               [:a {:href "/events" :class "mtz-nav-link"} "Events"]
               [:a {:href "/contact" :class "mtz-nav-link"} "Contact"]]]

             [:div {:style {:padding "2rem"}}
              [:h2 {:style {:font-size "1.875rem" :font-weight "bold" :text-align "center" :margin-bottom "1.5rem" :color "#1f2937"}} "Events"]
              ;; Use component system for events
              (let [components (render-page-components "events" "mtz-components-test.edn")]
                (if (seq components)
                  components
                  [:div {:class "mtz-content-card"}
                   [:h3 "No Events Currently Scheduled"]
                   [:p "Check back soon for upcoming events and community gatherings."]]))]]))]}])

(def contact-route (simple-page-route "/contact" "Contact Us"
  "We'd love to hear from you! Contact Mount Zion UCC for more information about our services, programs, or if you have any questions."))

(def tos-route (simple-page-route "/tos" "Terms of Service"
  "Terms of Service for the Mount Zion UCC website."))

(def privacy-route (simple-page-route "/privacy" "Privacy Policy"
  "Privacy Policy for the Mount Zion UCC website."))

(def module
  {:routes [home-page-route
            link-sent-route
            signin-page-route
            verify-code-route
            about-route
            worship-route
            ministries-route
            events-route
            contact-route
            tos-route
            privacy-route]})