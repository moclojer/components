(ns com.moclojer.components.mq-test
  (:require
   [clojure.test :refer [is]]
   [com.moclojer.components.core :as components]
   [com.moclojer.components.publisher :as publisher]
   [com.moclojer.components.test-utils :as utils]
   [com.stuartsierra.component :as component]
   [state-flow.api :refer [defflow]]
   [state-flow.assertions.matcher-combinators :refer [match?]]
   [state-flow.core :refer [flow]]
   [state-flow.state :as state]))

(def state (atom nil))

(defn fake-worker
  [message _]
  (is (= message {:event "test"}))
  (swap! state (fn [_] message)))

(def workers [{:handler fake-worker
               :queue-name "test"}])

(defn publish-message [msg queue-name]
  (flow
   "should publish a message"
   [publisher (state-flow.api/get-state :publisher)]

   (-> publisher
       (publisher/publish!
        queue-name
        msg)
       state-flow.api/return)))

(defn- create-and-start-system
  []
  (component/start-system
   (component/system-map
    :config (components/new-config "config.example.edn")
    :database (component/using (components/new-database) [:config])
    :sentry (component/using (components/new-sentry-mock) [:config])
    :publisher (component/using
                (components/new-publisher) [:config :sentry])
    :storage (component/using (components/new-storage) [:config])
    :workers (component/using
              (components/new-consumer workers false) [:config :database :storage :publisher]))))

(defflow flow-mq-test
  {:init (utils/start-system! create-and-start-system)
   :cleanup utils/stop-system!
   :fail-fast? true}
  (flow
   "should publish and consume a message"

   (publish-message {:event "test"} "test")

   (flow "sleeping and check the state"

         (match? {:event "test"}
                 (state/return
                  (utils/exec-with-timeout
                   #(deref state)
                   10000 500))))))
