(ns formatting-stack.strategies.impl
  (:require
   [clojure.java.shell :refer [sh]]
   [clojure.string :as str]
   [clojure.tools.namespace.file :as file]
   [clojure.tools.namespace.parse :as parse]
   [clojure.tools.reader]
   [clojure.tools.reader.reader-types :refer [push-back-reader]]
   [formatting-stack.formatters.clean-ns.impl :refer [ns-form-of safely-read-ns-contents]])
  (:import
   (java.io File)))

;; modified, added, renamed
(def git-completely-staged-regex #"^(M|A|R)  ")

;; modified, added
(def git-not-completely-staged-regex #"^( M|AM|MM|AD| D|\?\?|) ")

(defn file-entries [& args]
  (->> args (apply sh) :out str/split-lines (filter seq)))

(def ^:dynamic *filter-existing-files?* true)

(defn readable?
  "Is this file readable to clojure.tools.reader? (given custom reader tags, unbalanced parentheses or such)"
  [^String filename]
  (if-not (-> filename File. .exists)
    true ;; undecidable
    (try
      (let [ns-obj (some-> filename ns-form-of parse/name-from-ns-decl the-ns)]
        (and (do
               (if-not ns-obj
                 true
                 (-> filename slurp (safely-read-ns-contents ns-obj)))
               true)
             (if-let [decl (-> filename file/read-file-ns-decl)]
               (do
                 (-> decl parse/deps-from-ns-decl) ;; no exceptions thrown
                 true)
               true)))
      (catch Exception e
        false))))

(defn extract-clj-files [files]
  (cond->> files
    true                     (filter #(re-find #"\.(clj|cljc|cljs|edn)$" %))
    *filter-existing-files?* (filter (fn [^String f]
                                       (-> f File. .exists)))
    true                     (remove #(str/ends-with? % "project.clj"))
    true                     (filter readable?)))
