(ns formatting-stack.linters.caching-wrapper
  (:require
   [cisco.tools.namespace.impl.key-calculation :refer [key-of]]
   [clojure.string :as string]
   [formatting-stack.linters.caching-wrapper.impl :as impl]
   [formatting-stack.protocols.linter :as linter]
   [formatting-stack.protocols.spec :as protocols.spec]
   [formatting-stack.util :refer [ensure-sequential process-in-parallel!]]
   [formatting-stack.util :refer [rcomp]]
   [nedap.speced.def :as speced]
   [nedap.utils.modular.api :refer [implement]]
   [nedap.utils.spec.api :refer [check!]])
  (:import
   (java.io File)))

(defn lint! [{::keys [wrappee cache key-fn]
              :or    {cache  impl/cache
                      key-fn key-of}}
             filenames]
  (let [files (->> filenames
                   (map (speced/fn [^String filename]
                          [(File. filename) filename]))
                   (into {}))
        file-keys (->> files
                       (map (fn [[file filename]]
                              [filename (key-fn file)]))
                       (into {}))
        corpus-to-lint (->> file-keys
                            (keep (speced/fn [[^string? filename
                                               ^string? sha]]
                                    (when-not (contains? @cache sha)
                                      [filename sha])))
                            (into {}))
        filenames-to-lint (or (keys corpus-to-lint)
                              [])
        found-in-cache (->> file-keys
                            vals
                            (keep (partial get @cache)))
        _ (assert (check! (partial every? (set filenames))
                          (keys corpus-to-lint))
                  "Every filename of `corpus-to-lint` should be a member of `filenames`")
        ;; NOTE: no parallelism should be introduced here.
        ;; linters themselves already decide/implement parallelism in a per-case basis.
        linting-result (some->> filenames-to-lint
                                ;; important optimization: don't run the given linter at all for empty workloads
                                ;; (as the linter may have fixed costs)
                                seq
                                (linter/lint! wrappee))
        result (->> linting-result
                    (map (speced/fn [{:keys [filename]
                                      :as   ^::protocols.spec/report report}]
                           (speced/let [^some? sha (get corpus-to-lint filename)]
                             (swap! cache assoc sha report)
                             report)))
                    (into found-in-cache)
                    (filter identity)
                    (mapcat ensure-sequential))]
    (->> file-keys
         vals
         (remove (partial get @cache))
         ;; cache success cases:
         (run! (fn [sha]
                 (swap! cache assoc sha nil))))
    result))

(defn new [wrappee]
  (implement {:id ::id
              ::wrappee wrappee}
    linter/--lint! lint!))
