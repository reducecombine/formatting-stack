(ns formatting-stack.formatters.zprint
  (:require
   [formatting-stack.protocols.formatter :as formatter]
   [formatting-stack.util :refer [process-in-parallel!]]
   [medley.core :refer [deep-merge]]
   [nedap.utils.modular.api :refer [implement]]
   [zprint.core :as zprint]))

;; XXX indent specs? https://github.com/kkinnear/zprint/issues/225

(def default-zprint-opts (if false
                           ;; f-s preset
                           {:color? false
                            :parallel? false ;; already taken care of by f-s
                            :style [:community ;; https://github.com/bbatsov/clojure-style-guide
                                    :rod ;; Modelled after https://github.com/kkinnear/zprint/issues/170
                                    :hiccup ;; Detect and format hiccup vectors
                                    :sort-dependencies]}
                           ;; cyco preset
                           {:color? false
                            :parallel? false ;; already taken care of by f-s
                            :width 120,
                            :style [:community :justified :map-nl :pair-nl :binding-nl :rod :hiccup :sort-dependencies],
                            :fn-map {}
                            #_ {"defn-inst" (-> zprint.config/default-zprint-options
                                                :style-map
                                                :rod
                                                :fn-map
                                                (get "defn"))}
                            :map {:sort? false}}))

(comment
  (zprint/set-options! default-zprint-opts)
  (zprint/zprint nil :explain))

(defn format! [{:keys [zprint-options]} files]
  (process-in-parallel! (fn [filename]
                          (let [contents (slurp filename)
                                formatted (zprint/zprint-file-str contents filename zprint-options)]
                            (when-not (= contents formatted)
                              (println "Fixing with zprint:" filename)
                              (spit filename formatted))))
                        files)
  nil)

(defn new [{:keys [zprint-options]
            :or   {zprint-options {}}}]
  (implement {:id ::id
              :zprint-options (deep-merge default-zprint-opts zprint-options)}
    formatter/--format! format!))
