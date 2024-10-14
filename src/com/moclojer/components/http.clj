(ns com.moclojer.components.http
  (:require
   [clj-http.client :as http]
   [clj-http.util :as http-util]
   [com.moclojer.components.logs :as logs]
   [com.stuartsierra.component :as component]))

(defn request-fn
  "Accepts :req which should be a map containing the following keys:
  :url - string, containing the http address requested
  :method - keyword, contatining one of the following options:
    #{:get :head :post :put :delete :options :copy :move :patch}

  The following keys make an async HTTP request, like ring's CPS handler.
  * :respond
  * :raise"
  [{:keys [url] :as req} & [respond raise]]
  (http/check-url! url)
  (if (http-util/opt req :async)
    (if (some nil? [respond raise])
      (throw (IllegalArgumentException.
              "If :async? is true, you must pass respond and raise"))
      (http/request (dissoc req :respond :raise) respond raise))
    (http/request req)))

(defprotocol HttpProvider
  (request
    [this request-input ctx])
  (request-or-throw
    [this req expected-status ctx]))

(defrecord Http [_]
  component/Lifecycle
  (start [this] this)
  (stop  [this] this)

  HttpProvider
  (request
    [_ {:keys [method url] :as request-input} ctx]
    (logs/log :info "sending http request"
              (merge ctx {:method method
                          :url url}))
    (let [start-time (System/currentTimeMillis)
          {:keys [status] :as response} (request-fn request-input)
          end-time (System/currentTimeMillis)
          total-time (- end-time start-time)]
      (logs/log :info "received http response"
                (merge ctx {:response-time-millis total-time
                            :status status}))
      response))
  (request-or-throw
    [this req expected-status ctx]
    (let [{:keys [status] :as resp} (request this req ctx)]
      (if (= status expected-status)
        resp
        (do
          (logs/log :error "failed critical request. throwing..."
                    (merge ctx {:url (:url req)
                                :status status
                                :expected-status expected-status}))
          (throw (ex-info "failed critical request"
                          (merge ctx {:status status
                                      :expected-status expected-status
                                      :req req
                                      :resp resp}))))))))

(comment
  (def hp (component/start (->Http {})))

  (request hp {:url "https://google.com"
               :method :get} {})

  (component/stop hp)
  ;;
  )

(defrecord HttpMock [responses]
  component/Lifecycle
  (start [this]
    (merge this {:responses responses}))
  (stop [this] this)

  HttpProvider
  (request
    [this req ctx]

    (let [mreq (select-keys req [:method :url])
          resp (-> #(= mreq (select-keys % (keys mreq)))
                   (filter (:responses this))
                   first :response
                   (or {:status 500
                        :body "mocked response not set"}))]
      (logs/log :info "sending http request" (merge ctx mreq))
      (assoc
       (if (fn? resp) (resp req) resp)
       :instant (System/currentTimeMillis))))
  (request-or-throw
    [this req expected-status ctx]
    (let [{:keys [status] :as resp} (request this req ctx)]
      (if (= status expected-status)
        resp
        (do
          (logs/log :error "failed critical request. throwing..."
                    (merge ctx {:url (:url req)
                                :status status
                                :expected-status expected-status}))
          (throw (ex-info "failed critical request"
                          (merge ctx {:status status
                                      :expected-status expected-status
                                      :req req
                                      :resp resp}))))))))

(comment
  (def m (component/start
          (->HttpMock [{:url "https://test.com"
                        :method :get
                        :response
                        {:status 200
                         :body "hello world"}}
                       {:url "https://test.com"
                        :method :put
                        :response (fn [req]
                                    {:status 200
                                     :body (:body req)})}])))

  (request m {:url "https://test.com"} {})
  ;; => {:status 200
  ;;     :body "hello world"
  ;;     :instant 1715218184868}

  (request m {:url "https://test.com"
              :method :put
              :body "hello"} {})
  ;;
  )
