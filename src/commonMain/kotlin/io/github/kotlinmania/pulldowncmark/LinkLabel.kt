// port-lint: source linklabel.rs
package io.github.kotlinmania.pulldowncmark

public sealed class ReferenceLabel {
    public data class Link(public val label: CowStr) : ReferenceLabel()
    public data class Footnote(public val label: CowStr) : ReferenceLabel()
}

public data class UniCase(public val value: CowStr) {
    private val normalized: String = value.asString().lowercase()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UniCase) return false
        return normalized == other.normalized
    }

    override fun hashCode(): Int = normalized.hashCode()

    override fun toString(): String = value.asString()
}

public typealias LinkLabel = UniCase
public typealias FootnoteLabel = UniCase

public fun scanLinkLabelRest(
    text: String,
    linebreakHandler: ((String) -> Int?)?,
    isInTable: Boolean,
): Pair<Int, CowStr>? {
    var ix = 0
    var onlyWhiteSpace = true
    var codepoints = 0
    val label = StringBuilder()
    var mark = 0

    while (true) {
        if (codepoints >= 1000) {
            return null
        }
        if (ix >= text.length) return null
        val c = text[ix]
        when {
            c == '[' -> return null
            c == ']' -> break
            c == '|' && isInTable && ix != 0 && text[ix - 1] == '\\' -> {
                label.append(text.substring(mark, ix - 1))
                label.append('|')
                ix += 1
                onlyWhiteSpace = false
                mark = ix
            }
            c == '\\' && isInTable && ix + 1 < text.length && text[ix + 1] == '|' -> {
                label.append(text.substring(mark, ix))
                label.append('|')
                ix += 2
                codepoints += 1
                onlyWhiteSpace = false
                mark = ix
            }
            c == '\\' && ix + 1 < text.length && isAsciiPunctuation(text[ix + 1]) -> {
                ix += 2
                codepoints += 2
                onlyWhiteSpace = false
            }
            isAsciiWhitespace(c) -> {
                var whitespaces = 0
                var linebreaks = 0
                val whitespaceStart = ix

                while (ix < text.length && isAsciiWhitespace(text[ix])) {
                    val eolBytes = scanEol(text, ix)
                    if (eolBytes != null && eolBytes > 0) {
                        linebreaks += 1
                        if (linebreaks > 1) {
                            return null
                        }
                        ix += eolBytes
                        val handler = linebreakHandler ?: return null
                        val skipped = handler(text.substring(ix)) ?: return null
                        ix += skipped
                        whitespaces += 2
                    } else {
                        whitespaces += if (text[ix] == ' ') 1 else 2
                        ix += 1
                    }
                }
                if (whitespaces > 1) {
                    label.append(text.substring(mark, whitespaceStart))
                    label.append(' ')
                    mark = ix
                    codepoints += ix - whitespaceStart
                } else {
                    codepoints += 1
                }
            }
            else -> {
                onlyWhiteSpace = false
                ix += 1
                codepoints += 1
            }
        }
    }

    if (onlyWhiteSpace) {
        return null
    }

    val cow: CowStr = if (mark == 0) {
        val trimmed = text.substring(0, ix).trim { isAsciiWhitespace(it) }
        CowStr.from(trimmed)
    } else {
        label.append(text.substring(mark, ix))
        val trimmed = label.toString().trim { isAsciiWhitespace(it) }
        CowStr.from(trimmed)
    }
    return Pair(ix + 1, cow)
}
