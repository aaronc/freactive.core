# freactive-based User Interface Spec

The goal of this document is to suggest a convention that can used for **declaratively defining reactive user interfaces independent of the platform**.

**Motivation:** **Make it easy for anyone to write good user interfaces** by using a consistent set of powerful idioms across frameworks/platforms (with minor variations where needed).

Here are the conventions briefly:
* **A "virtual node" is data in the form of `[:node-type attrs-map? & children]`** (the style that was introduced in [hiccup][hiccup], more generally called a "recursive variant tree") for ease of use
* **If something that is "derefable"** (implements `IDeref`) **and "watchable"** (implements `cljs.core/IWatchable`, `clojure.lang.IRef` or `freactive.core/Invalidates`) **is passed as an attribute value or child in a virtual node, a reactive binding will be created** so that custom reactive data sources (*maybe even tied to database entities!*) can be used
* A **common set of reactive data types (atom, cursor, expression, etc.)** will be used so that state-management is decoupled from rendering
* **Functions can be bound as event handlers or lifecycle callbacks using attributes** (or Clojure metadata if needed)

Platforms that could conceivabily be targeted:

By ClojureScript: the DOM, of course, but also Qt/QML (for native + iOS & Android apps), Canvas, WebGL, Windows Metro, etc.

By Clojure: JavaFX, Processing/Quil, WPF, etc.

## Details

### Virtual Nodes

Since this convention is quite common in the Clojure(Script) nowadays, it probably needs little further explanation. A virtual node is a Clojure(Script) vector who's first element is a keyword known as the "tag" (in CS terms, I think this is called a tagged variant). The second element, if it is a map, will be treated as a map of attributes with keyword-keys. *Just a reminder: we can use namespaced keywords for tags and attributes to define custom, namespaced behavior.*

**Rationale**

Since this choice may be controversial, some rationale is provided. It is thought that vector/keyword/map virtual nodes are a good convention because:

* they intuitively make sense to people reducing the learning curve and cognitive load
* they correctly model something that really is a tree of heterogenous elements (apparently the nodes are called variants and the whole tree structure is called a recursive variant tree in CS terms)
* they are extensible - keywords allow an unlimited number of application specific tags and attributes
* they are just data

### Bindings

Bindings are initiated by the underlying rendering framework when they see something that is not a literal but rather a reference type as an attribute value or node in a tree. Clojure has a built-in concept for something that is a reference - likely to change - as opposed to a value - pure data. That is the `IDeref` interface/protocol. It allows one operation whih is dereferencing the reference type to get the "value" of the reference at the current time - by convention this "value" should be immutable - i.e. pure data. Clojure also has core interfaces for listening to changes on reference types - `IRef` in Clojure and `IWatchable` in Clojurescript. It makes sense to use these types as the basis for doing reactive data binding. It just seems to make sense to use they core interfaces to establish data bindings. By making it very general, a user interface renderer can bind to references implementing these protocols without knowing which implementations are used. An additional, optional protocol `IIInvalidates` (from freactive) is proposed which notifies listeners that something is dirty without necessarily recomputing its value (which could be expensive).

### Shared Library of Reactive Data Types

If each UI library provides its own set of observable reference types, we are complecting things. If we can settle on some idiomatic conventions for these data types and possibly a good base implementation, we can greatly simplify things because managing state and representing UI's can be neatly decoupled with a thing layer change notifications (`IWatchable`, `IRef` and `IInvalidates`) between them.

freactive.core is proposed as a base library of reference types and conventions. But if there is an agreed upon protocol, there can be any number of implementations - possibly some for special things like binding to database state.

### Events

Events should be framework-specific. Usually the underlying framework will provide a set of events and the Clojure wrapper for that framework will have its own events (sometimes called lifecycle callbacks). To simplify things, it is suggested that the general convention is for both events to be defined in the attribute map where possible. To distinguish between platform events and lifecycle callbacks either namespace-prefixed keywords or nested maps should be used. The :on- convention for distinguishing events from other types of attributes is well understood.
