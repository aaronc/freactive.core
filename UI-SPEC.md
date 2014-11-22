# freactive-based User Interface Spec

The goal of this document is to suggest a convention that can used for **declaratively defining reactive user interfaces independent of the platform**.

**Motivation:** **Make it easy for anyone to write good user interfaces** by using a consistent set of powerful idioms across frameworks/platforms (with minor variations where needed).

Here are the conventions briefly:
* **A "virtual node" is data in the form of `[:node-type attrs-map? & children]`** (the style that was introduced in [hiccup][hiccup], more generally called a "recursive variant tree") for ease of use
* **If something that is "derefable"** (implements `IDeref`) **and "watchable"** (implements `cljs.core/IWatchable`, `clojure.core/IRef` or `freactive.core/Invalidates`) **is passed as an attribute value or child in a virtual node, a reactive binding will be created** so that custom reactive data sources (*maybe even tied to database entities!*) can be used
* A **common set of reactive data types (atom, cursor, expression, etc.)** will be used so that state-management is decoupled from rendering
* **Functions can be bound as event handlers or lifecycle callbacks using attributes** (or Clojure metadata if needed)

Platforms that could conceivabily be targeted:

By ClojureScript: the DOM, of course, but also Qt/QML (for native + iOS & Android apps), Canvas, WebGL, Windows Metro, etc.

By Clojure: JavaFX, Processing/Quil, WPF, etc.

## Details

### Virtual Nodes

Since this convention is quite common in the Clojure(Script) nowadays, it probably needs little further explanation. A virtual node is a Clojure(Script) vector who's first element is a keyword known as the "tag" (in CS terms, I think this is called a tagged variant). The second element, if it is a map, will be treated as a map of attributes with keyword-keys. *Just a reminder: we can use namespaced keywords for tags and attributes to define custom, namespaced behavior.*

**Rationale:** Virtual nodes are a good convention because:

* they intuitively make sense to people reducing the learning curve and cognitive load
* they correctly model something that really is a tree of heterogenous elements (apparently the node are called variants and the whole tree structure is called a recursive variant tree in CS terms)
* they are extensible - keywords allow an unlimited number of application specific tags and attributes
* they are just data

### Bindings

### Shared Library of Reactive Data Types

This is provided in freactive.core, but because the convention defines anything that is "derefable" and "watchable", other data types can be used. It is important to have a good, tested set of defaults as well as the ability to extend.



### Events
