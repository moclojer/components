(ns components.redis-publisher
  (:require [clojure.data.json :as json]
            [com.stuartsierra.component :as component]
            [components.logs :as logs])
  (:import [redis.clients.jedis JedisPoolConfig JedisPooled]))

(defprotocol IPublisher
  (publish! [this queue-name message])
  (archive! [this queue-name message]
    "When a message is sent, but not received/read by
     any subscribers, we archive it in a separate queue
     and make sure to pop it when the subscriber is back
     on track. The created queue will be named pending.<queue-name>.")
  (start-job! [this job]))

(defrecord RedisPublisher [config jobs sentry]
  component/Lifecycle
  (start [this]
    (logs/log :info "starting redis publisher")
    (let [conn (JedisPooled.
                (doto (JedisPoolConfig.)
                  (.setTestOnBorrow true))
                (get-in config [:config :redis-publisher :uri]))
          conn-this (merge this {:conn conn
                                 :components {:sentry sentry}})
          job-futures (doall (map #(start-job! conn-this %) jobs))]
      (assoc conn-this :job-futures job-futures)))
  (stop [this]
    (logs/log :info "stopping redis publisher")
    (doseq [job-future (:job-futures this)]
      (future-cancel job-future))
    (update-in this [:conn] #(.close %)))

  IPublisher
  (publish! [this queue-name message]
    (let [subs-read (.publish (:conn this) queue-name (json/write-str message))]
      (when (= subs-read 0)
        (logs/log :warn "no subscribers read message, archiving..."
                  :ctx {:qname queue-name
                        :message message})
        (archive! this queue-name message))))
  (archive! [this queue-name message]
    (.lpush (:conn this) (str "pending." queue-name)
            (into-array [(json/write-str message)])))
  (start-job! [this {:keys [qname event delay]}]
    (logs/log :info "starting job" :ctx {:qname qname})
    (future
      (loop [i 0]
        (publish! this qname {:event event})
        (Thread/sleep delay)
        (recur (inc i))))))

(defn new-redis-publisher
  ([queue-jobs] (->RedisPublisher {} queue-jobs {}))
  ([] (->RedisPublisher {} [] {})))

(comment
  (def rp
    (component/start
     (->RedisPublisher {:config {:redis-publisher {:uri "redis://localhost:6379"}}}
                       []
                       nil)))

  rp

  (def jobs (for [job [{:qname "test.test"
                        :event {:hello true}
                        :delay 5000}]]
              job))

  jobs

  (publish! rp "test.test" {:hello true})
  (.rpop (:conn rp) "pending.test.test")

  (component/stop rp)

  ;;
  )

;; mock in memory publisher for testing 
(def mock-publisher (atom {}))

(defrecord MockRedisPublisher [config]
  component/Lifecycle
  (start [this]
    (assoc this :publish-conn nil))

  (stop [this]
    (dissoc this :publish-conn)
    (reset! mock-publisher {}))

  IPublisher
  (publish! [_ queue-name message]
    (if-let [state (get @mock-publisher queue-name)]
      (swap! mock-publisher assoc queue-name (conj state message))
      (swap! mock-publisher assoc queue-name [message]))))

(defn mock-redis-publisher []
  (->MockRedisPublisher {}))
