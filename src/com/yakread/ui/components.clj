(ns com.yakread.ui.components
  "Generic UI components ported from mtzUIX.

   This namespace provides reusable UI components using Hiccup syntax,
   including both generic components (button, heading, p, a) and utilities
   for building user interfaces with consistent styling."
  (:require [com.biffweb :as biff]))

;; --- GENERIC UI COMPONENTS ---

(defn button
  "Generic button component with gradient styling.
   Options:
   - :on-click - Click handler function
   - :class - Additional CSS classes (vector of strings/keywords)
   - :children - Button content
   - :type - Button type (default 'button')
   - :disabled - Disabled state (boolean)"
  [{:keys [on-click class children type disabled] :or {type "button"}}]
  [:button (merge
            {:type type
             :class (into ["text-white"
                          "bg-gradient-to-r"
                          "from-[#A2DA5F]"
                          "from-10%"
                          "to-[#97B3F8]"
                          "hover:bg-gradient-to-l"
                          "transition"
                          "ease-in-out"
                          "font-medium"
                          "rounded-full"
                          "text-sm"
                          "px-3.5"
                          "py-2"
                          "text-center"
                          "select-none"
                          "me-2"
                          "mb-2"]
                         (or class []))}
            (when on-click {:onclick on-click})
            (when disabled {:disabled true}))
   children])

(defn heading
  "Generic heading component with gradient text.
   Options:
   - :children - Heading text content
   - :class - Additional CSS classes (vector of strings/keywords)
   - :level - Heading level (1-6, defaults to 1)"
  [{:keys [children class level] :or {level 1}}]
  (let [tag (keyword (str "h" level))]
    [tag {:class (into ["text-transparent"
                       "bg-clip-text"
                       "bg-gradient-to-r"
                       "from-[#A2DA5F]"
                       "from-0%"
                       "to-[#97B3F8]"
                       "mb-4"
                       "text-6xl"
                       "text-center"
                       "font-extrabold"
                       "text-gray-900"]
                      (or class []))}
     children]))

(defn paragraph
  "Generic paragraph component.
   Options:
   - :children - Paragraph text content
   - :class - Additional CSS classes (vector of strings/keywords)"
  [{:keys [children class]}]
  [:p {:class (into ["my-2"
                    "text-lg"
                    "font-normal"
                    "text-gray-700"
                    "dark:text-gray-100"]
                   (or class []))}
   children])

(defn link
  "Generic anchor/link component.
   Options:
   - :href - Link URL
   - :children - Link text content
   - :class - Additional CSS classes (vector of strings/keywords)
   - :target - Link target (defaults to '_blank')
   - :external - If true, opens in new tab (default true)"
  [{:keys [href children class target external] :or {external true}}]
  [:a (merge
       {:href href
        :class (into ["hover:underline"] (or class []))}
       (when (and external (not target))
         {:target "_blank" :rel "noopener noreferrer"})
       (when target
         {:target target}))
   children])

;; --- UTILITY FUNCTIONS ---

(defn merge-classes
  "Utility function to merge CSS classes, handling both strings and vectors"
  [base-classes additional-classes]
  (into (if (vector? base-classes) base-classes [base-classes])
        (if (vector? additional-classes) additional-classes [additional-classes])))

(defn conditional-class
  "Add a class conditionally based on a predicate"
  [classes condition class-to-add]
  (if condition
    (conj (vec classes) class-to-add)
    (vec classes)))

;; --- LAYOUT HELPERS ---

(defn container
  "Generic container component with responsive max-width.
   Options:
   - :children - Container content
   - :class - Additional CSS classes
   - :size - Container size (:sm, :md, :lg, :xl, :full, defaults to :lg)"
  [{:keys [children class size] :or {size :lg}}]
  (let [size-class (case size
                     :sm "max-w-sm"
                     :md "max-w-2xl"
                     :lg "max-w-4xl"
                     :xl "max-w-6xl"
                     :full "max-w-none"
                     "max-w-4xl")]
    [:div {:class (into [size-class "mx-auto" "px-4"]
                       (or class []))}
     children]))

(defn section
  "Generic section wrapper component.
   Options:
   - :children - Section content
   - :class - Additional CSS classes
   - :padding - Padding size (:none, :sm, :md, :lg, defaults to :md)"
  [{:keys [children class padding] :or {padding :md}}]
  (let [padding-class (case padding
                        :none ""
                        :sm "py-8"
                        :md "py-16"
                        :lg "py-24"
                        "py-16")]
    [:section {:class (into [padding-class]
                           (or class []))}
     children]))

;; --- FORM COMPONENTS ---

(defn input
  "Generic input component.
   Options:
   - :type - Input type (default 'text')
   - :name - Input name
   - :placeholder - Placeholder text
   - :value - Input value
   - :class - Additional CSS classes
   - :required - Required field (boolean)
   - :disabled - Disabled state (boolean)"
  [{:keys [type name placeholder value class required disabled] :or {type "text"}}]
  [:input (merge
           {:type type
            :class (into ["block" "w-full" "px-3" "py-2" "border"
                         "border-gray-300" "rounded-md" "shadow-sm"
                         "focus:outline-none" "focus:ring-blue-500"
                         "focus:border-blue-500"]
                        (or class []))}
           (when name {:name name})
           (when placeholder {:placeholder placeholder})
           (when value {:value value})
           (when required {:required true})
           (when disabled {:disabled true}))])

(defn textarea
  "Generic textarea component.
   Options:
   - :name - Textarea name
   - :placeholder - Placeholder text
   - :value - Textarea value
   - :class - Additional CSS classes
   - :rows - Number of rows (default 4)
   - :required - Required field (boolean)
   - :disabled - Disabled state (boolean)"
  [{:keys [name placeholder value class rows required disabled] :or {rows 4}}]
  [:textarea (merge
              {:rows rows
               :class (into ["block" "w-full" "px-3" "py-2" "border"
                            "border-gray-300" "rounded-md" "shadow-sm"
                            "focus:outline-none" "focus:ring-blue-500"
                            "focus:border-blue-500"]
                           (or class []))}
              (when name {:name name})
              (when placeholder {:placeholder placeholder})
              (when required {:required true})
              (when disabled {:disabled true}))
   (or value "")])