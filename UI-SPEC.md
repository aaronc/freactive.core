# freactive-based User Interface Spec

The goal of this document is to suggest a convention that can used for **declaratively defining reactive user interfaces indepdendent of the platform** - i.e. the same syntax can be used for the DOM, JavaFX, Quil, etc.

Here are the conventions briefly:
* **A "virtual node" is data in the form of `[:node-type attrs-map? & children]`** (the style that was introduced in [hiccup][hiccup], more generally called a "recursive variant tree")
* A **common set of reactive data types (atom, cursor, expression, etc.)** will be used so that state-management is decoupled from rendering
* **If something that is "derefable"** (implements `IDeref`) **and "watchable"** (implements `cljs.core/IWatchable`, `clojure.core/IRef` or `freactive.core/Invalidates`) **is passed as an attribute value or child in a virtual node, a reactive binding will be created** so that custom reactive data sources (*maybe even tied to database entities!*) can be used
* **Functions can be bound as event handlers or lifecycle callbacks using attributes** (or Clojure metadata if needed)
