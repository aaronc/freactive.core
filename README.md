# freactive.core

Reactive atoms, expressions, cursors for Clojure(Script).

The goal of this library is to abstract the reactive atom and reactive computation
or computed observable concepts from [reagent][reagent] and [reflex][reflex], as
well as a variation of the cursor concept from [om][om]. The broader aim of this
goal is to enable a set of reactive data structures that can be shared between many
different front-end frameworks that is also based, as much as possible, only on idioms
already in Clojure core.

## Core Data Structures

### Reactive Atoms

### Reactive Expressions

### Reactive Cursors

## Core Idioms

Understanding this section is not needed to use this library, but is needed for developers of
extensions to this library as well as those who are curious about its underpinnings and
algorithms.

**Core Definitions and Patterns:**

* A `reactive` refers to any `IDeref` instance which will register itself as a dependency
to a parent computation that is `deref`ing them if the parent has bound an "invalidation"
function thread locally
* `reactive`'s register themselves as dependencies by adding an "invalidate" function
that the parent has bound thread locally during the scope of the computation to their
`add-watch` or `add-invalidation` watch (see [`IInvalidates`](#iinvalidates)).
* **Whenever a `reactive` notifies a parent computation that it has changed, it
stops telling the parent**  - see the next section

### When reactive computations are invalidated

### `*invalidate-rx*`

### `IInvalidates`

## License

Distributed under the Eclipse Public License, either version 1.0 or (at your option) any later version.

[reagent]: https://github.com/reagent-project/reagent
[om]: https://github.com/swannodette/om
[reflex]: https://github.com/lynaghk/reflex
