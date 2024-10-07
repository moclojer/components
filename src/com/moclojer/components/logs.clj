(ns com.moclojer.components.logs
  (:require
   [clojure.data.json :as json]
   [clj-http.client :as http-client]
   [taoensso.telemere :as t]
   [taoensso.telemere.timbre :as timbre])
  (:import (java.util.concurrent TimeoutException TimeUnit)))

(defn build-opensearch-base-req
  [config]
  (let [{:keys [username password host port]} config
        url (str "https://" host ":" port "/_bulk")]
    {:method :post
     :url url
     :async? true
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

(defn send-opensearch-signal-req
  [base-req signal]
  (let [log (-> (select-keys signal [:level :ctx :data
                                     :msg_ :error :thread
                                     :uid :inst])
                (->str-values)
                (json/write-str))
        future (http-client/request
                (update
                 base-req :body
                 str \newline log \newline)
                identity #(throw ^Exception %))]
    (try
      (.get future 1 TimeUnit/SECONDS)
      (catch TimeoutException _
        (.cancel future true)))))

(defn setup [config level env]
  (let [prod? (= env "prod")
        os-cfg (when prod? (:opensearch config))
        os-base-req (build-opensearch-base-req os-cfg)]
    (t/set-min-level! level)
    (t/set-ns-filter! {:disallow "*" :allow "com.moclojer.*"})
    (when prod?
      (telemere/add-signal!
       :opensearch
       (fn [signal])))))

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

  (send-opensearch-signal-req base-req my-signal)

  (http-client/request
   (update
    base-req :body
    str
    \newline
    (json/write-str my-signal)
    \newline))
  ;;
  )

(defmacro log [level & args]
  `(timbre/log ~level ~@args))

(defn gen-ctx-with-cid []
  {:cid (str "cid-" (random-uuid) "-" (System/currentTimeMillis))})

(comment
  (setup :dev)

  (log :info :world)
  ;;
  )
