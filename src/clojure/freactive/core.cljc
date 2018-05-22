(ns freactive.core
  (:refer-clojure :exclude [atom])
  (:require
   #?@(:cljs
       [[goog.object]
        [freactive.util]])
   [clojure.set :as set]
   [clojure.data.avl :as avl])
  #?(:cljs
     (:require-macros
      [freactive.macros :refer [defsubtype]]))
  #?(:clj
     (:import
      [freactive ReactiveExpression StatefulReactive]
      [clojure.lang ILookup IDeref IMeta IRef IAtom ITransientCollection
       ITransientAssociative ITransientMap ITransientVector IReference
       IFn])))

#?(:cljs
   (do ;; Core API for reactive binding

     (deftype BindingInfo [raw-deref add-watch remove-watch clean])

     (defprotocol IReactive
       (-get-binding-fns [this]))

     (def ^:private iwatchable-binding-fns
       (BindingInfo. cljs.core/-deref cljs.core/-add-watch cljs.core/-remove-watch (fn [])))

     (defn get-binding-fns [iref]
       (cond (satisfies? IReactive iref)
             (-get-binding-fns iref)
             (and (satisfies? IDeref iref) (satisfies? IWatchable iref))
             iwatchable-binding-fns))

     (def ^:dynamic *register-dep* nil)

     (defn register-dep
       ([dep]
        (when-let [rdep *register-dep*]
          (when-let [binding-fns (get-binding-fns dep)]
            (rdep dep (goog/getUid dep) binding-fns))))
       ([dep id binding-info]
        (when-let [rdep *register-dep*]
          (rdep dep id binding-info)))))
     :clj
     (do
      (def ^:dynamic *register-dep* nil)

       (defn register-dep
         ([dep]
          (ReactiveExpression/registerDep dep))
         ([dep binding-info]
          (ReactiveExpression/registerDep dep binding-info)))

       (defn get-binding-fns [iref]
         (ReactiveExpression/getBindingInfo iref))))

#?(:cljs
   (do
     (def ^:private auto-reactive-id 0)

     (defn new-reactive-id []
       (let [id auto-reactive-id]
         (set! auto-reactive-id (inc auto-reactive-id))
         (str "--r." id)))))

;; Core API for cursors

(defprotocol ICursor
  (-cursor-key [this])
  (-child-cursor [this key])
  (-parent-cursor [this])
  (-cursor-kvseq [this]))

(defprotocol IChangeNotifications
  (-add-change-watch [this key f])
  (-remove-change-watch [this key]))

(defn cursor-kvseq [cursor]
  (-cursor-kvseq cursor))

(defn add-change-watch [cur key f]
  (-add-change-watch cur key f))

(defn remove-change-watch [cur key]
  (-remove-change-watch cur key))

(defprotocol IAssociativeCursor
  (-update! [this key f args])
  (-update-in! [this ks f args])
  (-assoc-in! [this ks v]))

(defn update! [cursor key f & args]
  (-update! cursor key f args))

(defn update-in! [cursor ks f & args]
  (-update-in! cursor ks f args))

(defn assoc-in! [cursor ks v]
  (-assoc-in! cursor ks v))

(defn cursor-key [cursor]
  (-cursor-key cursor))

(defn child-cursor [cursor key]
  (-child-cursor cursor key))

(defn descendant-cursor [cursor path]
  (loop [[key & more] path
         res cursor]
    (if key
      (recur more (child-cursor res key))
      res)))

(defn parent-cursor [cursor]
  (-parent-cursor cursor))

(defn get-root-cursor [cursor]
  (loop [cursor cursor]
    (if-let [parent (parent-cursor cursor)]
      (recur parent)
      cursor)))

(defn cursor-accessor [cursor]
  (loop [cursor cursor
         path []]
    (let [parent (parent-cursor cursor)
          key (cursor-key cursor)]
      (if (and parent key)
        (recur parent (conj path key))
        {:root cursor :path path}))))

(defn cursor-path [cursor]
  (:path (cursor-accessor cursor)))

#?(:clj
   (definterface ICursorImpl
     (activate [])
     (clean [])
     (updateChild [key f args])
     (assocChild [key val])
     (resetChild [key val])
     (notifyWatches [old-state new-state])
     (notifyChangeWatches [changes])
     (updateCursor [new-state change-ks])
     (reactiveDeref [])
     (rawDeref [])))

(defn coll-keyset [coll]
  (cond (map? coll)
        (keys coll)

        (counted? coll)
        (range (count coll))

        :default
        nil))

(def ^:dynamic ^:private *change-ks* nil)

#?(:clj
   (def ^:private cursor-binding-info
     (freactive.IReactive$BindingInfo.
      (fn [^ICursorImpl cursor] (.rawDeref cursor))
      (fn [cursor k f]
        (.addWatch ^IRef cursor k f))
      (fn [^IRef cursor k] (.removeWatch cursor k))
      (fn [^ICursorImpl cursor] (.clean cursor))))
   :cljs
   (do
     (def fwatch-binding-info
       (BindingInfo.
        #(.rawDeref %) #(.addFWatch % %2 %3) #(.removeFWatch % %2) #(.clean %)))))


(deftype Cursor
    [#?(:cljs id)
     parent
     tkey
     get-fn
     swap-fn
     activate-fn
     clean-fn
     #?@(:cljs
         [^:mutable child-cursors
          ^:mutable watches
          ^:mutable change-watches
          ^:mutable fwatches
          ^:mutable watchers
          ^:mutable state
          ^:mutable metadata
          ^:mutable dirty 
          ^:mutable validator]
         :clj
         [child-cursors
          watches
          change-watches 
          ^:volatile-mutable state
          ^:volatile-mutable metadata
          ^:volatile-mutable dirty 
          ^:volatile-mutable validator])]
  #?(:cljs Object :clj ICursorImpl)
  (activate [this]
    (when dirty
      (when activate-fn
          (activate-fn this)
          (set! state (get-fn this))
          (set! dirty false))))
  (clean [this]
    (when #?(:cljs (identical? 0 watchers)
             :clj (and (empty? @watches) (empty? @change-watches)))
      (when clean-fn
        (set! dirty true)
        (clean-fn this))))
  (updateChild [this key f args]
    (binding [*change-ks* ;; TODO: (conj *change-ks* key)
              [key]]
      (apply swap-fn this update key f args))
    this)
  (assocChild [this key val]
    (binding [*change-ks* [key]]
      (swap-fn this assoc key val))
    this)
  (resetChild [this child-key new-val]
    (doseq [^Cursor child (get child-cursors child-key)]
      (.updateCursor child new-val nil)))

  (rawDeref [this]
    (when dirty 
      (set! state (get-fn this)))
    state)
  (reactiveDeref [this]
    #?(:clj
       (ReactiveExpression/registerDep this cursor-binding-info) 
       :cljs
       (register-dep this id fwatch-binding-info))
    (.rawDeref this))
  #?@(:cljs
       [(equiv [this other]
               (-equiv this other))
        (registerOne [this]
                     (set! watchers (inc watchers))
                     (.activate this))
        (unregisterOne [this]
                       (set! watchers (dec watchers))
                       ;; (when (.-auto-clean this) (.clean this))
                       (.clean this)
                       )
        (addFWatch [this key f]
                   (when-not (aget (.-fwatches this) key)
                     (aset (.-fwatches this) key f)
                     (.registerOne this)))
        (removeFWatch [this key]
                      (when (aget (.-fwatches this) key)
                        
                        (js-delete (.-fwatches this) key)
                        (.unregisterOne this)))])
  (notifyWatches [this old-state new-state]
    #?(:cljs
        (goog.object/forEach
         (.-fwatches this)
         (fn [f key _]
           (f key this old-state new-state))))
    (doseq [[key f] #?(:cljs watches :clj @watches)]
      (f key this old-state new-state)))
  (notifyChangeWatches [this changes]
    (doseq [[key f] #?(:clj @change-watches :cljs change-watches)]
      (f key this changes)))
  (updateCursor [^Cursor this new-state change-ks]
    (when-not (identical? state new-state)
      (let [old-state state
            has-change-watches (not (empty? #?(:cljs change-watches :clj @change-watches)))]
        (set! state new-state)
        (.notifyWatches this old-state state)
        (if change-ks
          (let [change-ks (if (keyword? change-ks)
                            [(case change-ks
                               :conj (dec (count state))
                               :pop (count state))]
                            change-ks)
                [change-key & descendant-ks] change-ks
                cursors (get child-cursors change-key)]
            (when (or cursors has-change-watches)
              (let [^Cursor cur (first cursors)
                    old-val (if cur (.-state cur) (get old-state change-key))
                    new-val (get state change-key)]
                (when-not (identical? old-val new-val)
                  (doseq [^Cursor cur cursors]
                    (.updateCursor cur new-val descendant-ks))
                  (when has-change-watches
                    (if (nil? new-val)
                      (.notifyChangeWatches this [[change-key]])
                      (.notifyChangeWatches this [[change-key new-val]])))))))
          (cond
            has-change-watches
            (let [old-keys (set (coll-keyset old-state))
                  new-keys (coll-keyset state)
                  changes
                  (loop [[key & more] new-keys
                         old-keys old-keys
                         changes []]
                    (if key
                      (let [new-val (get new-state key)]
                        (if (contains? old-keys key)
                          (let [old-val (get old-state key)
                                old-keys (disj old-keys key)]
                            (if (identical? old-val new-val)
                              (recur more old-keys changes)
                              (do
                                (.resetChild this key new-val)
                                (recur more old-keys (conj changes [key new-val])))))
                          (do
                            (.resetChild this key new-val)
                            (recur more old-keys (conj changes ^:added [key new-val])))))
                      (concat changes
                              (doall
                               (for [key old-keys]
                                 (do
                                   (.resetChild this key nil)
                                   [key]))))))]
              (.notifyChangeWatches this changes))

            child-cursors
            (doseq [[ckey cursors] #?(:cljs child-cursors :clj @child-cursors)]
              (let [old-val (get old-state ckey)
                    new-val (get state ckey)]
                (when-not (identical? old-val new-val)
                  (doseq [^Cursor cur cursors]
                    (.updateCursor cur new-val nil))))))))))


  IAssociativeCursor
  (-update! [this key f args] (.updateChild this key f args))
  (-update-in! [this ks f args]
    (binding [*change-ks* ks]
      (apply swap-fn this update-in ks f args))
    this)
  (-assoc-in! [this ks v]
    (binding [*change-ks* ks]
      (swap-fn this assoc-in ks v)
      this))

  ICursor
  (-cursor-key [this] tkey)
  (-child-cursor [this ckey]
    #?(:cljs
       (or (first (get child-cursors ckey))
           (Cursor.
            (new-reactive-id)
            this
            ckey
            (fn [cur]
              (get (.rawDeref this) ckey))
            (fn [cur f & args]
              (.updateChild this ckey f args))
            (fn [cur]
              (.registerOne this)
              (set! child-cursors (update child-cursors ckey conj cur)))
            (fn [cur]
              (set! child-cursors
                    (update child-cursors ckey
                            (fn [cursors]
                              (let [cursors (remove #(= % cur) cursors)]
                                (when-not (empty? cursors)
                                  cursors)))))
              (.unregisterOne this))
            nil
            nil
            nil
            #js {}
            0
            (get state ckey)
            nil
            true
            nil))))
  (-parent-cursor [this]
    (when tkey
      parent))
  (-cursor-kvseq [this]
    (when state
      (cond
        (map? state)
        (seq state)

        (counted? state)
        (zipmap (range (count state)) (seq state)))))

  IChangeNotifications
  (-add-change-watch [this key f]
    ;;TODO clj
    #?(:cljs
       (when-not (contains? change-watches key)
         (set! (.-change-watches this) (assoc change-watches key f))
         (.registerOne this)))
    this)
  (-remove-change-watch [this key]
    ;;TODO clj
    #?(:cljs
       (when (contains? change-watches key)
         (set! (.-change-watches this) (dissoc change-watches key))
         (.unregisterOne this)))
    this)

  ILookup
  (#?(:cljs -lookup :clj valAt)
    [this key]
    (get (.rawDeref this) key))
  (#?(:cljs -lookup :clj valAt)
    [this key not-found]
    (or (get (.rawDeref this) key) not-found))

  IDeref
  (#?(:cljs -deref :clj deref)
    [this] (.reactiveDeref this))

  IMeta
  #?(:cljs (-meta [this] metadata)
     :clj (meta [this] @metadata))

  #?@(:cljs
       [IWatchable
        (-add-watch [this key f]
                    (when-not (contains? watches key)
                      (set! (.-watches this) (assoc watches key f))
                      (.registerOne this))
                    this)
        (-remove-watch [this key]
                       (when (contains? watches key)
                         (set! (.-watches this) (dissoc watches key))
                         (.unregisterOne this))
                       this)]

       :clj
       [IRef
        (setValidator [this f])
        (getValidator [this])
        (getWatches [this] @watches)
        (addWatch [this key f]
                   (swap! watches assoc key f)
                  this)
        (removeWatch [this key]
                     (swap! watches dissoc key)
                     this)])

  IAtom

  #?(:cljs ISwap)
  (#?(:cljs -swap! :clj swap) [this f] (swap-fn this f))
  (#?(:cljs -swap! :clj swap) [this f x] (swap-fn this f x))
  (#?(:cljs -swap! :clj swap) [this f x y] (swap-fn this f x y))
  (#?(:cljs -swap! :clj swap) [this f x y more] (apply swap-fn this f x y more))

  #?(:cljs IReset)
  (#?(:cljs -reset! :clj reset) [this new-value] (swap-fn this (constantly new-value)))

  #?@(:cljs
       [IReactive
        (-get-binding-fns [this] fwatch-binding-info)]

       :clj
       [freactive.IReactive
        (getBindingInfo [this] freactive.IReactive/IRefBindingInfo)])

  ITransientCollection
  (#?(:cljs -conj! :clj conj) [this val]
    (binding [*change-ks* :conj]
      (swap-fn this conj val)))
  (#?(:cljs -persistent! :clj persistent) [this] state)

  ITransientAssociative
  (#?(:cljs -assoc! :clj assoc) [this key val] (.assocChild this key val))

  ITransientMap
  (#?(:cljs -dissoc! :clj without) [this key] 
    (binding [*change-ks* [key]]
      (swap-fn this dissoc key)))

  ITransientVector
  (#?(:cljs -assoc-n! :clj assocN) [this n val] (.assocChild this n val))
  (#?(:cljs -pop! :clj pop) [this]
    (binding [*change-ks* :pop]
      (swap-fn this pop)))

  #?@(:cljs
       [cljs.core/IEquiv
        (-equiv [o other] (identical? o other))

        IPrintWithWriter
        (-pr-writer [a writer opts]
                    (-write writer "#<Cursor: ")
                    (pr-writer state writer opts)
                    (-write writer ">"))

        IHash
        (-hash [this] (goog/getUid this))]

       :clj
       [IReference
        (alterMeta [this f args]
                   (swap! meta f args))
        (resetMeta [this new-val]
                   (reset! meta new-val))]))

(defn atom
  "Creates and returns a ReactiveAtom with an initial value of x and zero or
  more options (in any order):
  :meta metadata-map
  :validator validate-fn
  If metadata-map is supplied, it will be come the metadata on the
  atom. validate-fn must be nil or a side-effect-free fn of one
  argument, which will be passed the intended new state on any state
  change. If the new state is unacceptable, the validate-fn should
  return false or throw an Error. If either of these error conditions
  occur, then the value of the atom will not change."
  [init & {:keys [meta validator]}]
  #?(:cljs
     (Cursor.
      (new-reactive-id)
      nil
      nil
      (fn [cur] (.-state cur)) 
      (fn [cur f & args]
        (let [new-value (apply f (.-state cur) args)]
          (when-let [validate (.-validator cur)]
            (assert (validate new-value) "Validator rejected reference state"))
          (.updateCursor cur new-value *change-ks*)))
      (fn [])
      (fn [])
      nil
      nil
      nil
      #js {}
      0
      init
      meta
      false
      validator) 
     :clj
     (let [state-ref (ref init)
           notify-agent (agent nil)]
       (Cursor. nil
                nil
                (fn [] @state-ref)
                (fn [^Cursor this f & args]
                  (dosync
                   (let [new-state (apply alter state-ref f args)]
                     (send notify-agent
                           (fn [_ new-state] (.updateCursor this new-state *change-ks*))
                           new-state)
                     new-state)))
                (fn [this])
                (fn [this])
                (clojure.core/atom nil)
                (clojure.core/atom nil)
                (clojure.core/atom nil)
                init
                (clojure.core/atom meta)
                false
                nil))))

(defn lens-cursor
  "Creates a lens cursor. If the 1-arity version is used or setter is nil, the
cursor is read-only."
  ([parent getter]
   (lens-cursor parent getter nil))
  ([parent getter setter]
   #? (:cljs
       (let [binding-info (get-binding-fns parent)
             id (new-reactive-id)
             getter (fn [this] (getter ((.-raw-deref binding-info) parent)))]
         (Cursor.
          id
          parent
          nil
          getter
          (if setter
            (fn [this f & args]
              (swap! parent
                     (fn [x] (setter x (apply f (getter x) args)))))
            (fn [] (throw (ex-info "Cursor is read-only" {}))))
          (fn [this]
            ((.-add-watch binding-info) parent id
             (fn [] (.updateCursor this (getter this) nil))))
          (fn [this] ((.-remove-watch binding-info) parent id))
          nil
          nil
          nil
          #js {} 
          0
          (getter @parent)
          nil
          true
          nil)))))

(defn root-cursor [atom-like]
  (lens-cursor atom-like identity (fn [old new] new)))
  
(defn cursor
  "Creates an associative cursor from the given parent to the provided
key or key-sequence (korks). Lens cursors should be created explicitly
using the lens-cursor function."
  [parent korks]
  (if (sequential? korks)
    (descendant-cursor parent korks)
    (child-cursor parent korks)))

;; Reactive Expression Implementation

#?(:cljs
   (do

     (def ^:dynamic *do-trace-captures* nil)

     (def ^:dynamic *trace-capture* nil)

     (defn- make-register-dep [rx]
       (fn do-register-dep [dep id binding-info]
         (when *trace-capture* (*trace-capture* dep))
         (aset (.-deps rx) id #js [dep binding-info])
         ((.-add-watch binding-info)
          dep (.-id rx)
          (fn []
            ((.-remove-watch binding-info) dep (.-id rx))
            (js-delete (.-deps rx) id)
            (.invalidate rx)))))

     (def invalidates-binding-info
       (BindingInfo.
        #(.rawDeref %)
        #(.addInvalidationWatch % %2 %3)
        #(.removeInvalidationWatch % %2)
        #(.clean %)))
     
     (deftype ReactiveExpression [id ^:mutable state ^:mutable dirty f deps meta watches fwatches watchers
                                  invalidation-watches iwatchers
                                  register-dep-fn lazy trace-captures]
       Object
       (equiv [this other]
         (-equiv this other))
       (compute [this]
         (set! dirty false)
         (let [old-val state
               new-val (binding [*register-dep* register-dep-fn
                                 *trace-capture* (when trace-captures
                                                   (trace-captures)
                                                   trace-captures)] (f))]
           (when-not (identical? old-val new-val)
             (set! state new-val)
             (.notifyFWatches this old-val new-val)
             new-val)))
       (clean [this]
         (when (and (identical? 0 watchers) (identical? 0 iwatchers))
           (goog.object/forEach deps
                                (fn [val key obj]
                                  ;; (println "cleaning:" key val)
                                  (let [dep (aget val 0)
                                        binding-info (aget val 1)]
                                    (let [remove-watch* (.-remove-watch binding-info)]
                                      (remove-watch* dep id))
                                    (when-let [clean* (.-clean binding-info)]
                                      (clean* dep)))
                                  (js-delete obj key)))
           (set! (.-dirty this) true)))
       (dispose [this] (.clean this))
       (addFWatch [this key f]
         (when-not (aget (.-fwatches this) key)
           (set! (.-watchers this) (inc (.-watchers this)))
           (aset (.-fwatches this) key f)))
       (removeFWatch [this key]
         (when (aget (.-fwatches this) key)
           (set! (.-watchers this) (dec (.-watchers this)))
           (js-delete (.-fwatches this) key)))
       (notifyFWatches [this oldVal newVal]
         (goog.object/forEach
          (.-fwatches this)
          (fn [f key _]
            (f key this oldVal newVal)))
         (doseq [[key f] (.-watches this)]
           (f key this oldVal newVal)))

       (notifyInvalidationWatches [this]
         (goog.object/forEach
          (.-invalidation-watches this)
          (fn [f key _]
            (f key this))))
       (addInvalidationWatch [this key f]
         (when-not (aget (.-invalidation-watches this) key)
           (set! (.-iwatchers this) (inc (.-iwatchers this)))
           (aset (.-invalidation-watches this) key f))
         this)
       (removeInvalidationWatch [this key]
         (when (aget (.-invalidation-watches this) key)
           (set! (.-iwatchers this) (dec (.-iwatchers this)))
           (js-delete (.-invalidation-watches this) key))
         this)
       (invalidate [this]
         (when-not (.-dirty this)
           (set! (.-dirty this) true)
           (if (> (.-watchers this) 0)
             ;; updates state and notifies watches
             (when (.compute this)
               (.notifyInvalidationWatches this))
             ;; updates only invalidation watches
             (.notifyInvalidationWatches this))
           (.clean this)))
       (reactiveDeref [this]
         (if (.-lazy this)
           (register-dep this (.-id this) invalidates-binding-info)
           (register-dep this (.-id this) fwatch-binding-info))
         (when (.-dirty this) (.compute this))
         (.-state this))
       (rawDeref [this]
         (when (.-dirty this)
           (binding [*register-dep* nil]
             (.compute this)))
         (.-state this))
       
       IReactive
       (-get-binding-fns [this]
         (if lazy invalidates-binding-info fwatch-binding-info))

       IEquiv
       (-equiv [o other] (identical? o other))

       IDeref
       (-deref [this] (.reactiveDeref this))

       IMeta
       (-meta [_] meta)

       IPrintWithWriter
       (-pr-writer [a writer opts]
         (-write writer "#<ReactiveComputation: ")
         (pr-writer state writer opts)
         (-write writer ">"))

       IWatchable
       (-add-watch [this key f]
         (when-not (contains? watches key)
           (set! (.-watchers this) (inc watchers))
           (set! (.-watches this) (assoc watches key f)))
         this)
       (-remove-watch [this key]
         (when (contains? watches key)
           (set! (.-watchers this) (dec watchers))
           (set! (.-watches this) (dissoc watches key)))
         this)

       IHash
       (-hash [this] (goog/getUid this)))

     (defn rx*
       ([f] (rx* f true))
       ([f lazy]
        (let [id (new-reactive-id)
              reactive (ReactiveExpression. id nil true f #js {} nil nil #js {} 0 #js {} 0 nil lazy
                                            *do-trace-captures*)]
          (set! (.-register-dep-fn reactive) (make-register-dep reactive))
          reactive))))

   :clj
   (do
     (defn reactive* [f & options]
       (#'clojure.core/setup-reference (ReactiveExpression. f) options))

     (defn eager-reactive* [f & options]
       (#'clojure.core/setup-reference (ReactiveExpression. f false) options))

     (defmacro reactive [& body]
       `(freactive.core/reactive*
         (fn ~'reactive-computation-fn []
           ~@body)))

     (defmacro eager-reactive [& body]
       `(freactive.core/eager-reactive*
         (fn ~'reactive-computation-fn []
           ~@body)))

     (def rx* reactive*)

     (defmacro rx [& body]
       `(freactive.core/rx*
         (fn []
           ~@body)))

     (defn reactive-state [init-state f & options]
       (#'clojure.core/setup-reference (StatefulReactive. init-state f) options))))


(defn- rapply* [f atom-like args lazy]
  (rx* (fn [] (apply f @atom-like args))))

(defn rapply [f atom-like & args]
  (rapply* f atom-like args true))

(defn eager-rapply [f atom-like & args]
  (rapply* f atom-like args false))

;; Reactive Attributes

(defn dispose [this]
  #?(:cljs
     (when (.-dispose this)
       (try
         (.dispose this)
         true
         (catch :default e
           (.warn js/console "Error while disposing state" e this))))))

(declare bind-attr*)

#?(:clj
   (definterface IReactiveAttributeImpl
     (set [])
     (dispose [])
     (invalidate [])))

#?(:cljs
   (do
     (deftype ReactiveAttribute [#?(:cljs id) the-ref ^BindingInfo binding-info set-fn enqueue-fn
                                 #?(:cljs ^:mutable disposed
                                          :clj ^:volatile-mutable disposed)]
       IFn
       (#?(:cljs -invoke :clj invoke) [this new-val]
         (.dispose this)
         (bind-attr* new-val set-fn enqueue-fn))
       #?(:cljs Object :clj IReactiveAttributeImpl)
       (set [this]
         (when-not disposed 
           ((.-add-watch binding-info) the-ref #?(:cljs id :clj this) #(.invalidate this))
           (set-fn ((.-raw-deref binding-info) the-ref))))
       (dispose [this]
         (set! disposed true)
         ((.-remove-watch binding-info) the-ref #?(:cljs id :clj this))
         (when-let [clean (.-clean binding-info)] (clean the-ref))
         (when-let [binding-disposed (get (meta the-ref) :binding-disposed)]
           (binding-disposed)))
       (invalidate [this]
         ((.-remove-watch binding-info) the-ref #?(:cljs id :clj this))
         (enqueue-fn #(.set this))))

     (defn bind-attr* [the-ref set-fn enqueue-fn]
       (if (satisfies? IDeref the-ref)
         (let [binding
               (ReactiveAttribute. #?(:cljs (new-reactive-id)) the-ref (get-binding-fns the-ref) set-fn enqueue-fn false)]
           (.set binding)
           binding)
         (do
           (set-fn the-ref)
           (fn [new-val]
             (set-fn nil)
             (bind-attr* new-val set-fn enqueue-fn)))))

     (defn attr-binder** [enqueue-fn]
       (fn attr-binder* [set-fn]
         (fn attr-binder [value]
           (bind-attr* value set-fn enqueue-fn))))))

;; Reactive Sequence Projection Protocols

(defprotocol IProjectionSource
  (-source-pull [this idx]))

(defn source-pull [this idx] (-source-pull this idx))

(defprotocol IProjectionTarget
  (-target-insert [this projected-elem before-idx])
  (-target-peek [this elem-idx])
  (-target-take [this elem-idx])
  (-target-count [this])
  (-target-move [this elem-idx before-idx]))

(defn target-insert [this elem before-idx]
  (assert (>= before-idx 0))
  (-target-insert this elem before-idx))

(defn target-count [this] (-target-count this))

(defn target-peek [this idx]
  (when (and (>= 0 idx) (<= (- (target-count this) 1)))
    (-target-peek this idx)))

(defn target-take [this idx]
  (assert (>= idx 0))
  (-target-take this idx))

(defn target-move [this idx before-idx]
  (assert (>= idx 0))
  (assert (>= before-idx 0))
  (-target-move this idx before-idx))

(defn target-remove [this idx]
  (assert (>= idx 0))
  (let [res (-target-take this idx)]
    (dispose res)))

(defn target-clear [this]
  (dotimes [i (target-count this)]
    (target-remove this 0)))

(defprotocol IProjection
  (-project [this target enqueue-fn velem-fn]
    "Initializes a projection with a target IProjectionTarget and a platform
enqueue-fn. The return value should be the actual IProjectionSource that this
IProjection is binding to the IProjectionTarget. All updates should be
dispatched via enqueue-fn in batches that are as big as possible - this usually
means that updates are batched to enqueue-fn only if they are the direct result
of some state change, not updates propogated up from a lower level projection
source."))

(defn project [projection target enqueue-fn velem-fn]
  (-project projection target enqueue-fn velem-fn))

;; Reactive Sequence Projection Implementations

#?(:cljs
   (do
     (deftype FilterCursor [parent ^:mutable filter-fn ^:mutable change-watches ^:mutable active]
       Object
       (setFilterFn [this new-filter-fn])
       (onChanges [this updates]
         (map
          (fn [[k v :as update]]
            (cond
              (= (count update) 1)
              update

              (not (filter-fn v))
              [k]

              :default
              update))
          updates))

       ICursor
       (-cursor-key [this])
       (-child-cursor [this key]
         (child-cursor parent key))
       (-parent-cursor [this] parent)
       (-cursor-kvseq [this]
         (map (fn [[k v]] (when (filter-fn v) [k v]))
              (cursor-kvseq parent)))

       IChangeNotifications
       (-add-change-watch [this key f]
         (set! change-watches (assoc change-watches key f))
         (when-not active
           (set! active true)))
       (-remove-change-watch [this key]
         (set! change-watches (dissoc change-watches key))
         (when (empty? change-watches)
           (set! active false))))

     (defn cursor-filter [cursor filter])

     (deftype LensingCursor [parent getter setter ^:mutable change-watches]
       Object
       (onUpdates [this updates]
         (let [updates*
               (for [[k v :as update] updates]
                 (if (= (count update) 1)
                   update
                   [k (getter v)]))]
           (doseq [[k f] change-watches]
             (f k this updates*))))
       ICursor
       (-cursor-key [this])
       (-child-cursor [this key]
         (let [cur (lens-cursor (child-cursor parent key) getter setter)]
           (set! (.-tkey cur) key)
           cur))
       (-parent-cursor [this] parent)
       (-cursor-kvseq [this]
         (map (fn [[k v]] [k (getter v)])
              (cursor-kvseq parent)))

       IChangeNotifications
       (-add-change-watch [this key f]
         (set! change-watches (assoc change-watches key f)))
       (-remove-change-watch [this key]
         (set! change-watches (dissoc change-watches key))))

     (defn cursor-mapping [cursor getter setter])

     (defn cursor-sort [cursor {:keys [by-value by-key direction] :as sort-opts}])
     
     (deftype KeysetCursorProjection [cur target-fn opts
                                      ^:mutable avl-set ^:mutable target
                                      ^:mutable enqueue-fn
                                      ^:mutable filter-fn
                                      ^:mutable offset ^:mutable limit
                                      ^:mutable sort-by
                                      ^:mutable placeholder
                                      ^:mutable placeholder-idx]
       Object
       (dispose [this]
         (remove-change-watch cur this))
       (project [this]
         (target-clear target)
         (.onUpdates this (cursor-kvseq cur)))
       (updateSortBy [this new-sort-by]
         (enqueue-fn
          (fn []
            (when (or (not (identical? new-sort-by sort-by)) (nil? avl-set))
              (set! sort-by new-sort-by)
              (set! avl-set
                    (if sort-by
                      (avl/sorted-set-by sort-by)
                      (avl/sorted-set)))))))
       (updateFilter [this new-filter])
       (updateOffset [this new-offset])
       (updateLimit [this new-limit])
       (rankOf [this key]
         (let [idx (- (avl/rank-of avl-set key) offset)]
           (when (>= idx 0)
             (let [idx
                   (if limit
                     (when (<= idx limit)
                       idx)
                     idx)]
               (if (and placeholder-idx (>= idx placeholder-idx))
                 (inc idx)
                 idx)))))
       (onUpdates [this updates]
         ;; (println "updates" updates)
         (enqueue-fn
          (fn []
            (doseq [[k v :as update] updates]
              (if-let [cur-idx (.rankOf this k)]
                (if (or (= (count update) 1) (not (filter update)))
                  (do
                    (set! avl-set (disj avl-set k))
                    (target-remove target cur-idx))
                  (do
                    (set! avl-set (conj avl-set k))
                    (let [new-idx (.rankOf this k)]
                      (when-not (identical? cur-idx new-idx)
                        (target-move target cur-idx new-idx)))))
                (when (filter update)
                  (set! avl-set (conj avl-set k))
                  (target-insert target (rx* (fn [] (target-fn (cursor cur k)))) (.rankOf this k))))))))

       IProjection
       (-project [this proj-target enqueue velem-fn]
         (set! target proj-target)
         (set! enqueue-fn enqueue)
         (let [{:keys [filter sort-by offset limit]} opts]
           (when filter (bind-attr* filter #(.updateFilter this %) enqueue))
           (bind-attr* sort-by #(.updateSortBy this %) enqueue)
           (when offset (bind-attr* offset #(.updateOffset this %) enqueue))
           (when limit (bind-attr* limit #(.updateLimit this %) enqueue))
           ;; (bind-attr* placeholder-idx #(.updatePlaceholderIdx this) enqueue)
           ;; (bind-attr* placeholder #(.updatePlaceholder this) enqueue)
           )
         (add-change-watch cur this
                           (fn [k r updates] (.onUpdates this updates)))
         (.project this)))

     (deftype SeqProjectionSource [elements target enqueue]
       Object
       (refresh [this]
         (doseq [elem elements]
           (target-insert target elem nil)))

       IProjectionSource
       (-source-pull [this idx]
         (when (< idx (count elements))
           (nth elements idx)))) 

     (defn seq-projection [elements]
       (reify IProjection
         (-project [this target enqueue velem-fn]
           (let [source (SeqProjectionSource. elements target enqueue)]
             (enqueue (fn [] (.refresh source)))
             source))))

     (defprotocol IAsVirtualElement
       (-as-velem [this as-velem-fn]))

     (deftype VirtualElementWrapper [])

     (defn- wrap [wrap-fn elem] (wrap-fn elem))

     (defn- unwrap [elem] elem)

     (deftype MapProjection [wrap-fn target ^:mutable source]
       IProjectionSource
       (-source-pull [this idx]
         (wrap wrap-fn (source-pull source idx)))
       
       IProjectionTarget
       (-target-insert [this elem before-idx]
         (target-insert target (wrap wrap-fn elem) before-idx))
       (-target-peek [this i]
         (unwrap (target-peek target i)))
       (-target-take [this i]
         (unwrap (target-take target i)))
       (-target-count [this]
         (target-count target))
       (-target-move [this elem-idx before-idx]
         (target-move target elem-idx before-idx)))

     (defn pwrap [proj wrap-fn]
       (reify IProjection
         (-project [this target enqueue-fn velem-fn]
           (let [pproj (MapProjection. wrap-fn target nil)]
             (set! (.-source pproj) (project proj pproj enqueue-fn velem-fn))
             pproj))))

     (deftype OffsetProjection [offset source target]
       Object
       (translate [this i]
         (let [j (- i offset)]
           (when (>= j 0)
             j)))
       IProjectionTarget
       (-target-insert [this elem before-idx]
         (if (> before-idx offset)
           (target-insert target elem (- before-idx offset))))
       (-target-peek [this i]
         (when-let [j (.translate this i)]
           (target-peek target j)))
       (-target-take [this i]
         (when-let [j (.translate this i)]
           (target-take target j)))
       (-target-count [this]
         (target-count target))
       (-target-move [this elem-idx before-idx]))

     (defn poffset [proj offset]
       (reify IProjection
         (-project [this target enqueue-fn velem-fn]
           (let [pproj (OffsetProjection. offset target nil)]
             (set! (.-source pproj) (project proj pproj enqueue-fn velem-fn))
             pproj))))

     (deftype LimitProjection [offset source target]
       Object
       (translate [this i]
         (let [j (- i offset)]
           (when (>= j 0)
             j)))
       IProjectionTarget
       (-target-insert [this elem before-idx]
         (if (> before-idx offset)
           (target-insert target elem (- before-idx offset))))
       (-target-peek [this i]
         (when-let [j (.translate this i)]
           (target-peek target j)))
       (-target-take [this i]
         (when-let [j (.translate this i)]
           (target-take target j)))
       (-target-count [this]
         (target-count target))
       (-target-move [this elem-idx before-idx]))

     (defn plimit [proj limit]
       (reify IProjection
         (-project [this target enqueue-fn velem-fn]
           (let [pproj (LimitProjection. limit proj target)]
             (set! (.-source pproj) (project proj pproj enqueue-fn velem-fn))
             pproj))))
     

     (defn- cmap2* [f keyset-cursor
                    {:keys [filter sort offset limit placeholder-idx placeholder] :as opts}]
       (cond-> keyset-cursor
         filter (cursor-filter filter)
         true (cursor-sort sort)
         offset (poffset offset)
         limit (plimit limit)
         true (pwrap f)))

     (defn cmap*
       [f keyset-cursor opts]
       (KeysetCursorProjection. keyset-cursor f opts nil nil nil identity 0 nil nil nil nil))

     (defn cmap
       [f keyset-cursor & {:as opts}]
       (cmap* f keyset-cursor opts))))
