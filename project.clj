(defproject permacode "0.1.0-SNAPSHOT"
  :description "A pure dialect of Clojure with content-addressed modules"
  :url "http://github.com/brosenan/permacode"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.logic "0.8.11"]
                 [mvxcvi/multihash "2.0.1"]
                 [com.taoensso/nippy "2.13.0"]]
  :profiles {:dev {:dependencies [[midje "1.8.3"]
                                  [im.chit/lucid.publish "1.2.8"]
                                  [im.chit/hara.string.prose "2.4.8"]]
                   :plugins [[lein-midje "3.2.1"]]}}
  :publish {:theme  "bolton"
            :template {:site   "permacode"
                       :author "Boaz Rosenan"
                       :email  "brosenan@gmail.com"
                       :url "https://github.com/brosenan/permacode"}
            :output "docs"
            :files {"core"
                    {:input "test/permacode/core_test.clj"
                     :title "core"
                     :subtitle "Subsetting Clojure"}
                    "hasher"
                    {:input "test/permacode/hasher_test.clj"
                     :title "core"
                     :subtitle "Content Addressable Storage"}}})
