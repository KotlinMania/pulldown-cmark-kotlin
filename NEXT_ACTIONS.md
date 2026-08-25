# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 11/12 (91.7%)
- **Function parity:** 179/288 matched (target 274) — 62.2%
- **Class/type parity:** 46/60 matched (target 135) — 76.7%
- **Combined symbol parity:** 225/348 matched (target 409) — 64.7%
- **Average inline-code cosine:** 0.37 (function body across 10 matched files)
- **Average documentation cosine:** 0.44 (doc text across 10 matched files)
- **Cheat-zeroed Files:** 2
- **Critical Issues:** 9 files with <0.60 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

No incomplete high-dependency files detected.

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. parse

- **Target:** `pulldowncmark.Parse [PROVENANCE-FALLBACK]`
- **Similarity:** 0.43
- **Dependents:** 0
- **Priority Score:** 451405.7
- **Functions:** 49/88 matched (target 56)
- **Missing functions:** `fmt`, `into_static`, `index`, `handle_broken_link`, `parser_with_extensions`, `node_size`, `body_size`, `single_open_fish_bracket`, `lone_hashtag`, `lots_of_backslashes`, `issue_320`, `issue_319`, `issue_303`, `issue_313`, `issue_311`, `issue_283`, `issue_289`, `issue_306`, `issue_305`, `another_emphasis_panic`, `offset_iter`, `reference_link_offsets`, `footnote_offsets`, `table_offset`, `table_cell_span`, `offset_iter_issue_378`, `offset_iter_issue_404`, `link_def_at_eof`, `no_footnote_refs_without_option`, `ref_def_at_eof`, `ref_def_cr_lf`, `no_dest_refdef`, `broken_links_called_only_once`, `simple_broken_link_callback`, `code_block_kind_check_fenced`, `code_block_kind_check_indented`, `ref_defs`, `common_lifetime_patterns_allowed`, `function`
- **Types:** 21/26 matched (target 65)
- **Missing types:** `BrokenLink`, `Output`, `HtmlScanGuard`, `BrokenLinkCallback`, `DefaultBrokenLinkCallback`
- **Tests:** 0/35 matched
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `tmp/pulldown-cmark/src/parse.rs` vs expected `parse.rs`
- **Proposed provenance header:** `// port-lint: source parse.rs` (current: `// port-lint: source tmp/pulldown-cmark/src/parse.rs`)
- **Lint issues:** 1

### 2. strings

- **Target:** `pulldowncmark.Strings [PROVENANCE-FALLBACK]`
- **Similarity:** 0.09
- **Dependents:** 0
- **Priority Score:** 273309.1
- **Functions:** 4/26 matched (target 22)
- **Missing functions:** `as_ref`, `hash`, `eq`, `deref`, `fmt`, `serialize`, `expecting`, `visit_borrowed_str`, `deserialize`, `borrow`, `inlinestr_ascii`, `inlinestr_unicode`, `cowstr_size`, `cowstr_char_to_string`, `max_inline_str_len_atleast_four`, `inlinestr_fits_twentytwo`, `inlinestr_not_fits_twentythree`, `small_boxed_str_clones_to_stack`, `cow_to_cow_str`, `cow_str_to_cow`, `cow_char_to_cow_str`, `variant_eq`
- **Types:** 2/7 matched (target 2)
- **Missing types:** `StringTooLongError`, `Error`, `Target`, `CowStrVisitor`, `Value`
- **Tests:** 0/12 matched
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `tmp/pulldown-cmark/src/strings.rs` vs expected `strings.rs`
- **Proposed provenance header:** `// port-lint: source strings.rs` (current: `// port-lint: source tmp/pulldown-cmark/src/strings.rs`)
- **Lint issues:** 1

### 3. firstpass

- **Target:** `pulldowncmark.FirstPass [PROVENANCE-FALLBACK]`
- **Similarity:** 0.60
- **Dependents:** 0
- **Priority Score:** 155804.0
- **Functions:** 40/54 matched (target 44)
- **Missing functions:** `iterate_special_bytes`, `scalar_iterate_special_bytes`, `compute_lookup`, `add_lookup_byte`, `compute_mask`, `process_mask`, `simd_iterate_special_bytes`, `check_expected_indices`, `simple_no_match`, `simple_match`, `single_open_fish`, `long_match`, `border_skip`, `exhaustive_search`
- **Types:** 3/4 matched (target 5)
- **Missing types:** `LookupTable`
- **Tests:** 0/7 matched
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `tmp/pulldown-cmark/src/firstpass.rs` vs expected `firstpass.rs`
- **Proposed provenance header:** `// port-lint: source firstpass.rs` (current: `// port-lint: source tmp/pulldown-cmark/src/firstpass.rs`)
- **Lint issues:** 1

### 4. scanners

- **Target:** `pulldowncmark.Scanners [ZERO] [PROVENANCE-FALLBACK]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 126410.0
- **Functions:** 51/63 matched (target 55)
- **Missing functions:** `new`, `scan_rev_while`, `char_from_codepoint`, `scan_attribute_name`, `scan_whitespace_with_newline_handler`, `scan_whitespace_with_newline_handler_without_buffer`, `scan_attribute_value`, `is_html_tag`, `overflow_list`, `overflow_by_addition`, `good_emails`, `bad_emails`
- **Types:** 1/1 matched (target 3)
- **Missing types:** _none_
- **Tests:** 0/4 matched
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `tmp/pulldown-cmark/src/scanners.rs` vs expected `scanners.rs`
- **Proposed provenance header:** `// port-lint: source scanners.rs` (current: `// port-lint: source tmp/pulldown-cmark/src/scanners.rs`)
- **Lint issues:** 1

### 5. tree

- **Target:** `pulldowncmark.Tree [PROVENANCE-FALLBACK]`
- **Similarity:** 0.45
- **Dependents:** 0
- **Priority Score:** 102705.5
- **Functions:** 14/23 matched (target 18)
- **Missing functions:** `new`, `add`, `sub`, `with_capacity`, `cur`, `fmt`, `debug_tree`, `index`, `index_mut`
- **Types:** 3/4 matched (target 3)
- **Missing types:** `Output`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `tmp/pulldown-cmark/src/tree.rs` vs expected `tree.rs`
- **Proposed provenance header:** `// port-lint: source tree.rs` (current: `// port-lint: source tmp/pulldown-cmark/src/tree.rs`)
- **Lint issues:** 1

### 6. lib

- **Target:** `pulldowncmark.Lib [STUB] [PROVENANCE-FALLBACK]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 31810.0
- **Functions:** 6/8 matched (target 15)
- **Missing functions:** `fmt`, `try_from`
- **Types:** 9/10 matched (target 44)
- **Missing types:** `Error`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `tmp/pulldown-cmark/src/lib.rs` vs expected `lib.rs`
- **Proposed provenance header:** `// port-lint: source lib.rs` (current: `// port-lint: source tmp/pulldown-cmark/src/lib.rs`)
- **Lint issues:** 1

### 7. html

- **Target:** `pulldowncmark.Html [PROVENANCE-FALLBACK]`
- **Similarity:** 0.54
- **Dependents:** 0
- **Priority Score:** 21104.6
- **Functions:** 7/9 matched (target 37)
- **Missing functions:** `new`, `write_html`
- **Types:** 2/2 matched (target 3)
- **Missing types:** _none_
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `tmp/pulldown-cmark/src/html.rs` vs expected `html.rs`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `tmp/pulldown-cmark/src/html.rs` vs expected `html.rs`
- **Provenance warning:** port-lint provenance header matched only by basename: `tmp/pulldown-cmark/tests/html.rs` vs expected `html.rs`
- **Proposed provenance header:** `// port-lint: source html.rs` (current: `// port-lint: source tmp/pulldown-cmark/src/html.rs`)
- **Proposed provenance header:** `// port-lint: source html.rs` (current: `// port-lint: source tmp/pulldown-cmark/src/html.rs`)
- **Proposed provenance header:** `// port-lint: source html.rs` (current: `// port-lint: source tmp/pulldown-cmark/tests/html.rs`)
- **Lint issues:** 3

### 8. linklabel

- **Target:** `pulldowncmark.LinkLabel [PROVENANCE-FALLBACK]`
- **Similarity:** 0.29
- **Dependents:** 0
- **Priority Score:** 20607.1
- **Functions:** 1/3 matched (target 4)
- **Missing functions:** `whitespace_normalization`, `return_carriage_linefeed_ok`
- **Types:** 3/3 matched (target 6)
- **Missing types:** _none_
- **Tests:** 0/2 matched
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `tmp/pulldown-cmark/src/linklabel.rs` vs expected `linklabel.rs`
- **Proposed provenance header:** `// port-lint: source linklabel.rs` (current: `// port-lint: source tmp/pulldown-cmark/src/linklabel.rs`)
- **Lint issues:** 1

### 9. puncttable

- **Target:** `pulldowncmark.PunctTable [PROVENANCE-FALLBACK]`
- **Similarity:** 0.32
- **Dependents:** 0
- **Priority Score:** 20406.8
- **Functions:** 2/4 matched (target 9)
- **Missing functions:** `test_ascii`, `test_unicode`
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 0/2 matched
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `tmp/pulldown-cmark/src/puncttable.rs` vs expected `puncttable.rs`
- **Proposed provenance header:** `// port-lint: source puncttable.rs` (current: `// port-lint: source tmp/pulldown-cmark/src/puncttable.rs`)
- **Lint issues:** 1

### 10. utils

- **Target:** `pulldowncmark.Utils [PROVENANCE-FALLBACK]`
- **Similarity:** 0.62
- **Dependents:** 0
- **Priority Score:** 10703.8
- **Functions:** 4/4 matched (target 12)
- **Missing functions:** _none_
- **Types:** 2/3 matched (target 2)
- **Missing types:** `Item`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `tmp/pulldown-cmark/src/utils.rs` vs expected `utils.rs`
- **Proposed provenance header:** `// port-lint: source utils.rs` (current: `// port-lint: source tmp/pulldown-cmark/src/utils.rs`)
- **Lint issues:** 1

### 11. entities

- **Target:** `pulldowncmark.Entities [PROVENANCE-FALLBACK]`
- **Similarity:** 0.40
- **Dependents:** 0
- **Priority Score:** 106.0
- **Functions:** 1/1 matched (target 2)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `tmp/pulldown-cmark/src/entities.rs` vs expected `entities.rs`
- **Proposed provenance header:** `// port-lint: source entities.rs` (current: `// port-lint: source tmp/pulldown-cmark/src/entities.rs`)
- **Lint issues:** 1

## Success Criteria

For each file to be considered "complete":
- **Similarity ≥ 0.85** (Excellent threshold)
- All public APIs ported
- All tests ported
- Documentation ported
- port-lint header present

