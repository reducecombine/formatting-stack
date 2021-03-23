(ns formatting-stack.linters.caching-wrapper.impl
  "`cache` is defined in its own ns because otherwise,
  when modifying the `formatting-stack.linters.caching-wrapper` ns,
  either `(refresh)` or Eastwood can re-evaluate the `def cache`,
  erasing the cache and making things harder to debug/QA.")

(def cache (atom {}))
