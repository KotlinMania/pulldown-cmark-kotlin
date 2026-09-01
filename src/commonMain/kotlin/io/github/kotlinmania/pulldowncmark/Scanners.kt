// port-lint: source scanners.rs
package io.github.kotlinmania.pulldowncmark

internal class HtmlScanGuard(
    var cdata: Int = 0,
    var declaration: Int = 0,
    var processing: Int = 0,
    var comment: Int = 0,
)

// Sorted HTML tags for binary search
private val HTML_TAGS =
    arrayOf(
        "address",
        "article",
        "aside",
        "base",
        "basefont",
        "blockquote",
        "body",
        "caption",
        "center",
        "col",
        "colgroup",
        "dd",
        "details",
        "dialog",
        "dir",
        "div",
        "dl",
        "dt",
        "fieldset",
        "figcaption",
        "figure",
        "footer",
        "form",
        "frame",
        "frameset",
        "h1",
        "h2",
        "h3",
        "h4",
        "h5",
        "h6",
        "head",
        "header",
        "hr",
        "html",
        "iframe",
        "legend",
        "li",
        "link",
        "main",
        "menu",
        "menuitem",
        "nav",
        "noframes",
        "ol",
        "optgroup",
        "option",
        "p",
        "param",
        "search",
        "section",
        "summary",
        "table",
        "tbody",
        "td",
        "tfoot",
        "th",
        "thead",
        "title",
        "tr",
        "track",
        "ul",
    )

/**
 * Analysis of the beginning of a line, including indentation and container markers.
 */
internal class LineStart(
    private val text: String,
    val startIx: Int = 0,
    var ix: Int = startIx,
    private var tabStart: Int = startIx,
    private var spacesRemaining: Int = 0,
    private var minHruleOffset: Int = 0,
) {
    fun copy(): LineStart =
        LineStart(
            text = text,
            startIx = startIx,
            ix = ix,
            tabStart = tabStart,
            spacesRemaining = spacesRemaining,
            minHruleOffset = minHruleOffset,
        )

    fun scanSpace(nSpace: Int): Boolean = scanSpaceInner(nSpace) == 0

    fun scanSpaceUpto(nSpace: Int): Int = nSpace - scanSpaceInner(nSpace)

    private fun scanSpaceInner(nSpaceParam: Int): Int {
        var nSpace = nSpaceParam
        val nFromRemaining = spacesRemaining.coerceAtMost(nSpace)
        spacesRemaining -= nFromRemaining
        nSpace -= nFromRemaining

        while (nSpace > 0 && ix < text.length) {
            when (text[ix]) {
                ' ' -> {
                    ix++
                    nSpace--
                }
                '\t' -> {
                    val spaces = 4 - (ix - tabStart) % 4
                    ix++
                    tabStart = ix
                    val n = spaces.coerceAtMost(nSpace)
                    nSpace -= n
                    spacesRemaining = spaces - n
                }
                else -> break
            }
        }
        return nSpace
    }

    fun scanAllSpace() {
        spacesRemaining = 0
        while (ix < text.length && (text[ix] == ' ' || text[ix] == '\t')) {
            ix++
        }
    }

    fun isAtEol(): Boolean {
        if (ix >= text.length) return true
        val c = text[ix]
        return c == '\r' || c == '\n'
    }

    private fun scanCh(c: Char): Boolean {
        if (ix < text.length && text[ix] == c) {
            ix++
            return true
        }
        return false
    }

    fun scanBlockquoteMarker(): Boolean {
        val save = copy()
        scanSpace(3)
        if (scanCh('>')) {
            scanSpace(1)
            return true
        }
        restore(save)
        return false
    }

    fun scanListMarker(): Triple<Char, Long, Int>? {
        val save = copy()
        val indent = scanSpaceUpto(4)
        if (indent < 4 && ix < text.length) {
            val c = text[ix]
            if (c == '-' || c == '+' || c == '*') {
                if (ix >= minHruleOffset) {
                    val hruleRes = scanHrule(text, ix)
                    if (hruleRes != null && hruleRes > 0) {
                        restore(save)
                        return null
                    } else if (hruleRes != null && hruleRes <= 0) {
                        minHruleOffset = ix - hruleRes
                    }
                }
                ix++
                if (scanSpace(1) || isAtEol()) {
                    return finishListMarker(c, 0L, indent + 2, save)
                }
            } else if (c.isDigit()) {
                val startIx = ix
                var curIx = ix + 1
                var value = (c - '0').toLong()
                while (curIx < text.length && curIx - startIx < 10) {
                    val digitChar = text[curIx]
                    curIx++
                    if (digitChar.isDigit()) {
                        value = value * 10 + (digitChar - '0').toLong()
                    } else if (digitChar == ')' || digitChar == '.') {
                        ix = curIx
                        if (scanSpace(1) || isAtEol()) {
                            return finishListMarker(digitChar, value, indent + 1 + curIx - startIx, save)
                        } else {
                            break
                        }
                    } else {
                        break
                    }
                }
            }
        }
        restore(save)
        return null
    }

    private fun finishListMarker(c: Char, start: Long, initialIndent: Int, save: LineStart): Triple<Char, Long, Int> {
        var indent = initialIndent
        if (scanBlankLine(text, ix) != null) {
            return Triple(c, start, indent)
        }
        val postIndent = scanSpaceUpto(4)
        if (postIndent < 4) {
            indent += postIndent
        } else {
            restore(save)
        }
        return Triple(c, start, indent)
    }

    fun scanTaskListMarker(): Boolean? {
        val save = copy()
        scanSpaceUpto(3)
        if (!scanCh('[')) {
            restore(save)
            return null
        }
        if (ix >= text.length) {
            restore(save)
            return null
        }
        val c = text[ix]
        val isChecked =
            when {
                isAsciiWhitespaceNoNl(c) -> {
                    ix++
                    false
                }
                c == 'x' || c == 'X' -> {
                    ix++
                    true
                }
                else -> {
                    restore(save)
                    return null
                }
            }
        if (!scanCh(']')) {
            restore(save)
            return null
        }
        if (ix >= text.length || !isAsciiWhitespaceNoNl(text[ix])) {
            restore(save)
            return null
        }
        return isChecked
    }

    fun bytesScanned(): Int = ix - startIx

    val bytesScanned: Int get() = ix - startIx

    fun remainingSpace(): Int = spacesRemaining

    fun restore(saved: LineStart) {
        ix = saved.ix
        tabStart = saved.tabStart
        spacesRemaining = saved.spacesRemaining
        minHruleOffset = saved.minHruleOffset
    }

    fun restoreFrom(saved: LineStart) = restore(saved)
}

internal fun isAsciiWhitespace(c: Char): Boolean =
    c in '\t'..'\r' || c == ' '

internal fun isAsciiWhitespaceNoNl(c: Char): Boolean =
    c == '\t' || c == '\u000B' || c == '\u000C' || c == ' '

internal fun isAsciiAlpha(c: Char): Boolean =
    c in 'a'..'z' || c in 'A'..'Z'

internal fun isAsciiAlphanumeric(c: Char): Boolean =
    c in '0'..'9' || c in 'a'..'z' || c in 'A'..'Z'

internal fun isAsciiLetterDigitDash(c: Char): Boolean =
    c == '-' || isAsciiAlphanumeric(c)

internal fun isDigit(c: Char): Boolean =
    c in '0'..'9'

internal fun isValidUnquotedAttrValueChar(c: Char): Boolean =
    c != '\'' && c != '"' && c != ' ' && c != '=' && c != '>' && c != '<' && c != '`' && c != '\n' && c != '\r'

internal fun scanCh(text: String, ix: Int, c: Char): Int =
    if (ix < text.length && text[ix] == c) 1 else 0

internal inline fun scanWhile(text: String, startIx: Int, predicate: (Char) -> Boolean): Int {
    var i = startIx
    while (i < text.length && predicate(text[i])) {
        i++
    }
    return i - startIx
}

internal fun scanChRepeat(text: String, startIx: Int, c: Char): Int =
    scanWhile(text, startIx) { it == c }

internal fun scanWhitespaceNoNl(text: String, startIx: Int): Int =
    scanWhile(text, startIx) { isAsciiWhitespaceNoNl(it) }

internal fun scanAttrValueChars(text: String, startIx: Int): Int =
    scanWhile(text, startIx) { isValidUnquotedAttrValueChar(it) }

internal fun scanEol(text: String, ix: Int): Int? {
    if (ix >= text.length) return 0
    return when (text[ix]) {
        '\n' -> 1
        '\r' -> if (ix + 1 < text.length && text[ix + 1] == '\n') 2 else 1
        else -> null
    }
}

internal fun scanBlankLine(text: String, startIx: Int): Int? {
    val ws = scanWhitespaceNoNl(text, startIx)
    val eol = scanEol(text, startIx + ws) ?: return null
    return ws + eol
}

internal fun scanNextLine(text: String, startIx: Int): Int {
    val idx = text.indexOf('\n', startIx)
    return if (idx >= 0) idx + 1 - startIx else text.length - startIx
}

internal fun scanClosingCodeFence(text: String, startIx: Int, fenceChar: Char, nFenceChar: Int): Int? {
    if (startIx >= text.length) return 0
    var i = startIx
    val numFenceCharsFound = scanChRepeat(text, i, fenceChar)
    if (numFenceCharsFound < nFenceChar) return null
    i += numFenceCharsFound
    val numTrailingSpaces = scanChRepeat(text, i, ' ')
    i += numTrailingSpaces
    return if (scanEol(text, i) != null) i - startIx else null
}

internal fun scanClosingMetadataBlock(text: String, startIx: Int, fenceChar: Char): Int? {
    var i = startIx
    var numFenceCharsFound = scanChRepeat(text, i, fenceChar)
    if (numFenceCharsFound != 3) {
        if (fenceChar == '-') {
            numFenceCharsFound = scanChRepeat(text, i, '.')
            if (numFenceCharsFound != 3) return null
        } else {
            return null
        }
    }
    i += numFenceCharsFound
    val numTrailingSpaces = scanChRepeat(text, i, ' ')
    i += numTrailingSpaces
    return if (scanEol(text, i) != null) i - startIx else null
}

internal fun calcIndent(text: String, startIx: Int, max: Int): Pair<Int, Int> {
    var spaces = 0
    var offset = 0
    var i = startIx
    while (i < text.length) {
        offset = i - startIx
        val c = text[i]
        when (c) {
            ' ' -> {
                spaces++
                if (spaces == max) break
            }
            '\t' -> {
                val newSpaces = spaces + 4 - (spaces and 3)
                if (newSpaces > max) break
                spaces = newSpaces
            }
            else -> break
        }
        i++
    }
    return Pair(offset, spaces)
}

/**
 * Scan hrule opening sequence.
 * Positive return = size of line containing hrule.
 * Negative return = -min_offset before which no hrule can appear.
 */
internal fun scanHrule(text: String, startIx: Int): Int? {
    if (text.length - startIx < 3) return 0
    val c = text[startIx]
    if (c != '*' && c != '-' && c != '_') return 0
    var n = 0
    var i = startIx
    while (i < text.length) {
        val c2 = text[i]
        when {
            c2 == '\n' || c2 == '\r' -> {
                val eol = scanEol(text, i) ?: 0
                i += eol
                break
            }
            c2 == c -> n++
            c2 == ' ' || c2 == '\t' -> {}
            else -> return -(i - startIx)
        }
        i++
    }
    return if (n >= 3) i - startIx else -(i - startIx)
}

internal fun scanAtxHeading(text: String, startIx: Int): HeadingLevel? {
    val level = scanChRepeat(text, startIx, '#')
    val nextChar = if (startIx + level < text.length) text[startIx + level] else null
    if (nextChar == null || isAsciiWhitespace(nextChar)) {
        return HeadingLevel.from(level)
    }
    return null
}

internal fun scanSetextHeading(text: String, startIx: Int): Pair<Int, HeadingLevel>? {
    if (startIx >= text.length) return null
    val c = text[startIx]
    val level =
        when (c) {
            '=' -> HeadingLevel.H1
            '-' -> HeadingLevel.H2
            else -> return null
        }
    var i = startIx + 1 + scanChRepeat(text, startIx + 1, c)
    val blank = scanBlankLine(text, i) ?: return null
    i += blank
    return Pair(i - startIx, level)
}

internal fun scanTableHead(text: String, startIx: Int): Pair<Int, List<Alignment>> {
    val (indentOffset, spaces) = calcIndent(text, startIx, 4)
    if (spaces > 3 || startIx + indentOffset >= text.length) {
        return Pair(0, emptyList())
    }
    var i = startIx + indentOffset
    val cols = mutableListOf<Alignment>()
    var activeCol = Alignment.None
    var startCol = true
    var foundPipe = false
    var foundHyphen = false
    var foundHyphenInCol = false

    if (i < text.length && text[i] == '|') {
        i++
        foundPipe = true
    }

    while (i < text.length) {
        val eol = scanEol(text, i)
        if (eol != null) {
            i += eol
            break
        }
        val c = text[i]
        when (c) {
            ' ' -> {}
            ':' -> {
                activeCol =
                    when {
                        startCol && activeCol == Alignment.None -> Alignment.Left
                        !startCol && activeCol == Alignment.Left -> Alignment.Center
                        !startCol && activeCol == Alignment.None -> Alignment.Right
                        else -> activeCol
                    }
                startCol = false
            }
            '-' -> {
                startCol = false
                foundHyphen = true
                foundHyphenInCol = true
            }
            '|' -> {
                startCol = true
                foundPipe = true
                cols.add(activeCol)
                activeCol = Alignment.None
                if (!foundHyphenInCol) {
                    return Pair(0, emptyList())
                }
                foundHyphenInCol = false
            }
            else -> {
                return Pair(0, emptyList())
            }
        }
        i++
    }

    if (!startCol) {
        cols.add(activeCol)
    }
    if (!foundPipe || !foundHyphen) {
        return Pair(0, emptyList())
    }
    return Pair(i - startIx, cols)
}

internal fun scanCodeFence(text: String, startIx: Int): Pair<Int, Char>? {
    if (startIx >= text.length) return null
    val c = text[startIx]
    if (c != '`' && c != '~') return null
    val i = 1 + scanChRepeat(text, startIx + 1, c)
    if (i >= 3) {
        if (c == '`') {
            val nextLine = scanNextLine(text, startIx + i)
            val lineContent = text.substring(startIx + i, startIx + i + nextLine)
            if (lineContent.contains('`')) return null
        }
        return Pair(i, c)
    }
    return null
}

internal fun scanMetadataBlock(text: String, startIx: Int, yamlStyleEnabled: Boolean, plusesStyleEnabled: Boolean): Pair<Int, Char>? {
    if (!yamlStyleEnabled && !plusesStyleEnabled) return null
    if (startIx >= text.length) return null
    val c = text[startIx]
    if (!((c == '-' && yamlStyleEnabled) || (c == '+' && plusesStyleEnabled))) return null
    val i = 1 + scanChRepeat(text, startIx + 1, c)
    val nextLine = scanNextLine(text, startIx + i)
    for (idx in (startIx + i) until (startIx + i + nextLine)) {
        if (!isAsciiWhitespace(text[idx])) return null
    }
    if (i == 3) {
        var j = startIx + i
        var firstLine = true
        while (j < text.length) {
            j += scanNextLine(text, j)
            val closed = scanClosingMetadataBlock(text, j, c) != null
            if (firstLine) {
                if (closed || scanBlankLine(text, j) != null) return null
                firstLine = false
            }
            if (closed) return Pair(i, c)
        }
        return null
    }
    return null
}

internal fun scanBlockquoteStart(text: String, startIx: Int): Int? {
    if (startIx < text.length && text[startIx] == '>') {
        val space = if (startIx + 1 < text.length && text[startIx + 1] == ' ') 1 else 0
        return 1 + space
    }
    return null
}

internal fun scanListItem(text: String, startIx: Int): Quadruple<Int, Char, Long, Int>? {
    if (startIx >= text.length) return null
    var c = text[startIx]
    val (w, start) =
        when {
            c == '-' || c == '+' || c == '*' -> Pair(1, 0L)
            c.isDigit() -> {
                val (length, parsedVal) = parseDecimal(text, startIx, 9)
                if (startIx + length >= text.length) return null
                c = text[startIx + length]
                if (c != '.' && c != ')') return null
                Pair(length + 1, parsedVal)
            }
            else -> return null
        }
    val (postN, postIndentRaw) = calcIndent(text, startIx + w, 5)
    var postn = postN
    var postindent = postIndentRaw
    if (postindent == 0) {
        scanEol(text, startIx + w) ?: return null
        postindent += 1
    } else if (postindent > 4) {
        postn = 1
        postindent = 1
    }
    if (scanBlankLine(text, startIx + w) != null) {
        postn = 0
        postindent = 1
    }
    return Quadruple(w + postn, c, start, w + postindent)
}

internal data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

internal fun parseDecimal(text: String, startIx: Int, limit: Int): Pair<Int, Long> {
    var count = 0
    var acc = 0L
    var i = startIx
    while (i < text.length && count < limit && text[i].isDigit()) {
        val digit = (text[i] - '0').toLong()
        acc = acc * 10 + digit
        count++
        i++
    }
    return Pair(count, acc)
}

internal fun parseHex(text: String, startIx: Int, limit: Int): Pair<Int, Int> {
    var count = 0
    var acc = 0
    var i = startIx
    while (i < text.length && count < limit) {
        val c = text[i]
        val digit =
            when {
                c in '0'..'9' -> c - '0'
                c in 'a'..'f' -> c - 'a' + 10
                c in 'A'..'F' -> c - 'A' + 10
                else -> break
            }
        acc = acc * 16 + digit
        count++
        i++
    }
    return Pair(count, acc)
}

internal fun scanEntity(text: String, startIx: Int): Pair<Int, CowStr?> {
    var end = startIx + 1
    if (end < text.length && text[end] == '#') {
        end++
        val (bytecount, codepoint) =
            if (end < text.length && (text[end] == 'x' || text[end] == 'X')) {
                end++
                parseHex(text, end, 6)
            } else {
                val (c, num) = parseDecimal(text, end, 7)
                Pair(c, num.toInt())
            }
        end += bytecount
        if (bytecount == 0 || end >= text.length || text[end] != ';') {
            return Pair(0, null)
        }
        val ch =
            if (codepoint in 1..0x10FFFF) {
                if (codepoint <= 0xFFFF) {
                    codepoint.toChar().toString()
                } else {
                    val hexVal = codepoint - 0x10000
                    val high = (0xD800 + (hexVal shr 10)).toChar()
                    val low = (0xDC00 + (hexVal and 0x3FF)).toChar()
                    "$high$low"
                }
            } else {
                "\uFFFD"
            }
        return Pair(end + 1 - startIx, CowStr(ch))
    }
    val nameLen = scanWhile(text, end) { isAsciiAlphanumeric(it) }
    end += nameLen
    if (end < text.length && text[end] == ';') {
        val name = text.substring(startIx + 1, end)
        val entityVal = Entities.getEntity(name)
        if (entityVal != null) {
            return Pair(end + 1 - startIx, CowStr(entityVal))
        }
    }
    return Pair(0, null)
}

internal fun scanLinkDest(text: String, startIx: Int, maxNext: Int = 5): Pair<Int, String>? {
    if (startIx >= text.length) return null
    var i = startIx
    if (text[i] == '<') {
        // Pointy links
        i++
        val contentStart = i
        while (i < text.length) {
            when (text[i]) {
                '\n', '\r', '<' -> return null
                '>' -> return Pair(i + 1 - startIx, text.substring(contentStart, i))
                '\\' -> {
                    if (i + 1 < text.length && PunctTable.isAsciiPunctuation(text[i + 1].code)) {
                        i++
                    }
                }
            }
            i++
        }
        return null
    } else {
        // Non-pointy links
        var nest = 0
        val contentStart = i
        while (i < text.length) {
            val c = text[i]
            if (c.code in 0..0x20) break
            when (c) {
                '(' -> {
                    if (nest > maxNext) return null
                    nest++
                }
                ')' -> {
                    if (nest == 0) break
                    nest--
                }
                '\\' -> {
                    if (i + 1 < text.length && PunctTable.isAsciiPunctuation(text[i + 1].code)) {
                        i++
                    }
                }
            }
            i++
        }
        if (nest != 0) return null
        return Pair(i - startIx, text.substring(contentStart, i))
    }
}

internal fun unescape(input: String, isInTable: Boolean = false): CowStr {
    val result = StringBuilder()
    var mark = 0
    var i = 0
    while (i < input.length) {
        val c = input[i]
        when {
            c == '\\' && isInTable && i + 2 < input.length && input[i + 1] == '\\' && input[i + 2] == '|' -> {
                result.append(input.substring(mark, i))
                mark = i + 2
                i += 3
            }
            c == '\\' && i + 1 < input.length && PunctTable.isAsciiPunctuation(input[i + 1].code) -> {
                result.append(input.substring(mark, i))
                mark = i + 1
                i += 2
            }
            c == '&' -> {
                val (n, entity) = scanEntity(input, i)
                if (entity != null) {
                    result.append(input.substring(mark, i))
                    result.append(entity.value)
                    i += n
                    mark = i
                } else {
                    i++
                }
            }
            c == '\r' -> {
                result.append(input.substring(mark, i))
                i++
                mark = i
            }
            else -> i++
        }
    }
    return if (mark == 0) {
        CowStr(input)
    } else {
        result.append(input.substring(mark))
        CowStr(result.toString())
    }
}

internal fun startsHtmlBlockType6(text: String, startIx: Int): Boolean {
    var i = startIx
    if (i < text.length && text[i] == '/') i++
    val n = scanWhile(text, i) { isAsciiAlphanumeric(it) }
    val tag = text.substring(i, i + n).lowercase()
    if (!HTML_TAGS.contains(tag)) return false
    val remIx = i + n
    if (remIx >= text.length) return true
    val c = text[remIx]
    return c == ' ' ||
        c == '\t' ||
        c == '\r' ||
        c == '\n' ||
        c == '>' ||
        (text.length - remIx >= 2 && text.substring(remIx, remIx + 2) == "/>")
}

internal fun scanAutolink(text: String, startIx: Int): Triple<Int, CowStr, LinkType>? {
    val uri = scanUri(text, startIx)
    if (uri != null) {
        return Triple(uri.first, uri.second, LinkType.Autolink)
    }
    val email = scanEmail(text, startIx)
    if (email != null) {
        return Triple(email.first, email.second, LinkType.Email)
    }
    return null
}

private fun scanUri(text: String, startIx: Int): Pair<Int, CowStr>? {
    if (startIx >= text.length || !isAsciiAlpha(text[startIx])) return null
    var i = startIx + 1
    while (i < text.length) {
        val c = text[i]
        i++
        when {
            isAsciiAlphanumeric(c) || c == '.' || c == '-' || c == '+' -> {}
            c == ':' -> break
            else -> return null
        }
    }
    val schemeLen = i - startIx
    if (schemeLen !in 3..33) return null

    while (i < text.length) {
        val c = text[i]
        when {
            c == '>' -> return Pair(i + 1, CowStr(text.substring(startIx, i)))
            c.code in 0..32 || c == '<' -> return null
        }
        i++
    }
    return null
}

private fun scanEmail(text: String, startIx: Int): Pair<Int, CowStr>? {
    var i = startIx
    while (i < text.length) {
        val c = text[i]
        i++
        when {
            isAsciiAlphanumeric(c) ||
                c == '.' ||
                c == '!' ||
                c == '#' ||
                c == '$' ||
                c == '%' ||
                c == '&' ||
                c == '\'' ||
                c == '*' ||
                c == '+' ||
                c == '/' ||
                c == '=' ||
                c == '?' ||
                c == '^' ||
                c == '_' ||
                c == '`' ||
                c == '{' ||
                c == '|' ||
                c == '}' ||
                c == '~' ||
                c == '-' -> {}
            c == '@' && i - startIx > 1 -> break
            else -> return null
        }
    }

    while (true) {
        val labelStartIx = i
        var freshLabel = true
        while (i < text.length) {
            val c = text[i]
            when {
                isAsciiAlphanumeric(c) -> {}
                c == '-' && freshLabel -> return null
                c == '-' -> {}
                else -> break
            }
            freshLabel = false
            i++
        }
        if (i == labelStartIx || i - labelStartIx > 63 || text[i - 1] == '-') return null
        if (i >= text.length || text[i] != '.') break
        i++
    }

    if (i >= text.length || text[i] != '>') return null
    return Pair(i + 1, CowStr(text.substring(startIx, i)))
}

internal fun scanInlineHtmlComment(text: String, startIx: Int, scanGuard: HtmlScanGuard): Int? {
    if (startIx >= text.length) return null
    var ix = startIx
    val c = text[ix]
    ix++
    when {
        c == '-' -> {
            if (ix >= text.length || text[ix] != '-') return null
            ix--
            while (ix < text.length) {
                val nextDash = text.indexOf('-', ix)
                if (nextDash < 0) return null
                ix = nextDash + 1
                if (ix + 1 < text.length && text[ix] == '-' && text[ix + 1] == '>') {
                    return ix + 2
                }
            }
            return null
        }
        c == '[' && text.substring(ix).startsWith("CDATA[") && ix > scanGuard.cdata -> {
            ix += "CDATA[".length
            val closeBracket = text.indexOf(']', ix)
            if (closeBracket < 0) {
                scanGuard.cdata = text.length
                return null
            }
            ix = closeBracket
            val closeBrackets = scanChRepeat(text, ix, ']')
            ix += closeBrackets
            if (closeBrackets == 0 || ix >= text.length || text[ix] != '>') {
                scanGuard.cdata = ix
                return null
            }
            return ix + 1
        }
        isAsciiAlpha(c) && ix > scanGuard.declaration -> {
            val closeGt = text.indexOf('>', ix)
            if (closeGt < 0) {
                scanGuard.declaration = text.length
                return null
            }
            return closeGt + 1
        }
        else -> return null
    }
}

internal fun scanInlineHtmlProcessing(text: String, startIx: Int, scanGuard: HtmlScanGuard): Int? {
    var ix = startIx
    if (ix <= scanGuard.processing) return null
    while (ix < text.length) {
        val offset = text.indexOf('?', ix)
        if (offset < 0) break
        ix = offset + 1
        if (ix < text.length && text[ix] == '>') {
            return ix + 1
        }
    }
    scanGuard.processing = ix
    return null
}

internal fun scanHtmlBlockInner(
    data: String,
    newlineHandler: ((String) -> Int)?,
): Pair<ByteArray, Int>? {
    val closeTagBytes = if (data.length > 1 && data[1] == '/') 1 else 0
    val l = scanWhile(data, 1 + closeTagBytes) { isAsciiAlpha(it) }
    if (l == 0) return null
    var i = 1 + closeTagBytes + l
    i += scanWhile(data, i) { isAsciiLetterDigitDash(it) }

    if (closeTagBytes == 0) {
        while (true) {
            val oldI = i
            while (true) {
                i += scanWhitespaceNoNl(data, i)
                val eolBytes = scanEol(data, i)
                if (eolBytes != null) {
                    if (eolBytes == 0) return null
                    val handler = newlineHandler ?: return null
                    i += eolBytes
                    val skippedBytes = handler(data.substring(i))
                    if (skippedBytes > 0) {
                        i += skippedBytes
                    }
                } else {
                    break
                }
            }
            if (i < data.length && (data[i] == '/' || data[i] == '>')) {
                break
            }
            if (oldI == i) {
                return null
            }
            i = scanAttribute(data, i, newlineHandler) ?: return null
        }
    }

    i += scanWhitespaceNoNl(data, i)
    if (closeTagBytes == 0 && i < data.length && data[i] == '/') {
        i++
    }
    if (i >= data.length || data[i] != '>') return null
    return Pair(byteArrayOf(), i + 1)
}

internal fun scanAttribute(
    data: String,
    startIx: Int,
    newlineHandler: ((String) -> Int)?,
): Int? {
    var i = startIx
    val nameLen = scanWhile(data, i) { isAsciiLetterDigitDash(it) || it == '_' || it == ':' }
    if (nameLen == 0) return null
    i += nameLen
    val wsLen = scanWhitespaceNoNl(data, i)
    i += wsLen
    if (i < data.length && data[i] == '=') {
        i++
        i += scanWhitespaceNoNl(data, i)
        if (i >= data.length) return null
        val c = data[i]
        if (c == '"' || c == '\'') {
            i++
            val closeIdx = data.indexOf(c, i)
            if (closeIdx < 0) return null
            i = closeIdx + 1
        } else {
            val valLen = scanWhile(data, i) { !isAsciiWhitespace(it) && it != '"' && it != '\'' && it != '=' && it != '<' && it != '>' && it != '`' }
            if (valLen == 0) return null
            i += valLen
        }
    }
    return i
}

internal fun scanHtmlType7(data: String, startIx: Int = 0): Int? {
    val sub = if (startIx == 0) data else data.substring(startIx)
    val (_, i) = scanHtmlBlockInner(sub, null) ?: return null
    scanBlankLine(sub, i) ?: return null
    return i
}
