(ns com.moclojer.components.core
  (:require
   [com.moclojer.components.config    :as config]
   [com.moclojer.components.database  :as database]
   [com.moclojer.components.http      :as http]
   [com.moclojer.components.logs      :as logs]
   [com.moclojer.components.moclojer  :as moclojer]
   [com.moclojer.components.mq        :as mq]
   [com.moclojer.components.router    :as router]
   [com.moclojer.components.sentry    :as sentry]
   [com.moclojer.components.storage   :as storage]
   [com.moclojer.components.webserver :as webserver]))

(defn new-config
  ([filepath]
   (new-config filepath {}))
  ([filepath input-map]
   (config/map->Config {:config (config/read-config filepath input-map)})))

(defn new-database []
  (database/map->Database {}))

(defn new-http []
  (http/map->Http {}))

(defn new-http-mock
  [responses]
  (http/map->HttpMock {:responses responses}))

(def setup-logger logs/setup)

(defn new-mq
  ([workers blocking?]
   (new-mq workers [] blocking?))
  ([workers jobs blocking?]
   (mq/map->MQ {:workers workers
                :jobs jobs
                :blocking? blocking?})))

(defn new-mq-mock
  []
  (mq/map->MQMock {}))

(defn new-router
  [routes]
  (router/map->Router {:router (router/router routes)}))

(defn new-sentry []
  (sentry/map->Sentry {}))

(defn new-sentry-mock []
  (sentry/map->SentryMock {}))

(defn new-storage []
  (storage/map->Storage {}))

(defn new-webserver []
  (webserver/map->WebServer {}))

(defn new-moclojer [on-startup-fn]
  (moclojer/map->Moclojer {:on-startup-fn on-startup-fn}))
