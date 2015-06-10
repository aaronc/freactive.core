(ns freactive.util)

#?(:cljs
   (do
     (defn array-take [array idx]
       (aget (.splice array idx 1) 0))

     (defn array-insert [array x before-idx]
       (.splice array (or before-idx (alength array)) 0 x))

     (defn array-move [array cur-idx before-idx]
       (array-insert array (aget (.splice array cur-idx 1) 0) before-idx))))
