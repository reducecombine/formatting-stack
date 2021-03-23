(ns unit.formatting-stack.linters.caching-wrapper
  (:require
   [clojure.test :refer [are deftest is testing]]
   [formatting-stack.linters.caching-wrapper :as sut]
   [formatting-stack.protocols.linter :as linter]
   [nedap.speced.def :as speced]
   [nedap.utils.modular.api :refer [implement]])
  (:import
   (java.io File)))

(defn sample-linter [{:keys [file-c-is-ok? call-count]} filenames]
  (swap! call-count inc)
  (let [base [{:filename (first filenames)
               :source   ::sample-linter
               :level    :warning
               :msg      "."}]
        chosen (if (and (= ["c.clj"] filenames)
                        @file-c-is-ok?)
                 []
                 base)]
    (swap! file-c-is-ok? not)
    chosen))

(deftest lint!
  (let [cache (atom {})
        file-c-is-ok? (atom true)
        call-count (atom 0)
        wrapee (implement {:id ::id
                           :file-c-is-ok? file-c-is-ok?
                           :call-count call-count}
                 linter/--lint! sample-linter)
        key-fn (speced/fn [^File f]
                 (condp = (-> f str)
                   "a.clj" "hash-of-a"
                   "b.clj" "hash-of-b"
                   "c.clj" (if @file-c-is-ok?
                             "hash-of-c--ok"
                             "hash-of-c--faulty")))
        ;; The expected total of invocations to the linter.
        ;; It will not be increased in the last "row" of the `are`, precisely because of the performed caching:
        expected-final-count 4]
    (are [desc input expected-call-count expected-cache] (testing input
                                                           (sut/lint! {::sut/wrappee wrapee
                                                                       ::sut/key-fn  key-fn
                                                                       ::sut/cache   cache}
                                                                      input)
                                                           (is (= expected-cache
                                                                  @cache))
                                                           (is (= expected-call-count
                                                                  @call-count))
                                                           true)
      "Linting a file adds its sha and reports to the cache"
      ["a.clj"]                 1                    {"hash-of-a" {:filename "a.clj",
                                                                   :source   ::sample-linter
                                                                   :level    :warning,
                                                                   :msg      "."}}

      "Linting a file adds its sha and reports to the cache, accretively relative to prior data"
      ["b.clj"]                 2                    {"hash-of-a" {:filename "a.clj",
                                                                   :source   ::sample-linter
                                                                   :level    :warning,
                                                                   :msg      "."},
                                                      "hash-of-b" {:filename "b.clj",
                                                                   :source   ::sample-linter
                                                                   :level    :warning,
                                                                   :msg      "."}}

      "Linting a file that didn't have any faults associates a `nil` value, for caching the success"
      ["c.clj"]                 3                    {"hash-of-a"     {:filename "a.clj",
                                                                       :source   ::sample-linter
                                                                       :level    :warning,
                                                                       :msg      "."},
                                                      "hash-of-b"     {:filename "b.clj",
                                                                       :source   ::sample-linter
                                                                       :level    :warning,
                                                                       :msg      "."}
                                                      "hash-of-c--ok" nil}

      "Linting a file that changed adds its sha and report to the cache, without removing the prior cache entry
(because the file might go back to that state later, e.g. the user performs 'undo')"
      ["c.clj"]                 expected-final-count {"hash-of-a"         {:filename "a.clj",
                                                                           :source   ::sample-linter
                                                                           :level    :warning,
                                                                           :msg      "."},
                                                      "hash-of-b"         {:filename "b.clj",
                                                                           :source   ::sample-linter
                                                                           :level    :warning,
                                                                           :msg      "."}
                                                      "hash-of-c--ok"     nil
                                                      "hash-of-c--faulty" {:filename "c.clj",
                                                                           :source   ::sample-linter
                                                                           :level    :warning,
                                                                           :msg      "."}}

      "Linting already-linted files does not increase the call count"
      ["a.clj" "b.clj" "c.clj"] expected-final-count {"hash-of-a"         {:filename "a.clj",
                                                                           :source
                                                                           ::sample-linter
                                                                           :level    :warning,
                                                                           :msg      "."},
                                                      "hash-of-b"         {:filename "b.clj",
                                                                           :source   ::sample-linter
                                                                           :level    :warning,
                                                                           :msg      "."}
                                                      "hash-of-c--ok"     nil
                                                      "hash-of-c--faulty" {:filename "c.clj",
                                                                           :source   ::sample-linter
                                                                           :level    :warning,
                                                                           :msg      "."}})))
