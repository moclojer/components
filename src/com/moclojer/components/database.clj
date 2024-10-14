(ns com.moclojer.components.database
  (:require
   [com.moclojer.components.db-utils :refer [to-jdbc-uri]]
   [com.moclojer.components.logs :as logs]
   [com.stuartsierra.component :as component]
   [next.jdbc :as jdbc]
   [next.jdbc.connection :as connection])
  (:import
   (com.zaxxer.hikari HikariDataSource)))

;; https://github.com/seancorfield/next-jdbc/blob/develop/doc/getting-started.md#logging-sql-and-parameters
(defn build-db-logger
  "Builds a simple logger for debugging purposes."
  [ctx]
  (fn [operation query]
    (logs/log :info operation (assoc ctx :query query))))

(defprotocol DatabaseProvider
  (execute [self command ctx]
    "Low-level API to execute a command in the database"))

(defrecord Database [config ^HikariDataSource datasource]
  component/Lifecycle
  (start [this]
    (let [{:keys [jdbc-url]} (get-in config [:config :database])]
      (logs/log :info "starting database" (select-keys config [:env]))
      (if datasource
        this
        (assoc this :datasource (connection/->pool HikariDataSource {:jdbcUrl (to-jdbc-uri jdbc-url)})))))
  (stop [this]
    (logs/log :info "stopping database")
    (if datasource
      (do
        (.close datasource)
        (assoc this :datasource nil))
      this))

  DatabaseProvider
  (execute [this commands ctx]
    (let [ds (:datasource this)
          log-ds (jdbc/with-logging ds (build-db-logger ctx))]
      (jdbc/execute! log-ds commands))))
