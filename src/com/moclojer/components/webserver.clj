(ns com.moclojer.components.webserver
  (:require
   [ring.adapter.jetty :as jetty]
   [com.moclojer.components.logs :as logs]
   [com.stuartsierra.component :as component]))

(defrecord WebServer [config router]
  component/Lifecycle
  (start [this]
    (let [{:webserver/keys [port]
           :keys [env]} (:config config)
          cfg {:port port
               :host "0.0.0.0"
               :join? true
               :env env}]
      (logs/log :info "starting webserver config"
                :ctx {:env env :port port})
      (assoc this :webserver (jetty/run-jetty (:router router) cfg))))

  (stop [this]
    (logs/log :info "stopping webserver")
    (when-let [srv (:webserver this)]
      (.stop srv))
    (dissoc this :webserver)))
