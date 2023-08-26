;; Ensures you get the :dev alias when jacking in with CIDER!

((clojure-mode
  (cider-clojure-cli-aliases . "-A:dev")))
