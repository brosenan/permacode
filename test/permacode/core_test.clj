(ns permacode.core-test
  (:require [permacode.core :refer :all]
            [midje.sweet :refer :all]))

[[:chapter {:title "Introduction"}]]
"Like other LISPs, Clojure is an imperative functional programming language.
In many ways, Clojure has made strides towards 'purity', such as intensive use
of purely-functional data structures, but in other areas, such as Java interop,
Clojure is as imperative as Java.
While imperative programming is commonplace, with only few esoteric programming languages
taking the purely-declarative path, it has some disadvantages."

"permacode is an attempt to create a purely-functional dialect of Clojure.
This is a stripped-down Clojure, with only the purely-functional parts, including:
- Functions (well dah...)
- `let`, `loop`, `if` etc.
- maps, sets, vectors and lists
- A subset of the `clojure.core` library functions and macros, as well as
- `clojure.string`, `clojure.set` and potentially other pure libraries.

It does not include:
- Variables
- Atoms, refs, agents, etc.
- Java interop
- Imperative functions from `clojure.core`"

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
 (symbols '(fn foo [x y] (+ x y a))) => #{'a '+})

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

