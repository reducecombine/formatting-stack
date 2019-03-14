(ns formatting-stack.strategies.impl
  (:require
   [clojure.java.shell :refer [sh]]
   [clojure.string :as str])
  (:import
   (java.io File)))

;; modified, added, renamed
(def git-completely-staged-regex #"^(M|A|R)  ")

;; modified, added
(def git-not-completely-staged-regex #"^( M|AM|MM|AD| D|\?\?|) ")

(defn file-entries [& args]
  (->> args (apply sh) :out str/split-lines (filter seq)))

(def ^:dynamic *filter-existing-files?* true)

(defn extract-clj-files [files]
  (cond->> files
    true                     (filter #(re-find #"\.(clj|cljc|cljs|edn)$" %))
    *filter-existing-files?* (filter (fn [f]
                                       (-> f File. .exists)))
    true                     (remove #(str/ends-with? % "project.clj"))))
