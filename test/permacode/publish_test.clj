(ns permacode.publish-test
  (:require [permacode.publish :refer :all]
            [clojure.java.io :as io]
            [midje.sweet :refer :all]))

[[:chapter {:title "build-plan: Sorts Files by Dependencies" :tag "build-plan"}]]
"For the examples in this section we create a few dummy source files using this function:"
(defn create-example-file [name content]
  (with-open [file (io/writer name)]
    (doseq [expr content]
      (.write file (pr-str expr)))
    (io/file name)))

(def example-dir "/tmp/permacode.publish/example")
(.mkdirs (io/file example-dir))
(def foo (create-example-file (str example-dir "/foo.clj")
                              '[(ns example.foo)
                                (some-thing)]))

(def bar (create-example-file (str example-dir "/bar.clj")
                              '[(ns example.bar
                                  (:require [example.foo]))
                                (some-thing-else)]))

(def baz (create-example-file (str example-dir "/baz.clj")
                              '[(ns example.baz
                                  (:require [example.bar]))
                                (some-thing-else2)]))

"Given these files, `build-plan` should return these files in order of their dependencies."
(fact
 (build-plan [baz foo bar]) => [foo bar baz])

"In case of an unmet dependency, a proper error message is presented."
(fact
 (build-plan [bar baz]) => (throws "Unmet dependency: example.foo"))

"Supported namespaces (`permacode.core/white-listed-ns`) are ignored."
(def foo2 (create-example-file (str example-dir "/foo2.clj")
                               '[(ns example.foo2
                                   (:require [clojure.string :as str]))
                                 (some-thing)]))
(fact
 (build-plan [foo2]) => [foo2])

[[:section {:title "Under the Hood"}]]
"The helper function `get-ns` takes a file object and returns its first expression.
For example:"
(fact
 (get-ns bar) => '(ns example.bar
                    (:require [example.foo])))

[[:chapter {:title "hash-file: Hash a Source File" :tag "hash-file"}]]
"Now that we have our sorted list of files we can go one-by-one and "
