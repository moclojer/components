(ns components.logs
  (:require [clojure.string :as str]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.core :as core-appenders]
            [timbre-json-appender.core :as tas])
  (:import [java.util.logging Filter Handler Logger]))

(defn clean-dep-logs
  "clean logs on prod that are not from our application"
  []
  (doseq [^Handler handler (.. (Logger/getGlobal)
                               getParent
                               getHandlers)]
    (.setFilter
     handler
     (reify Filter
       (isLoggable [_ record]
         (if-let [^String logger-name (.getLoggerName record)]
           (not-any? #(.contains logger-name %)
                     ["jetty" "hikari" "pedestal" "migratus"])
           ;; returning false explicitly so Java interop doesn't
           ;; bugout for some reason in the future.
           false))))))

(defn clean-timbre-appenders []
  (->> (reduce-kv
        (fn [acc k _]
          (assoc acc k nil))
        {} (:appenders timbre/*config*))
       (assoc nil :appenders)
       timbre/merge-config!))

(defn setup [level stream env]
  (clean-timbre-appenders)
  (let [prod? (= env :prod)
        ns-filter (when prod?
                    #{"components.*" "back.api.*"
                      "yaml-generator.*" "cloud-ops.api.*"})
        appenders (if prod?
                    (tas/install)
                    {:appenders
                     {:println
                      (core-appenders/println-appender
                        {:stream stream})}})]
    (when prod? (clean-dep-logs))
    (timbre/merge-config!
      (merge appenders
             {:min-level level
              :ns-filter ns-filter}))))

(defmacro log [level & args]
  `(timbre/log ~level ~@args))

(comment
  (setup [["*" :info]] :auto :prod)
  (log :info :world)
  ;;
  )
