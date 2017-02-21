(ns permacode.publish
  (:require [permacode.core :as core]
            [loom.graph :as graph]
            [loom.alg :as alg]
            [clojure.java.io :as io]))

(defn get-ns [file]
  (with-open [f (java.io.PushbackReader. (io/reader file))]
    (binding [*read-eval* false]
      (read f))))

(defn pair-fn [x y]
  (fn [u]
    [(x u) (y u)]))

(defn build-plan [seq]
  (let [ns-seq (map (pair-fn get-ns identity) seq)
        ns-to-file (into {} (for [name core/white-listed-ns]
                              [(symbol name) :external]))
        ns-to-file (into ns-to-file (for [[[ns' name & _] file] ns-seq]
                                      [name file]))
        _ (println ns-to-file)
        edges (for [[[ns' name & clauses] _] ns-seq
                    [require' [dep & _]] clauses]
                [dep name])
        graph (apply graph/digraph edges)
        sort (alg/topsort graph)]
    (doseq [item sort]
      (when-not (ns-to-file item)
        (throw (Exception. (str "Unmet dependency: " item)))))
    (into [] (comp (map ns-to-file)
                   (filter #(not= % :external))) sort)))
