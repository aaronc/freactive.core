(defproject freactive.core "0.2.1-SNAPSHOT"
  :description "Generic reactive atoms, expressions, cursors"
  :url "https://github.com/aaronc/freactive.core"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies
  [[org.clojure/clojure "1.9.0"]
   [org.clojure/data.avl "0.0.17"]]
  :profiles
  {:dev
   {:dependencies 
    [[org.clojure/clojurescript "1.10.238"]]}}
  :source-paths ["src/clojure"]
  :javac-options ["-Xlint:unchecked"]
  :java-source-paths ["src/java"])
