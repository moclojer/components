(ns com.moclojer.components.router
  (:require
   [clojure.string :as str]
   [com.moclojer.components.logs :as logs]
   [com.moclojer.components.sentry :as sentry]
   [com.stuartsierra.component :as component]
   [muuntaja.core :as m]
   [reitit.coercion.malli :as reitit.malli]
   [reitit.dev.pretty :as pretty]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as coercion]
   [reitit.ring.middleware.multipart :as multipart]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]))

(defn send-sentry-evt-from-req! [req ex]
  (if-let [sentry-cmp (get-in req [:components :sentry])]
    (sentry/send-event! sentry-cmp {:throwable ex})
    (logs/log :error "failed to send sentry event (nil component)")))

(defn exception-middleware
  [handler-fn]
  (fn [request]
    (try
      (handler-fn request)
      (catch Exception e
        (logs/log :error (.getMessage e)
                  :ctx {:exception e})
        (send-sentry-evt-from-req! request e)
        {:status 500
         :body (.getMessage e)}))))

(defn log-request-middleware
  [handler-fn]
  (fn [request]
    (let [method (str/upper-case (name (:request-method request)))]
      (logs/log :info (str method " " (:uri request))
                :ctx {:method method
                      :host (:server-name request)
                      :uri (:uri request)
                      :query-string (:query-string request)})
      (handler-fn request))))

(defn cid-middleware
  "Extends incoming request with CID if not given already"
  [handler-fn]
  (fn [request]
    (->> {:cid (get-in request [:headers "cid"]
                       (:cid (logs/gen-ctx-with-cid)))}
         (assoc request :ctx)
         (handler-fn))))

(defn build-components-middleware
  [components]
  (fn [handler-fn]
    (fn [request]
      (handler-fn (assoc request :components components)))))

(defn build-router-settings
  [components]
  {:exception pretty/exception
   :data {:coercion reitit.malli/coercion
          :muuntaja (m/create
                     (-> m/default-options
                         (assoc-in [:formats "application/json"
                                    :decoder-opts :bigdecimals]
                                   true)))
          :middleware [(build-components-middleware components)
                       log-request-middleware
                       cid-middleware
                       exception-middleware
                       swagger/swagger-feature
                       parameters/parameters-middleware
                       muuntaja/format-negotiate-middleware
                       muuntaja/format-response-middleware
                       muuntaja/format-request-middleware
                       coercion/coerce-response-middleware
                       coercion/coerce-request-middleware
                       multipart/multipart-middleware]}})

(defrecord Router [routes]
  component/Lifecycle
  (start [this]
    (assoc this :router
           (ring/ring-handler
            (ring/router
             routes
             (build-router-settings this))
            (ring/routes
             (swagger-ui/create-swagger-ui-handler
              {:path "/"
               :config {:validatorUrl nil
                        :operationsSorter "alpha"}})
             (ring/create-resource-handler)
             (ring/create-default-handler)))))
  (stop [this]
    (dissoc this :router)))
