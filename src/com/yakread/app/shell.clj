(ns com.yakread.app.shell
  (:require [cheshire.core :as cheshire]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [com.biffweb :as biff]
            [com.wsscode.pathom3.connect.operation :as pco :refer [defresolver ?]]
            [com.yakread.lib.icons :as lib.icons]
            [com.yakread.lib.route :as lib.route]
            [ring.middleware.anti-forgery :as csrf]
            [ring.util.response :as ring-response]))

(def schema
  {:app.shell/include-plausible :boolean
   :app.shell/include-recaptcha :boolean})

(defn css-path []
  (if-some [last-modified (some-> (io/resource "public/css/main.css")
                                  ring-response/resource-data
                                  :last-modified
                                  (.getTime))]
    (str "/css/main.css?t=" last-modified)
    "/css/main.css"))

(defresolver pages [{:keys [biff/router uri]} {{:keys [user/admin]} :user/current}]
  #::pco{:input [{(? :user/current) [:user/admin]}]}
  {:app.shell/pages
   (->> (cond-> [#:app.shell.page{:route-name :app.for-you/page
                                  :title "For you"
                                  :icon "house"}
                 #:app.shell.page{:route-name :app.subscriptions/page
                                  :title "Subscriptions"
                                  :icon "rss"}
                 #:app.shell.page{:route-name :app.read-later/page
                                  :title "Read later"
                                  :icon "bookmark"}
                 #:app.shell.page{:route-name :app.favorites/page
                                  :title "Favorites"
                                  :icon "star"}
                 #:app.shell.page{:route-name :app.settings/page
                                  :title "Settings"
                                  :icon "gear"}]
          admin (conj #:app.shell.page{:route-name :app.admin/page
                                       :title "Admin"
                                       :icon "lock"}))
        (mapv (fn [{:keys [app.shell.page/route-name] :as page}]
                (let [href (lib.route/path router route-name {})]
                  (merge page #:app.shell.page{:href href
                                               :active (str/starts-with? uri href)})))))})

(defresolver app-head [{:keys [app.shell/include-plausible
                               app.shell/include-recaptcha]}
                       {user :user/current}]
  #::pco{:input [(? :user/current)]}
  {:app.shell/app-head
   [[:link {:rel "stylesheet" :href (css-path)}]
    [:script {:src "https://unpkg.com/htmx.org@2.0.2"}]
    [:script {:src "https://unpkg.com/hyperscript.org@0.9.12"}]
    [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/dompurify/3.0.1/purify.min.js"}]
    [:script {:src "/js/new-app.js"}]
    [:script {:src "https://unpkg.com/@popperjs/core@2"}]
    [:script {:src "https://unpkg.com/tippy.js@6"}]
    (when include-plausible
      [:script {:src "https://pl.tfos.co/js/script.js"
                :defer "defer"
                :data-domain "yakread.com"}])
    (when (and (nil? user) include-recaptcha)
      [:script {:src "https://www.google.com/recaptcha/api.js" :async "async" :defer "defer"}])
    [:link {:rel "manifest", :href "/site.webmanifest?a"}]
    [:link {:rel "apple-touch-icon", :sizes "180x180", :href "/apple-touch-icon.png"}]
    [:link {:rel "icon", :type "image/png", :sizes "32x32", :href "/favicon-32x32.png"}]
    [:link {:rel "icon", :type "image/png", :sizes "16x16", :href "/favicon-16x16.png"}]
    [:link {:rel "mask-icon", :href "/safari-pinned-tab.svg", :color "#5bbad5"}]
    [:meta {:name "msapplication-TileColor", :content "#da532c"}]
    [:meta {:name "theme-color", :content "#343a40"}]]})

(def default-metadata
  #:base{:title "Yakread"
         :lang "en-US"
         :description "Read stuff that matters."
         :image "https://platypub.sfo3.cdn.digitaloceanspaces.com/270d320a-d9d8-4cdf-bbed-2078ca595b16"
         :font-families ["Inter:wght@400;500;600;700"]})

(defresolver sidebar [{:keys [biff/router uri]} {current-user :user/current :keys [app.shell/pages]}]
  #::pco{:input [{(? :user/current) [:user/email]}
                 :app.shell/pages]}
  {:app.shell/sidebar
   [:div {:id "sidebar"
          :class '[max-md:fixed
                   -left-64
                   transition-all
                   inset-y-0
                   whitespace-nowrap
                   "w-[16rem]"
                   md:w-fit
                   "lg:w-[16rem]"
                   flex-shrink-0
                   bg-neut-900
                   text-neut-50
                   z-20]}
    [:.sticky.top-0.py-2.flex.flex-col.px-4.h-screen
     [:.h-4]
     [:a.self-center {:href "/"}
      [:img {:class '["h-[27px]"
                      opacity-85
                      pl-2
                      pr-4
                      lg:pr-6
                      mb-4
                      lg:mb-8
                      max-md:hidden]
             :src "/img/logo-navbar.svg"
             :height "27px"}]]
     [:div {:hx-boost "true"
            :class '[flex
                     flex-col
                     gap-1]}
      (for [{:app.shell.page/keys [route-name title icon]} pages
            :let [href (lib.route/path router route-name {})]]
        [:a {:href href
             :class (concat (if (str/starts-with? uri href)
                              '[bg-neut-800
                                text-white]
                              '[text-neut-200
                                hover:bg-neut-800
                                hover:text-white])
                            '[py-3
                              px-2
                              flex
                              items-center
                              rounded
                              font-semibold])}
         (lib.icons/base (str icon "-regular")
                         {:class "w-[18px] h-[18px] relative top-[1px]"})
         [:.w-2]
         [:span.leading-none title]])]
     [:.grow]
     (when current-user
       [:.relative
        [:div#user-dropdown
         {:class '[dropdown
                   hidden
                   absolute
                   bg-neut-700
                   py-1
                   text-sm
                   w-full
                   border
                   border-neut-600
                   shadow
                   rounded
                   "bottom-[2.25rem]"]}
         (biff/form
           {:action "/auth/signout"
            :class "inline"
            :hx-push-url "true"}
           [:button {:type "submit"
                     :class '[hover:bg-neut-800
                              px-2
                              py-1
                              w-full]}
            "Sign out"])]
        [:button
         {:class '[flex
                   items-center
                   gap-2
                   text-sm
                   p-2
                   max-w-full]
          :_ "on click toggle .hidden on #user-dropdown then halt"}
         [:div
          {:class '[truncate]
           :style {:text-overflow "ellipsis"}}
          (:user/email current-user)]
         (lib.icons/base "chevron-down-solid"
                         {:class '[h-4
                                   w-4
                                   relative
                                   "top-[1px]"
                                   text-neut-300]})]])]]})

(def interpunct " · ")

(defresolver footer [{current-user :user/current}]
  #::pco{:input [(? :user/current)]}
  {:app.shell/footer
   [:.text-sm.text-center.mt-10.text-neut-600
    [:div
     (biff/join
      interpunct
      ;; TODO use router?
      (for [[href label target] [["https://obryant.dev" "About"]
                                 ["/advertise" "Advertise" :same-tab]
                                 ["mailto:hello@obryant.dev?subject=Yakread" "Contact"]
                                 ["/tos/" "Terms of Service"]
                                 ["/privacy/" "Privacy Policy"]]]
        [:a.underline {:href href
                       :target (when-not (= target :same-tab)
                                 "_blank")}
         label]))]
    (when-not current-user
      [:<>
       [:.h-3]
       [:div
        "This site is protected by reCAPTCHA and the Google "
        [:a.underline
         {:href "https://policies.google.com/privacy",
          :target "_blank"}
         "Privacy Policy"]
        " and "
        [:a.underline
         {:href "https://policies.google.com/terms",
          :target "_blank"}
         "Terms of Service"]
        " apply."]])]})

(def navbar
  [:div
   [:div {:class '[md:hidden
                   absolute
                   inset-0
                   bg-black
                   opacity-50
                   z-10
                   hidden
                   navbar-button]
          :_ (str "on click toggle .sidebar-visible on #sidebar then "
                  "toggle .hidden on .navbar-button")}]
   [:div {:class '[md:hidden
                   px-4
                   sm:px-8
                   bg-neut-900
                   text-neut-50]}
    [:div {:class '[py-3
                    flex
                    items-center
                    max-w-screen-sm
                    mx-auto]}
     [:a {:href "/"}
      [:img {:class '["h-[25px]"
                      opacity-85]
             :src "/img/logo-navbar.svg"
             :height "25px"}]]
     [:.grow.h-1]
     (for [[icon class] [["bars-regular"]
                         ["xmark-regular" '[hidden]]]]
       [:button.navbar-button.z-20
        {:_ (str "on click toggle .sidebar-visible on #sidebar then "
                 "toggle .hidden on .navbar-button")
         :class class}
        (lib.icons/base icon {:class '[w-6 h-6]})])]]])

(defresolver app-shell [{:app.shell/keys [app-head pages sidebar footer]}]
  #::pco{:input [:app.shell/app-head
                 :app.shell/sidebar
                 :app.shell/footer
                 :app.shell/pages]}
  {:app.shell/app-shell
   (let [active-page (first (filterv :app.shell.page/active pages))]
     (fn [{:keys [title wide]} & content]
       (biff/base-html
        (-> default-metadata
            (biff/assoc-some :base/title (str (or title (:app.shell.page/title active-page))
                                              " | Yakread"))
            (update :base/head concat app-head))
        navbar
        [:.bg-neut-75.grow.flex
         {:hx-boost "true" ; TODO make sure this still works how we want it to
          :hx-headers (cheshire/generate-string
                       {:x-csrf-token csrf/*anti-forgery-token*})
          :_ "on click add .hidden to .dropdown"}
         sidebar
         [:div {:class (concat '[mx-auto
                                 p-4
                                 sm:p-8
                                 sm:pb-6
                                 flex
                                 flex-col
                                 w-full]
                               (when-not wide
                                 '[max-w-screen-sm]))}
          content
          [:.grow]
          footer]])))})

(def module
  {:resolvers [app-shell
               app-head
               pages
               sidebar
               footer]})
