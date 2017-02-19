(ns permacode.core-test
  (:require [permacode.core :refer :all]
            [midje.sweet :refer :all]))

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

