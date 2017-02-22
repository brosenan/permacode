(ns permacode.validate
  (:require [permacode.symbols :refer :all]
            [clojure.string :as str]))

(defmulti validate-ns-form (fn [[form & _] env]
                             form))

(defmethod validate-ns-form :require [[_ & ns-list] env]
  (apply merge (for [[ns-name & {:keys [as]}] ns-list]
                 (let [ns-name (name ns-name)
                       alias (if as
                               (name as)
                               ; else
                               ns-name)
                       ns-content (env ns-name)]
                   (if ns-content
                     {alias ns-content}
                     ; else
                     (if (str/starts-with? (str ns-name) "perm.")
                       {}
                       ; else
                       (throw (Exception. (str "Namespace " ns-name " is not approved for permacode")))))))))

(defn validate-ns [[ns' name & forms] global-env]
  (when-not (= ns' 'ns)
    (throw (Exception. (str "The first expression in a permacode source file must be an ns.  " ns' " given."))))
  (apply merge {"" (global-env "clojure.core")} (map #(validate-ns-form % global-env) forms)))

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
