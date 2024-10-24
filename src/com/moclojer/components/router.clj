(ns com.moclojer.components.router
  (:require
   [com.moclojer.components.logs :as logs]
   [com.stuartsierra.component :as component]
   [muuntaja.core :as m]
   [reitit.coercion.malli :as reitit.malli]
   [reitit.dev.pretty :as pretty]
   [reitit.http :as http]
   [reitit.http.coercion :as coercion]
   [reitit.http.interceptors.exception :as exception]
   [reitit.http.interceptors.multipart :as multipart]
   [reitit.http.interceptors.muuntaja :as muuntaja]
   [reitit.http.interceptors.parameters :as parameters]
   [reitit.pedestal :as pedestal]
   [reitit.ring :as ring]
   [com.moclojer.components.sentry :as sentry]
   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]))

(defn send-sentry-evt-from-req! [req data-ex]
  (let [sentry-cmp (get-in req [:components :sentry])]
    (try
      (sentry/send-event! sentry-cmp {:throwable data-ex})
      (catch Exception sentry-ex
        (logs/log-console!
         :error "failed to send sentry event"
         {:event data-ex} sentry-ex)))))

(defn- coercion-error-handler [status]
  (fn [exception request]
    (logs/log :error "failed to coerce req/resp"
              (logs/log :error "Failed to coerce request/response"
                        {:uri (:uri request)
                         :method (:request-method request)}
                        nil
                        exception))
    (send-sentry-evt-from-req! request exception)
    {:status status
     :body (if (= 400 status)
             (str "Invalid path or request parameters, with the following errors: "
                  (:errors (ex-data exception)))
             "Error checking path or request parameters.")}))

(defn- exception-info-handler [exception request]
  (logs/log :error "server exception"
            nil nil exception)
  (send-sentry-evt-from-req! request exception)
  {:status 500
   :body   "Internal error."})

(def router-settings
  {;:reitit.interceptor/transform dev/print-context-diffs ;; pretty context diffs
   ;;:validate spec/validate ;; enable spec validation for route data
   ;;:reitit.spec/wrap spell/closed ;; strict top-level validation
   :exception pretty/exception
   :data {:coercion reitit.malli/coercion
          :muuntaja (m/create
                     (-> m/default-options
                         (assoc-in [:formats "application/json" :decoder-opts :bigdecimals] true)))
          :interceptors [;; swagger feature
                         swagger/swagger-feature
                         ;; query-params & form-params
                         (parameters/parameters-interceptor)
                         ;; content-negotiation
                         (muuntaja/format-negotiate-interceptor)
                         ;; encoding response body
                         (muuntaja/format-response-interceptor)
                         ;; exception handling
                         (exception/exception-interceptor
                          (merge
                           exception/default-handlers
                           {:reitit.coercion/request-coercion  (coercion-error-handler 400)
                            :reitit.coercion/response-coercion (coercion-error-handler 500)
                            clojure.lang.ExceptionInfo exception-info-handler}))
                             ;; decoding request body
                         (muuntaja/format-request-interceptor)
                             ;; coercing response bodys
                         (coercion/coerce-response-interceptor)
                             ;; coercing request parameters
                         (coercion/coerce-request-interceptor)
                             ;; multipart
                         (multipart/multipart-interceptor)]}})

(defn router [routes]
  (pedestal/routing-interceptor
   (http/router routes router-settings)
   ;; optional default ring handler (if no routes have matched)
   (ring/routes
    (swagger-ui/create-swagger-ui-handler
     {:path "/"
      :config {:validatorUrl nil
               :operationsSorter "alpha"}})
    (ring/create-resource-handler)
    (ring/create-default-handler))))

(defrecord Router [router]
  component/Lifecycle
  (start [this] this)
  (stop  [this] this))
