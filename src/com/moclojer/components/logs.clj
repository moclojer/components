(ns com.moclojer.components.logs
  (:require
   [com.moclojer.components.logs :as logs]
   [com.stuartsierra.component :as component]
   [clojure.core.async :as async]
   [clojure.data.json :as json]
   [clj-http.client :as http-client]
   [taoensso.telemere :as t])
  (:import
   [clojure.core.async.impl.channels ManyToManyChannel]))

(let [log! (t/handler:console nil)]
  (defn log-console!
    "Uses telemere's console logger explicitly
    so other added handlers don't trigger."
    ([level msg]
     (log-console! level msg nil nil))
    ([level msg data]
     (log-console! level msg data nil))
    ([level msg data error]
     (log! {:level level
            :data data
            :error error
            :msg_ msg}))))

(defn ->str-values
  "Adapts all values (including nested maps' values) from given
  map `m` to a string.

  This is because OpenSearch only accepts string json values."
  [m]
  (reduce-kv
   (fn [acc k v]
     (assoc acc k (cond-> v
                    (map? v) ->str-values
                    (string? v) identity
                    (boolean? v) identity
                    :else str)))
   {} m))

(defn signal->opensearch-log
  "Adapts a telemere signal to a pre-defined schema for OpenSearch."
  [{:keys [thread location] :as signal}]
  (-> (select-keys signal [:level :ctx :data :msg_ :uid :inst])
      (merge {"thread/group" (:group thread)
              "thread/name" (:name thread)
              "thread/id" (:id thread)
              "location" (str (:ns location) ":"
                              (:line location) "x"
                              (:column location))})
      (->str-values)
      (assoc :error (:error signal))
      (json/write-str)))

(defn build-opensearch-base-req
  [config]
  (let [{:keys [username password host port index]} config
        url (str "https://" host ":" port "/_bulk")]
    {:method :post
     :url url
     :basic-auth [username password]
     :content-type :json
     :body (json/write-str {:index {:_index index}})}))

(defn send-opensearch-log-req
  [base-req log]
  (try
    (http-client/request
     (update base-req :body str \newline log \newline))
    (catch Exception e
      (log-console! :error "failed to send opensearch log"
                    {:log log} e))))

;; If on dev `env`, does basically nothing besides setting the min
;; level. On `prod` env however, an async channel waits for log events,
;; which are then sent to OpenSearch.
(defrecord Logger [config]
  component/Lifecycle
  (start [this]
    (let [prod? (= (:env config) :prod)
          os-cfg (:opensearch config)
          log-ch (async/chan)
          os-base-req (build-opensearch-base-req os-cfg)]

      (t/set-min-level!
       (or (get-in config [:logger :min-level]) :info))

      (when (and prod? (instance? ManyToManyChannel log-ch))
        (t/set-ns-filter! {:disallow #{"*jetty*" "*hikari*"
                                       "*pedestal*" "*migratus*"}})

        (t/add-handler!
         ::opensearch
         (fn [signal]
           (async/go
             (async/>! log-ch (signal->opensearch-log signal)))))

        (async/go
          (while true
            (let [[log _] (async/alts! [log-ch])]
              (send-opensearch-log-req os-base-req log)))))

      (assoc this :log-ch log-ch)))
  (stop [this]
    (t/remove-handler! ::opensearch)
    (update this :log-ch #(when % (async/close! %)))))

(defn log
  ([level msg]
   (log level msg nil nil nil))
  ([level msg data]
   (log level msg data nil nil))
  ([level msg data ctx]
   (log level msg data ctx nil))
  ([level msg data ctx error]
   (t/log! {:level level
            :ctx ctx
            :data data
            :error error}
           (str msg))))

(defn gen-ctx-with-cid []
  {:cid (str "cid-" (random-uuid) "-" (System/currentTimeMillis))})

(comment
  (component/start
   (map->Logger
    {:config
     {:env :prod
      :opensearch
      {:username "foobar"
       :password "foobar"
       :host "foobar"
       :port 25060
       :index "components-test-logs"}}}))

  (log :error "something happened" {:hello true})
  ;;
  )
