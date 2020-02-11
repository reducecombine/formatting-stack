(ns formatting-stack.util
  (:require
   [clojure.spec.alpha :as spec]
   [clojure.tools.namespace.file :as file]
   [clojure.tools.namespace.parse :as parse]
   [medley.core :refer [find-first]]
   [nedap.speced.def :as speced]
   [nedap.utils.collections.eager :refer [partitioning-pmap]]
   [nedap.utils.collections.seq :refer [distribute-evenly-by]]
   [nedap.utils.spec.predicates :refer [present-string?]])
  (:import
   (clojure.lang IBlockingDeref IPending)
   (java.io File)))

(defmacro rcomp
  "Like `comp`, but in reverse order.
  With it, members of `->>` chains can consistently be read left-to-right."
  [& fns]
  (cons `comp (reverse fns)))

(defn dissoc-by [m f]
  (->> m
       (filter (rcomp first f))
       (into {})))

(def ns-name-from-filename (rcomp file/read-file-ns-decl parse/name-from-ns-decl))

(defn serialized-writer [out]
  (let [state (atom {})
        write! (fn [input]
                 (swap! state update (Thread/currentThread) vec)
                 (swap! state update (Thread/currentThread) conj input)
                 nil)]
    (proxy [java.io.Writer] []
      (append [& input]
        (write! input))
      (close [])
      (flush []
        (binding [*out* out
                  *err* *out*]
          (let [contents (-> @state (get (Thread/currentThread)))]
            (->> contents
                 (apply concat)
                 (apply str)
                 (print))))
        (swap! state assoc (Thread/currentThread) [])
        nil)
      (write [& input]
        (write! input)))))

(defmacro with-serialized-output
  {:style/indent 0}
  [& forms]
  `(binding [*out* (serialized-writer *out*)
             *err* *out*
             *flush-on-newline* true]
     ~@forms))

(speced/defn report-processing-error [^Throwable e, filename]
  {:level     :exception
   :source    :formatting-stack/report-processing-error
   :filename  filename
   :msg       "Encountered an exception"
   :exception e})

(spec/def ::non-lazy-result
  (fn [x]
    (cond
      (sequential? x)              (vector? x)
      (instance? IBlockingDeref x) false
      (instance? IPending x)       false
      true                         true)))

(defn process-in-parallel! [f files]
  (->> files
       (distribute-evenly-by {:f (fn [^String filename]
                                   (-> (File. filename) .length))})
       (partitioning-pmap (bound-fn [filename]
                            (try
                              (let [v (f filename)]
                                (assert (spec/valid? ::non-lazy-result v)
                                        (pr-str "Parallel processing shouldn't return lazy computations"
                                                f))
                                v)
                              (catch Exception e
                                (report-processing-error e filename))
                              (catch AssertionError e
                                (report-processing-error e filename)))))))

(defn require-from-ns-decl [ns-decl]
  (->> ns-decl
       (find-first (fn [x]
                     (and (sequential? x)
                          (#{:require} (first x)))))))

;; `require` is not thread-safe in Clojure.
(def require-lock
  (Object.))

(speced/defn try-require [^present-string? filename]
  (try
    (when-let [namespace (some-> filename file/read-file-ns-decl parse/name-from-ns-decl)]
      (locking require-lock
        (require namespace)))
    true
    (catch Exception _
      false)))

(speced/defn ensure-coll [^some? x]
  (if (coll? x)
    x
    [x]))

;; Rationale: https://github.com/nedap/formatting-stack/pull/109/files#r376891779
(speced/defn ensure-sequential [^some? x]
  (if (sequential? x)
    x
    [x]))

(def ansi-colors
  {:reset  "[0m"
   :red    "[031m"
   :green  "[032m"
   :yellow "[033m"
   :cyan   "[036m"
   :grey   "[037m"})

(defn colorize [s color]
  (str \u001b (ansi-colors color) s \u001b (ansi-colors :reset)))
