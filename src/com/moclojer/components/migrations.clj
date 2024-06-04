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
        cfg-filepath (second args)
        args-rest (drop 2 args)
        cmd-args (into args-rest [(build-complete-db-config cfg-filepath)])]
    (apply (case command
             "create"            create
             "up"                up
             "down"              down
             "until-just-before" migrate-until-just-before
             "init"              init
             "migrate"           migrate
             "rollback"          rollback
             "pending-list"      pending-list
             (throw (ex-info (str "command not found " command)
                             {:cmd-args cmd-args})))
           cmd-args)))
