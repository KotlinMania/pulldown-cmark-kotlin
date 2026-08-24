# port-lint Proposed Changes

**Generated:** 2026-08-24
**Source:** tmp/pulldown-cmark
**Target:** src

These are review proposals only. They are emitted when a Rust -> Kotlin pair matches only after fallback normalization, so the existing `port-lint` header is not an exact provenance match.

| Target file | Current header | Proposed header | Source path | Reason |
|-------------|----------------|-----------------|-------------|--------|
| `commonMain/kotlin/io/github/kotlinmania/pulldowncmark/Parse.kt` | `// port-lint: source tmp/pulldown-cmark/src/parse.rs` | `// port-lint: source parse.rs` | `parse.rs` | `port-lint provenance header matched only after fallback normalization: 'tmp/pulldown-cmark/src/parse.rs' vs expected 'parse.rs'` |
| `commonMain/kotlin/io/github/kotlinmania/pulldowncmark/Strings.kt` | `// port-lint: source tmp/pulldown-cmark/src/strings.rs` | `// port-lint: source strings.rs` | `strings.rs` | `port-lint provenance header matched only after fallback normalization: 'tmp/pulldown-cmark/src/strings.rs' vs expected 'strings.rs'` |
| `commonMain/kotlin/io/github/kotlinmania/pulldowncmark/Escape.kt` | `// port-lint: source tmp/pulldown-cmark/src/html.rs` | `// port-lint: source tests/html.rs` | `tests/html.rs` | `port-lint provenance header matched only by basename: 'tmp/pulldown-cmark/src/html.rs' vs expected 'tests/html.rs'` |
| `commonMain/kotlin/io/github/kotlinmania/pulldowncmark/FirstPass.kt` | `// port-lint: source tmp/pulldown-cmark/src/firstpass.rs` | `// port-lint: source firstpass.rs` | `firstpass.rs` | `port-lint provenance header matched only after fallback normalization: 'tmp/pulldown-cmark/src/firstpass.rs' vs expected 'firstpass.rs'` |
| `commonMain/kotlin/io/github/kotlinmania/pulldowncmark/Scanners.kt` | `// port-lint: source tmp/pulldown-cmark/src/scanners.rs` | `// port-lint: source scanners.rs` | `scanners.rs` | `port-lint provenance header matched only after fallback normalization: 'tmp/pulldown-cmark/src/scanners.rs' vs expected 'scanners.rs'` |
| `commonMain/kotlin/io/github/kotlinmania/pulldowncmark/Tree.kt` | `// port-lint: source tmp/pulldown-cmark/src/tree.rs` | `// port-lint: source tree.rs` | `tree.rs` | `port-lint provenance header matched only after fallback normalization: 'tmp/pulldown-cmark/src/tree.rs' vs expected 'tree.rs'` |
| `commonMain/kotlin/io/github/kotlinmania/pulldowncmark/Lib.kt` | `// port-lint: source tmp/pulldown-cmark/src/lib.rs` | `// port-lint: source lib.rs` | `lib.rs` | `port-lint provenance header matched only after fallback normalization: 'tmp/pulldown-cmark/src/lib.rs' vs expected 'lib.rs'` |
| `commonMain/kotlin/io/github/kotlinmania/pulldowncmark/Html.kt` | `// port-lint: source tmp/pulldown-cmark/src/html.rs` | `// port-lint: source html.rs` | `html.rs` | `port-lint provenance header matched only after fallback normalization: 'tmp/pulldown-cmark/src/html.rs' vs expected 'html.rs'` |
| `commonTest/kotlin/io/github/kotlinmania/pulldowncmark/PulldownCmarkTest.kt` | `// port-lint: source tmp/pulldown-cmark/tests/html.rs` | `// port-lint: source html.rs` | `html.rs` | `port-lint provenance header matched only by basename: 'tmp/pulldown-cmark/tests/html.rs' vs expected 'html.rs'` |
| `commonMain/kotlin/io/github/kotlinmania/pulldowncmark/LinkLabel.kt` | `// port-lint: source tmp/pulldown-cmark/src/linklabel.rs` | `// port-lint: source linklabel.rs` | `linklabel.rs` | `port-lint provenance header matched only after fallback normalization: 'tmp/pulldown-cmark/src/linklabel.rs' vs expected 'linklabel.rs'` |
| `commonMain/kotlin/io/github/kotlinmania/pulldowncmark/PunctTable.kt` | `// port-lint: source tmp/pulldown-cmark/src/puncttable.rs` | `// port-lint: source puncttable.rs` | `puncttable.rs` | `port-lint provenance header matched only after fallback normalization: 'tmp/pulldown-cmark/src/puncttable.rs' vs expected 'puncttable.rs'` |
| `commonMain/kotlin/io/github/kotlinmania/pulldowncmark/Utils.kt` | `// port-lint: source tmp/pulldown-cmark/src/utils.rs` | `// port-lint: source utils.rs` | `utils.rs` | `port-lint provenance header matched only after fallback normalization: 'tmp/pulldown-cmark/src/utils.rs' vs expected 'utils.rs'` |
| `commonMain/kotlin/io/github/kotlinmania/pulldowncmark/Entities.kt` | `// port-lint: source tmp/pulldown-cmark/src/entities.rs` | `// port-lint: source entities.rs` | `entities.rs` | `port-lint provenance header matched only after fallback normalization: 'tmp/pulldown-cmark/src/entities.rs' vs expected 'entities.rs'` |
