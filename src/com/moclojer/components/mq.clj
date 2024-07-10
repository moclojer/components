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
  (start-job! [this job]
    "Starts a simple reoccuring `job` that triggers it.

     A job should look like this:
     `{:channel \"my-channel\"
       :event {:hello 1}
       :sleep 2500
       :max-attempts 50}

     Both `:sleep` and `:max-attempts` are optional."))

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

(defrecord MQ [config database storage http sentry workers jobs blocking?]
  component/Lifecycle
  (start [this]
    (logs/log :info "starting mq")
    (let [components {:config config
                      :database database
                      :storage storage
                      :http http
                      :sentry sentry}
          client (rq/create-client (get-in config [:config :mq :uri]))
          wrapped-workers (map #(wrap-worker % components sentry) workers)
          subs (rq-pubsub/subscribe! client wrapped-workers :blocking? blocking?)]

      (doseq [job jobs]
        (start-job! (assoc this :client client) job))

      (merge this {:client client
                   :subs subs
                   :jobs jobs
                   :components components})))
  (stop [this]
    (logs/log :info "stopping mq")
    (when-let [?client (:client this)]
      (rq/close-client ?client)))

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
  (start-job! [this job]
    (let [{:keys [channel event sleep max-attempts]
           :or {sleep 2000
                ;; max-attempts of `0` means infinite attempts.
                max-attempts 0}} job]
      (logs/log :info "starting job" :ctx {:job job})
      (future
        (loop [attempt 1]
          (let [infinite-attempts? (<= max-attempts 0)
                attempt? (<= attempt max-attempts)]
            (when (or infinite-attempts? attempt?)
              (logs/log :info "running job"
                        :ctx {:job job
                              :attempt attempt})
              (try-op! this :publish! [channel event] {})
              (Thread/sleep sleep)
              (recur (inc attempt)))))))))

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
