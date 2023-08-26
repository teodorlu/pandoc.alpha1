(ns teodorlu.pandoc.alpha1.cache
  "Plug in in your own pandoc cache to speed things up!

  Bind teodorlu.pandoc.alpha1.cache/*pandoc-cache* to something that satisfies
  teodorlu.pandoc.alpha1.cache/ICache to avoid shelling out to Pandoc when
  converting text we've converted before.

  In-memory and file-based caches are provided."
  (:require
   [babashka.fs :as fs]
   [clojure.edn :as edn]
   [clojure.string :as str]))

(def ^:dynamic *pandoc-cache* nil)

(defprotocol ICache
  (contains-key? [this key])
  (lookup [this key])
  (save [this key val])
  (clear! [this]))

(defn in-memory-cache []
  (let [cache (atom {})]
    (reify ICache
      (contains-key? [_ key]
        (contains? @cache key))
      (lookup [_ key]
        (get @cache key))
      (save [_ key val]
        (swap! cache assoc key val))
      (clear! [_]
        (reset! cache {})))))

(comment
  (fs/xdg-cache-home "teodorlu.pandoc.alpha1/cache.d"))

(defn file-cache []
  (let [hash-str (fn [s]
                   (->
                    (.encodeToString (java.util.Base64/getEncoder)
                                     (.digest
                                      (doto (java.security.MessageDigest/getInstance "SHA-256")
                                        (.update (.getBytes s "UTF-8")))))
                    (str/replace "/" "_")
                    (str/replace "+" "-")))
        cache-dir (fs/xdg-cache-home "teodorlu.pandoc.alpha1/cache.d")]
    (fs/create-dirs cache-dir)
    (reify ICache
      (contains-key? [_ k]
        (let [digest (hash-str k)
              cache-file (fs/file cache-dir (str digest ".edn"))]
          (fs/exists? cache-file)))
      (lookup [_ k]
        (let [digest (hash-str k)
              cache-file (fs/file cache-dir (str digest ".edn"))]
          (when (fs/exists? cache-file)
            (edn/read-string (slurp cache-file)))))
      (save [_ k v]
        (let [digest (hash-str k)
              cache-file (fs/file cache-dir (str digest ".edn"))]
          (spit cache-file (pr-str v))))
      (clear! [_]
        (fs/delete-tree cache-dir)
        (fs/create-dirs cache-dir)))))

(comment
  (let [cache (in-memory-cache)]
    (save cache :key :value)
    (contains-key? cache :key)
    (lookup cache :key)))

;; TODO
;;
;; JDBC cache
;;
;; Redis cache
