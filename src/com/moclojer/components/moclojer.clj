(ns com.moclojer.components.moclojer
  (:require
   [com.moclojer.adapters :as m.adapters]
   [com.moclojer.components.logs :as logs]
   [com.moclojer.io-utils :as m.io-utils]
   [com.moclojer.server :as server]
   [com.stuartsierra.component :as component]))

(defn moclojer-server! [{:keys [config-path join?]}]
  (let [*router (m.adapters/generate-routes (m.io-utils/open-file config-path))]
    {:stop-future (server/create-watcher *router {:config-path config-path})
     :server (server/start-server! *router {:join? join?})}))

(defrecord Moclojer [storage config on-startup-fn]
  component/Lifecycle
  (start [this]
    (let [{:keys [config-path join?]} (-> config :config :moclojer)]
      (logs/log :info :moclojer-start
                :info-server {:config-path config-path
                              :join? join?})
      (on-startup-fn storage config-path)
      (assoc this :moclojer
             (moclojer-server!
              {:config-path config-path
               :join? join?}))))

  (stop [this]
    (let [stop-fn (-> this :moclojer :stop-future)]
      (stop-fn)
      (.stop (-> this :moclojer :server))
      (assoc this :moclojer nil))))
