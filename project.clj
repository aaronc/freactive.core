(defproject freactive.core "0.2.0-alpha1"
  :description "Generic reactive atoms, expressions, cursors"
  :url "https://github.com/aaronc/freactive.core"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies
  [[org.clojure/clojure "1.7.0-alpha3"]]
  :profiles
  {:dev
    {:plugins [[com.cemerick/austin "0.1.5"]]
     :dependencies 
     [[org.clojure/clojurescript "0.0-2371"]
      [com.cemerick/clojurescript.test "0.3.1"]]}}
  :source-paths ["src-clj" "src-cljs"]
  :javac-options ["-Xlint:unchecked"]
  :java-source-paths ["src-java"])
