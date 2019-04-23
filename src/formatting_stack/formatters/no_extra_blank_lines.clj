(ns formatting-stack.formatters.no-extra-blank-lines
  "Ensures that no three consecutive newlines happen in a given file.

  That can happen naturally, or because of other formatters' intricacies."
  (:require
   [clojure.string :as string]
   [formatting-stack.protocols.formatter]
   [formatting-stack.util :refer [process-in-parallel!]]))

(defn without-extra-newlines [s]
  (-> s (string/replace #"(\n\n)(\n)+" "$1")))

(defrecord Formatter []
  formatting-stack.protocols.formatter/Formatter
  (format! [this files]
    (->> files
         (process-in-parallel! (fn [filename]
                                 (let [contents (-> filename slurp)
                                       formatted (without-extra-newlines contents)]
                                   (when-not (= contents formatted)
                                     (println "Removing extra blank lines:" filename)
                                     (spit filename contents))))))))