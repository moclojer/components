(ns com.moclojer.components.migrations
  (:require
   [com.moclojer.components.config :as config]
   [com.moclojer.components.db-utils :refer [to-jdbc-uri]]
   [com.moclojer.components.logs :as logs]
   [migratus.core :as migratus]
   [next.jdbc :as jdbc])
  (:gen-class))

(defn get-connection [cfg-filepath]
  (let [{:keys [jdbc-url] :as db} (:database (config/read-config cfg-filepath {}))]
    (logs/log :info "connecting migrator to db")
    (jdbc/get-connection (assoc db :jdbcUrl (to-jdbc-uri jdbc-url)))))

(defn build-base-db-config [migration-dir]
  {:store         :database
   :migration-dir migration-dir})

(defn build-complete-db-config [cfg-filepath]
  (assoc (-> (config/read-config cfg-filepath {})
             :migration-dir
             build-base-db-config)
         :db {:connection (get-connection cfg-filepath)}))

(defn init
  [config]
  (migratus/init config))

(defn migrate
  [config]
  (migratus/migrate config))

(defn up
  [config & args]
  (migratus/up config args))

(defn down
  [config & args]
  (migratus/down config args))

(defn create
  [config migration-name]
  (migratus/create config migration-name))

(defn rollback
  [config]
  (migratus/rollback config))

(defn pending-list
  [config]
  (migratus/pending-list config))

(defn migrate-until-just-before
  [config & args]
  (migratus/migrate-until-just-before config args))

(defn -main
  [& args]
  (let [command (first args)
        args-rest (drop 2 args)]
    (if (= command "create")
      (create (build-base-db-config (second args)) (first args-rest))
      (cond-> (build-complete-db-config (second args))
        (= command "init")              init
        (= command "migrate")           migrate
        (= command "up")                (up args-rest)
        (= command "down")              (down args-rest)
        (= command "rollback")          rollback
        (= command "pending-list")      pending-list
        (= command "until-just-before") (migrate-until-just-before args-rest)
        :else #(throw (ex-info (str "Command not found " command)
                               {:db-config %1}))))))
