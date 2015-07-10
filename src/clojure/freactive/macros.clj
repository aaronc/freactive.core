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

;; (defmacro defsubtype [t fields supertype & impls]
;;   (let [env &env
;;         r (:name (cljs.analyzer/resolve-var (dissoc env :locals) t))
;;         impls (cons 'Object impls)]
;;     `(do
;;        (deftype* ~t ~fields nil nil)
;;        (let [tmp# (fn [])]
;;          (set! (.-prototype tmp#) (.-prototype ~supertype))
;;          (set! (.-prototype ~t) (new tmp#)))
;;        (extend-type ~t ~@(cljs.core/dt->et t impls fields))
;;        (set! (.-getBasis ~t) (fn [] '[~@fields]))
;;        (let [basis# (set (.getBasis ~t))]
;;          (doseq [pf# (.getBasis ~supertype)]
;;            (assert (contains? basis# pf#) "Sub-types fields not compatible with parent fields")))
;;        (set! (.-cljs$lang$type ~t) true)
;;        (set! (.-cljs$lang$ctorStr ~t) ~(str r))
;;        (set! (.-cljs$lang$ctorPrWriter ~t) (fn [this# writer# opt#] (cljs.core/-write writer# ~(str r))))
;;        ~t)))


(defmacro defsubtype [t fields supertype & impls]
  (let [env &env
        r (:name (cljs.analyzer/resolve-var (dissoc env :locals) t))
        impls (cons 'Object impls)]
    `(do
       (deftype ~t ~fields ~@impls)
       (freactive.util/inherit! ~t ~supertype)
       ;; (if (.-setPrototypeOf js/Object)
       ;;   (.setPrototypeOf js/Object (.-prototype ~t) (.-prototype ~supertype))
       ;;   (goog.object/forEach
       ;;    (.-prototype ~supertype)
       ;;    (let [proto# (.-prototype ~t)]
       ;;      (fn [val# key# obj#]
       ;;        (when-not (.hasOwnProperty proto# key#)
       ;;          (aset proto# key# val#))))))
       ;; (let [basis# (set (.getBasis ~t))]
       ;;   (doseq [pf# (.getBasis ~supertype)]
       ;;     (assert (contains? basis# pf#) "Sub-types fields not compatible with parent fields")))
       ~t)))
