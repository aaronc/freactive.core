# freactive-based User Interface Spec

The goal of this document is to suggest a convention that can used for **declaratively defining reactive user interfaces indepdendent of the platform** - i.e. the same syntax can be used for the DOM, JavaFX, Quil, etc.

Here are the conventions briefly:
* **A "virtual node" is data in the form of `[:node-type attrs-map? & children]`** (the style that was introduced in [hiccup][hiccup], more generally called a "recursive variant tree")
* **If something that is "derefable"** (implements `IDeref`) **and "watchable"** (implements `cljs.core/IWatchable`, `clojure.core/IRef` or `freactive.core/Invalidates`) **is passed as an attribute value or child in a virtual node, a reactive binding will be created** so that custom reactive data sources (*maybe even tied to database entities!*) can be used
* A **common set of reactive data types (atom, cursor, expression, etc.)** will be used so that state-management is decoupled from rendering
* **Functions can be bound as event handlers or lifecycle callbacks using attributes** (or Clojure metadata if needed)

## Details

### Virtual Nodes

A virtual node is a Clojure(Script) vector who's first element is a keyword known as the "tag" (in CS terms, I think this is called a tagged variant). The second element, if it is a map, will be treated as a map of attributes with keyword-keys. Since this convention is quite common in the Clojure(Script) world nowadays, it needs little further explanation except possibly for a reminder that we can use namespaced keywords for tags and attributes.

### Shared Library of Reactive Data Types

This is provided in freactive.core, but because the convention defines anything that is "derefable" and "watchable", other data types can be used. It is important to have a good, tested set of defaults as well as the ability to extend.

### Bindings

### Events
