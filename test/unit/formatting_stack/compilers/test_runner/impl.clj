(ns unit.formatting-stack.compilers.test-runner.impl
  (:require
   [clojure.string :as string]
   [clojure.test :refer :all]
   [formatting-stack.compilers.test-runner.impl :as sut]
   [formatting-stack.project-parsing :refer [project-namespaces]]
   [nedap.utils.speced :as speced]))

(speced/defn make-ns [^keyword? k]
  (-> k
      str
      (string/replace "/" ".")
      (string/replace ":" "")
      symbol
      create-ns))

(deftest add-t
  (are [input expected] (= expected
                           (-> input make-ns sut/add-t))
    ::foo     "unit.formatting-stack.compilers.test-runner.impl.t-foo"
    ::foo.bar "unit.formatting-stack.compilers.test-runner.impl.foo.t-bar"))

(deftest sut-consumers
  (let [corpus (project-namespaces)]
    (are [input expected] (= expected
                             (sut/sut-consumers corpus input))
      (the-ns 'formatting-stack.strategies) [(the-ns 'unit.formatting-stack.strategies)])))

(deftest permutations
  (testing "Multi-segment ns"
    (is (= '("unit.com.example.thing"
             "test.com.example.thing"

             ;; note that this example doesn't make much sense but it is unavoidable:
             ;; `unit` could be anywhere in the middle of the ns
             "com.unit.example.thing"
             "com.test.example.thing"

             "com.example.unit.thing"
             "com.example.test.thing"

             "com.example.thing.unit"
             "com.example.thing.test")
           (sut/permutations (make-ns :com.example/thing) #{"test" "unit"}))))

  (testing "Single-segment ns"
    (is (= '("unit.thing"
             "test.thing"

             "thing.unit"
             "thing.test")
           (sut/permutations (make-ns :thing) #{"test" "unit"})))))

(deftest testable-namespaces
  (are [input expected] (= expected
                           (sut/testable-namespaces input))

    []
    []

    ;; `:test` metadata detection
    ["project.clj"
     "test/unit/formatting_stack/compilers/test_runner/impl.clj"
     "/"]
    [(the-ns 'unit.formatting-stack.compilers.test-runner.impl)]

    ;; `sut` alias detection
    ["src/formatting_stack/strategies.clj"]
    [(the-ns 'unit.formatting-stack.strategies)]))
