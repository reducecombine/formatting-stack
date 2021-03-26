(ns formatting-stack.util.caching
  "Wraps `clojure.core.cache` with some known-safe patterns.

  See:

  * https://dev.to/dpsutton/exploring-the-core-cache-api-57al
  * https://ask.clojure.org/index.php/10370/why-do-core-caches-deftypes-not-implement-equality

  Please double-check before growing this ns - most of `core.cache` is footguns!."
  (:refer-clojure :exclude [contains? get])
  (:require
   [clojure.core.cache]
   [clojure.core.cache.wrapped :as cache.wrapped]
   [clojure.spec.alpha :as spec]
   [nedap.speced.def :as speced])
  (:import
   (clojure.core.cache SoftCache)
   (clojure.lang IAtom)
   (java.lang.ref SoftReference)))

(def base-value {})

(speced/defn new-cache
  "Creates a cache.

Please only this namespace's public API to operate upon said cache."
  [{:keys      [^pos-int? capacity]
    cache-type :type
    ;; SoftReference-based caches are the default for not affecting CI, limited computers etc
    :or        {cache-type (System/getProperty "formatting-stack.caching.default-type" "soft")}}]
  (let [base base-value]
    ;; The cache object is hidden under an 'impl' key in face of
    ;; https://ask.clojure.org/index.php/10370/why-do-core-caches-deftypes-not-implement-equality
    ;; which comments advise against treating objects of type e.g. `clojure.core.cache.LRUCache` as plain maps:
    {::cache-impl (case cache-type
                    "soft"
                    (cache.wrapped/soft-cache-factory base)

                    "lru"
                    (cache.wrapped/lru-cache-factory base :threshold capacity))}))

(spec/def ::atom (partial instance? IAtom))

(speced/defn get-or-set!
  "Gets the value associated to `k`, or associates `v`, returning it, if the cache entry was absent/expired.

  Note that there's no 'evict' or 'overwrite' function. Cache entries expire only when the cache decides so.
  Otherwise one can increase contention, create transient nil entries, etc.

  Note that caches that can be backed by multiple implementations (LRU, FIFO, etc) aren't necessarily atoms,
  so often trying to emulate atomic functionality can create unexpected effects.

  If you are only getting or setting (but not expecting *any* of those, ambiguously),
it is recommended that you wrap your invocation with `assert` strategically."
  [{^::atom cache ::cache-impl}, k, v]
  (cache.wrapped/lookup-or-miss cache k (constantly v)))

(speced/defn get
  [{^::atom cache ::cache-impl}, k]
  (cache.wrapped/lookup cache k))

(speced/defn contains?
  [{^::atom cache ::cache-impl}, k]
  (cache.wrapped/has? cache k))

(speced/defn ^map? current-value
  "Returns the cache's entire value as a vanilla Clojure data structure that is safe to use in every way."
  [{^::atom cache ::cache-impl}]
  (let [v @cache
        soft? (instance? SoftCache v)
        target (if-not soft?
                 v
                 (-> ^SoftCache v .-cache))
        ;; get rid of a specific type (such as `clojure.core.cache.LRUCache`)
        ;; so that `=` always works properly:
        result (if-not soft?
                 (into base-value target)
                 (into base-value
                       (map (speced/fn [[k, ^SoftReference v]]
                              [k (-> v .get)]))
                       target))]
    (into {}
          (map (fn [[k v]]
                 ;; cleanup implementation details:
                 [k (if (= :clojure.core.cache/nil v)
                      nil
                      v)]))
          result)))
