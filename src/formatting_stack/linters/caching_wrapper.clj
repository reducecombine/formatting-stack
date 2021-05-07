(ns formatting-stack.linters.caching-wrapper
  (:require
   [clojure.spec.alpha :as spec]
   [formatting-stack.linters.caching-wrapper.impl :as impl]
   [formatting-stack.protocols.linter :as linter]
   [formatting-stack.protocols.spec :as protocols.spec]
   [formatting-stack.util :refer [ensure-sequential]]
   [formatting-stack.util.caching :as util.caching]
   [nedap.speced.def :as speced]
   [nedap.utils.modular.api :refer [implement]]
   [nedap.utils.spec.api :refer [check!]])
  (:import
   (java.io File)))

(speced/def-with-doc ::wrapee
  "Cacheable linters must have an :id so that their cache entries don't clash with other linters
(both having an empty configuration)."
  (spec/and (partial speced/satisfies? linter/Linter)
            (spec/keys :req-un [::id])))

(speced/defn lint! [{::keys [^::wrapee wrapee cache key-fn]
                     :or    {cache  impl/cache
                             key-fn impl/cache-key}}
                    filenames]
  (speced/let [files (->> filenames
                          (map (speced/fn [^String filename]
                                 [(File. filename) filename]))
                          (into {}))
               file-keys (->> files
                              (map (fn [[file filename]]
                                     [filename, (key-fn wrapee file)]))
                              (into {}))
               corpus-to-lint (->> file-keys
                                   (keep (speced/fn [[^string? filename
                                                      ^string? sha]]
                                           (when-not (util.caching/contains? cache sha)
                                             [filename sha])))
                                   (into {}))
               filenames-to-lint (or (keys corpus-to-lint)
                                     [])
               ^::protocols.spec/reports
               found-in-cache (->> file-keys
                                   vals
                                   (keep (partial util.caching/get cache))
                                   (apply concat))
               _ (assert (check! (partial every? (set filenames))
                                 (keys corpus-to-lint))
                         "Every filename of `corpus-to-lint` should be a member of `filenames`")
               ;; NOTE: no parallelism should be introduced here.
               ;; linters themselves already decide/implement parallelism in a per-case basis.
               linting-result (some->> filenames-to-lint
                                       ;; important optimization: don't run the given linter at all for empty workloads
                                       ;; (as the linter may have fixed costs)
                                       seq
                                       (linter/lint! wrapee))
               _ (->> linting-result
                      (group-by :filename) ;; XXX note that this key is absent on exceptions
                      (run! (speced/fn [[filename  ^::protocols.spec/reports reports]]
                              (assert (some? (get corpus-to-lint filename))
                                      {:f filename
                                       :c (keys corpus-to-lint)})
                              (speced/let [reports (->> reports
                                                        ;; ensure that the cached values are stable:
                                                        ;; XXX pr-str is potentially expensive
                                                        (sort-by pr-str)
                                                        vec)
                                           ^some? sha (get corpus-to-lint filename)
                                           ^{::speced/spec #{reports}} ;; assert that the cache succeeded in setting its value
                                           ;; set the cache:
                                           v (util.caching/get-or-set! cache sha reports)]))))
               result (->> linting-result
                           (into found-in-cache)
                           (filter identity)
                           (mapcat ensure-sequential))]
    (->> file-keys
         vals
         (remove (partial util.caching/get cache))
         ;; cache success cases:
         (run! (speced/fn ^nil? [sha]
                 (util.caching/get-or-set! cache sha nil))))
    result))

(defn new [wrapee]
  (implement {:id ::id
              ::wrapee wrapee}
    linter/--lint! lint!))
