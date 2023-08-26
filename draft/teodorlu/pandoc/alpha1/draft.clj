(ns teodorlu.pandoc.alpha1.draft
  (:require
   [next.jdbc]
   [nextjournal.clerk :as clerk]
   [teodorlu.pandoc.alpha1 :as pandoc]
   [teodorlu.pandoc.alpha1.cache :as cache]))

;; Can we create an example jdbc-backed pandoc cache?

(def sqlite-cache-file "pandoc-cache.sqlite")
(def datasource (next.jdbc/get-datasource {:dbtype "sqlite" :dbname sqlite-cache-file}))

(def sqlite-cache
  (let [hash-str (fn [s]
                   (.encodeToString (java.util.Base64/getEncoder)
                                    (.digest
                                     (doto (java.security.MessageDigest/getInstance "SHA-256")
                                       (.update (.getBytes s "UTF-8"))))))]
    (with-open [conn (next.jdbc/get-connection datasource)]
      (next.jdbc/execute!
       conn
       [(str "CREATE TABLE IF NOT EXISTS pandoc_cache"
             " (key string UNIQUE, value string)")]))
    (reify cache/ICache
      (contains-key? [_ k]
        (let [digest (hash-str k)]
          (some?
           (with-open [conn (next.jdbc/get-connection datasource)]
             (next.jdbc/execute-one!
              conn
              ["SELECT * FROM pandoc_cache WHERE key = ?"
               digest])))))
      (lookup [_ k]
        (let [digest (hash-str k)]
          (with-open [conn (next.jdbc/get-connection datasource)]
            (when-let [match
                       (next.jdbc/execute-one!
                        conn
                        ["SELECT value FROM pandoc_cache WHERE key = ?"
                         digest])]
              (:pandoc_cache/value match)))))
      (save [_ k v]
        (let [digest (hash-str k)]
          (with-open [conn (next.jdbc/get-connection datasource)]
            (next.jdbc/execute!
             conn
             [(str "INSERT INTO pandoc_cache (key, value) VALUES (?, ?)"
                   " ON CONFLICT (key) DO UPDATE SET value=?")
              digest
              v]))))
      (clear! [_]
        (with-open [conn (next.jdbc/get-connection datasource)]
          (next.jdbc/execute!
           conn
           ["DELETE FROM pandoc_cache"]))))))

(comment
  (binding [cache/*pandoc-cache* sqlite-cache]
    (time
     (pandoc/from-markdown (str "# Heading " (rand-int 5))))
    )

  (with-open [conn (next.jdbc/get-connection datasource)]
    (next.jdbc/execute!
     conn
     ["SELECT * FROM pandoc_cache LIMIT 20"]))

  (with-open [conn (next.jdbc/get-connection datasource)]
    (next.jdbc/execute-one!
     conn
     ["SELECT value FROM pandoc_cache"]))

  (with-open [conn (next.jdbc/get-connection datasource)]
    (next.jdbc/execute-one!
     conn
     ["SELECT value FROM pandoc_cache WHERE key=123"]))

  (cache/clear! sqlite-cache)

  )

(binding [cache/*pandoc-cache* sqlite-cache]
  (clerk/html (-> "# A _great_ heading"
                  pandoc/from-markdown
                  pandoc/to-html)))

(clerk/table
 (with-open [conn (next.jdbc/get-connection datasource)]
    (next.jdbc/execute!
     conn
     ["SELECT * FROM pandoc_cache LIMIT 20"])))
