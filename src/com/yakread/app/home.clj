(ns com.yakread.app.home
  (:require
   [clojure.string :as str]
   [com.biffweb :as biff]
   [com.yakread.lib.icons :as lib.icons]
   [com.yakread.lib.route :refer [href]]
   [com.yakread.lib.ui :as ui]
   [com.yakread.routes :as routes]
   [com.yakread.ui.components :as ui-components]
   [com.yakread.components.core :as components]
   [com.yakread.layouts.core :as layouts]
   [com.yakread.alfresco.calendar :as calendar]
   [hiccup.page]
   [clojure.tools.logging :as log]
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



(defn render-page-components
  "Render all components for a specific page using layout system"
  [page-name content-file]
  (let [page-components (load-page-components page-name content-file)
        page-layout (load-page-layout page-name content-file)]
    (layouts/render-with-layout page-layout page-components)))

(defn load-alfresco-events
  "Load calendar events from the synced content file (following sync_calendar.clj pipeline)"
  [ctx]
  (try
    (log/info "load-alfresco-events: Loading calendar events from synced content")

    ;; Load from the mtzUIX calendar events file created by sync_calendar.clj
    (if (.exists (clojure.java.io/file "mtzuix-calendar-events.edn"))
      (let [all-events (clojure.edn/read-string (slurp "mtzuix-calendar-events.edn"))
            ;; Filter for published events only (those with publish tag)
            published-events (filter :has-publish-tag all-events)]

        (log/info "load-alfresco-events: Found" (count all-events) "total events,"
                  (count published-events) "published events")

        ;; Transform to the format expected by the events page
        (map (fn [event]
               {:node-id (:event-id event)
                :title (:title event)
                :description (:description event)
                :event-date (:event-date event)
                :event-time (:event-time event)
                :location (:location event)
                :has-publish-tag (:has-publish-tag event)
                :is-upcoming (:is-upcoming event)})
             published-events))

      (do
        (log/warn "Calendar events file not found. Run sync_calendar.clj first.")
        []))

    (catch Exception e
      (log/error "Error loading calendar events from file:" (.getMessage e))
      [])))

(defn feature1-component
  "Simple Alfresco content display component"
  [ctx]
  (try
    (let [content-file "mtzuix-feature1-content.edn"]
      (if (.exists (clojure.java.io/file content-file))
        (let [feature1-content (clojure.edn/read-string (slurp content-file))
              content-data (first feature1-content)]
          (components/content-card
           (:title content-data "Feature 1")
           (or (:html-content content-data)
               (:text-content content-data "No content available."))))
        (components/content-card
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
              (components/content-card nil content)]]))}])

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
               [:p {:style {:color "#ffffff" :font-size "1.125rem" :margin-top "0.5rem"}}
                "A welcoming faith community since 1979"]]]
             [:nav {:class "mtz-nav-menu"}
              [:div {:style {:display "flex" :justify-content "center" :padding "12px 0" :gap "24px"}}
               [:a {:href "/" :class "mtz-nav-link"} "Home"]
               [:a {:href "/about" :class "mtz-nav-link"} "About"]
               [:a {:href "/worship" :class "mtz-nav-link"} "Worship"]
               [:a {:href "/ministries" :class "mtz-nav-link"} "Ministries"]
               [:a {:href "/events" :class "mtz-nav-link"} "Events"]
               [:a {:href "/contact" :class "mtz-nav-link"} "Contact"]]]
             [:div {:style {:padding "2rem"}}
              [:h2 {:style {:font-size "1.875rem" :font-weight "bold" :text-align "center"
                            :margin-bottom "1.5rem" :color "#1f2937"}} "Events"]
              (let [alfresco-events (load-alfresco-events ctx)]
                (if (seq alfresco-events)
                  [:div {:class "events-list"}
                   (for [event alfresco-events]
                     [:div {:class "mtz-content-card" :key (:node-id event)}
                      [:h3 (:title event)]
                      (when (:event-date event)
                        [:p [:strong "Date: "] (:event-date event)])
                      (when (:location event)
                        [:p [:strong "Location: "] (:location event)])
                      (when (:description event)
                        [:p (:description event)])])]
                  [:div {:class "mtz-content-card"}
                   [:h3 "No Events Currently Scheduled"]
                   [:p "Check back soon for upcoming events and community gatherings."]]))]]))}])



(def contact-route (simple-page-route "/contact" "Contact Us"
  "We'd love to hear from you! Contact Mount Zion UCC for more information about our services, programs, or if you have any questions."))

(def tos-route (simple-page-route "/tos" "Terms of Service"
  "Terms of Service for the Mount Zion UCC website."))

(def privacy-route (simple-page-route "/privacy" "Privacy Policy"
  "Privacy Policy for the Mount Zion UCC website."))

;; Module definition commented out - replaced by com.yakread.app.routes
;; (def module
;;   {:routes [home-page-route
;;             link-sent-route
;;             signin-page-route
;;             verify-code-route
;;             about-route
;;             worship-route
;;             ministries-route
;;             events-route
;;             contact-route
;;             tos-route
;;             privacy-route]})