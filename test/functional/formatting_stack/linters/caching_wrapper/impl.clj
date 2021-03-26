(ns functional.formatting-stack.linters.caching-wrapper.impl
  (:require
   [clojure.test :refer [are deftest is testing]]
   [formatting-stack.linters.caching-wrapper.impl :as sut])
  (:import
   (java.io File)))

(def filename-1 "project.clj")

(def filename-2 "README.md")

(deftest cache-key
  (testing "Different linters/contents result in different outputs"
    (are [linter filename expected] (testing [linter filename]
                                      (is (= expected
                                             (sut/cache-key linter
                                                            (File. filename))))
                                      true)
      ;; note that there are two SHA strings joined by a `-`. That facilitates understanding this test,
      ;; ensuring that all four strings are in fact different:
      {}     filename-1 "RBNvo1WzZ4oRRq0W9+hknpT7T8If536DEMBg9hyq/4o=-Nlp++hJX0cEogTEGDNvXNs/zUa9kZmLgzzZoUbX5OiQ="
      {:a 1} filename-1 "Ia2d9VITUzhB+bfw+cRcxIqaLx8aE4lnGbf67zSv3Ls=-Nlp++hJX0cEogTEGDNvXNs/zUa9kZmLgzzZoUbX5OiQ="
      {}     filename-2 "RBNvo1WzZ4oRRq0W9+hknpT7T8If536DEMBg9hyq/4o=-ndBia1Nq38TyQaCJsMIjXEMVgNPkKuCxAzgzDcOp+B4="
      {:a 1} filename-2 "Ia2d9VITUzhB+bfw+cRcxIqaLx8aE4lnGbf67zSv3Ls=-ndBia1Nq38TyQaCJsMIjXEMVgNPkKuCxAzgzDcOp+B4=")))
