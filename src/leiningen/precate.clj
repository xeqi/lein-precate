(ns leiningen.precate
  (:require [clojure.pprint :as pprint]
            [clojure.java.io :as io]
            [leiningen.core :as lein]))

(defn defaults [project]
  (let [default-file (io/file (:root project) "default-project.clj")]
    (try
      (spit default-file "(defproject dummy \"1.0\")")
      (lein/read-project (str default-file))
      (finally (.delete default-file)))))

(defn- tidy-key [default-project [k v]]
  (or (= (default-project k) v)
      ;; = on regexes is broken. ಠ_ಠ
      (and (#{:uberjar-exclusions :jar-exclusions} k)
           (= (str (default-project k) (str v))))))

(defn tidy-project [project]
  (let [default-project (defaults project)]
    (into {:root (str (:root project) "/")}
          (remove (partial tidy-key default-project) project))))

(defn suggest-profiles []
  ;; TODO:
  ;; ~/.lein/plugins
  ;; leiningen-auth
  ;; user-settings
  )

(defn- plugin? [[artifact _]]
  (re-find #"^lein-" (name artifact)))

(defn dev-dependencies [project]
  (if-let [dd (:dev-dependencies project)]
    (let [[plugins dev] ((juxt filter remove) plugin? dd)]
      (-> (dissoc project :dev-dependencies)
          (update-in [:plugins] (fnil into {}) plugins)
          (update-in [:profiles :dev] (fnil into {}) {})
          (update-in [:profiles :dev :dependencies] (fnil into {}) dev)))
    project))

(defn special-case-dev-deps [project]
  ;; TODO: handle swank-clojure -> lein-swank, etc
  project)

(defn min-lein-version [project]
  (assoc project :min-lein-version "2.0.0"))

(defn extra-classpath-dirs [project]
  (if-let [ecd (:extra-classpath-dirs project)]
    (-> (dissoc project :extra-classpath-dirs)
        (update-in [:profiles :dev] (fnil into {}) {})
        (update-in [:profiles :dev :resources-path] (fnil into []) ecd))
    project))

(defn repositories-format [project]
  (update-in project [:repositories] (partial into {})))

(defn dependencies-format [project]
  (update-in project [:dependencies] (partial into {})))

(def vec-paths [:source-path :java-source-path :test-path :resources-path])

(defn- vec-pathize [project key]
  (if-let [path (project key)]
    (assoc project key (vector (.replace path (:root project) "")))
    project))

(defn paths-as-vectors [project]
  (reduce vec-pathize project vec-paths))

(defn dissoc-empty [project key]
  (if (empty? (project key))
    (dissoc project key)
    project))

(defn dissoc-empty-keys [project]
  (-> project
      (dissoc-empty :plugins)
      (dissoc-empty :repositories)
      (dissoc-empty :dependencies)))

(defn suggest-project-map [project]
  (-> project
      tidy-project
      dev-dependencies
      special-case-dev-deps
      min-lein-version
      extra-classpath-dirs
      repositories-format
      dependencies-format
      dissoc-empty-keys
      paths-as-vectors))

(defn project-map-to-defproject [project]
  `(~'defproject ~(symbol (or (:group project)
                              (:name project))) ~(:version project)
     ~@(apply concat (dissoc project :name :group :version :root))))

(defn precate
  "Suggest a new project.clj that's compatible with Leiningen 2."
  [project]
  (when (.startsWith (System/getenv "LEIN_VERSION") "2")
    (println "This plugin is designed to be used with Leiningen 1.x."))
  (suggest-profiles)
  (pprint/with-pprint-dispatch pprint/code-dispatch ; kinda gross =\
    (pprint/pprint (project-map-to-defproject (suggest-project-map project))))
  (flush))