(ns permacode.core
  (:use [clojure.set :exclude [project]])
  (:require [permacode.validate :as validate]
            [permacode.symbols :refer :all]))

(defn ^:private create-bindings [syms env]
  (apply concat (for [sym syms
                      :when (empty? (namespace sym))] [sym `(~env '~sym)])))

(defn error [& args]
  (throw (Exception. (apply str args))))

(def core-white-list
  #{'+ '- '* '/ '= '== 'not= 'inc 'dec
    'map 'filter 'reduce 'into
    'count 'range 'apply 'concat
    'first 'second 'nth 'rest
    'class 'name
    'list 'seq 'vector 'vec 'str 'keyword 'namespace
    'empty?
    'meta 'with-meta
    'assoc 'assoc-in 'merge 'merge-with
    '*ns* ; TBD
    })

(def white-listed-ns
  #{"clojure.set" "clojure.string" "permacode.core"})

(defn symbols-for-namespaces [ns-map]
  (into #{} (for [[ns-name ns-val] ns-map
                  [member-name _] (ns-publics ns-val)]
              (symbol (str ns-name) (str member-name)))))

(def allowed-symbols
  (let [entries (for [name white-listed-ns]
                  [name (symbol name)])
        ns-map (into {} entries)]
    (clojure.set/union core-white-list
                       (set (map #(symbol "clojure.core" (str %)) core-white-list))
                       (symbols-for-namespaces ns-map))))

(defmacro pure [& defs]
  (let [allowed-symbols (clojure.set/union allowed-symbols
                                           (symbols-for-namespaces (ns-aliases *ns*)))
        res `(do ~@defs)]
    (validate/validate-expr allowed-symbols res)
    res))


