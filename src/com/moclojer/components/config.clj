(ns com.moclojer.components.config
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [com.stuartsierra.component :as component]))

(def ^:private current-profile (keyword (or (System/getenv "PROFILE") "dev")))

(defn- config [profile]
  (aero/read-config (clojure.java.io/resource "back/config.edn")
                    {:profile profile}))

(defn read-config [extra-inputs]
  (merge (config current-profile)
         {:env current-profile}
         extra-inputs))

(defrecord Config [config]
  component/Lifecycle
  (start [this] this)
  (stop  [this] this))
