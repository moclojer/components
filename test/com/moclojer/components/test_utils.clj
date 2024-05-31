(ns com.moclojer.components.test-utils
  (:require
   [com.moclojer.components.core :as components]
   [com.moclojer.components.migrations :as migrations]
   [com.stuartsierra.component :as component]
   [pg-embedded-clj.core :as pg-emb]))

(defn start-system!
  [system-start-fn]
  (fn []
    (components/setup-logger :info :auto :dev)
    (pg-emb/init-pg)
    (migrations/migrate (migrations/build-complete-db-config "config.example.edn"))
    (system-start-fn)))

(defn stop-system!
  [system]
  (component/stop-system system)
  (pg-emb/halt-pg!))
