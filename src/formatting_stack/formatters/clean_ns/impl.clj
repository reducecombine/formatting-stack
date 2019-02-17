(ns formatting-stack.formatters.clean-ns.impl
  (:require
   [clojure.tools.namespace.parse :as parse]
   [clojure.tools.reader :as tools.reader]
   [clojure.tools.reader.reader-types :refer [push-back-reader]]
   [clojure.walk :as walk]
   [com.gfredericks.how-to-ns :as how-to-ns]
   [refactor-nrepl.config]
   [refactor-nrepl.ns.clean-ns :refer [clean-ns]]))

(defn ns-form-of [filename]
  (-> filename slurp push-back-reader parse/read-ns-decl))

(defn used-namespace-names
  "NOTE: this returns the set of namespace _names_ that are used, not the set of namespaces that are used.

  e.g. a namespace which is exclusively used through `:refer` has a 'unused namespace name',
  but it is not unused (because it is referred).

  Use with caution accordingly, and not as a exclusive source of truth."
  [filename]
  (let [buffer (slurp filename)
        ns-obj (-> filename ns-form-of parse/name-from-ns-decl the-ns)
        _ (assert ns-obj)
        [ns-form & contents] (binding [tools.reader/*alias-map* (ns-aliases ns-obj)]
                               (tools.reader/read-string {} (str "[ " buffer " ]")))
        _ (assert (and (list? ns-form)
                       (= 'ns (first ns-form)))
                  (str "Filename " filename ": expected the first form to be of `(ns ...)` type."))
        requires (-> ns-form parse/deps-from-ns-decl set)
        result (atom #{})
        aliases-keys (-> ns-obj ns-aliases keys set)
        expand-ident (fn [ident]
                       (when-let [n (some-> ident namespace symbol)]
                         (cond (requires n)
                               n

                               (aliases-keys n)
                               (-> ns-obj ns-aliases (get n) str symbol))))]
    (walk/postwalk (fn traverse [x]
                     (some->> x meta (walk/postwalk traverse))
                     (when-let [n (and (ident? x) (expand-ident x))]
                       (when (requires n)
                         (swap! result conj n)))
                     x)
                   contents)
    @result))

(defn clean-ns-form [{:keys [how-to-ns-opts refactor-nrepl-opts filename original-ns-form]}]
  (let [whitelist (into [] (map str) (used-namespace-names filename))]
    (binding [refactor-nrepl.config/*config* (-> refactor-nrepl-opts
                                                 (update :libspec-whitelist into whitelist))]
      (when-let [c (clean-ns {:path filename})]
        (-> c
            (pr-str)
            (how-to-ns/format-ns-str how-to-ns-opts))))))
