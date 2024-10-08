(ns com.moclojer.components.logs
  (:require
   [clojure.core.async :as async]
   [clojure.data.json :as json]
   [clj-http.client :as http-client]
   [taoensso.telemere :as t])
  (:import [clojure.core.async.impl.channels ManyToManyChannel]))

(defn ->str-values
  [m]
  (reduce-kv
   (fn [acc k v]
     (assoc acc k (cond
                    (map? v) (->str-values v)
                    (string? v) (identity v)
                    :else (pr-str v))))
   {} m))

(defn signal->opensearch-log
  [{:keys [thread location] :as signal}]
  (-> (select-keys signal [:level :ctx :data :msg_ :error :uid :inst])
      (merge {"thread/group" (:group thread)
              "thread/name" (:name thread)
              "thread/id" (:id thread)
              :location (str (:ns location) ":"
                             (:line location) "x"
                             (:column location))})
      (->str-values)
      (json/write-str)))

(defn build-opensearch-base-req
  [config index]
  (let [{:keys [username password host port]} config
        url (str "https://" host ":" port "/_bulk")]
    {:method :post
     :url url
     :basic-auth [username password]
     :content-type :json
     :body (json/write-str {:index {:_index index}})}))

(defn send-opensearch-log-req
  [base-req log]
  (http-client/request
   (update base-req :body str \newline log \newline)))

(defonce log-ch (atom nil))

(defn setup [config level env & [index]]
  (let [prod? (= env :prod)
        log-ch' (swap!
                 log-ch
                 (fn [ch]
                   (when ch (async/close! ch))
                   (async/chan)))]

    (t/set-min-level! level)

    (when (and prod? (instance? ManyToManyChannel log-ch'))
      (let [os-cfg (when prod? (:opensearch config))
            os-base-req (build-opensearch-base-req os-cfg index)]

        (t/set-ns-filter! {:disallow #{"*jetty*" "*hikari*"
                                       "*pedestal*" "*migratus*"}})

        (t/add-handler!
         :opensearch
         (fn [signal]
           (async/go
             (async/>! log-ch' (signal->opensearch-log signal)))))

        (async/go
          (while true
            (let [[log _] (async/alts! [log-ch'])]
              (send-opensearch-log-req os-base-req log))))))))

(defn log [level msg & [:as data]]
  (t/log! {:level level
           :data (first data)}
          (str msg)))

(defn gen-ctx-with-cid []
  {:cid (str "cid-" (random-uuid) "-" (System/currentTimeMillis))})

(comment
  @log-ch

  (setup {:opensearch
          {:username "foobar"
           :password "foobar"
           :host "foobar"
           :port 25060}}
         :info :prod "moclojer-api-logs")

  (log :error "something happened" {:user "j0suetm"})
  ;;
  )
