# port-lint Proposed Changes

**Generated:** 2026-08-31
**Source:** tmp
**Target:** src/commonMain/kotlin/io/github/kotlinmania/pulldowncmark

These are review proposals only. They are emitted when a Rust -> Kotlin pair matches only after fallback normalization, so the existing `port-lint` header is not an exact provenance match.

| Target file | Current header | Proposed header | Source path | Reason |
|-------------|----------------|-----------------|-------------|--------|
| `src/commonMain/kotlin/io/github/kotlinmania/pulldowncmark/Escape.kt` | `// port-lint: source pulldown-cmark/src/html.rs` | `// port-lint: source pulldown-cmark/tests/html.rs` | `pulldown-cmark/tests/html.rs` | `port-lint provenance header matched only by basename: 'pulldown-cmark/src/html.rs' vs expected 'pulldown-cmark/tests/html.rs'` |
