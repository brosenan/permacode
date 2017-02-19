(ns permacode.validate)

(defmulti validate-ns-form (fn [[form & _] env]
                             form))

(defmethod validate-ns-form 'require [[_ & ns-list] env]
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
                     (throw (Exception. (str "Namespace " ns-name " is not approved for permacode"))))))))

(defn validate-ns [[ns' name & forms] global-env]
  (when-not (= ns' 'ns)
    (throw (Exception. (str "The first expression in a permacode source file must be an ns.  " ns' " given."))))
  (apply merge {"" (global-env "clojure.core")} (map #(validate-ns-form % global-env) forms)))
