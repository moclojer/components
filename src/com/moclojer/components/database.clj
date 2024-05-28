(ns components.database
  (:require
   [com.stuartsierra.component :as component]
   [components.db-utils :refer [to-jdbc-uri]]
   [components.logs :as logs]
   [next.jdbc :as jdbc]
   [next.jdbc.connection :as connection])
  (:import
   (com.zaxxer.hikari HikariDataSource)))

;; https://github.com/seancorfield/next-jdbc/blob/develop/doc/getting-started.md#logging-sql-and-parameters
(defn db-logger
  "Simple logger for debugging purposes."
  [operation query]
  (logs/log :info (str "executing database operation " operation)
            :ctx {:query query}))

(defprotocol DatabaseProvider
  (execute [self command]
    "Low-level API to execute a command in the database"))

(defrecord Database [config ^HikariDataSource datasource]
  component/Lifecycle
  (start [this]
    (let [{:keys [jdbc-url]} (get-in config [:config :database])]
      (logs/log :info "starting database")
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
  (execute [this commands]
    (let [ds (:datasource this)
          log-ds (jdbc/with-logging ds db-logger)]
      (jdbc/execute! log-ds commands))))

(defn new-database []
  (map->Database {}))
