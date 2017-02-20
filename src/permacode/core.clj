(ns permacode.core
  (:use [clojure.set :exclude [project]]))

(declare list-symbols)

(defmulti symbols class)
(derive Number ::primitive)
(derive String ::primitive)
(derive Boolean ::primitive)
(derive clojure.lang.Keyword ::primitive)

(defmethod symbols ::primitive [expr] #{})
(defmethod symbols nil [expr] #{})

(defmethod symbols clojure.lang.Symbol [symbol] #{symbol})

(defmethod symbols clojure.lang.ISeq [seq]
  (apply list-symbols seq))

(defmethod symbols clojure.lang.PersistentVector [vec]
  (if (empty? vec)
    #{}
    (union (symbols (first vec)) (symbols (rest vec)))))

(defmethod symbols clojure.lang.IPersistentMap [map]
  (union (symbols (keys map)) (symbols (vals map))))

(defmethod symbols clojure.lang.IPersistentSet [set]
  (symbols (seq set)))

(defmulti ^:private list-symbols (fn ([first & args] first)
                           ([] :function-call)) :default :function-call)

(defmethod list-symbols :function-call [& seq]
  (let [expanded (macroexpand seq)]
    (if (= expanded seq)
      (if (empty? seq)
        #{}
        (union (symbols (first seq)) (symbols (vec (rest seq)))))
      ; else
      (list-symbols expanded))))

(defn ^:private bindings-symbols [bindings syms]
  (if (empty? bindings)
    syms
    (let [[pattern val] (take 2 bindings)]
      (union (symbols val) (difference
                             (bindings-symbols (drop 2 bindings) syms)
                             (symbols pattern))))))

(defmethod list-symbols 'let* [_ bindings & expr]
  (bindings-symbols bindings (symbols expr)))

(defn ^:private fn-bindings [bindings]
  (if (empty? bindings)
    #{}
    (let [[args & body] (first bindings)]
      (union (difference (symbols body) (symbols args))
             (fn-bindings (rest bindings))))))

(defmethod list-symbols 'fn*
  ([_ & bindings]
   (if (symbol? (first bindings))
     (difference (fn-bindings (rest bindings)) #{(first bindings)})
     (fn-bindings bindings))))

(defmethod list-symbols 'def
  ([_ & args] (throw (Exception. "def is not allowed"))))

(defmethod list-symbols 'quote
  ([_ quoted] #{}))

(defmethod list-symbols 'if
  ([_ & args] (symbols args)))

(defmethod list-symbols 'do
  ([_ & args] (symbols args)))

(defmethod list-symbols 'var
  ([_ sym] (throw (Exception. "vars are not allowed"))))

(defmethod list-symbols 'loop*
  ([_ bindings & body] (bindings-symbols bindings (symbols body))))

(defmethod list-symbols 'recur
  ([_ & exprs] (symbols exprs)))

(defmethod list-symbols 'throw
  ([_ exception] (throw (Exception. "throw is not allowed. Use error instead"))))

(defmethod list-symbols 'try
  ([_ & body] (throw (Exception. "try/catch is not allowed"))))

(defmethod list-symbols 'for
  ([_ bindings body] (bindings-symbols bindings (symbols body))))


(defn ^:private create-bindings [syms env]
  (apply concat (for [sym syms
                      :when (empty? (namespace sym))] [sym `(~env '~sym)])))

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

(def black-list [#"print.*" #".*!" #"ns-.*" #".*agent.*" #".*-ns"])

(def safe-filters
  (map (fn [re] (name-filter-out re)) black-list))

(def safe-env
  (->> {}
       (add-ns 'clojure.core safe-filters)
       (add-ns 'clojure.set)))
