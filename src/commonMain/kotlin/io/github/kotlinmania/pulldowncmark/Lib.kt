// port-lint: source pulldown-cmark/tests/lib.rs
package io.github.kotlinmania.pulldowncmark

public enum class HeadingLevel(public val level: Int) {
    H1(1),
    H2(2),
    H3(3),
    H4(4),
    H5(5),
    H6(6);

    override fun toString(): String = when (this) {
        H1 -> "h1"
        H2 -> "h2"
        H3 -> "h3"
        H4 -> "h4"
        H5 -> "h5"
        H6 -> "h6"
    }

    public companion object {
        public fun from(value: Int): HeadingLevel? = when (value) {
            1 -> H1
            2 -> H2
            3 -> H3
            4 -> H4
            5 -> H5
            6 -> H6
            else -> null
        }

        public fun fromInt(value: Int): HeadingLevel? = from(value)
    }
}

public class InvalidHeadingLevel(public val value: Int) : Exception("Invalid heading level: $value")

public sealed class CodeBlockKind {
    public data object Indented : CodeBlockKind()
    public data class Fenced(public val info: CowStr) : CodeBlockKind()

    public fun isIndented(): Boolean = this is Indented
    public fun isFenced(): Boolean = this is Fenced
}

public enum class MetadataBlockKind {
    YamlStyle,
    PlusesStyle,
}

public enum class LinkType {
    Inline,
    Reference,
    ReferenceUnknown,
    Collapsed,
    CollapsedUnknown,
    Shortcut,
    ShortcutUnknown,
    Autolink,
    Email;

    public fun toUnknown(): LinkType = when (this) {
        Reference -> ReferenceUnknown
        Collapsed -> CollapsedUnknown
        Shortcut -> ShortcutUnknown
        else -> error("Cannot convert $this to unknown")
    }
}

public enum class Alignment {
    None,
    Left,
    Center,
    Right,
}

public data class HeadingAttribute(
    public val key: CowStr,
    public val value: CowStr?,
)

public data class Options(public val raw: UInt = 0u) {
    public fun contains(other: Options): Boolean = (raw and other.raw) == other.raw

    public fun insert(other: Options): Options = Options(raw or other.raw)

    public fun remove(other: Options): Options = Options(raw and other.raw.inv())

    public operator fun plus(other: Options): Options = Options(raw or other.raw)

    public operator fun minus(other: Options): Options = Options(raw and other.raw.inv())

    internal fun hasGfmFootnotes(): Boolean =
        contains(ENABLE_FOOTNOTES) && !contains(ENABLE_OLD_FOOTNOTES)

    public companion object {
        public val NONE: Options = Options(0u)
        public val ENABLE_TABLES: Options = Options(1u shl 1)
        public val ENABLE_FOOTNOTES: Options = Options(1u shl 2)
        public val ENABLE_STRIKETHROUGH: Options = Options(1u shl 3)
        public val ENABLE_TASKLISTS: Options = Options(1u shl 4)
        public val ENABLE_SMART_PUNCTUATION: Options = Options(1u shl 5)
        public val ENABLE_HEADING_ATTRIBUTES: Options = Options(1u shl 6)
        public val ENABLE_YAML_STYLE_METADATA_BLOCKS: Options = Options(1u shl 7)
        public val ENABLE_PLUSES_DELIMITED_METADATA_BLOCKS: Options = Options(1u shl 8)
        public val ENABLE_OLD_FOOTNOTES: Options = Options((1u shl 9) or (1u shl 2))

        public val EMPTY: Options = NONE
        public val ALL: Options = Options(
            ENABLE_TABLES.raw or
                ENABLE_FOOTNOTES.raw or
                ENABLE_STRIKETHROUGH.raw or
                ENABLE_TASKLISTS.raw or
                ENABLE_SMART_PUNCTUATION.raw or
                ENABLE_HEADING_ATTRIBUTES.raw or
                ENABLE_YAML_STYLE_METADATA_BLOCKS.raw or
                ENABLE_PLUSES_DELIMITED_METADATA_BLOCKS.raw or
                ENABLE_OLD_FOOTNOTES.raw
        )

        public fun empty(): Options = NONE
        public fun all(): Options = ALL
    }
}

public sealed class TagEnd {
    public data object Paragraph : TagEnd()
    public data class Heading(public val level: HeadingLevel) : TagEnd()
    public data object BlockQuote : TagEnd()
    public data object CodeBlock : TagEnd()
    public data object HtmlBlock : TagEnd()
    public data class List(public val isOrdered: Boolean) : TagEnd()
    public data object Item : TagEnd()
    public data object FootnoteDefinition : TagEnd()
    public data object Table : TagEnd()
    public data object TableHead : TagEnd()
    public data object TableRow : TagEnd()
    public data object TableCell : TagEnd()
    public data object Emphasis : TagEnd()
    public data object Strong : TagEnd()
    public data object Strikethrough : TagEnd()
    public data object Link : TagEnd()
    public data object Image : TagEnd()
    public data class MetadataBlock(public val kind: MetadataBlockKind) : TagEnd()
}

public sealed class Tag {
    public data object Paragraph : Tag()
    public data class Heading(
        public val level: HeadingLevel,
        public val id: CowStr? = null,
        public val classes: kotlin.collections.List<CowStr> = emptyList(),
        public val attrs: kotlin.collections.List<HeadingAttribute> = emptyList(),
    ) : Tag()
    public data object BlockQuote : Tag()
    public data class CodeBlock(public val kind: CodeBlockKind) : Tag()
    public data object HtmlBlock : Tag()
    public data class List(public val startNumber: Long? = null) : Tag()
    public data object Item : Tag()
    public data class FootnoteDefinition(public val label: CowStr) : Tag()
    public data class Table(public val alignments: kotlin.collections.List<Alignment>) : Tag()
    public data object TableHead : Tag()
    public data object TableRow : Tag()
    public data object TableCell : Tag()
    public data object Emphasis : Tag()
    public data object Strong : Tag()
    public data object Strikethrough : Tag()
    public data class Link(
        public val linkType: LinkType,
        public val destUrl: CowStr,
        public val title: CowStr,
        public val id: CowStr,
    ) : Tag()
    public data class Image(
        public val linkType: LinkType,
        public val destUrl: CowStr,
        public val title: CowStr,
        public val id: CowStr,
    ) : Tag()
    public data class MetadataBlock(public val kind: MetadataBlockKind) : Tag()

    public fun toEnd(): TagEnd = when (this) {
        is Paragraph -> TagEnd.Paragraph
        is Heading -> TagEnd.Heading(level)
        is BlockQuote -> TagEnd.BlockQuote
        is CodeBlock -> TagEnd.CodeBlock
        is HtmlBlock -> TagEnd.HtmlBlock
        is List -> TagEnd.List(startNumber != null)
        is Item -> TagEnd.Item
        is FootnoteDefinition -> TagEnd.FootnoteDefinition
        is Table -> TagEnd.Table
        is TableHead -> TagEnd.TableHead
        is TableRow -> TagEnd.TableRow
        is TableCell -> TagEnd.TableCell
        is Emphasis -> TagEnd.Emphasis
        is Strong -> TagEnd.Strong
        is Strikethrough -> TagEnd.Strikethrough
        is Link -> TagEnd.Link
        is Image -> TagEnd.Image
        is MetadataBlock -> TagEnd.MetadataBlock(kind)
    }
}

public sealed class Event {
    public data class Start(public val tag: Tag) : Event()
    public data class End(public val tagEnd: TagEnd) : Event()
    public data class Text(public val text: CowStr) : Event() {
        public constructor(s: String) : this(CowStr(s))
    }
    public data class Code(public val text: CowStr) : Event() {
        public constructor(s: String) : this(CowStr(s))
    }
    public data class Html(public val text: CowStr) : Event() {
        public constructor(s: String) : this(CowStr(s))
    }
    public data class InlineHtml(public val text: CowStr) : Event() {
        public constructor(s: String) : this(CowStr(s))
    }
    public data class FootnoteReference(public val label: CowStr) : Event() {
        public constructor(s: String) : this(CowStr(s))
    }
    public data object SoftBreak : Event()
    public data object HardBreak : Event()
    public data object Rule : Event()
    public data class TaskListMarker(public val checked: Boolean) : Event()
}

public data class BrokenLink(
    public val linkType: LinkType,
    public val reference: CowStr,
    public val destUrl: CowStr = CowStr(""),
    public val title: CowStr = CowStr(""),
    public val span: IntRange = 0..0,
)

public fun interface BrokenLinkCallback {
    public fun handleBrokenLink(brokenLink: BrokenLink): BrokenLink?
}

public data class SpannedEvent(
    public val event: Event,
    public val range: IntRange,
)
