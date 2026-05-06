# pulldown-cmark-kotlin in Kotlin

[![GitHub link](https://img.shields.io/badge/GitHub-KotlinMania%2Fpulldown--cmark--kotlin-blue.svg)](https://github.com/KotlinMania/pulldown-cmark-kotlin)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kotlinmania/pulldown-cmark-kotlin)](https://central.sonatype.com/artifact/io.github.kotlinmania/pulldown-cmark-kotlin)
[![Build status](https://img.shields.io/github/actions/workflow/status/KotlinMania/pulldown-cmark-kotlin/ci.yml?branch=main)](https://github.com/KotlinMania/pulldown-cmark-kotlin/actions)

This is a Kotlin Multiplatform line-by-line transliteration port of [`raphlinus/pulldown-cmark`](https://github.com/raphlinus/pulldown-cmark).

**Original Project:** This port is based on [`raphlinus/pulldown-cmark`](https://github.com/raphlinus/pulldown-cmark). All design credit and project intent belong to the upstream authors; this repository is a faithful port to Kotlin Multiplatform with no behavioural changes intended.

### Porting status

This is an **in-progress port**. The goal is feature parity with the upstream Rust crate while providing a native Kotlin Multiplatform API. Every Kotlin file carries a `// port-lint: source <path>` header naming its upstream Rust counterpart so the AST-distance tool can track provenance.

---

## Upstream README — `raphlinus/pulldown-cmark`

> The text below is reproduced and lightly edited from [`https://github.com/raphlinus/pulldown-cmark`](https://github.com/raphlinus/pulldown-cmark). It is the upstream project's own description and remains under the upstream authors' authorship; links have been rewritten to absolute upstream URLs so they continue to resolve from this repository.

## pulldown-cmark

[![Tests](https://github.com/pulldown-cmark/pulldown-cmark/actions/workflows/rust.yml/badge.svg)](https://github.com/pulldown-cmark/pulldown-cmark/actions/workflows/rust.yml)
[![Docs](https://docs.rs/pulldown-cmark/badge.svg)](https://docs.rs/pulldown-cmark)
[![Crates.io](https://img.shields.io/crates/v/pulldown-cmark.svg?maxAge=2592000)](https://crates.io/crates/pulldown-cmark)

[Documentation](https://docs.rs/pulldown-cmark/)

This library is a pull parser for [CommonMark](http://commonmark.org/), written
in [Rust](http://www.rust-lang.org/). It comes with a simple command-line tool,
useful for rendering to HTML, and is also designed to be easy to use from as
a library.

It is designed to be:

- Fast; a bare minimum of allocation and copying
- Safe; written in pure Rust with no unsafe blocks (except in the opt-in SIMD feature)
- Versatile; in particular source-maps are supported
- Correct; the goal is 100% compliance with the [CommonMark spec](http://spec.commonmark.org/)

Further, it optionally supports parsing footnotes,
[Github flavored tables](https://github.github.com/gfm/#tables-extension-),
[Github flavored task lists](https://github.github.com/gfm/#task-list-items-extension-) and
[strikethrough](https://github.github.com/gfm/#strikethrough-extension-).

Rustc 1.71.1 or newer is required to build the crate.

## Example

Example usage:

```rust
// Create parser with example Markdown text.
let markdown_input = "hello world";
let parser = pulldown_cmark::Parser::new(markdown_input);

// Write to a new String buffer.
let mut html_output = String::new();
pulldown_cmark::html::push_html(&mut html_output, parser);
assert_eq!(&html_output, "<p>hello world</p>\n");
```

## Why a pull parser?

There are many parsers for Markdown and its variants, but to my knowledge none
use pull parsing. Pull parsing has become popular for XML, especially for
memory-conscious applications, because it uses dramatically less memory than
constructing a document tree, but is much easier to use than push parsers. Push
parsers are notoriously difficult to use, and also often error-prone because of
the need for user to delicately juggle state in a series of callbacks.

In a clean design, the parsing and rendering stages are neatly separated, but
this is often sacrificed in the name of performance and expedience. Many Markdown
implementations mix parsing and rendering together, and even designs that try
to separate them (such as the popular [hoedown](https://github.com/hoedown/hoedown)),
make the assumption that the rendering process can be fully represented as a
serialized string.

Pull parsing is in some sense the most versatile architecture. It's possible to
drive a push interface, also with minimal memory, and quite straightforward to
construct an AST. Another advantage is that source-map information (the mapping
between parsed blocks and offsets within the source text) is readily available;
you can call `into_offset_iter()` to create an iterator that yields `(Event, Range)`
pairs, where the second element is the event's corresponding range in the source
document.

While manipulating ASTs is the most flexible way to transform documents,
operating on iterators is surprisingly easy, and quite efficient. Here, for
example, is the code to transform soft line breaks into hard breaks:

```rust
let parser = parser.map(|event| match event {
	Event::SoftBreak => Event::HardBreak,
	_ => event
});
```

Or expanding an abbreviation in text:

```rust
let parser = parser.map(|event| match event {
	Event::Text(text) => Event::Text(text.replace("abbr", "abbreviation").into()),
	_ => event
});
```

Another simple example is code to determine the max nesting level:

```rust
let mut max_nesting = 0;
let mut level = 0;
for event in parser {
	match event {
		Event::Start(_) => {
			level += 1;
			max_nesting = std::cmp::max(max_nesting, level);
		}
		Event::End(_) => level -= 1,
		_ => ()
	}
}
```

Note that consecutive text events can happen due to the manner in which the
parser evaluates the source. A utility `TextMergeStream` exists to improve
the comfort of iterating the events:

```rust
use pulldown_cmark::{Event, Parser, Options};

let markdown_input = "Hello world, this is a ~~complicated~~ *very simple* example.";

let iterator = TextMergeStream::new(Parser::new(markdown_input));

for event in iterator {
    match event {
        Event::Text(text) => println!("{}", text),
        _ => {}
    }
}
```

There are some basic but fully functional examples of the usage of the crate in the
`examples` directory of this repository.

## Using Rust idiomatically

A lot of the internal scanning code is written at a pretty low level (it
pretty much scans byte patterns for the bits of syntax), but the external
interface is designed to be idiomatic Rust.

Pull parsers are at heart an iterator of events (start and end tags, text,
and other bits and pieces). The parser data structure implements the
Rust Iterator trait directly, and Event is an enum. Thus, you can use the
full power and expressivity of Rust's iterator infrastructure, including
for loops and `map` (as in the examples above), collecting the events into
a vector (for recording, playback, and manipulation), and more.

Further, the `Text` event (representing text) is a small copy-on-write string.
The vast majority of text fragments are just
slices of the source document. For these, copy-on-write gives a convenient
representation that requires no allocation or copying, but allocated
strings are available when they're needed. Thus, when rendering text to
HTML, most text is copied just once, from the source document to the
HTML buffer.

When using the pulldown-cmark's own HTML renderer, make sure to write to a buffered
target like a `Vec<u8>` or `String`. Since it performs many (very) small writes, writing
directly to stdout, files, or sockets is detrimental to performance. Such writers can
be wrapped in a [`BufWriter`](https://doc.rust-lang.org/std/io/struct.BufWriter.html).

## Build options

By default, the binary is built as well. If you don't want/need it, then build like this:

```bash
> cargo build --no-default-features
```

Or add this package as dependency of your project using `cargo add`:

```bash
> cargo add pulldown-cmark --no-default-features
```

SIMD accelerated scanners are available for the x64 platform from version 0.5 onwards. To
enable them, build with simd feature:

```bash
> cargo build --release --features simd
```

Or add this package as dependency of your project with the feature using `cargo add`:

```bash
> cargo add pulldown-cmark --no-default-features --features=simd
```

For a higher release performance you may want this configuration in your profile release:

```
lto = true
codegen-units = 1
panic = "abort"
```

### `no_std` support

`no_std` support can be enabled by compiling with `--no-default-features` to
disable `std` support and `--features hashbrown` for `Hash` collections that are only
defined in `std` for internal usages in crate. For example:

```toml
[dependencies]
pulldown-cmark = { version = "*", default-features = false, features = ["hashbrown", "other features"] }
```

To support both `std` and `no_std` builds in project, you can use the following
in your `Cargo.toml`:

```toml
[features]
default = ["std", "other features"]

std = ["pulldown-cmark/std"]
hashbrown = ["pulldown-cmark/hashbrown"]
other_features = []
[dependencies]
pulldown-cmark = { version = "*", default-features = false }
```

## Authors

The main author is Raph Levien. The implementation of the new design (v0.3+) was
completed by Marcus Klaas de Vries. Since 2023, the development has been driven
by Martín Pozo, Michael Howell, Roope Salmi and Martin Geisler.

## License

This software is under the MIT license. See details in [license file](https://github.com/raphlinus/pulldown-cmark/blob/HEAD/LICENSE).

## Contributions

We gladly accept contributions via GitHub pull requests. Please see
[CONTRIBUTING.md](https://github.com/raphlinus/pulldown-cmark/blob/HEAD/CONTRIBUTING.md) for more details.

---

## About this Kotlin port

### Installation

```kotlin
dependencies {
    implementation("io.github.kotlinmania:pulldown-cmark-kotlin:0.1.0-SNAPSHOT")
}
```

### Building

```bash
./gradlew build
./gradlew test
```

### Targets

- macOS arm64
- Linux x64
- Windows mingw-x64
- iOS arm64 / simulator-arm64 (Swift export + XCFramework)
- JS (browser + Node.js)
- Wasm-JS (browser + Node.js)
- Android (API 24+)

### Porting guidelines

See [AGENTS.md](AGENTS.md) and [CLAUDE.md](CLAUDE.md) for translator discipline, port-lint header convention, and Rust → Kotlin idiom mapping.

### License

This Kotlin port is distributed under the same MIT license as the upstream [`raphlinus/pulldown-cmark`](https://github.com/raphlinus/pulldown-cmark). See [LICENSE](LICENSE) (and any sibling `LICENSE-*` / `NOTICE` files mirrored from upstream) for the full text.

Original work copyrighted by the pulldown-cmark authors.  
Kotlin port: Copyright (c) 2026 Sydney Renee and The Solace Project.

### Acknowledgments

Thanks to the [`raphlinus/pulldown-cmark`](https://github.com/raphlinus/pulldown-cmark) maintainers and contributors for the original Rust implementation. This port reproduces their work in Kotlin Multiplatform; bug reports about upstream design or behavior should go to the upstream repository.
