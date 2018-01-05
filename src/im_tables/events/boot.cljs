(ns im-tables.events.boot
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [re-frame.core :refer [reg-event-fx reg-event-db]]
            [cljs.core.async :refer [<! >! chan]]
            [im-tables.db :as db]
            [im-tables.interceptors :refer [sandbox]]
            [imcljs.fetch :as fetch]))

(defn deep-merge
  "Recursively merges maps. If keys are not maps, the last value wins."
  [& vals]
  (if (every? map? vals)
    (apply merge-with deep-merge vals)
    (last vals)))

(defn boot-flow
  [loc service]
  {:first-dispatch [:imt.auth/fetch-anonymous-token loc service]
   :rules [{:when :seen?
            :events [:imt.auth/store-token]
            :dispatch [:imt.main/fetch-assets loc]}
           {:when :seen-all-of?
            :events [:imt.main/save-summary-fields
                     :imt.main/save-model]
            :dispatch [:im-tables.main/run-query loc]}]})

(defn <<!
  "Given a collection of channels, returns a collection containing
  the first result of each channel (similiar to JS Promise.all)"
  [chans]
  (go-loop [coll '()
            chans (reverse chans)]
           (if (seq chans)
             (recur (conj coll (<! (first chans)))
                    (rest chans))
             coll)))



; TODO - WIP - If assets are missing when a component is mounted then
; fetch them and/or run necessary queries
(reg-event-fx :im-tables/sync
              (sandbox)
              (fn [{db :db} [_ loc {:keys [query service location]}]]

                ; Fetch assets if they don't already exist in the database
                ; Since <<! expects channels, we create channels for assets that already exist
                ; and then immediately take from them
                (go (<!
                      (<<!
                        (-> []
                            ; Existing model or fetch...
                            (conj (if-let [f (-> db :service :model)] (let [c (chan)] (go (>! c f)) c) (fetch/model service)))
                            ; Existing summary fields or fetch...
                            (conj (if-let [f (-> db :service :summary-fields)] (let [c (chan)] (go (>! c f)) c) (fetch/summary-fields service)))))))










                {:db db}))

(reg-event-fx
  :initialize-db-old
  (fn [_ [_ loc initial-state]]
    (println "old")
    (let [new-db (deep-merge db/default-db initial-state)]
      {:db (if loc (assoc-in {} loc new-db) new-db)
       :async-flow (boot-flow loc (get new-db :service))}
      (js/console.log "R" db/default-db)
      db/default-db)))

(reg-event-db
  :initialize-db
  (fn [_] db/default-db))

; Fetch an anonymous token for a give
(reg-event-fx
  :imt.auth/fetch-anonymous-token
  (sandbox)
  (fn [{db :db} [_ loc service]]
    {:db db
     :im-tables/im-operation {:on-success [:imt.auth/store-token loc]
                              :op (partial fetch/session service)}}))

; Store an auth token for a given mine
(reg-event-db
  :imt.auth/store-token
  (sandbox)
  (fn [db [_ loc token]]
    (assoc-in db [:service :token] token)))

(reg-event-fx
  :fetch-asset
  (fn [{db :db} [_ im-op]]
    {:db db
     :im-tables/im-operation im-op}))

(reg-event-fx
  :imt.main/fetch-assets
  (sandbox)
  (fn [{db :db} [_ loc]]
    {:db db
     :dispatch-n [[:fetch-asset {:on-success [:imt.main/save-summary-fields loc]
                                 :op (partial fetch/summary-fields (get db :service))}]
                  [:fetch-asset {:on-success [:imt.main/save-model loc]
                                 :op (partial fetch/model (get db :service))}]]}))

(reg-event-db
  :imt.main/save-model
  (sandbox)
  (fn [db [_ loc model]]
    (assoc-in db [:service :model] model)))

(reg-event-db
  :imt.main/save-summary-fields
  (sandbox)
  (fn [db [_ loc summary-fields]]
    (assoc-in db [:assets :summary-fields] summary-fields)))
