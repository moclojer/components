(ns com.moclojer.components.mq
  (:require
   [com.moclojer.components.logs :as logs]
   [com.moclojer.components.sentry :as sentry]
   [com.moclojer.rq :as rq]
   [com.moclojer.rq.pubsub :as rq-pubsub]
   [com.stuartsierra.component :as component]))

(defprotocol IMQ
  (try-op! [this op-name args ctx]
    "Wraps our `clj-rq` operations in order for our components to call sentry
     if an error or exception occurs.")
  (run-job! [this channel job ctx]))

(defn op-name->op-fn
  "Adapts given mq `operation name` to its relative function."
  [op-name]
  (or (get {:publish! rq-pubsub/publish!
            :subscribe! rq-pubsub/subscribe!}
           op-name)
      (throw (ex-info (str "There is no mq operation named " op-name)
                      {:op-name op-name}))))

(defn wrap-worker
  "Wraps given `worker` with a secure try-catch. Also, adds `components`
  to it's invocation."
  [worker components sentry]
  (let [handler-fn (:handler worker)]
    (assoc worker :handler
           (fn [message]
             (try
               (handler-fn message components)
               (catch Throwable ex
                 (logs/log :error "failed to handle worker"
                           :ctx {:worker worker
                                 :message message
                                 :ex-message (.getMessage ex)})
                 (sentry/send-event! sentry {:throwable ex})))))))

(defrecord MQ [config database storage http sentry workers jobs]
  component/Lifecycle
  (start [this]
    (logs/log :info "starting rq")
    (let [components {:config config
                      :database database
                      :storage storage
                      :http http
                      :sentry sentry}
          client (rq/create-client (get-in config [:config :mq :uri]))]
      (merge this {:client client
                   :subs (->> (map #(wrap-worker % components sentry) workers)
                              (rq-pubsub/subscribe! client))
                   :components components})))
  (stop [this]
    (rq/close-client (:client this)))

  IMQ
  (try-op! [this op-name args ctx]
    (try
      (let [op-fn (op-name->op-fn op-name)
            op-args (cons (:client this) args)]
        (apply op-fn op-args))
      (catch Throwable ex
        (logs/log :error "failed to exec mq operation"
                  :ctx (merge ctx {:op-name op-name
                                   :args args
                                   :ex-message (.getMessage ex)}))
        (sentry/send-event! (get-in this [:components :sentry])
                            {:throwable ex}))))
  (run-job! [_this _channel _job _ctx]))

(defonce mock-channels (atom {}))

(defn mock-op-name->op-fn
  "Adapts given mq `operation name` to its relative function."
  [op-name]
  (or (get {:publish! (fn [channel message]
                        (swap! mock-channels #(update % channel conj message)))}
           op-name)
      (throw (ex-info (str "There is no mq operation named " op-name)
                      {:op-name op-name}))))

(defrecord MQMock []
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  IMQ
  (try-op! [_ op-name args _]
    (apply (mock-op-name->op-fn op-name) args)))
