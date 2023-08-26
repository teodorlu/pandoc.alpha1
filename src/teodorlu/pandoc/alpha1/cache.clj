(ns teodorlu.pandoc.alpha1.cache)

;; Provide a cache for low-level pandoc operations to speed things up
;;
;; Per 2023-08-26 not in use.

(def ^:dynamic *pandoc-cache* nil)

(defprotocol ICache
  (contains-key? [this key])
  (lookup [this key])
  (save [this key val]))

(defn in-memory-cache []
  (let [cache (atom {})]
    (reify ICache
      (contains-key? [_ key]
        (contains? @cache key))
      (lookup [_ key]
        (get @cache key))
      (save [_ key val]
        (swap! cache assoc key val)))))

(let [cache (in-memory-cache)]
  (save cache :key :value)
  (contains-key? cache :key)
  (lookup cache :key))
