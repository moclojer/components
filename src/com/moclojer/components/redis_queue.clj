(ns components.redis-queue
  (:require [clojure.data.json :as json]
            [com.stuartsierra.component :as component]
            [components.logs :as logs]
            [components.sentry :as sentry])
  (:import [redis.clients.jedis JedisPoolConfig JedisPooled JedisPubSub]
           [redis.clients.jedis.exceptions JedisConnectionException]))

(defprotocol ISubscriber
  (unarchive-queue! [this conn qname on-msg-fn])
  (subscribe-workers [this conn components workers blocking?])
  (safely-subscribe-workers [this conn pubsub qnames cur-try]))

(defn group-work-handlers-by-qname [workers]
  (reduce
   (fn [acc {:keys [queue-name handler]}]
     (assoc acc queue-name handler))
   {} workers))

(defn create-pubsub [components workers]
  (let [work-handlers-by-qname (group-work-handlers-by-qname workers)]
    (proxy [JedisPubSub] []
      (onMessage [qname json-msg]
        (logs/log :info "received a message"
                  :ctx {:qname qname})
        (if-let [work-handler (get work-handlers-by-qname qname)]
          (try
            (work-handler (json/read-str json-msg :key-fn keyword) components)
            (catch Throwable e
              (logs/log :error "failed to handle message"
                        :ctx {:ex-message (.getMessage e)})
              (.printStackTrace e)
              (some-> (:sentry components)
                      (sentry/send-event! {:status "error"
                                           :message "failed to handle message"
                                           :throwable e}))))
          (logs/log :warn "no work handler for queue"
                    :ctx {:qname qname}))))))

(defrecord RedisWorkers [config database storage publisher http workers sentry blocking?]
  component/Lifecycle
  (start [this]
    (logs/log :info "starting redis workers")
    (let [conn (JedisPooled.
                (doto (JedisPoolConfig.)
                  (.setTestOnBorrow true))
                (get-in config [:config :redis-worker :uri]))
          comps {:database  database  :storage storage
                 :publisher publisher :config  config
                 :http      http      :sentry  sentry}
          pubsub (subscribe-workers this conn comps workers blocking?)]
      (merge this {:conn conn
                   :components comps
                   :pubsub pubsub})))
  (stop [this]
    (update-in this [:pubsub] #(.unsubscribe %))
    (update-in this [:conn] #(.close %)))

  ISubscriber
  (unarchive-queue! [_ conn qname on-msg-fn]
    (loop [unarchived-cnt 0]
      (if-let [msg (.rpop conn (str "pending." qname))]
        (do
          (on-msg-fn msg)
          (recur (inc unarchived-cnt)))
        (logs/log :info "unarchived queue"
                  :ctx {:qname qname
                        :count unarchived-cnt}))))
  (subscribe-workers [this conn components workers blocking?]
    (let [qnames (vec (map :queue-name workers))
          pubsub (create-pubsub components workers)]

      (let [safe-sub-fn #(safely-subscribe-workers this conn pubsub qnames 1)]
        (if blocking?
          (safe-sub-fn)
          (future (safe-sub-fn))))

      pubsub))
  (safely-subscribe-workers [this conn pubsub qnames cur-try]
    (try
      (logs/log :info "subscribing workers"
                :ctx {:workers qnames})

      (doseq [qname qnames]
        (unarchive-queue! this conn qname #(.onMessage pubsub qname %)))
      (.subscribe conn pubsub (into-array qnames))

      (catch JedisConnectionException e
        (logs/log :warn "subscriber connection got killed. trying again..."
                  :ctx {:ex-message (.getMessage e)
                        :cur-try cur-try})
        (Thread/sleep 1000)
        (safely-subscribe-workers this conn pubsub qnames (inc cur-try))))))

(defn new-redis-queue [workers blocking?]
  (->RedisWorkers {} {} {} {} {} workers {} blocking?))

(comment
  (def rw
    (component/start
     (->RedisWorkers {:config {:redis-worker {:uri "redis://localhost:6379"}}}
                     nil nil nil nil
                     [{:handler (fn [ev _cmp] (prn :ev ev))
                       :queue-name "test.test"}]
                     nil false)))

  (component/stop rw)

  ;;
  )
