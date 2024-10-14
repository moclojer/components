(ns com.moclojer.components.sentry
  (:require
   [com.moclojer.components.logs :as logs]
   [com.stuartsierra.component :as component]
   [sentry-clj.core :as sentry]
   [sentry-clj.tracing :as sentry-tr])
  (:import
   [io.sentry CustomSamplingContext]))

(defprotocol SentryLogger
  (send-event! [this event])
  (set-as-default-exception-handler [this]))

(defrecord Sentry [config]
  component/Lifecycle
  (start [this]
    (let [sentry-cfg (get-in config [:config :sentry])]
      (when-let [dns (:dns sentry-cfg)]
        (logs/log-console!
         :info "starting sentry" (select-keys config [:env]))
        (sentry/init! dns sentry-cfg)
        (set-as-default-exception-handler this)))
    this)
  (stop [_])

  SentryLogger
  (send-event! [_ event]
    (try
      (sentry/send-event event)
      (catch Exception e
        (logs/log-console!
         :error "failed to send sentry event"
         {:event event} e))))

  (set-as-default-exception-handler [this]
    (Thread/setDefaultUncaughtExceptionHandler
     (reify Thread$UncaughtExceptionHandler
       (uncaughtException [_ _ exception]
         (send-event! this {:throwable exception}))))))

(defrecord SentryMock [config]
  component/Lifecycle
  (start [this]
    (assoc this :sentry-mem {}))
  (stop [_])

  SentryLogger
  (send-event! [this event]
    (let [{:keys [sentry-mem]} this]
      (assoc sentry-mem :error event)))

  (set-as-default-exception-handler [this] this))

(comment
  (def sc
    (-> {:config {:sentry {:dns "foobar"
                           :traces-sample-rate 1.0
                           :env "prod"}}}
        ->Sentry
        component/start))

  (send-event! sc {:message "Oh no!"
                   :throwable (RuntimeException. "Something has happened")})

  (sentry-tr/with-start-child-span "task" "logging in"
    (send-event! sc {:throwable (ex-info "failed to log in" {})}))

  (let [tr (sentry-tr/start-transaction "update-user" "Updates a user in db"
                                        (CustomSamplingContext.)
                                        nil)]
    (sentry-tr/with-start-child-span "get-user" "getting user"
      (Thread/sleep 2000)
      (sentry-tr/with-start-child-span "update-user" "updating user"
        (send-event! sc {:user {:id (str (random-uuid))
                                :email "teodoro.josue@pm.me"}
                         :breadcrumbs [{:type "http"
                                        :category "xhr"
                                        :data {"method" "PUT"
                                               "url" "/users/j0suetm"}}
                                       {:type "query"
                                        :category "update"
                                        :data {"query" "UPDATE AAA;; INVALID"}}]
                         :throwable (ex-info "failed to update user" {})})))

    (sentry-tr/finish-transaction! tr))
  ;;
  )
