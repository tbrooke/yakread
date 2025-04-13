(ns com.yakread.lib.ui-email
  (:require [rum.core :as rum]))

(def address "138 E 12300 S, Unit #654, Draper, UT 84020")

(defn centered [& contents]
  [:table
   {:border "0",
    :dir "ltr",
    :style {:max-width "640px"
            :width "100%"}
    :aria-label "",
    :aria-roledescription "email",
    :cellpadding "0",
    :align "center",
    :role "article",
    :cellspacing "0"}
   [:tr
    [:td.mlTemplateContainer
     [:table.mlMainContent
      {:width "100%", :cellspacing "", :cellpadding "0", :border "0"}
      [:tr
       [:td contents]]]]]])

(defn button [{:keys [bg-color text-color href label]}]
  [:table
   {:border "0",
    :cellpadding "0",
    :cellspacing "0",
    :width "100%",
    :style {:width "100%"
            :min-width "100%"}
    :role "presentation"}
   [:tbody
    [:tr
     [:td
      {:align "center",
       :style {:padding-top 0
               :padding-right 0
               :padding-left 0
               :font-family "Helvetica, sans-serif"}}
      [:a
       {:rel "noopener noreferrer",
        :style {:font-family "Helvetica, sans-serif"
                :background-color bg-color
                :border-radius "3px"
                :color (or text-color "white")
                :display "inline-block"
                :font-size "14px"
                :line-height "20px"
                :padding-top "10px"
                :padding "8px 15px"
                :text-align "center"
                :font-weight 700
                :font-style "normal"
                :text-decoration "none"}
        :href href}
       [:span {:style {:vertical-align "middle"}} label]]]]]])

(defn email [& {:keys [title content hide-unsubscribe]}]
  (str
   "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n"
   (rum/render-static-markup
    [:html {:xmlns "http://www.w3.org/1999/xhtml"}
     [:head
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
      [:meta {:name "x-apple-disable-message-reformatting"}]
      [:meta {:http-equiv "Content-Type" :content "text/html; charset=UTF-8"}]
      [:title title]]
     [:body {:style {:width "100% !important"
                     :height "100%"
                     :margin "0"
                     :-webkit-text-size-adjust "none"
                     :background-color "#e7e7e7"
                     :padding-top "16px"
                     :font-family "ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,Helvetica Neue,Arial,Noto Sans,sans-serif,Apple Color Emoji,Segoe UI Emoji,Segoe UI Symbol,Noto Color Emoji;"
                     :font-size "16px"}}
      (centered
       [:div {:style {:background-color "#222"
                      :line-height "0"}}
        [:a {:href "https://yakread.com"}
         [:img {:src "https://yakread.com/img/logo-navbar.png"
                :alt "Yakread"
                :style {:height "30px"
                        :padding "10px 15px"}}]]]
       [:div {:style {:background-color "white"
                      :color "#3b3b3b"
                      :padding "16px"}}
        content]
       (when-not hide-unsubscribe
         [:div {:style {:padding "12px"
                        :font-size "85%"
                        :color "#3b3b3b"}}
          [:div [:a {:href "{{{ pm:unsubscribe }}}"
                     :style {:text-decoration "underline"
                             :color "#3b3b3b"}}
                 "Unsubscribe"]
           ". " address "."]]))]])))

(defn h-space [height]
  [:div {:style {:height height}}])
