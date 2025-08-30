(ns com.yakread.model.ad
  (:require
   [clojure.string :as str]
   [com.biffweb :as biff :refer [q]]
   [com.wsscode.pathom3.connect.operation :as pco :refer [? defresolver]]
   [com.yakread.lib.content :as lib.content]
   [com.yakread.lib.core :as lib.core]
   [com.yakread.lib.pipeline :as lib.pipe :refer [defpipe]]
   [com.yakread.routes :as routes]
   [lambdaisland.uri :as uri]))

;; TODO keep recent-cost updated (or calculate it on the fly if it's fast enough)
(defresolver effective-bid [{:ad/keys [bid budget recent-cost]}]
  {:ad/effective-bid (min bid (max 0 (- budget recent-cost)))})

(defresolver ad-id [{:keys [xt/id ad/user]}]
  {:ad/id id})

(defresolver xt-id [{:keys [ad/id]}]
  {:xt/id id})

(defresolver user-ad [{:keys [biff/db]} {:keys [xt/id]}]
  {::pco/output [{:user/ad [:xt/id]}]}
  (when-some [ad-id (biff/lookup-id db :ad/user id)]
    {:user/ad {:xt/id ad-id}}))

(defresolver url-with-protocol [{:keys [ad/url]}]
  {:ad/url-with-protocol (lib.content/add-protocol url)})

(defresolver recording-url [{:biff/keys [base-url href-safe]}
                            {:ad/keys [id url-with-protocol click-cost]}]
  {:ad/recording-url
   (fn [{:keys [params :ad.click/source]
         user-id :user/id}]
     (str base-url
          (href-safe routes/click-ad
                     (merge params
                            {:action :action/click-ad
                             :ad/id id
                             :ad/url url-with-protocol
                             :ad/click-cost click-cost
                             :ad.click/source source
                             :user/id user-id}))))})

(def ^:private required-fields
  [:ad/payment-method
   :ad/bid
   :ad/budget
   :ad/url
   :ad/title
   :ad/description
   :ad/image-url])

(defresolver state [{:ad/keys [paused
                               payment-failed
                               approve-state]
                     :as ad}]
  {::pco/input (into [(? :ad/paused)
                      (? :ad/payment-failed)
                      :ad/approve-state]
                     (mapv ? required-fields))
   ::pco/output [:ad/state
                 :ad/incomplete-fields]}
  (let [incomplete-fields (remove (comp lib.core/something? ad) required-fields)]
    {:ad/state (cond
                 payment-failed :payment-failed
                 paused :paused
                 (not-empty incomplete-fields) :incomplete
                 (= approve-state :approved) :running
                 (= approve-state :rejected) :rejected
                 (= approve-state :pending) :pending)
     :ad/incomplete-fields incomplete-fields}))

(defresolver n-clicks [{:keys [biff/db]} {:keys [ad/id]}]
  {:ad/n-clicks
   (or (first
        (q db
           '{:find (count-distinct user)
             :in [ad]
             :where [[click :ad.click/ad ad]
                     [click :ad.click/user user]]}
           id))
       0)})

(defresolver host [{:keys [ad/url-with-protocol]}]
  {:ad/host (some-> url-with-protocol uri/uri :host str/trim not-empty)})

(defn merge-by [id id->m k xs]
  (mapv (fn [x]
          (into x
                (filter (comp some? val))
                {k (id->m (get x id))}))
        xs))

(defresolver payment-period-start [{:keys [biff/db]} ads]
  {::pco/input [:xt/id]
   ::pco/output [:ad/last-charged
                 :ad/payment-period-start]
   ::pco/batch? true}
  (let [ad->last-charged (into {} (q db
                                     '{:find [ad (max t)]
                                       :in [[ad ...] charge confirmed]
                                       :where [[credit :ad.credit/source charge]
                                               [credit :ad.credit/ad ad]
                                               [credit :ad.credit/created-at t]
                                               [credit :ad.credit/charge-status confirmed]]}
                                     (mapv :xt/id ads)
                                     :charge
                                     :confirmed))
        ad->start (into {} (q db
                              '{:find [ad (min t)]
                                :in [[[ad last-charged]]]
                                :where [[click :ad.click/ad ad]
                                        [click :ad.click/cost cost]
                                        [(< 0 cost)]
                                        [click :ad.click/created-at t]
                                        [(< last-charged t)]]}
                              (for [{:keys [xt/id]} ads]
                                [id (get ad->last-charged id lib.core/epoch)])))]
    (->> ads
         (merge-by :xt/id ad->last-charged :ad/last-charged)
         (merge-by :xt/id ad->start :ad/payment-period-start))))

(defresolver amount-pending [{:keys [biff/db]} ads]
  {::pco/input [:xt/id]
   ::pco/output [:ad/amount-pending]
   ::pco/batch? true}
  (let [ad->amount-pending (into {} (q db
                                       '{:find [ad (sum amount)]
                                         :where [[charge :ad.credit/ad ad]
                                                 [charge :ad.credit/charge-status :pending]
                                                 [charge :ad.credit/amount amount]]}))]
    (merge-by :xt/id ad->amount-pending :ad/amount-pending ads)))

(defresolver chargeable [{:keys [biff/now]} {:ad/keys [payment-method
                                                       payment-failed
                                                       balance
                                                       amount-pending
                                                       payment-period-start
                                                       paused]}]
  {::pco/input [(? :ad/payment-method)
                (? :ad/payment-failed)
                :ad/balance
                (? :ad/amount-pending)
                (? :ad/payment-period-start)
                (? :ad/paused)
                ;; ensure these are set / user account hasn't been deleted
                :ad/customer-id
                {:ad/user [:user/email]}]}
  {:ad/chargeable
   (boolean
    (and payment-method
         (not payment-failed)
         (not amount-pending)
         payment-period-start
         (biff/elapsed? payment-period-start now 7 :days)
         (or (<= 2000 balance)
             (and (<= 500 balance)
                  (biff/elapsed? payment-period-start now 30 :days))
             (and (<= 50 balance)
                  paused))))})

(defpipe get-stripe-status
  :start
  (fn [{:keys [biff/secret biff.pipe.pathom/output]}]
    (let [{:ad.credit/keys [ad created-at]} output
          {:ad/keys [customer-id]} ad]
      {:biff.pipe/next [(lib.pipe/http :get
                                       "https://api.stripe.com/v1/payment_intents"
                                       {:basic-auth [(secret :stripe/api-key) ""]
                                        :flatten-nested-form-params true
                                        :as :json
                                        :query-params {:limit 100
                                                       :customer customer-id
                                                       :created {:gt (-> created-at
                                                                         inst-ms
                                                                         (quot 1000)
                                                                         str)}}})
                        :end]}))

  :end
  (fn [{credit :biff.pipe.pathom/output
        http-output :biff.pipe.http/output}]
    (when-some [status (some (fn [{:keys [metadata status]}]
                               (when (= (str (:xt/id credit))
                                        (:charge_id metadata))
                                 status))
                             (get-in http-output [:body :data]))]
      {:ad.credit/stripe-status status})))

(defresolver stripe-status [ctx {:ad.credit/keys [charge-status] :as credit}]
  {::pco/input [:xt/id
                {:ad.credit/ad [:ad/customer-id]}
                :ad.credit/created-at
                :ad.credit/charge-status]
   ::pco/output [:ad.credit/stripe-status]}
  (when (= charge-status :pending)
    (get-stripe-status (assoc ctx :biff.pipe.pathom/output credit))))

(defresolver pending-charge [{:keys [biff/db]} {:keys [xt/id]}]
  {::pco/output [{:ad/pending-charge [:xt/id]}]}
  (when-some [credit (biff/lookup-id db :ad.credit/ad id :ad.credit/charge-status :pending)]
    {:ad/pending-charge {:xt/id credit}}))

(defresolver pending-charges [{:keys [biff/db]} _]
  {::pco/output [{:admin/pending-charges [:xt/id]}]}
  {:admin/pending-charges
   (vec (q db
           '{:find [charge]
             :keys [xt/id]
             :where [[charge :ad.credit/charge-status :pending]]}))})

(def module {:resolvers [ad-id
                         xt-id
                         effective-bid
                         user-ad
                         url-with-protocol
                         recording-url
                         state
                         n-clicks
                         host
                         payment-period-start
                         amount-pending
                         chargeable
                         stripe-status
                         pending-charge
                         pending-charges]})
