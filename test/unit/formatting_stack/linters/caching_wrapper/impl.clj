(ns unit.formatting-stack.linters.caching-wrapper.impl
  (:require
   [clojure.test :refer [are deftest is testing]]
   [formatting-stack.linters.caching-wrapper.impl :as sut]))

(deftest sha256
  (are [input expected] (testing input
                          (is (= expected
                                 (sut/sha256 input)))
                          true)
    ""         "47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU="
    "a"        "ypeBEsobvcr6wjGzmiPcTaeG7/gUfE5yuYB3ha/uSLs="
    "bsdfsdfs" "QzDCfbsDDYCYY5maTJC3a7VPy7qsrWknuZ8GQz2QnRw="))
