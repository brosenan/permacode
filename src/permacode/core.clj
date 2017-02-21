(ns permacode.core
  (:use [clojure.set :exclude [project]])
  (:require [permacode.validate :as validate]
            [permacode.symbols :refer :all]))

(defn ^:private create-bindings [syms env]
  (apply concat (for [sym syms
                      :when (empty? (namespace sym))] [sym `(~env '~sym)])))

(defn error [& args]
  (throw (Exception. (apply str args))))

(defmacro pure [& defs]
  (let [allowed-symbols (clojure.set/union allowed-symbols
                                           (symbols-for-namespaces (ns-aliases *ns*)))
        res `(do ~@defs)]
    (validate/validate-expr allowed-symbols res)
    res))


