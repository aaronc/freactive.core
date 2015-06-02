(defproject freactive.core "0.2.0-SNAPSHOT"
  :description "Generic reactive atoms, expressions, cursors"
  :url "https://github.com/aaronc/freactive.core"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :profiles
  {:dev
   {:dependencies 
    [[org.clojure/clojure "1.7.0-RC1"]
     [org.clojure/clojurescript "0.0-3269"]]}}
  :source-paths ["src/clojure"]
  :javac-options ["-Xlint:unchecked"]
  :java-source-paths ["src/java"])
