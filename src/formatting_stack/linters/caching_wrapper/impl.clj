(ns formatting-stack.linters.caching-wrapper.impl
  "`cache` needs to be defined in its own ns because otherwise,
  when modifying the `formatting-stack.linters.caching-wrapper` ns,
  either `(refresh)` or Eastwood can re-evaluate the `def cache`,
  erasing the cache and making things harder to debug/QA."
  (:require
   [clojure.tools.namespace.reload :as reload]
   [nedap.speced.def :as speced])
  (:import
   (java.security MessageDigest)
   (java.util Base64)))

(def cache (atom {}))

(speced/defn sha256 [^String s]
  (let [input (-> s (.getBytes "UTF-8"))
        output (-> (MessageDigest/getInstance "SHA-256")
                   (doto (.update input))
                   .digest)]
    (-> (Base64/getEncoder) (.encodeToString output))))
