(ns permacode.core-test
  (:require [permacode.core :refer :all]
            [permacode.symbols :refer :all]
            [midje.sweet :refer :all])
  (:require [permacode.symbols :as pureclj]
            [clojure.core.logic :as logic]
            [clojure.set :as set]
            [clojure.string :as string]))

[[:chapter {:title "Introduction"}]]
"Just like the term *permalink* refers to a URL that will always link to the same content,
*permacode* refers to code that will always behave the same way.
More percisely, it refers to an *expression* that will always *evaluate to the same value*."

"In some cases the is easier to achieve than in other cases.
For example, the expression `3.14` will probably, in almost any programming language, mean
a number greater than 3 and a little smaller than the ratio between the circumference of a
circle and its diameter.  However, the expression `pi` is not that lucky, as it gets different
meanings in different contexts."

"Making permacode possible in the context of Clojure is a two-step process.
First, we need to extract the *purely-functional core* of Clojure, since only
purely-functional languages can guarantee behaving the same way regardless of
their surroundings.
Then we need to add *content addressing* to replace Clojure's module system."

[[:section {:title "A Purely-Functional Clojure"}]]
"permacode attempts to create a purely-functional dialect of Clojure.
This is a stripped-down Clojure, with only the purely-functional parts, including:
- Functions (well duh...)
- `let`, `loop`, `if` etc.
- maps, sets, vectors and lists
- A subset of the `clojure.core` library functions and macros, as well as
- `clojure.string`, `clojure.set` and potentially other pure libraries.

It does not include:
- Variables
- Atoms, refs, agents, etc.
- Java interop
- Imperative functions from `clojure.core`
- Arbitrary (non-permacode) libraries"

[[:section {:title "Content Addressing"}]]
"The term *content addressing* means that some objects (in our case, permacode modules)
are *addressed* by their *content*, as opposed to addressing them by *name*, which is
the way Clojure works by default."

"In Clojure, a `:require` clause states a name of a module, relative to the classpath
of the containing project.  This has two problems as far as permacode is concerned.
First, it relies on the concept of a *project*, so in two different projects there
could be a different module with the same name.
Second, while a name is stated explicitly, the *version* is not.
It is stated in the project description, but that could just as well be a `SNAPSHOT`
version, which is not a version at all."

"In Permacode, we would like code artifacts to be explicitly stated.
Content addressing allows us to do that, because instead of referring to modules
by their names, we refer to them by their *content*."

"Someone reading these lines could think, what is the point of having modules in the first
place, if we refer to them by their content, that  is, including them instead if `:require`ing them?
Well, the answer is that we do not *include* one module in another, we just use a *cryptographic hash*
over its content as its name.
This is similar to how git represents objects like directories and commits.
A directory in git is merely a list of hash codes for versions of underlying files and sub-dirs.
A commit contains the hash code of the root directory at the time of the commit.
This data structure is called a [Merkle Tree](https://en.wikipedia.org/wiki/Merkle_tree)."

"To support this, we use an abstraction of a *hasher*, some data-store that can either *hash*
an s-expression (serialize, hash and store its content, returning the hash code), and
*unhash* a hash-code (retrieve from the data-store, de-serialize and return the s-expression)."

"With such a hasher in place we can:
1. Given a set of Clojure files (typically the source tree of a Clojure project),
  - Validate that all *.clj files conform with permacode, and if so --
  - Hash all these files, modifying them to reference each other by hash rather than by name.
  - Return a map from module name to module hash.
2. Given a module hash, return the module.
3. Given an s-expression containing fully-qualified names, evaluate the expression."

[[:chapter {:title "symbols: Extract symbols from expressions"}]]
"The `symbols` function is the key in our static analysis.  It takes a s-expression,
and returns a set of symbols defined by this s-expression."

"For Constants, it returns an empty set."
(fact 
 (symbols 2) => #{}
 (symbols true) => #{}
 (symbols "foo") => #{}
 (symbols :foo) => #{}
 (symbols nil) => #{})

"For a symbol, `symbols` returns a set with that symbol."
(fact
 (symbols 'x) => #{'x})
"`symbols` goes into Clojure structures and aggergates symbols from there."
(fact
 (symbols '(+ a b)) => #{'+ 'a 'b}
 (symbols '[1 a 2 b]) => #{'a 'b}
 (symbols {'x 'y}) => #{'x 'y}
 (symbols #{'a 'b 3 4 5}) => #{'a 'b})

"In a `let*` special form, the bindings are removed from the returned set."
(fact
 (symbols '(let* [x 1 y 2] (+ x y))) => #{'+}
 (symbols '(let* [x a] x)) => #{'a})

"`symbols` expands macros, so it also handles the more familiar `let` form"
(fact
 (symbols '(let [x a] x a)) => #{'a} )

"`loop` is supported similarly."
(fact
 (symbols '(loop [x a y b] a x b y c)) => #{'a 'b 'c} )

"In the `fn*` special form (used by the `fn` macro) `symbols` removes the argument names from the set."
(fact
 (symbols '(fn [x y] (+ x y a))) => #{'a '+}
 (symbols '(fn foo [x y]  (+ x (foo y a)))) => #{'a '+})

"The function name (in named functions) is also removed."
(fact
 (symbols '(fn foo [x] (foo x))) => #{} )

"Multi-arity functions are supported as well."
(fact
 (symbols '(fn ([x] (+ a x))
             ([x y] (+ b x y)))) => #{'a 'b '+} )
"`def` is not allowed inside an expression (it is allowed on the top-level, as described below)."
(fact
 (symbols '(def x 2)) => (throws "def is not allowed") )

"Similarly, reference to variables is not allowed."
(fact
 (symbols '#'var) => (throws "vars are not allowed") )

"Quoted symbols are ignored."
(fact
 (symbols ''(a b c)) => #{} )

"In special forms such as `if` and `do`, the form's name is ignored."
(fact
 (symbols '(if a b c)) => #{'a 'b 'c}
 (symbols '(do a b c)) => #{'a 'b 'c}
 (symbols '(recur a b)) => #{'a 'b})

"Exceptions are not supported because they use Java interop."
(fact
 (symbols '(throw foo)) => (throws Exception "throw is not allowed. Use error instead")
 (symbols '(try foo bar baz)) => (throws "try/catch is not allowed"))

"While `for` is a macro and not a special form, its definition makes use of Java interop, which we disallow.
We therefore make `for` a special case."
(fact
 (symbols '(for [x foo] (* x 2))) => #{'foo '*})

[[:chapter {:title "pure: Compile-time Verification"}]]
"A permacode module should be wrapped in a `permacode.core/pure` macro.
This macro validates the underlying code under the module's own namespace.
Validation involves macro expansion, and macros are expanded based on how they are defined
relative to that module."

"A valid set of definitions evaluates to a `do` block with the same content."
(fact
 (pure
  (defn foo [x y]
    (+ x y))
  (defn bar [x y]
    (* x y)))
 =expands-to=> (do
                 (defn foo [x y]
                   (+ x y))
                 (defn bar [x y]
                   (* x y))))

"For definitions that use disallowed symbols `pure` throws an exception during macro expansion."
(fact
 (macroexpand '(pure
                (def some-atom (atom 0))))
 => (throws "symbols #{atom} are not allowed"))

"Allowed symbols should be available unqualified or fully-qualified."
(fact
 (pure
  (def x (+ 1 2))
  (def y (clojure.core/+ 2 3)))
 =expands-to=> (do
                 (def x (+ 1 2))
                 (def y (clojure.core/+ 2 3))))

"Some namespaces, such as `clojure.string` and `clojure.set` are white-listed, and all their publics are allowed."
(fact
 (pure
  (def x (clojure.string/join ", " (clojure.set/union #{1 2} #{3})))))

"When namespaces are aliased (like `clojure.string` is aliased to `string` in this file),
symbols referring to the alias are also allowed."
(fact
 (pure
  (def x (string/join ", " (set/union #{1 2} #{3})))))

[[:section {:title "Stress Testing"}]]
"The following is a usage example coming from [cloudlog.clj](https://brosenan.github.io/cloudlog.clj/core.html).
It is supposed to be all pure, so it's a good test case..."

(pure

 (declare generate-rule-func)

 (defmulti propagate-symbols (fn [cond symbols] (first cond)) :default :no-bindings)
 (defmethod propagate-symbols :no-bindings [cond symbols]
   symbols)

 (defn binding-symbols [bindings cond]
   (pureclj/symbols (map bindings (range 0 (count bindings) 2))))

 (defmethod propagate-symbols 'let [cond symbols]
   (set/union symbols (binding-symbols (second cond) cond)))

 (defmethod propagate-symbols 'for [cond symbols]
   (set/union symbols (binding-symbols (second cond) cond)))

 (defmulti process-conds (fn [conds symbols] (str (class (first conds)))))

                                        ; fact
 (defmethod process-conds  "class clojure.lang.IPersistentVector" [conds symbols]
   (let [target (first conds)
         target-name (first target)]
     (if (= (count conds) 1)
       (do ; Target fact
         [[(vec (rest target))] {:target-fact [target-name (count (rest target))]}])
                                        ; Continuation
       (let [[func meta] (generate-rule-func (first conds) (rest conds) symbols)
             key (second target)
             params (vec (set/intersection symbols (pureclj/symbols func)))
             missing (set/difference (pureclj/symbols key) symbols)
             meta {:continuation (with-meta `(fn [[~'$key$ ~@params]] ~func) meta)}]
         (when-not (empty? missing)
           (permacode.core/error "variables " missing " are unbound in the key for " (first target)))
         [`[[~key ~@params]] meta]))))

                                        ; guard
 (defmethod process-conds  "class clojure.lang.ISeq" [conds symbols]
   (let [cond (first conds)
         [body meta] (process-conds (rest conds) (propagate-symbols cond symbols))
         body (seq (concat cond [body]))
         meta (if (string/starts-with? (str (first cond)) "by")
                (assoc meta :checked true)
                meta)]
     (if (= (first cond) 'for)
       [`(apply concat ~body) meta]
                                        ; else
       [body meta])))

 (defmacro norm-run* [vars goal]
   (let [run `(logic/run* ~vars ~goal)]
     (if (= (count vars) 1)
       `(let [~'$res$ ~run]
          (if (empty? ~'$res$)
            nil
            [~'$res$]))
       run)))

 (defn generate-rule-func [source-fact conds ext-symbols]
   (let [symbols (set/difference (pureclj/symbols (rest source-fact)) ext-symbols)
         [body meta] (process-conds conds (set/union symbols ext-symbols))
         meta (merge meta {:source-fact [(first source-fact) (count (rest source-fact))]})
         vars (vec symbols)
         func `(fn [~'$input$]
                 ~(if (empty? vars)
                                        ; No unbound variables
                    `(if (= ~'$input$ [~@(rest source-fact)])
                       ~body
                       [])
                                        ; vars contains the unbound variables
                    `(let [~'$poss$ (norm-run* ~vars
                                               (logic/== ~'$input$ [~@(rest source-fact)]))]
                       (apply concat (for [~vars ~'$poss$] 
                                       ~body)))))]
     [func meta]))

 (defn validate-rule [metadata]
   (loop [metadata metadata
          link 0]
     (when-not (:checked metadata)
       (permacode.core/error "Rule is insecure. Link " link " is not checked."))
     (when (:continuation metadata)
       (recur (-> metadata :continuation meta) (inc link)))))

 (defmacro defrule [rulename args source-fact & body]
   (let [conds (concat body [`[~(keyword (str *ns*) (name rulename)) ~@args]])
         [func meta] (generate-rule-func source-fact conds #{})]
     (validate-rule meta)
     `(def ~rulename (with-meta ~func ~(merge meta {:ns *ns* :name (str rulename)})))))

 (defn append-to-keyword [keywd suffix]
   (keyword (namespace keywd) (str (name keywd) suffix)))

 (defmacro defclause [clausename pred args-in args-out & body]
   (let [source-fact `[~(append-to-keyword pred "?") ~'$unique$ ~@args-in]
         conds (concat body [`[~(append-to-keyword pred "!") ~'$unique$ ~@args-out]])
         [func meta] (generate-rule-func source-fact conds #{})]
     `(def ~clausename (with-meta ~func ~(merge meta {:ns *ns* :name (str clausename)})))))

 (defn with* [seq]
   (apply merge-with set/union
          (for [fact seq]
            (let [fact-name (first fact)
                  metadata (meta fact)
                  arity (-> fact rest count)]
              {[fact-name arity] #{(with-meta (vec (rest fact)) metadata)}}))))

 (defn simulate* [rule factmap]
   (let [source-fact (-> rule meta :source-fact)
         input-set (factmap source-fact)
         after-first (into #{} (apply concat (map rule input-set)))
         cont (-> rule meta :continuation)]
     (if cont
       (let [next-rules (map cont after-first)]
         (into #{} (apply concat (for [next-rule next-rules]
                                   (simulate* (with-meta next-rule (meta cont)) factmap)))))
                                        ;else
       after-first)))

 (defn simulate-with [rule & facts]
   (simulate* rule (with* facts)))

 (defmulti fact-table (fn [[name arity]] (str (class name))))

 (defmethod fact-table "class clojure.lang.Named" [[name arity]]
   (str (namespace name) "/" (clojure.core/name name)))
 (defmethod fact-table "class clojure.lang.IFn" [[name arity]]
   (let [ns (-> name meta :ns)
         name (-> name meta :name)]
     (str ns "/" name)))
 (prefer-method fact-table "class clojure.lang.Named" "class clojure.lang.IFn")

 (defmacro by [set body]
   `(when (contains? (-> ~'$input$ meta :writers) ~set)
      ~body))

 (defmacro by-anyone [body]
   body))

[[:chapter {:title "error: A Replacement for throw"}]]
"We do not allow pure code to `throw`, because throwing exceptions involves creating Java classes.
Instead, we provide the `error` function, which throws an `Exception` with the given string."
(fact
 (error 1000 " bottles of beer on the wall") => (throws #"1000 bottles of beer"))

[[:chapter {:title "box: Evaluate expressions through an environment" :tag "box"}]]
"should return a constant for a constant"
(fact
 (box 3.14 {}) => 3.14)
"should throw an exception if a symbol that exists in expr is not a key in env"
(fact
 (box 'x {}) => (throws "symbols #{x} are not defined in the environment") )
"should assign symbols their value in the environment"
(fact
 (box '(+ x 2) {'x 3 '+ *}) => 6 )
"should work with functions already defined in the environment"
(fact
 (let [env (update-env '(defn foo [x] (inc x)) {'inc inc})]
   (box '(foo 2) env)) => 3 )
"should work with qualified symbols as long as they are in env"
(fact
 (box '(clojure.core/inc 2) (add-ns 'clojure.core {})) => 3 )


[[:chapter "update-env: Update an environment based on defs" :tag "update-env"]]
"should add a key to env of a def form"
(fact
 (update-env '(def y (inc x)) {'x 2 'inc inc}) => {'x 2 'y 3 'inc inc} )
"should apply macros"
(fact
 (let [env (update-env '(defn foo [x] (inc x)) {'inc inc})
       f (env 'foo)]
   (f 1)) => 2 )
"should apply all defs in a do block"
(fact
 (update-env '(do (def x 1) (def y 2)) {}) => {'x 1 'y 2} )

[[:chapter {:title "add-ns: Import a namespace to an environment" :tag "add-ns"}]]

"should extend env"
(fact
 (add-ns 'clojure.core {'x 3}) => #(contains? % 'x))
"should add all publics from ns"
(fact
 (add-ns 'clojure.core {'x 3}) => #(contains? % '+))
"should apply filters if supplied"
(fact
 (add-ns 'clojure.core [(name-filter-out #".*!")] {}) => #(not (contains? % 'swap!)))
"should apply multiple filters if supplied"
(fact
 (add-ns 'clojure.core [(name-filter-out #".*!") (name-filter-out #"print.*")] {}) => #(not (contains? % 'println)))
"should return an env containing functions"
(fact
 (let [env (add-ns 'clojure.core {})
       f (env 'inc)]
   (f 2)) => 3 )
"should also add the fully-qualified names to env"
(fact
 (add-ns 'clojure.core {}) => #(contains? % 'clojure.core/+))

[[:chapter {:title "name-filter"}]]
"should return true for env entries that  match the patter"
(fact
 (filter (name-filter #"x+") ['x 'xx 'xy 'yy]) => '(x xx) )

[[:chapter {:title "name-filter-out"}]]
"should return true for env entries that  match the patter"
(fact
 (filter (name-filter-out #"x+") ['x 'xx 'xy 'yy]) => '(xy yy) )
