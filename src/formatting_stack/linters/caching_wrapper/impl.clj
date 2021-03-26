(ns formatting-stack.linters.caching-wrapper.impl
  "`cache` needs to be defined in its own ns because otherwise,
  when modifying the `formatting-stack.linters.caching-wrapper` ns,
  either `(refresh)` or Eastwood can re-evaluate the `def cache`,
  erasing the cache and making things harder to debug/QA."
  (:require
   [clojure.string :as string]
   [clojure.tools.namespace.reload :as reload]
   [formatting-stack.util.caching :as util.caching]
   [nedap.speced.def :as speced])
  (:import
   (java.security MessageDigest)
   (java.util Base64)))

;; 7000: let's say 1000 files (big project), 2 linters (kondo + eastwood), only some of those files will change
;; *but* a given repl can have a very long uptime.
;; It is benefitial to keep large caches because one can checkout different Git branches, perform undo, etc
;; so one can end up linting content that already was linted in the past.

;; NOTE: the capacity is ignored by default (as the default is a SoftReference-based cache - see impl)
(def cache (util.caching/new-cache {:capacity 7000}))

(speced/defn sha256 [^String s]
  (let [input (-> s (.getBytes "UTF-8"))
        output (-> (MessageDigest/getInstance "SHA-256")
                   (doto (.update input))
                   .digest)]
    (-> (Base64/getEncoder) (.encodeToString output))))

(defn cache-key [wrapee file]
  ;; The linter must form part of the cache key:
  ;; a linter with different config can emit different reports.
  (->> [(pr-str wrapee)
        (slurp file)]
       (map sha256)
       (string/join "-")))
