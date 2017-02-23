(defproject permacode "0.1.0-SNAPSHOT"
  :description "A pure dialect of Clojure with content-addressed modules"
  :url "http://github.com/brosenan/permacode"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [mvxcvi/multihash "2.0.1"]
                 [com.taoensso/nippy "2.13.0"]
                 [aysylu/loom "1.0.0"]]
  :profiles {:dev {:dependencies [[midje "1.8.3"]
                                  [org.clojure/core.logic "0.8.11"]
                                  [im.chit/lucid.publish "1.2.8"]
                                  [im.chit/hara.string.prose "2.4.8"]]
                   :plugins [[lein-midje "3.2.1"]
                             [lein-pprint "1.1.2"]
                             [permacode/permacode "0.1.0-SNAPSHOT"]]}}
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
                     :title "hasher"
                     :subtitle "Content Addressable Storage"}
                    "validate"
                    {:input "test/permacode/validate_test.clj"
                     :title "validate"
                     :subtitle "Static Analysis"}
                    "symbols"
                    {:input "test/permacode/symbols_test.clj"
                     :title "symbols"
                     :subtitle "Extracting Symbols Used by Expressions"}
                    "publish"
                    {:input "test/permacode/publish_test.clj"
                     :title "publish"
                     :subtitle "Store Local Code and Get Hashes"}}})
