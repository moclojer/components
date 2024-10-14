(ns com.moclojer.components.database-test
  (:require
   [com.moclojer.components.core :as components]
   [com.moclojer.components.database :as database]
   [com.moclojer.components.test-utils :as utils]
   [com.stuartsierra.component :as component]
   [state-flow.api :refer [defflow]]
   [state-flow.assertions.matcher-combinators :refer [match?]]
   [state-flow.core :refer [flow]]))

(defn execute!
  [commands]
  (flow "makes database execution"
    [database (state-flow.api/get-state :database)]
    (-> database
        (database/execute commands {})
        state-flow.api/return)))

(defn- create-and-start-system
  []
  (component/start-system
   (component/system-map
    :config (components/new-config "config.example.edn")
    :logger (component/using (components/new-logger) [:config])
    :database (component/using (components/new-database) [:config]))))

(defflow flow-database-test
  {:init (utils/start-system! create-and-start-system)
   :cleanup utils/stop-system!
   :fail-fast? true}

  (flow "creates a table and insert a row and retreive"
    (execute! ["CREATE TABLE test (id SERIAL PRIMARY KEY, name VARCHAR(255))"])

    (execute! ["INSERT INTO test (name) VALUES ('test')"])

    (match? [#:test {:name "test"
                     :id 1}]
            (execute! ["SELECT * FROM test"]))))
