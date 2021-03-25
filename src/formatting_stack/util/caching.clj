(ns formatting-stack.util.caching
  "Wraps `clojure.core.cache` with some known-safe patterns.

  See: https://dev.to/dpsutton/exploring-the-core-cache-api-57al

  Please double-check before growing this ns - most of `core.cache` is footguns!."
  (:require
   [clojure.core.cache.wrapped :as cache.wrapped]
   [nedap.speced.def :as speced])
  (:import
   (clojure.lang IAtom)))

(speced/defn new-cache
  "Creates a cache.

Returns a vanilla-looking `atom` (backed by said cache) that can be queried like any other atom."
  [^pos-int? capacity]
  (cache.wrapped/lru-cache-factory {} :threshold capacity))

(speced/defn get-or-set!
  "Gets the value associated to `k`, or associates `v`, returning it, if the cache entry was absent/expired.

  Note that there's no 'evict' or 'overwrite' function. Cache entries expire only when the cache decides so.
  Otherwise one can increase contention, create transient nil entries, etc.

  Note that caches that can be backed by multiple implementations (LRU, FIFO, etc) aren't necessarily atoms,
  so often trying to emulate atomic functionality can create unexpected effects.

  If you are only getting or setting (but not expecting *any*),
it is recommended that you wrap your invocation with `assert` strategically."
  [^{::speced/spec (partial instance? IAtom)}
   cache k v]
  (cache.wrapped/lookup-or-miss cache k (constantly v)))
