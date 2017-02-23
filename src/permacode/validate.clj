(ns permacode.validate
  (:require [permacode.symbols :refer :all]
            [clojure.string :as str]))

(defmulti validate-ns-form (fn [[form & _] env]
                             form))

(defmethod validate-ns-form :require [[_ & ns-list] env]
  (let [env (set env)]
    (some identity (for [[ns-name & {:keys [as refer]}] ns-list]
                     (let [ns-name (name ns-name)
                           alias (if as
                                   (name as)
                                        ; else
                                   ns-name)]
                       (cond
                         (not (nil? refer))            (throw (Exception. ":refer is not allowed in ns expressions in permacode modules"))
                         (= ns-name "permacode.core")  (symbol alias)
                         (str/starts-with?
                          (str ns-name) "perm.")       nil
                         (not (env ns-name))           (throw (Exception. (str "Namespace " ns-name " is not approved for permacode")))))))))

(defn validate-ns [[ns' name & forms] env]
  (when-not (= ns' 'ns)
    (throw (Exception. (str "The first expression in a permacode source file must be an ns.  " ns' " given."))))
  (some identity (for [form forms]
                   (validate-ns-form form env))))

(defmulti validate-expr (fn [env expr] (first expr)))

(defmethod validate-expr :default [env expr]
  (let [expanded (macroexpand expr)]
    (if (= expanded expr)
      (throw (Exception. (str (first expr) " is not a valid top-level form.")))
      ; else
      (validate-expr env expanded))))


(defn validate-value [env expr]
  (let [s (symbols expr)
        forbidden (clojure.set/difference s env)]
    (when-not (empty? forbidden)
      (throw (Exception. (str "symbols " forbidden " are not allowed"))))))

(defmethod validate-expr 'def [env expr]
  (let [env (conj env (second expr))]
    (when (= (count expr) 3)
      (validate-value env (nth expr 2)))
    env))

(defmethod validate-expr 'do [env expr]
  (loop [env env
         exprs (rest expr)]
    (if (empty? exprs)
      env
      ; else
      (let [env (validate-expr env (first exprs))]
        (recur env (rest exprs))))))

(defmethod validate-expr 'defmacro [env expr]
  (let [[defmacro' name & body] expr])
  (validate-expr env (cons 'defn (rest expr))))
(defmethod validate-expr 'defmulti [env expr]
  (validate-expr env (concat ['def (second expr)] (vec (drop 2 expr)))))
(defmethod validate-expr 'defmethod [env expr]
  (let [[defmethod' multifn dispatch-val & fn-tail] expr]
    (validate-expr env `(defn ~multifn ~@(concat fn-tail [dispatch-val])))))
(defmethod validate-expr 'prefer-method [env expr]
  (validate-value env (vec (rest expr)))
  env)

(defmethod validate-expr nil [env expr]
  env)

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
    'defn 'defmacro 'fn 'for '-> '->>})

(def white-listed-ns
  #{"clojure.set" "clojure.string" "permacode.core"})

(defn symbols-for-namespaces [ns-map]
  (into #{} (for [[ns-name ns-val] ns-map
                  [member-name _] (ns-publics ns-val)]
              (symbol (str ns-name) (str member-name)))))

(def allowed-symbols
  (delay (let [entries (for [name white-listed-ns]
                         [name (symbol name)])
               ns-map (into {} entries)]
           (clojure.set/union core-white-list
                              (set (map #(symbol "clojure.core" (str %)) core-white-list))
                              (symbols-for-namespaces ns-map)))))

(def ^:dynamic *hasher* nil)

(defn perm-require [module & {:keys [as] :or {as nil}}]
  (when (= *hasher* nil)
    (throw (Exception. "When calling perm-require, the *hasher* variable must be bound")))
  (if (find-ns module)
    nil
    ; else
    (let [hash (-> module str (str/replace-first "perm." ""))
          old-ns (symbol (str *ns*))
          [hasher unhasher] *hasher*
          content (unhasher hash)
          [ns' name & clauses] (first content)]
      (validate-ns (first content) white-listed-ns)
      (try
        (in-ns module)
        (refer-clojure :only (vec core-white-list))
        (doseq [[req' & specs] clauses
                spec specs]
          (if (str/starts-with? (str (first spec)) "perm.")
            (apply perm-require spec)
            ; else
            (require (vec spec))))
        (eval (cons 'do (rest content)))
        (finally
          (in-ns old-ns)))))
  (when-not (nil? as)
    (alias as module)))