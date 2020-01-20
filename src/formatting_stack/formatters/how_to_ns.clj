(ns formatting-stack.formatters.how-to-ns
  (:require
   [clojure.string :as str]
   [com.gfredericks.how-to-ns :as how-to-ns]
   [com.gfredericks.how-to-ns.main :as how-to-ns.main]
   [formatting-stack.protocols.formatter :as formatter]
   [formatting-stack.protocols.linter :as linter]
   [formatting-stack.util :refer [process-in-parallel!]]
   [medley.core :refer [deep-merge]]
   [nedap.utils.modular.api :refer [implement]]))

(def default-how-to-ns-opts {:require-docstring?      false
                             :sort-clauses?           true
                             ;; should be false, but https://git.io/fhMLm can break code:
                             :allow-refer-all?        true
                             :allow-extra-clauses?    false
                             :align-clauses?          false
                             :import-square-brackets? false})

(defn format! [{:keys [how-to-ns-options]} files]
  (->> (remove #(str/ends-with? % ".edn") files)
       (process-in-parallel! (fn [filename]
                               (how-to-ns.main/fix [filename] how-to-ns-options))))
  nil)

(defn lint! [{:keys [how-to-ns-options]} files]
  (->> (remove #(str/ends-with? % ".edn") files)
       (process-in-parallel! (fn [filename]
                               (try
                                 (let [contents  (slurp filename)
                                       formatted (how-to-ns/format-initial-ns-str contents how-to-ns-options)]
                                   (when-not (= contents formatted)
                                     {:filename filename
                                      :diff (#'how-to-ns.main/unified-diff
                                             (str filename)
                                             contents
                                             formatted)
                                      :msg "Badly formatted namespace"
                                      :source :how-to-ns/ns}))
                                 (catch Exception e
                                   {:filename filename
                                    :msg (str "Failed to parse " filename)
                                    :exception e
                                    :source :how-to-ns/ns
                                    :level :exception}))))
       (remove nil?)))

(defn new [{:keys [how-to-ns-options]
            :or   {how-to-ns-options {}}}]
  (implement {:id ::id
              :how-to-ns-options (deep-merge default-how-to-ns-opts how-to-ns-options)}
    linter/--lint! lint!
    formatter/--format! format!))
