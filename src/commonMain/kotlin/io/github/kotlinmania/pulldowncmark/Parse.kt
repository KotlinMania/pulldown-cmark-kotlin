// port-lint: source tmp/pulldown-cmark/src/parse.rs
package io.github.kotlinmania.pulldowncmark

import kotlin.math.max
import kotlin.math.min

internal const val LINK_MAX_NESTED_PARENS: Int = 32

data class Item(
    var start: Int,
    var end: Int,
    var body: ItemBody,
)

sealed class ItemBody {
    data object Root : ItemBody()
    data class Text(val backslashEscaped: Boolean) : ItemBody()
    data class Code(val cowIndex: CowIndex) : ItemBody()
    data class SynthesizeText(val cowIndex: CowIndex) : ItemBody()
    data class SynthesizeChar(val char: Char) : ItemBody()
    data object HtmlBlock : ItemBody()
    data object Html : ItemBody()
    data object InlineHtml : ItemBody()
    data class OwnedHtml(val cowIndex: CowIndex) : ItemBody()
    data object SoftBreak : ItemBody()
    data class HardBreak(val isBackslash: Boolean) : ItemBody()
    data class FootnoteReference(val cowIndex: CowIndex) : ItemBody()
    data class TaskListMarker(val isChecked: Boolean) : ItemBody()
    data object Rule : ItemBody()
    data object Paragraph : ItemBody()
    data object Emphasis : ItemBody()
    data object Strong : ItemBody()
    data object Strikethrough : ItemBody()
    data class Link(val linkIndex: LinkIndex) : ItemBody()
    data class Image(val linkIndex: LinkIndex) : ItemBody()
    data class Heading(val level: HeadingLevel, val headingIndex: HeadingIndex?) : ItemBody()
    data class FencedCodeBlock(val cowIndex: CowIndex) : ItemBody()
    data object IndentCodeBlock : ItemBody()
    data object BlockQuote : ItemBody()
    data class List(val isTight: Boolean, val listChar: Char, val listStart: Long) : ItemBody()
    data class ListItem(val indent: Int) : ItemBody()
    data object TableHead : ItemBody()
    data object TableCell : ItemBody()
    data object TableRow : ItemBody()
    data class Table(val alignmentIndex: AlignmentIndex) : ItemBody()
    data class FootnoteDefinition(val cowIndex: CowIndex) : ItemBody()
    data class MetadataBlock(val kind: MetadataBlockKind) : ItemBody()

    data object MaybeHtml : ItemBody()
    data class MaybeCode(val count: Int, val precededByBackslash: Boolean) : ItemBody()
    data class MaybeEmphasis(val count: Int, val canOpen: Boolean, val canClose: Boolean) : ItemBody()
    data class MaybeSmartQuote(val char: Char, val canOpen: Boolean, val canClose: Boolean) : ItemBody()
    data object MaybeLinkOpen : ItemBody()
    data object MaybeImage : ItemBody()
    data class MaybeLinkClose(val couldBeRef: Boolean) : ItemBody()

    fun isInline(): Boolean {
        return this is MaybeHtml ||
            this is MaybeCode ||
            this is MaybeEmphasis ||
            this is MaybeSmartQuote ||
            this is MaybeLinkOpen ||
            this is MaybeImage ||
            this is MaybeLinkClose ||
            (this is HardBreak && this.isBackslash)
    }

    fun isBlock(): Boolean {
        return this is Paragraph ||
            this is Heading ||
            this is FencedCodeBlock ||
            this is IndentCodeBlock ||
            this is BlockQuote ||
            this is List ||
            this is ListItem ||
            this is TableHead ||
            this is TableCell ||
            this is TableRow ||
            this is Table ||
            this is FootnoteDefinition ||
            this is MetadataBlock
    }
}

typealias CowIndex = Int
typealias LinkIndex = Int
typealias AlignmentIndex = Int
typealias HeadingIndex = Int

class Allocations internal constructor() {
    val refdefs: RefDefs = RefDefs()
    val footdefs: FootnoteDefs = FootnoteDefs()
    internal val links: MutableList<LinkTuple> = ArrayList(128)
    internal val cows: MutableList<CowStr> = ArrayList()
    internal val alignments: MutableList<kotlin.collections.List<Alignment>> = ArrayList()
    internal val headings: MutableList<HeadingAttributes> = ArrayList()

    fun allocateCow(cow: CowStr): CowIndex {
        val ix = cows.size
        cows.add(cow)
        return ix
    }

    fun allocateLink(
        type: LinkType,
        url: CowStr,
        title: CowStr,
        id: CowStr,
    ): LinkIndex {
        val ix = links.size
        links.add(LinkTuple(type, url, title, id))
        return ix
    }

    fun allocateAlignment(alignment: kotlin.collections.List<Alignment>): AlignmentIndex {
        val ix = alignments.size
        alignments.add(alignment)
        return ix
    }

    fun allocateHeading(attrs: HeadingAttributes): HeadingIndex {
        val ix = headings.size
        headings.add(attrs)
        return ix
    }

    fun takeCow(ix: CowIndex): CowStr {
        val res = cows[ix]
        cows[ix] = CowStr.from("")
        return res
    }

    fun takeLink(ix: LinkIndex): LinkTuple {
        val res = links[ix]
        links[ix] = LinkTuple(LinkType.ShortcutUnknown, CowStr.from(""), CowStr.from(""), CowStr.from(""))
        return res
    }

    fun takeAlignment(ix: AlignmentIndex): kotlin.collections.List<Alignment> {
        val res = alignments[ix]
        alignments[ix] = emptyList()
        return res
    }
}

data class LinkTuple(
    val type: LinkType,
    val url: CowStr,
    val title: CowStr,
    val id: CowStr,
)

data class HeadingAttributes(
    val id: CowStr?,
    val classes: kotlin.collections.List<CowStr>,
    val attrs: kotlin.collections.List<HeadingAttribute>,
)

data class LinkDef(
    val dest: CowStr,
    var title: CowStr?,
    var span: IntRange,
)

data class FootnoteDef(
    var useCount: Int,
)

class RefDefs internal constructor(
    internal val map: MutableMap<UniCase, LinkDef> = LinkedHashMap()
) {
    fun get(key: String): LinkDef? {
        return map[UniCase(CowStr.from(key))]
    }

    fun iter(): kotlin.collections.List<Pair<String, LinkDef>> {
        return map.entries.map { Pair(it.key.value.asString(), it.value) }
    }
}

class FootnoteDefs internal constructor(
    internal val map: MutableMap<UniCase, FootnoteDef> = LinkedHashMap()
) {
    fun contains(key: String): Boolean {
        return map.containsKey(UniCase(CowStr.from(key)))
    }

    fun getMut(key: CowStr): FootnoteDef? {
        return map[UniCase(key)]
    }
}

class Parser(
    text: String,
    private val options: Options = Options.empty(),
    private val brokenLinkCallback: BrokenLinkCallback? = null,
) : Iterator<Event> {

    private val text: String = text
    private val tree: Tree<Item>
    private val allocs: Allocations
    private val linkStack: LinkStack = LinkStack()
    private val inlineStack: InlineStack = InlineStack()
    private var linkRefExpansionLimit: Int = max(1 shl 16, text.length * 4)
    private val htmlScanGuard: HtmlScanGuard = HtmlScanGuard()

    init {
        val firstPassResult = runFirstPass(text, options)
        this.tree = firstPassResult.first
        this.allocs = firstPassResult.second
        this.tree.reset()
    }

    companion object {
        fun new(text: String): Parser = Parser(text, Options.empty(), null)
        fun newExt(text: String, options: Options): Parser = Parser(text, options, null)
        fun newWithBrokenLinkCallback(
            text: String,
            options: Options,
            brokenLinkCallback: BrokenLinkCallback?,
        ): Parser = Parser(text, options, brokenLinkCallback)
    }

    fun referenceDefinitions(): RefDefs = allocs.refdefs

    private fun fetchLinkTypeUrlTitle(
        linkLabel: CowStr,
        span: IntRange,
        linkType: LinkType,
    ): Triple<LinkType, CowStr, CowStr>? {
        if (linkRefExpansionLimit <= 0) {
            return null
        }

        val matchingDef = allocs.refdefs.get(linkLabel.asString())
        val result: Triple<LinkType, CowStr, CowStr>? = if (matchingDef != null) {
            val title = matchingDef.title ?: CowStr.from("")
            val url = matchingDef.dest.clone()
            Triple(linkType, url, title)
        } else if (brokenLinkCallback != null) {
            val brokenLink = BrokenLink(
                span = span,
                linkType = linkType,
                reference = linkLabel,
            )
            val fallback = brokenLinkCallback.handleBrokenLink(brokenLink)
            fallback?.let { fb ->
                Triple(linkType.toUnknown(), fb.destUrl, fb.title)
            }
        } else {
            null
        }

        if (result != null) {
            val (_, url, title) = result
            linkRefExpansionLimit = max(0, linkRefExpansionLimit - (url.length + title.length))
        }

        return result
    }

    private fun handleInline() {
        handleInlinePass1()
        handleEmphasisAndHardBreak()
    }

    private fun handleInlinePass1() {
        val codeDelims = CodeDelims()
        var cur = tree.cur()
        var prev: TreeIndex? = null

        val peekUp = tree.peekUp()
        val blockEnd = if (peekUp != null) tree[peekUp].item.end else text.length
        val blockText = text.substring(0, min(blockEnd, text.length))

        while (cur != null) {
            var curIx = cur
            val item = tree[curIx].item
            when (val body = item.body) {
                is ItemBody.MaybeHtml -> {
                    val next = tree[curIx].next
                    val autolink = if (next != null) {
                        scanAutolink(blockText, tree[next].item.start)
                    } else {
                        null
                    }

                    if (autolink != null) {
                        val (ix, uri, linkType) = autolink
                        val node = scanNodesToIx(tree, next, ix)
                        val textNode = tree.createNode(
                            Item(
                                start = tree[curIx].item.start + 1,
                                end = ix - 1,
                                body = ItemBody.Text(backslashEscaped = false),
                            )
                        )
                        val linkIx = allocs.allocateLink(linkType, uri, CowStr.from(""), CowStr.from(""))
                        tree[curIx].item.body = ItemBody.Link(linkIx)
                        tree[curIx].item.end = ix
                        tree[curIx].next = node
                        tree[curIx].child = textNode
                        prev = cur
                        cur = node
                        if (cur != null) {
                            tree[cur].item.start = max(tree[cur].item.start, ix)
                        }
                        continue
                    } else {
                        val inlineHtml = if (next != null) {
                            scanInlineHtml(blockText, tree[next].item.start)
                        } else {
                            null
                        }
                        if (inlineHtml != null) {
                            val (span, ix) = inlineHtml
                            val node = scanNodesToIx(tree, next, ix)
                            tree[curIx].item.body = if (span.isNotEmpty()) {
                                val convertedString = span.decodeToString()
                                ItemBody.OwnedHtml(allocs.allocateCow(CowStr.from(convertedString)))
                            } else {
                                ItemBody.InlineHtml
                            }
                            tree[curIx].item.end = ix
                            tree[curIx].next = node
                            prev = cur
                            cur = node
                            if (cur != null) {
                                tree[cur].item.start = max(tree[cur].item.start, ix)
                            }
                            continue
                        }
                    }
                    tree[curIx].item.body = ItemBody.Text(backslashEscaped = false)
                }
                is ItemBody.MaybeCode -> {
                    var searchCount = body.count
                    val precededByBackslash = body.precededByBackslash
                    if (precededByBackslash) {
                        searchCount -= 1
                        if (searchCount == 0) {
                            tree[curIx].item.body = ItemBody.Text(backslashEscaped = false)
                            prev = cur
                            cur = tree[curIx].next
                            continue
                        }
                    }

                    if (codeDelims.isPopulated()) {
                        val scanIx = codeDelims.find(curIx, searchCount)
                        if (scanIx != null) {
                            makeCodeSpan(curIx, scanIx, precededByBackslash)
                        } else {
                            tree[curIx].item.body = ItemBody.Text(backslashEscaped = false)
                        }
                    } else {
                        var scan = if (searchCount > 0) tree[curIx].next else null
                        while (scan != null) {
                            val scanIx = scan
                            val scanBody = tree[scanIx].item.body
                            if (scanBody is ItemBody.MaybeCode) {
                                if (searchCount == scanBody.count) {
                                    makeCodeSpan(curIx, scanIx, precededByBackslash)
                                    codeDelims.clear()
                                    break
                                } else {
                                    codeDelims.insert(scanBody.count, scanIx)
                                }
                            }
                            if (tree[scanIx].item.body.isBlock()) {
                                scan = null
                                break
                            }
                            scan = tree[scanIx].next
                        }
                        if (scan == null) {
                            tree[curIx].item.body = ItemBody.Text(backslashEscaped = false)
                        }
                    }
                }
                is ItemBody.MaybeLinkOpen -> {
                    tree[curIx].item.body = ItemBody.Text(backslashEscaped = false)
                    linkStack.push(LinkStackEl(curIx, LinkStackTy.Link))
                }
                is ItemBody.MaybeImage -> {
                    tree[curIx].item.body = ItemBody.Text(backslashEscaped = false)
                    linkStack.push(LinkStackEl(curIx, LinkStackTy.Image))
                }
                is ItemBody.MaybeLinkClose -> {
                    val couldBeRef = body.couldBeRef
                    tree[curIx].item.body = ItemBody.Text(backslashEscaped = false)
                    val tos = linkStack.pop()
                    if (tos != null) {
                        if (tos.ty == LinkStackTy.Disabled) {
                            // continue
                        } else {
                            val next = tree[curIx].next
                            val inlineLink = scanInlineLink(blockText, tree[curIx].item.end, next)
                            if (inlineLink != null) {
                                val (nextIx, url, title) = inlineLink
                                val nextNode = scanNodesToIx(tree, next, nextIx)
                                if (prev != null) {
                                    tree[prev].next = null
                                }
                                cur = tos.node
                                curIx = tos.node
                                val linkIx = allocs.allocateLink(LinkType.Inline, url, title, CowStr.from(""))
                                tree[curIx].item.body = if (tos.ty == LinkStackTy.Image) {
                                    ItemBody.Image(linkIx)
                                } else {
                                    ItemBody.Link(linkIx)
                                }
                                tree[curIx].child = tree[curIx].next
                                tree[curIx].next = nextNode
                                tree[curIx].item.end = nextIx
                                if (nextNode != null) {
                                    tree[nextNode].item.start = max(tree[nextNode].item.start, nextIx)
                                }
                                if (tos.ty == LinkStackTy.Link) {
                                    linkStack.disableAllLinks()
                                }
                            } else {
                                val scanResult = scanReference(
                                    tree,
                                    blockText,
                                    next,
                                    options.contains(Options.ENABLE_FOOTNOTES),
                                    options.hasGfmFootnotes(),
                                )
                                val refScanOutcome = when (scanResult) {
                                    is RefScan.LinkLabel -> {
                                        val referenceCloseNode = scanNodesToIx(tree, next, scanResult.endIx - 1)
                                        if (referenceCloseNode == null) {
                                            null
                                        } else {
                                            tree[referenceCloseNode].item.body = ItemBody.MaybeLinkClose(false)
                                            val nextNode = tree[referenceCloseNode].next
                                            Pair(nextNode, LinkType.Reference)
                                        }
                                    }
                                    is RefScan.Collapsed -> {
                                        if (!couldBeRef) null else Pair(scanResult.nextNode, LinkType.Collapsed)
                                    }
                                    is RefScan.Failed -> {
                                        if (!couldBeRef) null else Pair(next, LinkType.Shortcut)
                                    }
                                    is RefScan.UnexpectedFootnote -> null
                                }

                                if (refScanOutcome != null) {
                                    val (nodeAfterLink, linkType) = refScanOutcome
                                    val labelOutcome: Pair<ReferenceLabel, Int>? = when (scanResult) {
                                        is RefScan.LinkLabel -> Pair(ReferenceLabel.Link(scanResult.label), scanResult.endIx)
                                        is RefScan.Collapsed, is RefScan.Failed -> {
                                            val labelStart = tree[tos.node].item.end - 1
                                            val labelEnd = tree[curIx].item.end
                                            val sub = text.substring(labelStart, labelEnd)
                                            val scanned = scanLinkLabel(
                                                tree,
                                                sub,
                                                options.contains(Options.ENABLE_FOOTNOTES),
                                                options.hasGfmFootnotes(),
                                            )
                                            if (scanned != null && labelStart + scanned.first == labelEnd) {
                                                Pair(scanned.second, labelStart + scanned.first)
                                            } else {
                                                null
                                            }
                                        }
                                        else -> null
                                    }

                                    val id = when (val l = labelOutcome?.first) {
                                        is ReferenceLabel.Link -> l.label.clone()
                                        is ReferenceLabel.Footnote -> l.label.clone()
                                        null -> CowStr.from("")
                                    }

                                    if (labelOutcome != null) {
                                        val (refLabel, end) = labelOutcome
                                        when (refLabel) {
                                            is ReferenceLabel.Footnote -> {
                                                val footref = allocs.allocateCow(refLabel.label)
                                                val def = allocs.footdefs.getMut(allocs.cows[footref])
                                                if (def != null) {
                                                    def.useCount += 1
                                                }
                                                if (!options.hasGfmFootnotes() || allocs.footdefs.contains(allocs.cows[footref].asString())) {
                                                    val footnoteIx = if (tos.ty == LinkStackTy.Image) {
                                                        tree[tos.node].next = curIx
                                                        tree[tos.node].child = null
                                                        tree[tos.node].item.body = ItemBody.SynthesizeChar('!')
                                                        curIx
                                                    } else {
                                                        tos.node
                                                    }
                                                    tree[footnoteIx].next = next
                                                    tree[footnoteIx].child = null
                                                    tree[footnoteIx].item.body = ItemBody.FootnoteReference(footref)
                                                    tree[footnoteIx].item.end = end
                                                    prev = footnoteIx
                                                    cur = next
                                                    linkStack.clear()
                                                    continue
                                                }
                                            }
                                            is ReferenceLabel.Link -> {
                                                val fetched = fetchLinkTypeUrlTitle(
                                                    refLabel.label,
                                                    tree[tos.node].item.start until end,
                                                    linkType,
                                                )
                                                if (fetched != null) {
                                                    val (defLinkType, url, title) = fetched
                                                    val linkIx = allocs.allocateLink(defLinkType, url, title, id)
                                                    tree[tos.node].item.body = if (tos.ty == LinkStackTy.Image) {
                                                        ItemBody.Image(linkIx)
                                                    } else {
                                                        ItemBody.Link(linkIx)
                                                    }
                                                    val labelNode = tree[tos.node].next
                                                    tree[tos.node].next = nodeAfterLink
                                                    if (labelNode != cur) {
                                                        tree[tos.node].child = labelNode
                                                        if (prev != null) {
                                                            tree[prev].next = null
                                                        }
                                                    }
                                                    tree[tos.node].item.end = end
                                                    cur = tos.node
                                                    curIx = tos.node
                                                    if (tos.ty == LinkStackTy.Link) {
                                                        linkStack.disableAllLinks()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    if (tree[curIx].item.body.isBlock()) {
                        linkStack.clear()
                    }
                }
            }
            prev = cur
            cur = tree[curIx].next
        }
        linkStack.clear()
    }

    private fun handleEmphasisAndHardBreak() {
        var prev: TreeIndex? = null
        var prevIx: TreeIndex
        var cur = tree.cur()

        var singleQuoteOpen: TreeIndex? = null
        var doubleQuoteOpen = false

        while (true) {
            var curIx = cur ?: break
            val body = tree[curIx].item.body
            when (body) {
                is ItemBody.MaybeEmphasis -> {
                    var count = body.count
                    val canOpen = body.canOpen
                    val canClose = body.canClose
                    val runLength = count
                    val c = text[tree[curIx].item.start]
                    val both = canOpen && canClose
                    if (canClose) {
                        while (true) {
                            val el = inlineStack.findMatch(tree, c, runLength, both) ?: break
                            if (prev != null) {
                                tree[prev].next = null
                            }
                            val matchCount = min(count, el.count)
                            var end = curIx - 1
                            var start = el.start + el.count

                            while (start > el.start + el.count - matchCount) {
                                val inc = if (start > el.start + el.count - matchCount + 1) 2 else 1
                                val ty = if (c == '~') {
                                    ItemBody.Strikethrough
                                } else if (inc == 2) {
                                    ItemBody.Strong
                                } else {
                                    ItemBody.Emphasis
                                }

                                val root = start - inc
                                end += inc
                                tree[root].item.body = ty
                                tree[root].item.end = tree[end].item.end
                                tree[root].child = start
                                tree[root].next = null
                                start = root
                            }

                            prevIx = el.start + el.count - matchCount
                            prev = prevIx
                            cur = tree[curIx + matchCount - 1].next
                            tree[prevIx].next = cur

                            if (el.count > matchCount) {
                                inlineStack.push(
                                    InlineEl(
                                        start = el.start,
                                        count = el.count - matchCount,
                                        runLength = el.runLength,
                                        c = el.c,
                                        both = el.both,
                                    )
                                )
                            }
                            count -= matchCount
                            if (count > 0 && cur != null) {
                                curIx = cur
                            } else {
                                break
                            }
                        }
                    }
                    if (count > 0) {
                        if (canOpen) {
                            inlineStack.push(
                                InlineEl(
                                    start = curIx,
                                    runLength = runLength,
                                    count = count,
                                    c = c,
                                    both = both,
                                )
                            )
                        } else {
                            for (i in 0 until count) {
                                tree[curIx + i].item.body = ItemBody.Text(backslashEscaped = false)
                            }
                        }
                        prevIx = curIx + count - 1
                        prev = prevIx
                        cur = tree[prevIx].next
                    }
                }
                is ItemBody.MaybeSmartQuote -> {
                    val c = body.char
                    val canOpen = body.canOpen
                    val canClose = body.canClose
                    tree[curIx].item.body = when (c) {
                        '\'' -> {
                            if (singleQuoteOpen != null && canClose) {
                                tree[singleQuoteOpen].item.body = ItemBody.SynthesizeChar('‘')
                                singleQuoteOpen = null
                            } else if (canOpen) {
                                singleQuoteOpen = curIx
                            }
                            ItemBody.SynthesizeChar('’')
                        }
                        else -> {
                            if (canClose && doubleQuoteOpen) {
                                doubleQuoteOpen = false
                                ItemBody.SynthesizeChar('”')
                            } else {
                                if (canOpen && !doubleQuoteOpen) {
                                    doubleQuoteOpen = true
                                }
                                ItemBody.SynthesizeChar('“')
                            }
                        }
                    }
                    prev = cur
                    cur = tree[curIx].next
                }
                is ItemBody.HardBreak -> {
                    if (body.isBackslash && tree[curIx].next == null) {
                        tree[curIx].item.body = ItemBody.SynthesizeChar('\\')
                    }
                    prev = cur
                    cur = tree[curIx].next
                }
                else -> {
                    prev = cur
                    if (tree[curIx].item.body.isBlock()) {
                        inlineStack.popAll(tree)
                    }
                    cur = tree[curIx].next
                }
            }
        }
        inlineStack.popAll(tree)
    }

    private fun scanInlineLink(
        underlying: String,
        ixParam: Int,
        node: TreeIndex?,
    ): Triple<Int, CowStr, CowStr>? {
        var ix = ixParam
        if (ix >= underlying.length || underlying[ix] != '(') {
            return null
        }
        ix += 1

        val scanSeparator: () -> Unit = {
            ix += scanWhile(underlying, ix) { isAsciiWhitespaceNoNl(it) }
            val bl = scanEol(underlying, ix)
            if (bl != null && bl > 0) {
                ix += bl
                val lineStart = LineStart(underlying, ix)
                scanContainers(tree, lineStart, options.hasGfmFootnotes())
                ix += lineStart.bytesScanned
            }
            ix += scanWhile(underlying, ix) { isAsciiWhitespaceNoNl(it) }
        }

        scanSeparator()

        val destRes = scanLinkDest(underlying, ix, LINK_MAX_NESTED_PARENS) ?: return null
        val (destLength, dest) = destRes
        val destUnescaped = unescape(dest, tree.isInTable())
        ix += destLength

        scanSeparator()

        val titleRes = scanLinkTitle(underlying, ix, node)
        val title = if (titleRes != null) {
            val (bytesScanned, t) = titleRes
            ix += bytesScanned
            scanSeparator()
            t
        } else {
            CowStr.from("")
        }

        if (ix >= underlying.length || underlying[ix] != ')') {
            return null
        }
        ix += 1

        return Triple(ix, destUnescaped, title)
    }

    private fun scanLinkTitle(
        textParam: String,
        startIx: Int,
        node: TreeIndex?,
    ): Pair<Int, CowStr>? {
        if (startIx >= textParam.length) return null
        val open = textParam[startIx]
        if (open != '\'' && open != '"' && open != '(') return null
        val close = if (open == '(') ')' else open

        val title = StringBuilder()
        var mark = startIx + 1
        var i = startIx + 1

        while (i < textParam.length) {
            val c = textParam[i]
            if (c == close) {
                val cow = if (mark == startIx + 1) {
                    Pair(i - startIx + 1, CowStr.from(textParam.substring(mark, i)))
                } else {
                    title.append(textParam.substring(mark, i))
                    Pair(i - startIx + 1, CowStr.from(title.toString()))
                }
                return cow
            }
            if (c == open) {
                return null
            }
            if (c == '\n' || c == '\r') {
                val nodeIx = scanNodesToIx(tree, node, i + 1)
                if (nodeIx != null && tree[nodeIx].item.start > i) {
                    title.append(textParam.substring(mark, i))
                    title.append('\n')
                    i = tree[nodeIx].item.start
                    mark = i
                    continue
                }
            }
            if (c == '&') {
                val (n, entityVal) = scanEntity(textParam, i)
                if (entityVal != null) {
                    title.append(textParam.substring(mark, i))
                    title.append(entityVal.value)
                    i += n
                    mark = i
                    continue
                }
            }
            if (tree.isInTable() && c == '\\' && i + 2 < textParam.length && textParam[i + 1] == '\\' && textParam[i + 2] == '|') {
                title.append(textParam.substring(mark, i))
                i += 2
                mark = i
            }
            if (c == '\\' && i + 1 < textParam.length && isAsciiPunctuation(textParam[i + 1])) {
                title.append(textParam.substring(mark, i))
                i += 1
                mark = i
            }
            i += 1
        }
        return null
    }

    private fun makeCodeSpan(open: TreeIndex, close: TreeIndex, precedingBackslash: Boolean) {
        val spanStart = tree[open].item.end
        val spanEnd = tree[close].item.start
        var buf: StringBuilder? = null

        var startIx = spanStart
        var ix = spanStart
        while (ix < spanEnd) {
            val c = text[ix]
            if (c == '\r' || c == '\n') {
                if (buf == null) buf = StringBuilder(ix + 1 - spanStart)
                buf.append(text.substring(startIx, ix))
                buf.append(' ')
                ix += 1
                val lineStart = LineStart(text, ix)
                scanContainers(tree, lineStart, options.hasGfmFootnotes())
                ix += lineStart.bytesScanned
                startIx = ix
            } else if (c == '\\' && ix + 1 < text.length && text[ix + 1] == '|' && tree.isInTable()) {
                if (buf == null) buf = StringBuilder(ix + 1 - spanStart)
                buf.append(text.substring(startIx, ix))
                buf.append('|')
                ix += 2
                startIx = ix
            } else {
                ix += 1
            }
        }

        val s = if (buf != null) {
            buf.append(text.substring(startIx, spanEnd))
            buf.toString()
        } else {
            text.substring(spanStart, spanEnd)
        }

        val opening = s.startsWith(' ')
        val closing = s.endsWith(' ')
        val allSpaces = s.all { it == ' ' }

        val cowStr = if (!allSpaces && opening && closing) {
            if (buf != null) {
                buf.deleteAt(0)
                buf.deleteAt(buf.length - 1)
                CowStr.from(buf.toString())
            } else {
                val lo = spanStart + 1
                val hi = max(lo, spanEnd - 1)
                CowStr.from(text.substring(lo, hi))
            }
        } else if (buf != null) {
            CowStr.from(buf.toString())
        } else {
            CowStr.from(text.substring(spanStart, spanEnd))
        }

        if (precedingBackslash) {
            tree[open].item.body = ItemBody.Text(backslashEscaped = true)
            tree[open].item.end = tree[open].item.start + 1
            tree[open].next = close
            tree[close].item.body = ItemBody.Code(allocs.allocateCow(cowStr))
            tree[close].item.start = tree[open].item.start + 1
        } else {
            tree[open].item.body = ItemBody.Code(allocs.allocateCow(cowStr))
            tree[open].item.end = tree[close].item.end
            tree[open].next = tree[close].next
        }
    }

    private fun scanInlineHtml(blockText: String, ix: Int): Pair<ByteArray, Int>? {
        if (ix >= blockText.length) return null
        val c = blockText[ix]
        return if (c == '!') {
            val endIx = scanInlineHtmlComment(blockText, ix + 1, htmlScanGuard) ?: return null
            Pair(byteArrayOf(), endIx)
        } else if (c == '?') {
            val endIx = scanInlineHtmlProcessing(blockText, ix + 1, htmlScanGuard) ?: return null
            Pair(byteArrayOf(), endIx)
        } else {
            val inner = scanHtmlBlockInner(blockText.substring(ix - 1)) { s ->
                val lineStart = LineStart(s, 0)
                scanContainers(tree, lineStart, options.hasGfmFootnotes())
                lineStart.bytesScanned
            } ?: return null
            val (span, i) = inner
            Pair(span, i + ix - 1)
        }
    }

    fun intoOffsetIter(): OffsetIter = OffsetIter(this)

    override fun hasNext(): Boolean {
        return tree.cur() != null || tree.peekUp() != null
    }

    override fun next(): Event {
        val curIx = tree.cur()
        return if (curIx == null) {
            val ix = tree.pop() ?: throw NoSuchElementException()
            val tagEnd = bodyToTagEnd(tree[ix].item.body)
            tree.nextSibling(ix)
            Event.End(tagEnd)
        } else {
            if (tree[curIx].item.body.isInline()) {
                handleInline()
            }

            val node = tree[curIx]
            val item = node.item
            val event = itemToEvent(item, text, allocs)
            if (event is Event.Start) {
                tree.push()
            } else {
                tree.nextSibling(curIx)
            }
            event
        }
    }

    internal fun nextWithSpan(): SpannedEvent? {
        val curIx = tree.cur()
        return if (curIx == null) {
            val ix = tree.pop() ?: return null
            val tagEnd = bodyToTagEnd(tree[ix].item.body)
            tree.nextSibling(ix)
            val span = tree[ix].item.start until tree[ix].item.end
            SpannedEvent(Event.End(tagEnd), span)
        } else {
            if (tree[curIx].item.body.isInline()) {
                handleInline()
            }

            val node = tree[curIx]
            val item = node.item
            val event = itemToEvent(item, text, allocs)
            if (event is Event.Start) {
                tree.push()
            } else {
                tree.nextSibling(curIx)
            }
            SpannedEvent(event, item.start until item.end)
        }
    }
}

class OffsetIter internal constructor(
    private val inner: Parser
) : Iterator<SpannedEvent> {

    fun referenceDefinitions(): RefDefs = inner.referenceDefinitions()

    override fun hasNext(): Boolean {
        return inner.hasNext()
    }

    override fun next(): SpannedEvent {
        return inner.nextWithSpan() ?: throw NoSuchElementException()
    }
}

internal fun bodyToTagEnd(body: ItemBody): TagEnd {
    return when (body) {
        is ItemBody.Paragraph -> TagEnd.Paragraph
        is ItemBody.Emphasis -> TagEnd.Emphasis
        is ItemBody.Strong -> TagEnd.Strong
        is ItemBody.Strikethrough -> TagEnd.Strikethrough
        is ItemBody.Link -> TagEnd.Link
        is ItemBody.Image -> TagEnd.Image
        is ItemBody.Heading -> TagEnd.Heading(body.level)
        is ItemBody.IndentCodeBlock, is ItemBody.FencedCodeBlock -> TagEnd.CodeBlock
        is ItemBody.BlockQuote -> TagEnd.BlockQuote
        is ItemBody.HtmlBlock -> TagEnd.HtmlBlock
        is ItemBody.List -> {
            val isOrdered = body.listChar == '.' || body.listChar == ')'
            TagEnd.List(isOrdered)
        }
        is ItemBody.ListItem -> TagEnd.Item
        is ItemBody.TableHead -> TagEnd.TableHead
        is ItemBody.TableCell -> TagEnd.TableCell
        is ItemBody.TableRow -> TagEnd.TableRow
        is ItemBody.Table -> TagEnd.Table
        is ItemBody.FootnoteDefinition -> TagEnd.FootnoteDefinition
        is ItemBody.MetadataBlock -> TagEnd.MetadataBlock(body.kind)
        else -> error("unexpected item body $body")
    }
}

internal fun itemToEvent(item: Item, text: String, allocs: Allocations): Event {
    val tag = when (val body = item.body) {
        is ItemBody.Text -> return Event.Text(CowStr.from(text.substring(item.start, item.end)))
        is ItemBody.Code -> return Event.Code(allocs.takeCow(body.cowIndex))
        is ItemBody.SynthesizeText -> return Event.Text(allocs.takeCow(body.cowIndex))
        is ItemBody.SynthesizeChar -> return Event.Text(CowStr.from(body.char.toString()))
        is ItemBody.HtmlBlock -> Tag.HtmlBlock
        is ItemBody.Html -> return Event.Html(CowStr.from(text.substring(item.start, item.end)))
        is ItemBody.InlineHtml -> return Event.InlineHtml(CowStr.from(text.substring(item.start, item.end)))
        is ItemBody.OwnedHtml -> return Event.Html(allocs.takeCow(body.cowIndex))
        is ItemBody.SoftBreak -> return Event.SoftBreak
        is ItemBody.HardBreak -> return Event.HardBreak
        is ItemBody.FootnoteReference -> return Event.FootnoteReference(allocs.takeCow(body.cowIndex))
        is ItemBody.TaskListMarker -> return Event.TaskListMarker(body.isChecked)
        is ItemBody.Rule -> return Event.Rule
        is ItemBody.Paragraph -> Tag.Paragraph
        is ItemBody.Emphasis -> Tag.Emphasis
        is ItemBody.Strong -> Tag.Strong
        is ItemBody.Strikethrough -> Tag.Strikethrough
        is ItemBody.Link -> {
            val tuple = allocs.takeLink(body.linkIndex)
            Tag.Link(
                linkType = tuple.type,
                destUrl = tuple.url,
                title = tuple.title,
                id = tuple.id,
            )
        }
        is ItemBody.Image -> {
            val tuple = allocs.takeLink(body.linkIndex)
            Tag.Image(
                linkType = tuple.type,
                destUrl = tuple.url,
                title = tuple.title,
                id = tuple.id,
            )
        }
        is ItemBody.Heading -> {
            if (body.headingIndex != null) {
                val headingAttrs = allocs.headings[body.headingIndex]
                Tag.Heading(
                    level = body.level,
                    id = headingAttrs.id,
                    classes = headingAttrs.classes,
                    attrs = headingAttrs.attrs,
                )
            } else {
                Tag.Heading(
                    level = body.level,
                    id = null,
                    classes = emptyList(),
                    attrs = emptyList(),
                )
            }
        }
        is ItemBody.FencedCodeBlock -> Tag.CodeBlock(CodeBlockKind.Fenced(allocs.takeCow(body.cowIndex)))
        is ItemBody.IndentCodeBlock -> Tag.CodeBlock(CodeBlockKind.Indented)
        is ItemBody.BlockQuote -> Tag.BlockQuote
        is ItemBody.List -> {
            if (body.listChar == '.' || body.listChar == ')') {
                Tag.List(body.listStart)
            } else {
                Tag.List(null)
            }
        }
        is ItemBody.ListItem -> Tag.Item
        is ItemBody.TableHead -> Tag.TableHead
        is ItemBody.TableCell -> Tag.TableCell
        is ItemBody.TableRow -> Tag.TableRow
        is ItemBody.Table -> Tag.Table(allocs.takeAlignment(body.alignmentIndex))
        is ItemBody.FootnoteDefinition -> Tag.FootnoteDefinition(allocs.takeCow(body.cowIndex))
        is ItemBody.MetadataBlock -> Tag.MetadataBlock(body.kind)
        else -> error("unexpected item body $body")
    }

    return Event.Start(tag)
}

internal fun scanContainers(
    tree: Tree<Item>,
    lineStart: LineStart,
    gfmFootnotes: Boolean,
): Int {
    var i = 0
    val spine = tree.walkSpine()
    for (nodeIx in spine) {
        val body = tree[nodeIx].item.body
        when {
            body is ItemBody.BlockQuote -> {
                if (!lineStart.scanBlockquoteMarker()) {
                    break
                }
            }
            body is ItemBody.ListItem -> {
                val save = lineStart.copy()
                if (!lineStart.scanSpace(body.indent) && !lineStart.isAtEol()) {
                    lineStart.restoreFrom(save)
                    break
                }
            }
            body is ItemBody.FootnoteDefinition && gfmFootnotes -> {
                val save = lineStart.copy()
                if (!lineStart.scanSpace(4) && !lineStart.isAtEol()) {
                    lineStart.restoreFrom(save)
                    break
                }
            }
        }
        i += 1
    }
    return i
}

internal fun Tree<Item>.appendText(start: Int, end: Int, backslashEscaped: Boolean) {
    if (end > start) {
        val curIx = cur()
        if (curIx != null) {
            val item = this[curIx].item
            if (item.body is ItemBody.Text && item.end == start) {
                item.end = end
                return
            }
        }
        append(
            Item(
                start = start,
                end = end,
                body = ItemBody.Text(backslashEscaped),
            )
        )
    }
}

internal fun Tree<Item>.isInTable(): Boolean {
    fun mightBeInTable(item: Item): Boolean {
        return item.body.isInline() ||
            item.body is ItemBody.TableHead ||
            item.body is ItemBody.TableRow ||
            item.body is ItemBody.TableCell
    }
    val spine = walkSpine().reversed()
    for (ix in spine) {
        if (this[ix].item.body is ItemBody.Table) {
            return true
        }
        if (!mightBeInTable(this[ix].item)) {
            return false
        }
    }
    return false
}

private data class InlineEl(
    val start: TreeIndex,
    val count: Int,
    val runLength: Int,
    val c: Char,
    val both: Boolean,
)

private class InlineStack {
    val stack: MutableList<InlineEl> = mutableListOf()
    val lowerBounds: IntArray = IntArray(9)

    companion object {
        const val UNDERSCORE_NOT_BOTH: Int = 0
        const val ASTERISK_NOT_BOTH: Int = 1
        const val ASTERISK_BASE: Int = 2
        const val TILDES: Int = 5
        const val UNDERSCORE_BASE: Int = 6
    }

    fun popAll(tree: Tree<Item>) {
        for (el in stack) {
            for (i in 0 until el.count) {
                tree[el.start + i].item.body = ItemBody.Text(backslashEscaped = false)
            }
        }
        stack.clear()
        lowerBounds.fill(0)
    }

    fun getLowerbound(c: Char, count: Int, both: Boolean): Int {
        return if (c == '_') {
            val mod3Lower = lowerBounds[UNDERSCORE_BASE + count % 3]
            if (both) mod3Lower else min(mod3Lower, lowerBounds[UNDERSCORE_NOT_BOTH])
        } else if (c == '*') {
            val mod3Lower = lowerBounds[ASTERISK_BASE + count % 3]
            if (both) mod3Lower else min(mod3Lower, lowerBounds[ASTERISK_NOT_BOTH])
        } else {
            lowerBounds[TILDES]
        }
    }

    fun setLowerbound(c: Char, count: Int, both: Boolean, newBound: Int) {
        if (c == '_') {
            if (both) {
                lowerBounds[UNDERSCORE_BASE + count % 3] = newBound
            } else {
                lowerBounds[UNDERSCORE_NOT_BOTH] = newBound
            }
        } else if (c == '*') {
            lowerBounds[ASTERISK_BASE + count % 3] = newBound
            if (!both) {
                lowerBounds[ASTERISK_NOT_BOTH] = newBound
            }
        } else {
            lowerBounds[TILDES] = newBound
        }
    }

    fun truncate(newBound: Int) {
        while (stack.size > newBound) {
            stack.removeAt(stack.size - 1)
        }
        for (i in lowerBounds.indices) {
            if (lowerBounds[i] > newBound) {
                lowerBounds[i] = newBound
            }
        }
    }

    fun findMatch(
        tree: Tree<Item>,
        c: Char,
        runLength: Int,
        both: Boolean,
    ): InlineEl? {
        val lowerbound = min(stack.size, getLowerbound(c, runLength, both))
        var matchingIx: Int? = null
        var matchingEl: InlineEl? = null

        for (i in (stack.size - 1) downTo lowerbound) {
            val el = stack[i]
            if (c == '~' && runLength != el.runLength) {
                continue
            }
            if (el.c == c && (!both && !el.both || (runLength + el.runLength) % 3 != 0 || runLength % 3 == 0)) {
                matchingIx = i
                matchingEl = el
                break
            }
        }

        return if (matchingIx != null && matchingEl != null) {
            for (i in (matchingIx + 1) until stack.size) {
                val el = stack[i]
                for (k in 0 until el.count) {
                    tree[el.start + k].item.body = ItemBody.Text(backslashEscaped = false)
                }
            }
            truncate(matchingIx)
            matchingEl
        } else {
            setLowerbound(c, runLength, both, stack.size)
            null
        }
    }

    fun trimLowerBound(ix: Int) {
        lowerBounds[ix] = min(lowerBounds[ix], stack.size)
    }

    fun push(el: InlineEl) {
        if (el.c == '~') {
            trimLowerBound(TILDES)
        }
        stack.add(el)
    }
}

internal sealed class RefScan {
    data class LinkLabel(val label: CowStr, val endIx: Int) : RefScan()
    data class Collapsed(val nextNode: TreeIndex?) : RefScan()
    data object UnexpectedFootnote : RefScan()
    data object Failed : RefScan()
}

internal fun scanNodesToIx(
    tree: Tree<Item>,
    nodeParam: TreeIndex?,
    ix: Int,
): TreeIndex? {
    var node = nodeParam
    while (node != null) {
        val nodeIx = node
        if (tree[nodeIx].item.end <= ix) {
            node = tree[nodeIx].next
        } else {
            break
        }
    }
    return node
}

internal fun scanLinkLabel(
    tree: Tree<Item>,
    text: String,
    allowFootnoteRefs: Boolean,
    gfmFootnotes: Boolean,
): Pair<Int, ReferenceLabel>? {
    if (text.length < 2 || text[0] != '[') {
        return null
    }
    val linebreakHandler: (String) -> Int? = { s ->
        val lineStart = LineStart(s, 0)
        scanContainers(tree, lineStart, gfmFootnotes)
        lineStart.bytesScanned
    }
    if (allowFootnoteRefs && text[1] == '^' && (text.length <= 2 || text[2] != ']')) {
        val handler: ((String) -> Int?)? = if (gfmFootnotes) {
            null
        } else {
            linebreakHandler
        }
        val scanRest = scanLinkLabelRest(text.substring(2), handler, tree.isInTable())
        if (scanRest != null) {
            val (byteIndex, cow) = scanRest
            return Pair(byteIndex + 2, ReferenceLabel.Footnote(cow))
        }
    }
    val scanRest = scanLinkLabelRest(text.substring(1), linebreakHandler, tree.isInTable()) ?: return null
    val (byteIndex, cow) = scanRest
    return Pair(byteIndex + 1, ReferenceLabel.Link(cow))
}


internal fun scanReference(
    tree: Tree<Item>,
    text: String,
    cur: TreeIndex?,
    allowFootnoteRefs: Boolean,
    gfmFootnotes: Boolean,
): RefScan {
    val curIx = cur ?: return RefScan.Failed
    val start = tree[curIx].item.start
    if (start >= text.length) return RefScan.Failed

    if (text[start] == '[' && start + 1 < text.length && text[start + 1] == ']') {
        val closingNode = tree[curIx].next ?: return RefScan.Failed
        return RefScan.Collapsed(tree[closingNode].next)
    } else {
        val label = scanLinkLabel(tree, text.substring(start), allowFootnoteRefs, gfmFootnotes)
        return when (label?.second) {
            is ReferenceLabel.Link -> RefScan.LinkLabel((label.second as ReferenceLabel.Link).label, start + label.first)
            is ReferenceLabel.Footnote -> RefScan.UnexpectedFootnote
            null -> RefScan.Failed
        }
    }
}

private class LinkStack {
    private val inner: MutableList<LinkStackEl> = mutableListOf()
    private var disabledIx: Int = 0

    fun push(el: LinkStackEl) {
        inner.add(el)
    }

    fun pop(): LinkStackEl? {
        if (inner.isEmpty()) return null
        val el = inner.removeAt(inner.size - 1)
        disabledIx = min(disabledIx, inner.size)
        return el
    }

    fun clear() {
        inner.clear()
        disabledIx = 0
    }

    fun disableAllLinks() {
        for (i in disabledIx until inner.size) {
            if (inner[i].ty == LinkStackTy.Link) {
                inner[i] = LinkStackEl(inner[i].node, LinkStackTy.Disabled)
            }
        }
        disabledIx = inner.size
    }
}

private data class LinkStackEl(
    val node: TreeIndex,
    val ty: LinkStackTy,
)

private enum class LinkStackTy {
    Link,
    Image,
    Disabled,
}

private class CodeDelims {
    private val inner: MutableMap<Int, ArrayDeque<TreeIndex>> = mutableMapOf()
    private var seenFirst: Boolean = false

    fun insert(count: Int, ix: TreeIndex) {
        if (seenFirst) {
            inner.getOrPut(count) { ArrayDeque() }.add(ix)
        } else {
            seenFirst = true
        }
    }

    fun isPopulated(): Boolean = inner.isNotEmpty()

    fun find(openIx: TreeIndex, count: Int): TreeIndex? {
        val deque = inner[count] ?: return null
        while (deque.isNotEmpty()) {
            val ix = deque.removeFirst()
            if (ix > openIx) {
                return ix
            }
        }
        return null
    }

    fun clear() {
        inner.clear()
        seenFirst = false
    }
}
