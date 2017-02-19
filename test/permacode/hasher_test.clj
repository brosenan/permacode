(ns permacode.hasher-test
  (:require [permacode.hasher :refer :all]
            [midje.sweet :refer :all]
            [taoensso.nippy :as nippy]
            [multihash.digest :as digest]
            [multihash.core :as multihash]))

[[:chapter {:title "Introduction"}]]
"A *hasher-pair* is a pair `[hasher, unhasher]` of functions, where `hasher` is a functions that converts
s-expressions into a hash-code, and `unhasher` is a function that converts hash codes back into s-expressions."

"For that to work, the `hasher` function needs to store the s-expression as a side-effect, so that the
`unhasher` function would be able to retrieve it."

"Three components are at play when hashing and unhashing expressions:
1. A serializer/deserializer pair.  Standard choices for Clojure can be [EDN](https://github.com/edn-format/edn) or [Nippy](https://github.com/ptaoussanis/nippy).
2. A hash function.  The [multihash](https://github.com/multiformats/clj-multihash) project gives a versatile and compatible solution.
3. Persistent storage.  A real-life application will probably store hashed expressions to some networked storage like Amazon's S3.  We provide here storage that uses local files."

[[:chapter {:title "hasher-pair: Construct a Hasher Pair" :tag "hasher-pair"}]]
"To construct a hasher-pair, one must supply:
1. A serializer/deserializer pair
2. A hash function (binary array -> hash string)
3. A store/retrieve pair."

"For the examples below we use the following mock functions:"
(defn mock-ser [expr]
  [:ser expr])
(defn mock-deser [bin]
  [:deser bin])
(def last-stored (atom nil))
(defn mock-store [hash bin]
  (reset! last-stored [hash bin]))
(defn mock-retr [hash]
  [:retr hash])
(defn mock-hash [bin]
  [:hash bin])

(def hpair (hasher-pair [mock-ser mock-deser] mock-hash [mock-store mock-retr]))
(fact
 hpair => vector?)

"The `hasher` serializes the s-expression and then calls the hash function on it"
(fact
 (let [hasher (first hpair)]
   (hasher 123) => [:hash [:ser 123]]))

"As side-effect, hashing stores the serialized content under the hash."
(fact
 @last-stored => [[:hash [:ser 123]] [:ser 123]])

"The `unhasher` retrieves the content by its hash an de-serializes it."
(fact
 (let [unhasher (second hpair)]
   (unhasher "abc") => [:deser [:retr "abc"]]))

[[:chapter {:title "nippy-multi-hasher: Hasher based on `multihash` and `nippy`" :tag "nippy-multi-hasher"}]]
"To fix some of the variables and create a *standard* way to hash expressions we use an [ipfs](https://ipfs.io/)-compatible
has (using the [multihash](https://github.com/multiformats/clj-multihash) project), and
[nippy](https://github.com/ptaoussanis/nippy)-based serialization."
(fact
 (let [[hasher unhasher] (nippy-multi-hasher [mock-store mock-retr])]
   (hasher ..expr..) => ..hashcode..
   (provided
    (nippy/freeze ..expr.. nil) => ..bin..
    (digest/sha2-256 ..bin..) => ..mhash..
    (multihash/base58 ..mhash..) => ..hashcode..)
   @last-stored => [..hashcode.. ..bin..]
   (unhasher ..hashcode..) => ..expr..
   (provided
    (nippy/thaw [:retr ..hashcode..] nil) => ..expr..)))

[[:chapter {:title "file-store: Store content into local files" :tag "file-store"}]]
