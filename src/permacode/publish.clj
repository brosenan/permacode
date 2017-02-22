(ns permacode.publish
  (:require [permacode.validate :as validate]
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
        ns-to-file (into {} (for [name validate/white-listed-ns]
                              [(symbol name) :external]))
        ns-to-file (into ns-to-file (for [[[ns' name & _] file] ns-seq]
                                      [name file]))
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

(defn convert-dep [dep hashes]
  (if-let [hash (hashes dep)]
    (symbol (str "perm." hash))
    ; else
    dep))

(defn convert-clauses [clauses hashes]
  (for [[require' & specs] clauses
        [dep & opts] specs]
    [require' (concat [(convert-dep dep hashes)] opts)]))

(defn hash-file [[hash unhash] file hashes]
  (let [content (-> (str "[" (slurp file)  "]")
                    read-string)
        [[ns' name & clauses] & exprs] content]
    (hash (concat [(concat [ns' name] (convert-clauses clauses hashes))] exprs))))

(defn hash-all [hasher dir]
  (let [files (into [] (filter #(re-matches #".*\.clj" (str %))) (file-seq dir))
        plan (build-plan files)]
    (loop [hashes {}
           plan plan]
      (if (empty? plan)
        hashes
        ; else
        (let [current (first plan)
              [ns' name & clauses] (get-ns current)]
          (recur (assoc hashes name (hash-file hasher current hashes)) (rest plan)))))))
