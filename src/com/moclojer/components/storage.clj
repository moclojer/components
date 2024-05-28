(ns components.storage
  (:require [clojure.java.io :as io]
            [cognitect.aws.client.api :as aws]
            [cognitect.aws.credentials :as cred]
            [com.stuartsierra.component :as component]
            [components.config :as config]
            [components.logs :as logs]))

(defprotocol IStorage
  (initialize [this bucket-name])
  (get-bucket [this bucket-name])
  (list-buckets [this])
  (create-bucket! [this bucket-name])
  (delete-bucket! [this bucket-name])
  (delete-file! [this bucket-name filename])
  (get-file [this bucket-name filename])
  (list-files [this bucket-name])
  (upload!
    [this bucket-name k value]
    [this bucket-name k value cp]))

(defn assoc-if [m k v]
  (if v
    (assoc m k v)
    m))

(defn ^:private endpoint-override [{:keys [config]}]
  (let [port (-> config :storage :port)
        config-override (->
                         {:protocol (-> config :storage :protocol)
                          :hostname (-> config :storage :host)}
                         (assoc-if :port port))]
    (println "config-override" config-override)
    config-override))

(defrecord Storage [config]
  component/Lifecycle
  (start [this]
    (println "Starting storage component")
    (let [s3 (aws/client {:api :s3
                          :region (or (-> config :config :storage :region) "us-east-1")
                          :credentials-provider (cred/basic-credentials-provider
                                                 {:access-key-id (-> config :config :storage :access-key-id)
                                                  :secret-access-key (-> config :config :storage :secret-access-key)})
                          :endpoint-override (endpoint-override config)})]
      (-> this
          (assoc :storage s3)
          (initialize "moclojer"))))
  (stop [this]
    (println "Stopping storage component")
    (assoc this :storage nil))

  IStorage
  (initialize [this bucket-name]
    (when (nil? (get-bucket this bucket-name))
      (create-bucket! this bucket-name))
    this)

  (get-bucket [this bucket-name]
    (-> (-> this :storage)
        (aws/invoke {:op :ListObjects
                     :request {:Bucket bucket-name}})
        :Contents))

  (list-buckets [this]
    (-> (-> this :storage)
        (aws/invoke {:op :ListBuckets})
        :Buckets))

  (create-bucket! [this bucket-name]
    (-> (-> this :storage)
        (aws/invoke {:op :CreateBucket
                     :request {:Bucket bucket-name}})))

  (list-files [this bucket-name]
    (-> (:storage this)
        (aws/invoke {:op :ListObjects
                     :request {:Bucket bucket-name}})
        :Contents))

  (upload! [this bucket-name filename value]
    (logs/log :info "uploading to storage"
              :ctx {:bucket bucket-name
                    :filename filename
                    :value value})
    (upload! this bucket-name filename value "application/yml"))

  (upload! [this bucket-name filename value content-type]
    (logs/log :info "uploading to storage"
              :ctx {:bucket bucket-name
                    :filename filename
                    :content-type content-type
                    :value value})
    (-> (-> this :storage)
        (aws/invoke {:op :PutObject
                     :Content-Type content-type
                     :request {:Bucket bucket-name
                               :Key filename
                               :Body (.getBytes value)}})))

  (get-file [this bucket-name filename]
    (-> (:storage this)
        (aws/invoke {:op :GetObject
                     :request {:Bucket bucket-name
                               :Key filename}})
        :Body))

  (delete-file! [this bucket-name filename]
    (-> (-> this :storage)
        (aws/invoke {:op :DeleteObject
                     :request {:Bucket bucket-name
                               :Key filename}})))

  (delete-bucket! [this bucket-name]
    (-> (-> this :storage)
        (aws/invoke {:op :DeleteBucket
                     :request {:Bucket bucket-name}}))))

(defn new-storage []
  (map->Storage {}))

(comment
  (def yml "
- endpoint:
    # Note: the method could be omitted because GET is the default
    method: GET
    path: /hello/:username
    response:
      # Note: the status could be omitted because 200 is the default
      status: 200
      headers:
        Content-Type: application/json
      # Note: the body will receive the value passed in the url using the
      # :username placeholder
      body: >
        {
          \"hello\": \"{{path-params.username}}!\"
        }")

  (def system-map
    (component/system-map
     :config (config/new-config)
     :storage (component/using
               (new-storage) [:config])))

  (def system-atom (atom {}))

  (->> system-map
       component/start
       (reset! system-atom))

  (def storage
    (:storage @system-atom))

  (create-bucket! storage "moclojer")

  (get-bucket storage "moclojer")

  (list-buckets storage)

  (delete-bucket! storage "moclojer")

  (list-files storage "moclojer")

  (->> (list-files storage "moclojer")
       (map :Key)
       (map #(delete-file! storage "moclojer" %)))
  #_(get-file storage "moclojer" "1/2/test.yml")

  (upload! storage "moclojer" "moclojer.yml" "[]\n")

  (slurp (io/reader
          (get-file storage "moclojer" "moclojer.yml")))

  ;; testing upload/retrieval time diff bug
  (let [filepath (str (random-uuid) ".yml")]
    (prn :res (upload! storage "moclojer" filepath "[]\n"))
    (-> (get-file storage "moclojer" filepath)
        io/reader slurp))

  #_(list-buckets storage)
  (create-bucket! storage "moclojer")
  (delete-file! storage "moclojer" "moclojer.yml")
  ;
  )
