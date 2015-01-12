(ns freactive.api)

(def ^:dynamic *invalidate* nil)

(deftype BindingInfo [raw-deref add-watch remove-watch clean-watch])

(defprotocol IReactive
  (-get-binding-fns [this]))



