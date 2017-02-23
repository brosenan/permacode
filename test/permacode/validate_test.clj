(ns permacode.validate-test
  (:require [permacode.core :refer :all]
            [permacode.validate :refer :all]
            [permacode.hasher :as hasher]
            [midje.sweet :refer :all]
            [clojure.string :as string]))

[[:chapter {:title "Introduction"}]]
"Permacode validation is intended to make sure that a give Clojure source file conforms to the
permacode sub-language."

"Validation is a two-step process.  Its first part is done on a source file that has been read somehow,
and makes sure that it has all the safeguards necessary for the second part to kick in.
The second part is done with the `pure` macro, at compile time.
It makes sure that only allowed language constructs are used."

[[:section {:title "Environment Representation"}]]
"The global environment is represented as a *map* where the keys are *names of namespaces* (strings)
and the values are *predicates* for symbols inside each namespace.
Such a predicate can be a Clojure set of specific names, or it can be a function that somehow determines
whether some symbol is inside the environment or not.  The function `(constantly true)` can be used
to indicate that all members of a namespace are game."

"Below is an example for a global environment definition."
(def global-env
  {"clojure.core" core-white-list
   "clojure.string" (constantly true)
   "clojure.set" (constantly true)
   "my.proj.core" (constantly true)
   "my.proj.other" (constantly true)})

[[:chapter {:title "validate-ns: Validating the ns Expression" :tag "validate-ns"}]]
"`validate-ns` takes a global environment and an `ns` expression.
For a trivial `ns` expression it returns a local environment containing only the default namespace."
(fact
 (validate-ns '(ns foo.bar) global-env) => {"" core-white-list})

"If simple `:require` blocks are present, the corresponding entries from the global environment are copied
to the local environment."
(fact
 (validate-ns '(ns foo.bar
                 (:require [clojure.string]
                          [clojure.set])
                 (:require [my.proj.core])) global-env)
 => {"" core-white-list
     "clojure.string" (global-env "clojure.string")
     "clojure.set" (global-env "clojure.set")
     "my.proj.core" (global-env "my.proj.core")})

"`:as` causes a `:require` to create a namespace with a different name in the local environment."
(fact
 (validate-ns '(ns foo.bar
                 (:require [clojure.string :as string])) global-env)
 => {"" core-white-list
     "string" (global-env "clojure.string")})

"If a `:require` refers to a namespace that is not in the environment, an exception is thrown."
(fact
 (validate-ns '(ns foo.bar
                 (:require [clojure.core.async])) global-env)
 => (throws #"Namespace clojure.core.async is not approved for permacode"))

"`validate-ns` also throws an exception if the expression it is given is not an `ns` expression."
(fact
 (validate-ns '(not-an-ns-expr foo.bar) global-env)
 => (throws #"The first expression in a permacode source file must be an ns.  not-an-ns-expr given."))

"Permacode modules can `:require` other permacode modules by naming them as `perm.*` where `*` is replaecd
with their hash code.
We consider `:require`ing such a module safe."
(fact
 (validate-ns '(ns foo.bar
                 (:require [perm.ABCD1234])) global-env)
 =not=> (throws))

[[:chapter {:title "validate-expr: Validate a Body Expression" :tag "validate-expr"}]]
"Given a set of allowed symbols we can validate a body  expression.
A declaration adds a symbol to the environment."
(fact
 (let [new-env (validate-expr #{'foo 'bar} '(def quux))]
   (new-env 'quux) => 'quux))

"The same works with definitions as well."
(fact
 (let [new-env (validate-expr #{'foo 'bar} '(def quux 123))]
   (new-env 'quux) => 'quux))

"The environment provided to the body contains the variable being `def`ed, so that recursive
definitions are possible."
(fact
  (validate-expr #{} '(def quux [1 quux]))
  => #{'quux})
"Macros are expanded.  For example. `defn` is interpreted as a `def` with `fn`."
(fact
 (let [new-env (validate-expr #{'foo 'bar} '(defn quux [x] x))]
   (new-env 'quux) => 'quux))

"When a form is not a valid top-level form, an exception is thrown."
(fact
 (validate-expr #{'foo 'bar} '(let [x 2] (+ x 3)))
 => (throws "let* is not a valid top-level form."))

"For a `do`, we recurse to validate the child forms."
(fact
 (validate-expr #{} '(do
                       (def foo 1)
                       (def bar 2)))
 => #{'foo 'bar})

"`defmacro`, `defmulti` and `defmethod` use Java interop when expanded.
We allow them in permacode, by making special cases out of them."
(fact
 (validate-expr #{'clojure.core/concat 'clojure.core/list 'clojure.core/seq} ; used by the syntax-quote
                '(defmacro foo [x y z] `(~x ~y ~z)))
 => #{'foo 'clojure.core/concat 'clojure.core/list 'clojure.core/seq}
 (validate-expr #{'first}
                '(defmulti bar first))
 => #{'first 'bar}
 (validate-expr #{}
                '(defmulti bar first))
 => (throws "symbols #{first} are not allowed")
 (validate-expr #{'+}
                '(defmethod bar :x [a b]
                   (+ a b)))
 => #{'+ 'bar}
 (validate-expr #{}
                '(defmethod bar c [a b]
                   (+ a b)))
 => (throws "symbols #{c +} are not allowed"))

"To support multimethods, `prefer-method` is allowed at the top-level."
(fact
 (validate-expr #{'foo 'a 'b}
                '(prefer-method foo a b)) => #{'foo 'a 'b}
  (validate-expr #{}
                '(prefer-method foo a b)) => (throws "symbols #{a foo b} are not allowed"))
[[:section {:title "Validating Values"}]]
"In definitions, the values are validated to only refer to allowed symbols."
(fact
 (validate-expr #{'foo} '(def bar (+ foo 2)))
 => (throws "symbols #{+} are not allowed")
 (validate-expr #{'foo '+} '(def bar (+ foo 2)))
 => #{'foo 'bar '+})

"In `do` blocks, symbols defined (or even declared) in previous definitions can be used in succeeding definitions."
(fact
 (validate-expr #{} '(do
                       (declare foo)
                       (def bar foo)))
 => #{'bar 'foo})

"Comments (that expand to `nil`) are allowed as top-level expressions."
(fact
 (validate-expr #{} '(comment
                       (foo bar)))
 => #{})
[[:chapter {:title "perm-require: Load a Hashed Namespace"}]]
"When we have a hash-code that represents a permacode namespace, we need to `perm-require` it so that it becomes available
for programs."

"The `perm-require` function takes a hash code and possibly an alias, and loads the module along with its dependencies,
similar to the `clojure.core/require` function."

"For `perm-require` to work, it needs a [hasher](hasher.html).
`perm-require` assumes a hasher is provided in the dynamic variable `*hasher*`."

(fact
 (perm-require 'perm.abcd) => (throws "When calling perm-require, the *hasher* variable must be bound"))

"If the hash's namespace is already loaded, we return."
(def hasher (hasher/nippy-multi-hasher (hasher/atom-store)))

(fact
 (binding [*hasher* hasher]
   (perm-require 'perm.abcd) => nil
   (provided
    (find-ns 'perm.abcd) => :something)))

"If the namespace if not loaded, the code is retrieved from the hasher."
(def hash-to-require (symbol (str "perm.abcd" (rand-int 10000))))
(def unhash-called (atom false))
(def mock-content '[(ns foo
                      (:require [permacode.core :as perm]))
                    (perm/pure
                     (defn f [x] (+ 1 x)))])
(defn mock-unhash [hash]
  (assert (= hash (-> hash-to-require str (string/replace-first "perm." ""))))
  (swap! unhash-called not)
  mock-content)

"After retrieving the code it is `eval`uated in the new namespace."
(fact
 (binding [*hasher* [nil mock-unhash]]
   (perm-require hash-to-require) => nil
   (provided
    (find-ns hash-to-require) => nil)
   @unhash-called => true
   (find-ns hash-to-require) =not=> nil?
   ((ns-aliases hash-to-require) 'perm) =not=> nil?
   (let [f @((ns-publics hash-to-require) 'f)]
     (f 2)) => 3))

"If the header requires a forbidden module, an exception is thrown."
(def mock-content '[(ns foo
                      (:require [clojure.java.io]))])
(def hash-to-require (symbol (str "abcd" (rand-int 10000)))) ; Fresh namespace
(fact
 (binding [*hasher* [nil mock-unhash]]
   (perm-require hash-to-require) => (throws "Namespace clojure.java.io is not approved for permacode")))

"When a module `:require`s some other permacode module, `perm-require` recursively loads it."
(def mock-content '[(ns foo
                      (:require [perm.FOOBAR1234]))])
(def hash-to-require (symbol (str "abcd" (rand-int 10000)))) ; Fresh namespace
(fact
 (binding [*hasher* [nil mock-unhash]]
   (perm-require hash-to-require) => nil
   (provided
    (find-ns hash-to-require) => nil
    (find-ns 'perm.FOOBAR1234) => :something)))

"`perm-require` accepts an optional `:as` keyword argument, and creates an alias accordingly."
(fact
 (let [alias (symbol (str "foobar" (rand-int 100000)))]
   (binding [*hasher* [nil mock-unhash]]
     (perm-require hash-to-require :as alias) => nil
     ((ns-aliases *ns*) alias) =not=> nil?)))
