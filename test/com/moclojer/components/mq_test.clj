(ns com.moclojer.components.mq-test
  (:require
   [clojure.test :refer [is]]
   [com.moclojer.components.core :as components]
   [com.moclojer.components.mq :as mq]
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

(defn publish-message [msg channel]
  (flow
    "should publish a message"
    [mq (state-flow.api/get-state :mq)]
    (state-flow.api/return (mq/try-op! mq :publish! [channel msg] {}))))

(defn- create-and-start-system
  []
  (component/start-system
   (component/system-map
    :config (components/new-config "config.example.edn")
    :database (component/using (components/new-database) [:config])
    :sentry (component/using (components/new-sentry-mock) [:config])
    :mq (component/using (components/new-mq [{:handler fake-worker
                                              :channel "test"}])
                         [:config :database :storage :sentry])
    :storage (component/using (components/new-storage) [:config]))))

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
