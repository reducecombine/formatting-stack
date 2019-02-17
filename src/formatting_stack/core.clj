(ns formatting-stack.core
  (:require
   [formatting-stack.formatters.cider :as formatters.cider]
   [formatting-stack.formatters.clean-ns :as formatters.clean-ns]
   [formatting-stack.formatters.cljfmt :as formatters.cljfmt]
   [formatting-stack.formatters.how-to-ns :as formatters.how-to-ns]
   [formatting-stack.formatters.newlines :as formatters.newlines]
   [formatting-stack.indent-specs :refer [default-third-party-indent-specs]]
   [formatting-stack.linters.bikeshed :as linters.bikeshed]
   [formatting-stack.linters.eastwood :as linters.eastwood]
   [formatting-stack.protocols.compiler :as protocols.compiler]
   [formatting-stack.protocols.formatter :as protocols.formatter]
   [formatting-stack.protocols.linter :as protocols.linter]
   [formatting-stack.strategies :as strategies]))

(def default-strategies [strategies/git-completely-staged])

(def extended-strategies [strategies/git-completely-staged
                          strategies/git-not-completely-staged])

(defn default-formatters [third-party-indent-specs]
  (let [opts {:third-party-indent-specs third-party-indent-specs}]
    [(formatters.cider/map->Formatter (assoc opts :strategies extended-strategies))
     (formatters.cljfmt/map->Formatter opts)
     (formatters.how-to-ns/map->Formatter opts)
     (formatters.newlines/map->Formatter opts)
     (formatters.clean-ns/map->Formatter (assoc opts :strategies (conj default-strategies
                                                                       strategies/do-not-use-cached-results!)))]))

(def default-linters [(linters.bikeshed/map->Bikeshed {:strategies extended-strategies})
                      (linters.eastwood/map->Eastwood {:strategies extended-strategies})])

(def default-compilers [])

(defn files-from-strategies [strategies]
  (->> strategies
       (mapcat (fn [f]
                 (f)))
       distinct))

(defn process! [method members category-strategies default-strategies]
  ;; `memoize` rationale: results are cached not for performance,
  ;; but for avoiding the scenario where one `member` alters the git status,
  ;; so the subsequent `member`s' strategies won't perceive the same set of files than the first one.
  ;; e.g. cljfmt may operate upon `strategies/git-completely-staged`, formatting some files accordingly.
  ;; Then `how-to-ns`, which follows the same strategy, would perceive a dirty git status.
  ;; Accordingly it would do nothing, which is undesirable.
  (let [files (memoize (fn [strategies]
                         (files-from-strategies strategies)))]
    (doseq [member members]
      (let [{specific-strategies :strategies} member
            strategies (or specific-strategies category-strategies default-strategies)]
        (->> strategies files (method member))))))

(defn format! [& {:keys [strategies
                         third-party-indent-specs
                         formatters
                         linters
                         compilers]}]
  ;; the following `or` clauses ensure that Components don't pass nil values
  (let [strategies               (or strategies default-strategies)
        third-party-indent-specs (or third-party-indent-specs default-third-party-indent-specs)
        formatters               (or formatters (default-formatters third-party-indent-specs))
        linters                  (or linters default-linters)
        compilers                (or compilers default-compilers)
        {formatters-strategies
         :formatters
         linters-strategies
         :linters
         compilers-strategies
         :compilers
         default-strategies
         :default}               strategies]
    (process! protocols.formatter/format! formatters formatters-strategies strategies)
    (process! protocols.linter/lint!      linters    linters-strategies    strategies)
    (process! protocols.compiler/compile! compilers  compilers-strategies  strategies)))
