(ns com.moclojer.components.config
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [com.stuartsierra.component :as component]))

(def ^:private current-profile
  (keyword (or (System/getenv "PROFILE") "dev")))

(defn- config [filepath profile]
  (aero/read-config (io/resource filepath)
                    {:profile profile}))

(defn read-config [filepath extra-inputs]
  (merge (config filepath current-profile)
         {:env current-profile}
         extra-inputs))

(defrecord Config [config]
  component/Lifecycle
  (start [this] this)
  (stop  [this] this))
