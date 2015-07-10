(ns freactive.util
  #?(:cljs
     (:require
      [goog.object])))

#?(:cljs
   (do
     (defn inherit! [child parent]
       (if (.-setPrototypeOf js/Object)
         (.setPrototypeOf js/Object (.-prototype child) (.-prototype parent))
         (let [proto (.-prototype child)]
           (goog.object/forEach
            (.-prototype parent)
            (fn [val key _]
              (when-not (.hasOwnProperty proto key)
                (aset proto key val))))))
       (let [child-fields (set (.getBasis child))]
         (doseq [parent-field (.getBasis parent)]
           (assert (contains? child-fields parent-field) "Sub-types fields not compatible with parent fields"))))

     (defn array-take [array idx]
       (aget (.splice array idx 1) 0))

     (defn array-insert [array x before-idx]
       (.splice array (or before-idx (alength array)) 0 x))

     (defn array-move [array cur-idx before-idx]
       (array-insert array (aget (.splice array cur-idx 1) 0) before-idx))))
