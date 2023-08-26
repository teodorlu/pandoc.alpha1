(ns teodorlu.pandoc.alpha1
  (:require
   [babashka.process]
   [cheshire.core :as json]
   [clojure.string :as str]
   [teodorlu.pandoc.alpha1.cache :as cache]
   [babashka.fs :as fs]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; LOW LEVEL PANDOC WRAPPER

(defn- from-json-str [s]
  (json/parse-string s keyword))

(defn- to-json-str [x]
  (json/generate-string x))

(defn- run-pandoc* [stdin command]
  (let [process-handle (deref (babashka.process/process {:in stdin :out :string} command))]
    (when (= 0 (:exit process-handle))
      (:out process-handle))))

(defn- run-pandoc [stdin command]
  (if-let [cache cache/*pandoc-cache*]
    (let [k (pr-str (sorted-map :stdin stdin :command command))]
      (if (cache/contains-key? cache k)
        (cache/lookup cache k)
        (let [v (run-pandoc* stdin command)]
          (cache/save cache k v)
          v)))
    (run-pandoc* stdin command)))

(comment
  (time
   (from-markdown "# A header"))

  (def in-mem-cache (cache/in-memory-cache))

  (binding [cache/*pandoc-cache* in-mem-cache]
    (time
     (from-markdown "# A header")))

  (def file-cache (cache/file-cache))

  (binding [cache/*pandoc-cache* file-cache]
    (time
     (from-markdown "# A header")))

  (def some-cache-key "abc123")

  (cache/contains-key? file-cache some-cache-key)
  (cache/save file-cache some-cache-key "a solution!")
  (cache/lookup file-cache some-cache-key)

  (slurp (fs/expand-home "~/dev/iterate/mikrobloggeriet/o/olorm-1/index.md"))

  (binding [cache/*pandoc-cache* file-cache]
    (time
     (from-markdown "# Another header")))

  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; PANDOC IR HELPERS

(defn pandoc? [x]
  (and
   (map? x)
   (contains? x :pandoc-api-version)
   (contains? x :blocks)
   (contains? x :meta)))

(declare el->plaintext)

(defn els->plaintext [els]
  (str/join
   (->> els
        (map el->plaintext)
        (filter some?))))

(defn el->plaintext
  "Convert a pandoc expression to plaintext without shelling out to pandoc"
  [expr]
  (cond (= "Str" (:t expr))
        (:c expr)
        (= "MetaInlines" (:t expr))
        (els->plaintext (:c expr))
        (= "Space" (:t expr))
        " "
        (= "Para" (:t expr))
        (els->plaintext (:c expr))
        (= "Emph" (:t expr))
        (els->plaintext (:c expr))
        :else nil))

(defn set-title [pandoc title]
  (assert (pandoc? pandoc))
  (assoc-in pandoc [:meta :title] {:t "MetaInlines" :c [{:t "Str" :c title}]}))

(defn title [pandoc]
  (when-let [title-el (-> pandoc :meta :title)]
    (el->plaintext title-el)))

(defn header? [el ]
  (= (:t el) "Header"))

(defn h1? [el]
  (let [header-level (-> el :c first)]
    (and (header? el)
         (= header-level 1))))

(defn header->plaintext [el]
  (when (header? el)
    (els->plaintext (get-in el [:c 2]))))

(defn infer-title [pandoc]
  (or (title pandoc)
      (when-let [first-h1 (->> (:blocks pandoc)
                               (filter h1?)
                               first)]
        (header->plaintext first-h1))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; READ FROM FORMAT INTO IR

(defn from-markdown [markdown-str]
  (when (string? markdown-str)
    (from-json-str (run-pandoc markdown-str "pandoc --from markdown+smart --to json"))))

(defn from-html [html-str]
  (when (string? html-str)
    (from-json-str (run-pandoc html-str "pandoc --from html --to json"))))

(defn from-org [org-str]
  (when (string? org-str)
    (from-json-str (run-pandoc org-str "pandoc --from org+smart --to json"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; WRITE IR TO FORMAT

(defn to-html [pandoc]
  (when (pandoc? pandoc)
    (run-pandoc (to-json-str pandoc) "pandoc --from json --to html")))

(defn to-html-standalone [pandoc]
  (when (pandoc? pandoc)
    (run-pandoc (to-json-str pandoc) "pandoc --standalone --from json --to html")))

(defn to-markdown [pandoc]
  (when (pandoc? pandoc)
    (run-pandoc (to-json-str pandoc) "pandoc --from json --to markdown")))

(defn to-markdown-standalone [pandoc]
  (when (pandoc? pandoc)
    (run-pandoc (to-json-str pandoc) "pandoc --standalone --from json --to markdown")))

(defn to-org [pandoc]
  (when (pandoc? pandoc)
    (run-pandoc (to-json-str pandoc) "pandoc --from json --to org")))

(defn to-org-standalone [pandoc]
  (when (pandoc? pandoc)
    (run-pandoc (to-json-str pandoc) "pandoc --standalone --from json --to org")))
