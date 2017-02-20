(ns permacode.core
  (:use [clojure.set :exclude [project]])
  (:require [permacode.validate :as validate]
            [permacode.symbols :refer :all]))

(defn ^:private create-bindings [syms env]
  (apply concat (for [sym syms
                      :when (empty? (namespace sym))] [sym `(~env '~sym)])))

(defn error [& args]
  (throw (Exception. (apply str args))))

(defn box [expr env]
  (let [syms (symbols expr)
        missing (difference syms (set (keys env)))]
    (if (empty? missing)
      ((eval `(fn [~'$env] (let [~@(create-bindings syms '$env)] ~expr))) env)
      (throw (Exception. (str "symbols " missing " are not defined in the environment"))))))

(declare update-env)

(defmulti ^:private apply-defs (fn [env form & args] form))
(defmethod apply-defs 'def
  ([env _ sym value] (assoc env sym (box value env))))
(defmethod apply-defs 'do [env _ & exprs]
  (if (empty? exprs)
    env
    (let [env (update-env (first exprs) env)]
      (apply apply-defs env 'do (rest exprs)))))

(defn update-env [expr env]
  (let [expr (macroexpand expr)]
    (apply apply-defs env expr)))

(defn ^:private conjunction [& funcs]
  (fn [x]
    (if (empty? funcs)
      true
      (and ((first funcs) x) ((apply conjunction (rest funcs)) x)))))

(defn add-ns
  ([ns env] (add-ns ns [] env))
  ([ns filters env]
   (let [filters (map (fn [filt] (comp filt first)) filters)
         filtered (filter (apply conjunction filters) (ns-publics ns))]
     (-> env
         (merge filtered)
         (merge (apply merge (map (fn [[key val]] {(symbol (name ns) (name key)) val}) filtered)))))))

(defn name-filter [re]
  (comp (partial re-matches re) name))

(defn name-filter-out [re]
  (complement (name-filter re)))

(def black-list [#"print.*" #".*!" #"ns-.*" #".*agent.*" #".*-ns" #".*atom.*"])

(def safe-filters
  (map (fn [re] (name-filter-out re)) black-list))

(def safe-env
  (->> {}
       (add-ns 'clojure.core safe-filters)
       (add-ns 'clojure.set)))

(def core-white-list
  #{'+ '- '* '/ '= '== '!= 'inc 'dec
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


