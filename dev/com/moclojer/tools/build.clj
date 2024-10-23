(ns com.moclojer.tools.build
  (:refer-clojure :exclude [test])
  (:require
   [clojure.string :as string]
   [clojure.tools.build.api :as b]))

(def version "0.1.4")

(def class-dir "target/classes")
(def jar-file "target/com.moclojer.components.jar")

(set! *warn-on-reflection* true)

(defmacro with-err-str
  [& body]
  `(let [s# (new java.io.StringWriter)]
     (binding [*err* s#]
       ~@body
       (str s#))))

(def pom-template
  [[:description "The base components we our Web Services at moclojer."]
   [:url "https://github.com/moclojer/components"]
   [:licenses
    [:license
     [:name "MIT License"]
     [:url "https://opensource.org/licenses/MIT"]]]
   [:scm
    [:url "https://github.com/moclojer/components"]
    [:connection "scm:git:https://github.com/moclojer/components.git"]
    [:developerConnection "scm:git:ssh:git@github.com:moclojer/components.git"]
    [:tag (str "v" version)]]])

(def options
  (let [basis (b/create-basis {:project "deps.edn"})]
    {:class-dir  class-dir
     :lib        'com.moclojer/components
     :version    version
     :basis      basis
     :ns-compile '[com.moclojer.components.core]
     :uber-file  jar-file
     :jar-file   jar-file
     :target     "target"
     :src-dirs   (:paths basis)
     :pom-data   pom-template
     :exclude    ["docs/*" "test/*" "target/*"]}))

(defn -main
  [& _]
  (let [basis (b/create-basis {:project "deps.edn"})]
    (println "Clearing target directory")
    (b/delete {:path "target"})

    (println "Writing pom")
    (->> (b/write-pom options)
         with-err-str
         string/split-lines
         ;; Avoid confusing future me/you: suppress "Skipping coordinate" messages for our jars, we don't care, we are creating an uberjar
         (remove #(re-matches #"^Skipping coordinate: \{:local/root .*target/(lib1|lib2|graal-build-time).jar.*" %))
         (run! println))
    (b/copy-dir {:src-dirs (:paths basis)
                 :target-dir class-dir})

    (println "Compile sources to classes")
    (b/compile-clj options)

    (println "Packaging classes into jar")
    (b/jar options)))
