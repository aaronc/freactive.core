(ns freactive.framework
  (:require
   [freactive.api :as api]))

(def ^:private iwatchable-binding-fns
  #+cljx (api/->BindingInfo
          cljs.core/-deref
          cljs.core/-add-watch
          cljs.core/-remove-watch
          nil)
  #+clj (api/->BindingInfo
         clojure.core/deref
         clojure.core/add-watch
         clojure.core/remove-watch
         nil))

(def ^:private deref-only-binding-fns
  #+cljx (api/->BindingInfo
          cljs.core/-deref
          nil
          nil
          nil)
  #+clj (api/->BindingInfo
         clojure.core/deref
         nil
         nil
         nil))

(defn get-binding-fns [iref]
  (cond
   #+cljs (satisfies? api/IReactive iref)
   (api/-get-binding-fns iref)

   #+clj (instance? freactive.IReactive iref)
   (.getBindingFns iref)

   #+cljs (satisfies? IWatchable iref)
   #+clj (instance? clojure.lang.IRef iref)
   iwatchable-binding-fns

   :default deref-only-binding-fns))

(deftype BindingState [disposed disposed-callback])

(defn bind* [iref invalidated-fn]
  (let [binding-fns (r/get-binding-fns ref)
        add-watch* (.-add-watch binding-fns)
        remove-watch* (.-remove-watch binding-fns)
        clean-watch* (or (.-clean-watch binding-fns) remove-watch*)
        raw-deref* (.-raw-deref binding-fns)
        ref-meta (meta ref)
        on-invalidated (:binding/on-invalidated ref-meta)
        on-disposed (:binding/on-disposed ref-meta)]
    (when (and add-watch* remove-watch*)
      (let [id 0 ;; todo
            disposed-fn
            (fn []
              (clean-watch* iref id)
              (when on-disposed
                (on-disposed)))
            binding-state (BindingState. false disposed-fn)
            invalidated
            (fn binding-invalidated []
                          (remove-watch* iref id)
                          (invalidated-fn binding-state))
            invalidated
            (if binding-invalidated
              (fn []
                (when-not
                    (#+cljs keyword-identical? #+clj =
                            :cancel
                            (binding-invalidated (.-cur-element state) iref))
                  (invalidated)))
              invalidated)]
        binding-state))))
