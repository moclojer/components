(ns com.moclojer.components.logs
  (:require
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
    [level msg & [data error]]
    (log! {:level level
           :data data
           :error error
           :msg_ msg})))

(defn ->str-values
  "Adapts all values (including nested maps' values) from given
  map `m` to a string.

  This is because OpenSearch only accepts string json values."
  [v]
  (cond
    (map? v) (reduce-kv (fn [m k v] (assoc m k (->str-values v))) {} v)
    (coll? v) (into (empty v) (map ->str-values v))
    (string? v) v
    (nil? v) v
    :else (str v)))

(defn signal->opensearch-log
  "Adapts a telemere signal to a pre-defined schema for OpenSearch."
  [{:keys [thread location parent root msg_] :as signal}]
  (-> (select-keys signal [:level :ctx :data :uid :id
                           :inst :end-inst :run-nsecs])
      (merge {"msg_" (when-not (delay? msg_) msg_)
              "thread/group" (:group thread)
              "thread/name" (:name thread)
              "thread/id" (:id thread)
              "parent/uid" (:uid parent)
              "parent/id" (:id parent)
              "root/uid" (:uid root)
              "root/id" (:id root)
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
    (let [config (:config config)
          prod? (= (:env config) :prod)
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

(defmacro trace
  [id data & body]
  `(taoensso.telemere/trace!
    {:id ~id
     :data ~data}
    (do ~@body)))

(defn log
  [level msg & [data ctx error]]
  (t/log! {:level level
           :ctx ctx
           :data data
           :error error}
          (str msg)))

(defn gen-ctx-with-cid []
  {:cid (str "cid-" (random-uuid) "-" (System/currentTimeMillis))})

(comment
  (def logger
    (map->Logger
     {:config
      {:config
       {:env :prod
        :opensearch
        {:username "foobar"
         :password "foobar"
         :host "foobar"
         :port 25060
         :index "components-test-logs"}}}}))

  (component/start logger)
  (component/stop logger)

  (trace ::testing-stuff {:testing? :definitely}
         (log :error "aaaaa aaaa" {:hello true}))
  ;;
  )
