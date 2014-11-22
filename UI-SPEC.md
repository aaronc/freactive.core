# freactive-based User Interface Spec

The goal of this document is to describe a model for declaratively specifying user interfaces which reactively update their state based on changes in app state using simple conventions which are independent of the underlying user interface renderer/event-system/etc. In simpler language, this means: **we want to write reactive user interfaces using a common set of convention that is independent of the platform**. (*This does not mean that we are writing things that are platform independent!*)

This model requires two inputs:
* Clojure(Script) immutable data structures - specifically vectors, maps, keywords, strings, numbers & booleans
* A set of reactive data structures

## Syntax

User interfaces generally consist of a tree of nodes. For simplicity and generality, user interface node trees can  be represented by tagged recursive variant trees based on Clojure(Script) vectors, keywords and maps. In simpler language this means, **we will use "hiccup"-like vectors to represent elements in the user interface**.

