(ns freactive.macros)

(defmacro rx [& body]
  `(freactive.core/rx*
     (fn []
       ~@body)
     true))

(defmacro eager-rx [& body]
  `(freactive.core/rx*
     (fn []
       ~@body)
     false))

(defmacro non-reactively [& body]
  `(binding [freactive.core/*register-dep* nil]
     ~@body))

(def ^:private auto-id (atom 0))

(defmacro debug-rx [rx]
  (let [dbg-str (str "rx-debug" (pr-str rx))
        id (str "debug-rx-" (swap! auto-id inc))]
    `(let [dbg-str# ~dbg-str
           res#
           (binding [freactive.core/*do-trace-captures*
                     (fn
                       ([] (println dbg-str# ": starting capture"))
                       ([c#] (println dbg-str# "captured :" c#)))]
             ~rx)
           invalidation-cb#
           (fn [k# r#] (println dbg-str#
                               "notifiying invalidation watches:"
                               (cljs.core/js-keys (.-invalidation-watches res#))
                               "& watches:"
                               (cljs.core/keys (.-watches res#))))]
       (.addInvalidationWatch res# ~id invalidation-cb#)
       res#)))

(defmacro cfor [[bind-sym keyset-cursor & {:as opts}] body]
  `(freactive.core/cmap*
    (fn [~bind-sym] ~body)
    ~keyset-cursor ~opts))

(defmacro defsubtype [t fields supertype & impls]
  (let [env &env
        r (:name (cljs.analyzer/resolve-var (dissoc env :locals) t))
        impls (cons 'Object impls)]
    `(do
       (deftype ~t ~fields ~@impls)
       (freactive.util/inherit! ~t ~supertype)
       ~t)))
