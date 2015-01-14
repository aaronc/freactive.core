# freactive.core

Reactive atoms, expressions, cursors for Clojure(Script).

**This library should be considered pre-alpha.** Although it is approaching a more stable release, there is still a fair amount of flux internally. Currently the Clojure (JVM) and ClojureScript implementations are more or less in line. These data structurs can currently be used with [freactive](https://github.com/aaronc/freactive) (a Clojurescript DOM library) and [fx-clj](https://github.com/aaronc/freactive) (a Clojure library for JavaFX).

The goal of this library is to abstract the reactive atom and reactive computation
or computed observable concepts from [reagent][reagent] and [reflex][reflex] and many others, as
well as a generalization of the cursor concept from [om][om]. The broader aim of this
goal is to enable a set of reactive data structures that can be shared between many
different front-end frameworks that is also based, as much as possible, only on idioms
already in Clojure core.

The data structures in this library aim to be:
* **Generalized** - not tied to a specific problem domain or framework
* **Idiomatic** - trying to follow and use the conventions of Clojure core
* **Efficient** - not cause much more performance overhead than doing updates manually (possibly less in some cases)
* **Predictable** - have predictable effects based on inputs
* **Configurable** - in terms of laziness vs eagerness
* **Disposable** - implement simple patterns that allow trees of reactive updates to be deactivated almost automatically
* **Debuggable** - allow for easy ways to observe dependency capture and invalidation events

## Core Data Structures

### Reactive Atoms

A reactive atom is exactly like a Clojure atom except that it allows a parent computation to register it as a dependency. It is a construct that has been described previously in [reagent][reagent] and [reflex][reflex]. The implementation here is slightly different, but the concept is the same.

### Reactive Expressions

A reactive expression, or `rx` for short, is the same concept as `computed-observable` in [reflex][reflex] and `reaction` in [reagent][reagent] (although it is currently undocumented there).

Basically it is an observable reference value that attempts to capture its dependencies (such as reactive atoms, cursors and other reactive expressions). It will become dirty if a dependency changes and it will notify its change listeners that it is dirty. The next time it is `deref`'ed its value is recomputed based on the innitial expression that was passed in.

### Reactive Cursors

`cursor`'s in freactive.core behave and look exactly like `atom`'s. You can use Clojurescript's built-in `swap!` and `reset!` functions on them and state will be propogated back to their parents. By default, change notifications from the parent propagate to the cursor when and only when they affect the state of the cursor.

Fundamentally, cursors are based on [lenses](https://speakerdeck.com/markhibberd/lens-from-the-ground-up-in-clojure). That means that you can pass any arbitrary getter (of the form `(fn [parent-state])`) and setter (of the form `(fn [parent-state cursor-state])`) and the cursor will handle it.

```clojure
(def my-atom (atom 0))
(defn print-number [my-atom-state]
  ;; print the number with some formmating
)
(defn parse-number [my-atom-state new-cursor-state]
  ;; parse new-cursor-state into a number and return it
  ;; if parsing fails you can just return my-atom-state
  ;; to cancel the update or throw a validation
  ;; exception
)
(def a-str (cursor my-atom print-number parse-number))
;; @a-str -> "0"
(reset! a-str "1.2")
(println @my-atom)
;; 1.2
```

cursors can also be created by passing in a keyword or a key sequence that would be passed to `get-in` or `assoc-in` to the `cursor` function:

```clojure
(def my-atom (atom {:a {:b [{:x 0}]}}))
(def ab0 (cursor my-atom [:a :b 0])) ;; @ab0 -> {:x 0}
(def a (cursor my-atom :a) ;; a keyword can be used as well
```

This is somewhat similar (but not exactly) to cursors in [om][om] - which was the inspiration for cursors in freactive. It should be noted that in freactive, cursors were designed to work with lenses first and then with key or key sequences (`korks`) for convenience. A cursor doesn't know anything about the structure of data it references (i.e. the associative path from parent to child).

## License

Distributed under the Eclipse Public License, either version 1.0 or (at your option) any later version.

[reagent]: https://github.com/reagent-project/reagent
[om]: https://github.com/swannodette/om
[reflex]: https://github.com/lynaghk/reflex
