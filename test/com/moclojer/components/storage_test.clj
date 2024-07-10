(ns com.moclojer.components.storage-test
  (:require
   [clojure.java.io :as io]
   [com.moclojer.components.core :as components]
   [com.moclojer.components.storage :as storage]
   [com.moclojer.components.test-utils :as utils]
   [com.stuartsierra.component :as component]
   [state-flow.api :refer [defflow get-state]]
   [state-flow.assertions.matcher-combinators :refer [match?]]
   [state-flow.core :refer [flow]]
   [state-flow.state :as state]))

(defn- create-and-start-system! []
  (component/start-system
   (component/system-map
    :config (components/new-config "config.example.edn")
    :storage (component/using (components/new-storage) [:config]))))

(defflow flow-storage-test
  {:init (utils/start-system! create-and-start-system!)
   :cleanup utils/stop-system!
   :fail-fast? true}
  (flow
    "should create a file on storage and read it back"
    [storage (get-state :storage)]
    (state/invoke #(storage/create-bucket! storage "test"))
    (state/invoke #(do
                    ;; wait for bucket creation
                     (Thread/sleep 1000)
                     (storage/upload! storage "test" "test.txt" "hello world!" {})))
    (match? "hello world!"
            (state/return
             (utils/exec-with-timeout
              #(some-> (storage/get-file storage "test" "test.txt")
                       io/reader
                       slurp)
              10000 500)))))
