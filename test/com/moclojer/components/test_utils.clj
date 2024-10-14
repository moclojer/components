(ns com.moclojer.components.test-utils
  (:require
   [com.moclojer.components.core :as components]
   [com.moclojer.components.migrations :as migrations]
   [com.stuartsierra.component :as component]
   [pg-embedded-clj.core :as pg-emb]))

(defn start-system!
  [system-start-fn]
  (fn []
    (pg-emb/init-pg)
    (migrations/migrate (migrations/build-complete-db-config "config.example.edn"))
    (system-start-fn)))

(defn stop-system!
  [system]
  (component/stop-system system)
  (pg-emb/halt-pg!))

(defn exec-with-timeout [exec-fn timeout throttle]
  (let [deadline (+ (System/currentTimeMillis) timeout)]
    (loop [current-try 0]
      (Thread/sleep throttle)
      (if (< (System/currentTimeMillis) deadline)
        (let [ret (exec-fn)]
          (prn :current-try current-try
               :current-ret ret)
          (or ret (recur (inc current-try))))
        (throw (ex-info "timeout reached"
                        {:exec-fn exec-fn
                         :timeout timeout}))))))

(comment
  (exec-with-timeout (constantly false) 5000 500)
  ;;
  )
