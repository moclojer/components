(ns com.moclojer.components.logs
  (:require
   [clojure.core.async :as async]
   [clojure.data.json :as json]
   [clj-http.client :as http-client]
   [taoensso.telemere :as t])
  (:import [clojure.core.async.impl.channels ManyToManyChannel]))

(defn build-opensearch-base-req
  [config]
  (let [{:keys [username password host port]} config
        url (str "https://" host ":" port "/_bulk")]
    {:method :post
     :url url
     :basic-auth [username password]
     :content-type :json
     :body (json/write-str {:index {:_index "logs"}})}))

(defn ->str-values
  [m]
  (reduce-kv
   (fn [acc k v]
     (assoc acc k (cond
                    (map? v) (->str-values v)
                    (string? v) (identity v)
                    :else (pr-str v))))
   {} m))

(defn send-opensearch-log-req
  [base-req log]
  (http-client/request
   (update
    base-req :body
    str \newline (json/write-str (->str-values log)) \newline)
   identity #(throw ^Exception %)))

(defonce log-ch (atom (async/chan)))

(defn setup [config level env]
  (let [prod? (= env :prod)
        log-ch' (swap!
                 log-ch
                 (fn [ch]
                   (when ch (async/close! ch))
                   (async/chan)))]

    (t/set-min-level! level)

    (when (and prod? (instance? ManyToManyChannel log-ch'))
      (let [os-cfg (when prod? (:opensearch config))
            os-base-req (build-opensearch-base-req os-cfg)]

        (t/set-ns-filter! {:disallow #{"*jetty*" "*hikari*"
                                       "*pedestal*" "*migratus*"}})

        (t/add-handler!
         :opensearch
         (fn [signal]
           (async/go
             (async/>!
              log-ch'
              (select-keys signal [:level :ctx :data :msg_ :error
                                   :thread :uid :inst])))))

        (async/go
          (while true
            (let [[log _] (async/alts! [log-ch'])]
              (send-opensearch-log-req os-base-req log))))))))

(comment
  (def my-signal
    (t/with-signal
      (t/log! {:level :info
               :data {:hello true
                      :time "hello world"}}
              "hello")))

  (def base-req
    (build-opensearch-base-req
     {:username "foobar"
      :password "foobar"
      :host "foobar"
      :port 25060}))

  (http-client/request
   (update
    base-req :body
    str
    \newline
    (json/write-str my-signal)
    \newline))
  ;;
  )

(defn log [level msg & [:as data]]
  (t/log! {:level level
           :data (first data)}
          (str msg)))

(comment
  ;; DEPRECATED
  (defmacro log [level & args]
    `(timbre/log ~level ~@args))
  ;;
  )

(defn gen-ctx-with-cid []
  {:cid (str "cid-" (random-uuid) "-" (System/currentTimeMillis))})

(comment
  log-ch
  
  (setup {:opensearch
          {:username "foobar"
           :password "foobar"
           :host "foobar"
           :port 25060}}
         :info :prod)

  (log :info "something happened" {:user "j0suetm"})
  ;;
  )
