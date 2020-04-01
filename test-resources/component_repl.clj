(ns component-repl
  "Documents a working setup for Component, as a runnable example.

  Not a part of the test suite.

  See also:

    * https://github.com/nedap/formatting-stack/blob/master/README.md
    * https://github.com/nedap/formatting-stack/wiki/FAQ
    * The `customization-example` sibling namespace."
  (:require
   [com.stuartsierra.component :as component]
   [com.stuartsierra.component.repl :as component.repl]
   [formatting-stack.component]
   [formatting-stack.git-status-formatter]))

(def linter-overrides
  {::id (reify formatting-stack.protocols.linter/Linter
          (--lint! [this filenames]
            [{:source   ::my-linter
              :level    :warning
              :column   40
              :line     6
              :msg      "Hello, I am a sample linter!"
              :filename "path.clj"}]))})

(defn init [_]
  (component/system-map :formatting-stack (formatting-stack.component/new {:overrides {:linters linter-overrides}})))

(comment

  (component.repl/set-init init)

  (component.repl/reset))
