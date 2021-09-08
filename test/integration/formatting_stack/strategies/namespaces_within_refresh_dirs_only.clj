(ns integration.formatting-stack.strategies.namespaces-within-refresh-dirs-only
  (:require
   [clojure.set :as set]
   [clojure.test :refer [deftest is]]
   [formatting-stack.strategies :as sut]
   [formatting-stack.util :refer [rcomp]]
   [nedap.speced.def :as speced]))

(deftest namespaces-within-refresh-dirs-only
  (speced/let [^{::speced/spec (rcomp count (partial < 100))}
               all-files (sut/all-files :files [])
               result (sut/namespaces-within-refresh-dirs-only :files all-files)]
    (is (seq result)
        "Returns non-empty results (since f-s itself has namespaces within `src`, `test`, etc)")

    (is (< (count result)
           (count all-files))
        "Doesn't include files outside the refresh dirs")

    (is (set/subset? (set result)
                     (set all-files))
        "Is a subtractive (and not additive) mechanism")))
