(ns permacode.hasher
  (:require [taoensso.nippy :as nippy]
            [multihash.digest :as digest]
            [multihash.core :as multihash]))

(defn hasher-pair [[ser deser] hash [store retr]]
  [(fn [expr]
     (let [s (ser expr)
           h (hash s)]
       (store h s)
       h))
   (fn [hashcode]
     (-> hashcode retr deser))])

(defn nippy-multi-hasher [storage]
  (let [hash (fn [bin]
               (-> bin digest/sha2-256 multihash/base58))]
    (hasher-pair [nippy/freeze nippy/thaw] hash storage)))
