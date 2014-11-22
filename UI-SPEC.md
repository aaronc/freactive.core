# freactive-based User Interface Spec

The goal of this document is to describe a convention for **declaratively defining reactive user interfaces indepedent of the underlying platfrom** - i.e. the same syntax can be used for the DOM, Quil, JavaFX, etc.

Here are the conventions briefly:
* **A "virtual node" is data in the form of `[:node-type attrs-map? & children]`** (the style that was introduced in [hiccup][hiccup], more generally called a "recursive variant tree")
* A **common set of reactive data types (atom, cursor, expression, etc.)** will be used so that state-management is decoupled from rendering
* **If something that is "derefable"** (implements `IDeref`) **and "watchable"** (implements `cljs.core/IWatchable`, `clojure.core/IRef` or `freactive.core/Invalidates`) **is passed as an attribute value or child in a virtual node, a reactive binding will be created**
* **Functions can be bound as event handlers or lifecycle callbacks using attributes (or Clojure metadata if needed)**

The goal of this document is to describe a model for declaratively specifying user interfaces which reactively update their state based on changes in app state using simple conventions which are independent of the underlying user interface renderer/event-system/etc. In simpler language, this means: **we want to write reactive user interfaces using a common set of convention that is independent of the platform**. (*This does not mean that we are writing things that are platform independent!*)

This model requires two inputs:
* Clojure(Script) immutable data structures - specifically vectors, maps, keywords, strings, numbers & booleans
* A set of reactive data structures

## Syntax

User interfaces generally consist of a tree of nodes. For simplicity and generality, user interface node trees can  be represented by tagged recursive variant trees based on Clojure(Script) vectors, keywords and maps. In simpler language this means, **we will use "hiccup"-like vectors to represent elements in the user interface**.

