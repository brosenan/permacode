(ns permacode.core
  (:use [clojure.set :exclude [project]])
  (:require [permacode.symbols :refer :all]
            [permacode.validate :as validate]
            [clojure.string :as str]))

(defn ^:private create-bindings [syms env]
  (apply concat (for [sym syms
                      :when (empty? (namespace sym))] [sym `(~env '~sym)])))

(defn error [& args]
  (throw (Exception. (apply str args))))

(defmacro pure [& defs]
  (let [allowed-symbols (clojure.set/union @validate/allowed-symbols
                                           (validate/symbols-for-namespaces (ns-aliases *ns*)))
        res `(do ~@defs)]
    (validate/validate-expr allowed-symbols res)
    res))


