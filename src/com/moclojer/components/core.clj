(ns com.moclojer.components.core
  (:require
   [com.moclojer.components.config    :as config]
   [com.moclojer.components.consumer  :as consumer]
   [com.moclojer.components.database  :as database]
   [com.moclojer.components.http      :as http]
   [com.moclojer.components.logs      :as logs]
   [com.moclojer.components.publisher :as publisher]
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

(defn new-publisher
  ([]
   (new-publisher []))
  ([jobs]
   (publisher/map->Publisher {:jobs jobs})))

(defn new-publisher-mock []
  (publisher/map->Publisher {}))

(defn new-consumer
  [workers blocking?]
  (consumer/map->Consumer {:workers workers
                           :blocking? blocking?}))

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
