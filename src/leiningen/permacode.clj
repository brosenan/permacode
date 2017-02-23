(ns leiningen.permacode
  (:require [clojure.java.io :as io]
            [permacode.hasher :as hasher]
            [permacode.publish :as publish]))

(defn get-hasher [project]
  (let [repo (or (:permacode-repo project)
                 (str (System/getProperty "user.home") "/.permacode"))]
    (.mkdirs (io/file repo))
    (hasher/nippy-multi-hasher (hasher/file-store repo))))

(defn publish
  "Publish the current version of the project as permacode"
  [project args]
  (doseq [dir (:source-paths project)]
    (publish/hash-all (get-hasher project) (io/file dir))))

(defn deps 
  "Retrieve all permacode dependencies for this project"
  [project args]
  (println "Deps..."))

(defn permacode
  "Share and use pure functional code"
  {:subtasks [#'publish #'deps]}
  [project & [sub-task & args]]
  (case sub-task
    "publish" (publish project args)
    "deps" (deps project args)
    (println "A valid task name must be specified.  See lein help permacode")))
