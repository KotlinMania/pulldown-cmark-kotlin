// port-lint: source pulldown-cmark/src/firstpass.rs
package io.github.kotlinmania.pulldowncmark

import kotlin.math.max

internal fun runFirstPass(text: String, options: Options): Pair<Tree<Item>, Allocations> {
    val startCapacity = max(128, text.length / 32)
    val lookupTable = createLut(options)
    val firstPass =
        FirstPass(
            text = text,
            tree = Tree({ Item(0, 0, ItemBody.Root) }, startCapacity),
            beginListItem = null,
            lastLineBlank = false,
            allocs = Allocations(),
            options = options,
            lookupTable = lookupTable,
            nextParagraphTask = null,
        )
    return firstPass.run()
}

internal class FirstPass(
    private val text: String,
    private val tree: Tree<Item>,
    private var beginListItem: Int?,
    private var lastLineBlank: Boolean,
    private val allocs: Allocations,
    private val options: Options,
    private val lookupTable: BooleanArray,
    private var nextParagraphTask: Item?,
) {
    fun run(): Pair<Tree<Item>, Allocations> {
        var ix = 0
        while (ix < text.length) {
            ix = parseBlock(ix)
        }
        val spineLen = tree.spineLen()
        for (i in 0 until spineLen) {
            pop(ix)
        }
        return Pair(tree, allocs)
    }

    private fun parseBlock(startIxParam: Int): Int {
        var startIx = startIxParam
        var lineStart = LineStart(text, startIx)

        val i = scanContainers(tree, lineStart, options.hasGfmFootnotes())
        val spineLen = tree.spineLen()
        for (k in i until spineLen) {
            pop(startIx)
        }

        if (options.contains(Options.ENABLE_OLD_FOOTNOTES)) {
            val nodeIx = tree.peekUp()
            if (nodeIx != null && tree[nodeIx].item.body is ItemBody.FootnoteDefinition) {
                if (lastLineBlank) {
                    pop(startIx)
                }
            }

            val containerStart = startIx + lineStart.bytesScanned
            val bytecount = parseFootnote(containerStart)
            if (bytecount != null) {
                startIx = containerStart + bytecount
                startIx += scanBlankLine(text, startIx) ?: 0
                lineStart = LineStart(text, startIx)
            }
        }

        while (true) {
            if (options.hasGfmFootnotes() || options.contains(Options.ENABLE_OLD_FOOTNOTES)) {
                val save = lineStart.copy()
                val indent = lineStart.scanSpaceUpto(4)
                if (indent < 4) {
                    val containerStart = startIx + lineStart.bytesScanned
                    val bytecount = parseFootnote(containerStart)
                    if (bytecount != null) {
                        startIx = containerStart + bytecount
                        lineStart = LineStart(text, startIx)
                        continue
                    } else {
                        lineStart = save
                    }
                } else {
                    lineStart = save
                }
            }

            val containerStart = startIx + lineStart.bytesScanned
            val listMarker = lineStart.scanListMarker()
            if (listMarker != null) {
                val (ch, index, indent) = listMarker
                val afterMarkerIndex = startIx + lineStart.bytesScanned
                continueList(containerStart, ch, index)
                tree.append(
                    Item(
                        start = containerStart,
                        end = afterMarkerIndex,
                        body = ItemBody.ListItem(indent),
                    ),
                )
                tree.push()
                val n = scanBlankLine(text, afterMarkerIndex)
                if (n != null) {
                    beginListItem = afterMarkerIndex + n
                    return afterMarkerIndex + n
                }
                if (options.contains(Options.ENABLE_TASKLISTS)) {
                    val isChecked = lineStart.scanTaskListMarker()
                    nextParagraphTask =
                        isChecked?.let {
                            Item(
                                start = afterMarkerIndex,
                                end = startIx + lineStart.bytesScanned,
                                body = ItemBody.TaskListMarker(it),
                            )
                        }
                }
            } else if (lineStart.scanBlockquoteMarker()) {
                finishList(startIx)
                tree.append(
                    Item(
                        start = containerStart,
                        end = 0,
                        body = ItemBody.BlockQuote,
                    ),
                )
                tree.push()
            } else {
                break
            }
        }

        val ix = startIx + lineStart.bytesScanned
        val n = scanBlankLine(text, ix)
        if (n != null) {
            val nodeIx = tree.peekUp()
            if (nodeIx != null) {
                val body = tree[nodeIx].item.body
                when {
                    body is ItemBody.BlockQuote -> {}
                    body is ItemBody.ListItem && beginListItem != null -> {
                        lastLineBlank = true
                        tree[nodeIx].item.body = ItemBody.ListItem(0)
                    }
                    else -> {
                        lastLineBlank = true
                    }
                }
            }
            return ix + n
        }

        finishList(startIx)

        val remainingSpace = lineStart.remainingSpace()
        val indent = lineStart.scanSpaceUpto(4)
        if (indent == 4) {
            val ixIndented = startIx + lineStart.bytesScanned
            val remSpace = lineStart.remainingSpace()
            return parseIndentedCodeBlock(ixIndented, remSpace)
        }

        val ixScan = startIx + lineStart.bytesScanned
        val metadataBlock =
            scanMetadataBlock(
                text,
                ixScan,
                options.contains(Options.ENABLE_YAML_STYLE_METADATA_BLOCKS),
                options.contains(Options.ENABLE_PLUSES_DELIMITED_METADATA_BLOCKS),
            )
        if (metadataBlock != null) {
            return parseMetadataBlock(ixScan, indent, metadataBlock.second)
        }

        if (ixScan < text.length && text[ixScan] == '<') {
            val htmlEndTag = getHtmlEndTag(text, ixScan + 1)
            if (htmlEndTag != null) {
                return parseHtmlBlockType1To5(ixScan, htmlEndTag, remainingSpace, indent)
            }

            if (startsHtmlBlockType6(text, ixScan + 1)) {
                return parseHtmlBlockType6Or7(ixScan, remainingSpace, indent)
            }

            if (scanHtmlType7(text, ixScan) != null) {
                return parseHtmlBlockType6Or7(ixScan, remainingSpace, indent)
            }
        }

        val hruleRes = scanHrule(text, ixScan)
        if (hruleRes != null && hruleRes > 0) {
            return parseHrule(hruleRes, ixScan)
        }

        val atxHeading = scanAtxHeading(text, ixScan)
        if (atxHeading != null) {
            return parseAtxHeading(ixScan, atxHeading)
        }

        val codeFence = scanCodeFence(text, ixScan)
        if (codeFence != null) {
            return parseFencedCodeBlock(ixScan, indent, codeFence.second, codeFence.first)
        }

        while (true) {
            val refdef = parseRefdefTotal(startIx + lineStart.bytesScanned) ?: break
            val (bytecount, label, linkDef) = refdef
            if (!allocs.refdefs.map.containsKey(label)) {
                allocs.refdefs.map[label] = linkDef
            }
            val containerStart = startIx + lineStart.bytesScanned
            var currentIx = containerStart + bytecount

            val nl = scanBlankLine(text, currentIx)
            if (nl != null) {
                currentIx += nl
                val lazyLineStart = LineStart(text, currentIx)
                val currentContainer =
                    scanContainers(
                        tree,
                        lazyLineStart,
                        options.hasGfmFootnotes(),
                    ) == tree.spineLen()
                if (!lazyLineStart.scanSpace(4) &&
                    scanParagraphInterrupt(currentIx + lazyLineStart.bytesScanned, currentContainer)
                ) {
                    return currentIx
                } else {
                    lineStart = lazyLineStart
                    lineStart.scanAllSpace()
                    startIx = currentIx
                }
            } else {
                return currentIx
            }
        }

        val finalIx = startIx + lineStart.bytesScanned
        return parseParagraph(finalIx)
    }

    private fun parseTable(tableCols: Int, headStart: Int, bodyStart: Int): Int? {
        val missingEmptyCells = intArrayOf(0)
        val headRes = parseTableRowInner(headStart, tableCols, missingEmptyCells) ?: return null
        val (_, theadIx) = headRes
        tree[theadIx].item.body = ItemBody.TableHead

        var ix = bodyStart
        while (true) {
            val rowRes = parseTableRow(ix, tableCols, missingEmptyCells) ?: break
            ix = rowRes.first
        }

        pop(ix)
        return ix
    }

    private fun parseTableRowInner(
        ixParam: Int,
        rowCells: Int,
        missingEmptyCells: IntArray,
    ): Pair<Int, TreeIndex>? {
        val maxAutocompletedCells = 1 shl 18
        var cells = 0
        var finalCellIx: TreeIndex? = null
        var ix = ixParam

        val oldCur = tree.cur
        val rowIx =
            tree.append(
                Item(
                    start = ix,
                    end = 0,
                    body = ItemBody.TableRow,
                ),
            )
        tree.push()

        while (true) {
            ix += scanCh(text, ix, '|')
            val startIx = ix
            ix += scanWhitespaceNoNl(text, ix)

            val eolBytes = scanEol(text, ix)
            if (eolBytes != null) {
                ix += eolBytes
                break
            }

            val cellIx =
                tree.append(
                    Item(
                        start = startIx,
                        end = ix,
                        body = ItemBody.TableCell,
                    ),
                )
            tree.push()
            val (nextIx, _) = parseLine(ix, null, TableParseMode.Active)

            tree[cellIx].item.end = nextIx
            tree.pop()

            ix = nextIx
            cells += 1

            if (cells == rowCells) {
                finalCellIx = cellIx
            }
        }

        if (oldCur != null && cells == 0) {
            pop(ix)
            tree[oldCur].next = null
            return null
        }

        for (c in cells until rowCells) {
            if (missingEmptyCells[0] >= maxAutocompletedCells) {
                return null
            }
            missingEmptyCells[0] += 1
            tree.append(
                Item(
                    start = ix,
                    end = ix,
                    body = ItemBody.TableCell,
                ),
            )
        }

        if (finalCellIx != null) {
            tree[finalCellIx].next = null
        }

        pop(ix)
        return Pair(ix, rowIx)
    }

    private fun parseTableRow(
        ixParam: Int,
        rowCells: Int,
        missingEmptyCells: IntArray,
    ): Pair<Int, TreeIndex>? {
        var ix = ixParam
        val lineStart = LineStart(text, ix)
        val currentContainer =
            scanContainers(
                tree,
                lineStart,
                options.hasGfmFootnotes(),
            ) == tree.spineLen()
        if (!currentContainer) {
            return null
        }
        lineStart.scanAllSpace()
        ix += lineStart.bytesScanned
        if (scanParagraphInterruptNoTable(text, ix, currentContainer, options.hasGfmFootnotes(), tree)) {
            return null
        }

        return parseTableRowInner(ix, rowCells, missingEmptyCells)
    }

    private fun parseParagraph(startIx: Int): Int {
        val nodeIx =
            tree.append(
                Item(
                    start = startIx,
                    end = 0,
                    body = ItemBody.Paragraph,
                ),
            )
        tree.push()

        if (nextParagraphTask != null) {
            tree.append(nextParagraphTask!!)
            nextParagraphTask = null
        }

        var ix = startIx
        while (true) {
            val scanMode =
                if (options.contains(Options.ENABLE_TABLES) && ix == startIx) {
                    TableParseMode.Scan
                } else {
                    TableParseMode.Disabled
                }
            val (nextIx, brk) = parseLine(ix, null, scanMode)

            if (brk != null && brk.body is ItemBody.Table) {
                val alignmentIx = (brk.body as ItemBody.Table).alignmentIndex
                val tableCols = allocs.alignments[alignmentIx].size
                tree[nodeIx].item.body = ItemBody.Table(alignmentIx)
                tree[nodeIx].child = null
                tree.pop()
                tree.push()
                val tableEndIx = parseTable(tableCols, ix, nextIx)
                if (tableEndIx != null) {
                    return tableEndIx
                }
            }

            ix = nextIx
            val lineStart = LineStart(text, ix)
            val currentContainer =
                scanContainers(
                    tree,
                    lineStart,
                    options.hasGfmFootnotes(),
                ) == tree.spineLen()
            val trailingBackslashPos =
                if (brk != null && brk.body is ItemBody.HardBreak && (brk.body as ItemBody.HardBreak).isBackslash && brk.start < text.length && text[brk.start] == '\\') {
                    brk.start
                } else {
                    null
                }

            if (!lineStart.scanSpace(4)) {
                val ixNew = ix + lineStart.bytesScanned
                if (currentContainer) {
                    val ixSetext = parseSetextHeading(ixNew, nodeIx, trailingBackslashPos != null)
                    if (ixSetext != null) {
                        if (trailingBackslashPos != null) {
                            tree.appendText(trailingBackslashPos, trailingBackslashPos + 1, false)
                        }
                        ix = ixSetext
                        break
                    }
                }
                if (scanParagraphInterrupt(ixNew, currentContainer)) {
                    if (trailingBackslashPos != null) {
                        tree.appendText(trailingBackslashPos, trailingBackslashPos + 1, false)
                    }
                    break
                }
            }
            lineStart.scanAllSpace()
            if (lineStart.isAtEol()) {
                if (trailingBackslashPos != null) {
                    tree.appendText(trailingBackslashPos, trailingBackslashPos + 1, false)
                }
                break
            }
            ix = nextIx + lineStart.bytesScanned
            if (brk != null) {
                tree.append(brk)
            }
        }

        pop(ix)
        return ix
    }

    private fun parseSetextHeading(
        ix: Int,
        nodeIx: TreeIndex,
        hasTrailingContent: Boolean,
    ): Int? {
        val setext = scanSetextHeading(text, ix) ?: return null
        val (n, level) = setext
        var attrs: HeadingAttributes? = null

        val curIx = tree.cur
        if (curIx != null) {
            val parentIx = tree.peekUp()!!
            val headerStart = tree[parentIx].item.start
            val headerEnd = tree[curIx].item.end

            val (contentEnd, attrsParsed) = extractAndParseHeadingAttributeBlock(headerStart, headerEnd)
            attrs = attrsParsed

            val newEnd =
                if (hasTrailingContent) {
                    contentEnd
                } else {
                    val trailingWs = scanRevWhile(text, headerStart, contentEnd) { isAsciiWhitespaceNoNl(it) }
                    contentEnd - trailingWs
                }

            if (attrs != null) {
                tree.truncateSiblings(newEnd)
            }

            val updatedCur = tree.cur
            if (updatedCur != null) {
                tree[updatedCur].item.end = newEnd
            }
        }

        tree[nodeIx].item.body =
            ItemBody.Heading(
                level,
                attrs?.let { allocs.allocateHeading(it) },
            )

        return ix + n
    }

    private fun parseLine(
        start: Int,
        endParam: Int?,
        mode: TableParseMode,
    ): Pair<Int, Item?> {
        val textEnd = endParam ?: text.length
        var pipes = 0
        var lastPipeIx = start
        var beginText = start
        var backslashEscaped = false

        val (finalIx, brk) =
            iterateSpecialChars(lookupTable, text, start, textEnd) { ix, c ->
                when (c) {
                    '\n', '\r' -> {
                        if (mode == TableParseMode.Active) {
                            LoopInstruction.BreakAtWith(ix, null)
                        } else {
                            var i = ix
                            val eolBytes = scanEol(text, ix) ?: 1
                            val endIx = ix + eolBytes
                            val trailingBackslashes = scanRevWhile(text, 0, ix) { it == '\\' }
                            if (trailingBackslashes % 2 == 1 && endIx < textEnd) {
                                i -= 1
                                tree.appendText(beginText, i, backslashEscaped)
                                backslashEscaped = false
                                LoopInstruction.BreakAtWith(
                                    endIx,
                                    Item(
                                        start = i,
                                        end = endIx,
                                        body = ItemBody.HardBreak(true),
                                    ),
                                )
                            } else if (mode == TableParseMode.Scan && pipes > 0) {
                                val nextLineIx = ix + eolBytes
                                val lineStart = LineStart(text, nextLineIx)
                                if (scanContainers(tree, lineStart, options.hasGfmFootnotes()) == tree.spineLen()) {
                                    val tableHeadIx = nextLineIx + lineStart.bytesScanned
                                    val (tableHeadBytes, alignment) = scanTableHead(text, tableHeadIx)

                                    if (tableHeadBytes > 0) {
                                        val headerCount = countHeaderCols(text, pipes, start, lastPipeIx)
                                        if (alignment.size == headerCount) {
                                            val alignmentIx = allocs.allocateAlignment(alignment)
                                            val endTableIx = tableHeadIx + tableHeadBytes
                                            LoopInstruction.BreakAtWith(
                                                endTableIx,
                                                Item(
                                                    start = i,
                                                    end = endTableIx,
                                                    body = ItemBody.Table(alignmentIx),
                                                ),
                                            )
                                        } else {
                                            emitSoftOrHardBreak(ix, eolBytes, i, beginText, backslashEscaped)
                                        }
                                    } else {
                                        emitSoftOrHardBreak(ix, eolBytes, i, beginText, backslashEscaped)
                                    }
                                } else {
                                    emitSoftOrHardBreak(ix, eolBytes, i, beginText, backslashEscaped)
                                }
                            } else {
                                emitSoftOrHardBreak(ix, eolBytes, i, beginText, backslashEscaped)
                            }
                        }
                    }
                    '\\' -> {
                        if (ix + 1 < textEnd && isAsciiPunctuation(text[ix + 1])) {
                            tree.appendText(beginText, ix, backslashEscaped)
                            val nextByte = text[ix + 1]
                            if (nextByte == '`') {
                                val count = 1 + scanChRepeat(text, ix + 2, '`')
                                tree.append(
                                    Item(
                                        start = ix + 1,
                                        end = ix + count + 1,
                                        body = ItemBody.MaybeCode(count, true),
                                    ),
                                )
                                beginText = ix + 1 + count
                                backslashEscaped = false
                                LoopInstruction.ContinueAndSkip(count)
                            } else if (nextByte == '|' && mode == TableParseMode.Active) {
                                beginText = ix + 1
                                backslashEscaped = false
                                LoopInstruction.ContinueAndSkip(1)
                            } else if (ix + 2 < textEnd && nextByte == '\\' && text[ix + 2] == '|' && mode == TableParseMode.Active) {
                                beginText = ix + 2
                                backslashEscaped = true
                                LoopInstruction.ContinueAndSkip(2)
                            } else {
                                beginText = ix + 1
                                backslashEscaped = true
                                LoopInstruction.ContinueAndSkip(1)
                            }
                        } else {
                            LoopInstruction.ContinueAndSkip(0)
                        }
                    }
                    '*', '_', '~' -> {
                        val count = 1 + scanChRepeat(text, ix + 1, c)
                        val stringSuffix = text.substring(ix)
                        val lineSub = text.substring(start)
                        val canOpen = delimRunCanOpen(lineSub, stringSuffix, count, ix - start, mode)
                        val canClose = delimRunCanClose(lineSub, stringSuffix, count, ix - start, mode)
                        val isValidSeq = c != '~' || count <= 2

                        if ((canOpen || canClose) && isValidSeq) {
                            tree.appendText(beginText, ix, backslashEscaped)
                            backslashEscaped = false
                            for (k in 0 until count) {
                                tree.append(
                                    Item(
                                        start = ix + k,
                                        end = ix + k + 1,
                                        body = ItemBody.MaybeEmphasis(count - k, canOpen, canClose),
                                    ),
                                )
                            }
                            beginText = ix + count
                        }
                        LoopInstruction.ContinueAndSkip(count - 1)
                    }
                    '`' -> {
                        tree.appendText(beginText, ix, backslashEscaped)
                        backslashEscaped = false
                        val count = 1 + scanChRepeat(text, ix + 1, '`')
                        tree.append(
                            Item(
                                start = ix,
                                end = ix + count,
                                body = ItemBody.MaybeCode(count, false),
                            ),
                        )
                        beginText = ix + count
                        LoopInstruction.ContinueAndSkip(count - 1)
                    }
                    '<' -> {
                        if (ix + 1 < text.length && text[ix + 1] == '\\') {
                            LoopInstruction.ContinueAndSkip(0)
                        } else {
                            tree.appendText(beginText, ix, backslashEscaped)
                            backslashEscaped = false
                            tree.append(
                                Item(
                                    start = ix,
                                    end = ix + 1,
                                    body = ItemBody.MaybeHtml,
                                ),
                            )
                            beginText = ix + 1
                            LoopInstruction.ContinueAndSkip(0)
                        }
                    }
                    '!' -> {
                        if (ix + 1 < textEnd && text[ix + 1] == '[') {
                            tree.appendText(beginText, ix, backslashEscaped)
                            backslashEscaped = false
                            tree.append(
                                Item(
                                    start = ix,
                                    end = ix + 2,
                                    body = ItemBody.MaybeImage,
                                ),
                            )
                            beginText = ix + 2
                            LoopInstruction.ContinueAndSkip(1)
                        } else {
                            LoopInstruction.ContinueAndSkip(0)
                        }
                    }
                    '[' -> {
                        tree.appendText(beginText, ix, backslashEscaped)
                        backslashEscaped = false
                        tree.append(
                            Item(
                                start = ix,
                                end = ix + 1,
                                body = ItemBody.MaybeLinkOpen,
                            ),
                        )
                        beginText = ix + 1
                        LoopInstruction.ContinueAndSkip(0)
                    }
                    ']' -> {
                        tree.appendText(beginText, ix, backslashEscaped)
                        backslashEscaped = false
                        tree.append(
                            Item(
                                start = ix,
                                end = ix + 1,
                                body = ItemBody.MaybeLinkClose(true),
                            ),
                        )
                        beginText = ix + 1
                        LoopInstruction.ContinueAndSkip(0)
                    }
                    '&' -> {
                        val (entityBytes, entityVal) = scanEntity(text, ix)
                        if (entityVal != null) {
                            tree.appendText(beginText, ix, backslashEscaped)
                            backslashEscaped = false
                            tree.append(
                                Item(
                                    start = ix,
                                    end = ix + entityBytes,
                                    body = ItemBody.SynthesizeText(allocs.allocateCow(CowStr.from(entityVal))),
                                ),
                            )
                            beginText = ix + entityBytes
                            LoopInstruction.ContinueAndSkip(entityBytes - 1)
                        } else {
                            LoopInstruction.ContinueAndSkip(0)
                        }
                    }
                    '|' -> {
                        if (ix != 0 && text[ix - 1] == '\\') {
                            LoopInstruction.ContinueAndSkip(0)
                        } else if (mode == TableParseMode.Active) {
                            LoopInstruction.BreakAtWith(ix, null)
                        } else {
                            lastPipeIx = ix
                            pipes += 1
                            LoopInstruction.ContinueAndSkip(0)
                        }
                    }
                    '.' -> {
                        if (ix + 2 < text.length && text[ix + 1] == '.' && text[ix + 2] == '.') {
                            tree.appendText(beginText, ix, backslashEscaped)
                            backslashEscaped = false
                            tree.append(
                                Item(
                                    start = ix,
                                    end = ix + 3,
                                    body = ItemBody.SynthesizeChar('…'),
                                ),
                            )
                            beginText = ix + 3
                            LoopInstruction.ContinueAndSkip(2)
                        } else {
                            LoopInstruction.ContinueAndSkip(0)
                        }
                    }
                    '-' -> {
                        val count = 1 + scanChRepeat(text, ix + 1, '-')
                        if (count == 1) {
                            LoopInstruction.ContinueAndSkip(0)
                        } else {
                            val itembody =
                                when (count) {
                                    2 -> ItemBody.SynthesizeChar('–')
                                    3 -> ItemBody.SynthesizeChar('—')
                                    else -> {
                                        val ems =
                                            when (count % 6) {
                                                0, 3 -> count / 3
                                                1 -> count / 3 - 1
                                                else -> count / 3
                                            }
                                        val ens =
                                            when (count % 6) {
                                                0, 3 -> 0
                                                2, 4 -> count / 2
                                                1 -> 2
                                                else -> 1
                                            }
                                        val buf = StringBuilder()
                                        for (k in 0 until ems) buf.append('—')
                                        for (k in 0 until ens) buf.append('–')
                                        ItemBody.SynthesizeText(allocs.allocateCow(CowStr.from(buf.toString())))
                                    }
                                }
                            tree.appendText(beginText, ix, backslashEscaped)
                            backslashEscaped = false
                            tree.append(
                                Item(
                                    start = ix,
                                    end = ix + count,
                                    body = itembody,
                                ),
                            )
                            beginText = ix + count
                            LoopInstruction.ContinueAndSkip(count - 1)
                        }
                    }
                    '\'', '"' -> {
                        val stringSuffix = text.substring(ix)
                        val lineSub = text.substring(start)
                        val canOpen = delimRunCanOpen(lineSub, stringSuffix, 1, ix - start, mode)
                        val canClose = delimRunCanClose(lineSub, stringSuffix, 1, ix - start, mode)

                        tree.appendText(beginText, ix, backslashEscaped)
                        backslashEscaped = false
                        tree.append(
                            Item(
                                start = ix,
                                end = ix + 1,
                                body = ItemBody.MaybeSmartQuote(c, canOpen, canClose),
                            ),
                        )
                        beginText = ix + 1
                        LoopInstruction.ContinueAndSkip(0)
                    }
                    else -> LoopInstruction.ContinueAndSkip(0)
                }
            }

        if (brk == null) {
            val trailingWhitespace = scanRevWhile(text, beginText, finalIx) { isAsciiWhitespaceNoNl(it) }
            tree.appendText(beginText, finalIx - trailingWhitespace, backslashEscaped)
        }

        return Pair(finalIx, brk)
    }

    private fun emitSoftOrHardBreak(
        ix: Int,
        eolBytes: Int,
        iParam: Int,
        beginText: Int,
        backslashEscaped: Boolean,
    ): LoopInstruction<Item?> {
        var i = iParam
        val endIx = ix + eolBytes
        val trailingWhitespace = scanRevWhile(text, 0, ix) { isAsciiWhitespaceNoNl(it) }
        return if (trailingWhitespace >= 2) {
            i -= trailingWhitespace
            tree.appendText(beginText, i, backslashEscaped)
            LoopInstruction.BreakAtWith(
                endIx,
                Item(
                    start = i,
                    end = endIx,
                    body = ItemBody.HardBreak(false),
                ),
            )
        } else {
            tree.appendText(beginText, ix - trailingWhitespace, backslashEscaped)
            LoopInstruction.BreakAtWith(
                endIx,
                Item(
                    start = i,
                    end = endIx,
                    body = ItemBody.SoftBreak,
                ),
            )
        }
    }

    private fun parseHtmlBlockType1To5(
        startIx: Int,
        htmlEndTag: String,
        remainingSpaceParam: Int,
        indentParam: Int,
    ): Int {
        var remainingSpace = remainingSpaceParam
        var indent = indentParam
        tree.append(
            Item(
                start = startIx,
                end = 0,
                body = ItemBody.HtmlBlock,
            ),
        )
        tree.push()

        var ix = startIx
        val endIx: Int
        while (true) {
            val lineStartIx = ix
            ix += scanNextLine(text, ix)
            appendHtmlLine(max(remainingSpace, indent), lineStartIx, ix)

            val lineStart = LineStart(text, ix)
            val nContainers = scanContainers(tree, lineStart, options.hasGfmFootnotes())
            if (nContainers < tree.spineLen()) {
                endIx = ix
                break
            }

            if (text.substring(lineStartIx, ix).contains(htmlEndTag)) {
                endIx = ix
                break
            }

            val nextLineIx = ix + lineStart.bytesScanned
            if (nextLineIx == text.length) {
                endIx = nextLineIx
                break
            }
            ix = nextLineIx
            remainingSpace = lineStart.remainingSpace()
            indent = 0
        }
        pop(endIx)
        return ix
    }

    private fun parseHtmlBlockType6Or7(
        startIx: Int,
        remainingSpaceParam: Int,
        indentParam: Int,
    ): Int {
        var remainingSpace = remainingSpaceParam
        var indent = indentParam
        tree.append(
            Item(
                start = startIx,
                end = 0,
                body = ItemBody.HtmlBlock,
            ),
        )
        tree.push()

        var ix = startIx
        val endIx: Int
        while (true) {
            val lineStartIx = ix
            ix += scanNextLine(text, ix)
            appendHtmlLine(max(remainingSpace, indent), lineStartIx, ix)

            val lineStart = LineStart(text, ix)
            val nContainers = scanContainers(tree, lineStart, options.hasGfmFootnotes())
            if (nContainers < tree.spineLen() || lineStart.isAtEol()) {
                endIx = ix
                break
            }

            val nextLineIx = ix + lineStart.bytesScanned
            if (nextLineIx == text.length || scanBlankLine(text, nextLineIx) != null) {
                endIx = nextLineIx
                break
            }
            ix = nextLineIx
            remainingSpace = lineStart.remainingSpace()
            indent = 0
        }
        pop(endIx)
        return ix
    }

    private fun parseIndentedCodeBlock(startIx: Int, remainingSpaceParam: Int): Int {
        var remainingSpace = remainingSpaceParam
        tree.append(
            Item(
                start = startIx,
                end = 0,
                body = ItemBody.IndentCodeBlock,
            ),
        )
        tree.push()
        var lastNonblankChild: TreeIndex? = null
        var lastNonblankIx = 0
        var endIx = 0
        lastLineBlank = false

        var ix = startIx
        while (true) {
            val lineStartIx = ix
            ix += scanNextLine(text, ix)
            appendCodeText(remainingSpace, lineStartIx, ix)

            if (!lastLineBlank) {
                lastNonblankChild = tree.cur
                lastNonblankIx = ix
                endIx = ix
            }

            val lineStart = LineStart(text, ix)
            val nContainers = scanContainers(tree, lineStart, options.hasGfmFootnotes())
            if (nContainers < tree.spineLen() || !(lineStart.scanSpace(4) || lineStart.isAtEol())) {
                break
            }
            val nextLineIx = ix + lineStart.bytesScanned
            if (nextLineIx == text.length) {
                break
            }
            ix = nextLineIx
            remainingSpace = lineStart.remainingSpace()
            lastLineBlank = scanBlankLine(text, ix) != null
        }

        if (lastNonblankChild != null) {
            tree[lastNonblankChild].next = null
            tree[lastNonblankChild].item.end = lastNonblankIx
        }
        pop(endIx)
        return ix
    }

    private fun parseFencedCodeBlock(
        startIx: Int,
        indent: Int,
        fenceCh: Char,
        nFenceChar: Int,
    ): Int {
        var infoStart = startIx + nFenceChar
        infoStart += scanWhitespaceNoNl(text, infoStart)
        var ix = infoStart + scanNextLine(text, infoStart)
        val infoEnd = ix - scanRevWhile(text, infoStart, ix) { isAsciiWhitespace(it) }
        val infoString = unescape(text.substring(infoStart, infoEnd), tree.isInTable())
        tree.append(
            Item(
                start = startIx,
                end = 0,
                body = ItemBody.FencedCodeBlock(allocs.allocateCow(CowStr.from(infoString))),
            ),
        )
        tree.push()
        while (true) {
            val lineStart = LineStart(text, ix)
            val nContainers = scanContainers(tree, lineStart, options.hasGfmFootnotes())
            if (nContainers < tree.spineLen()) {
                pop(ix)
                return ix
            }
            lineStart.scanSpace(indent)
            val closeLineStart = lineStart.copy()
            if (!closeLineStart.scanSpace(4 - indent)) {
                val closeIx = ix + closeLineStart.bytesScanned
                val n = scanClosingCodeFence(text, closeIx, fenceCh, nFenceChar)
                if (n != null) {
                    ix = closeIx + n
                    pop(ix)
                    return ix + (scanBlankLine(text, ix) ?: 0)
                }
            }
            val remainingSpace = lineStart.remainingSpace()
            ix += lineStart.bytesScanned
            val nextIx = ix + scanNextLine(text, ix)
            appendCodeText(remainingSpace, ix, nextIx)
            ix = nextIx
        }
    }

    private fun parseMetadataBlock(
        startIx: Int,
        indent: Int,
        metadataBlockCh: Char,
    ): Int {
        if (indent > 0) {
            return 0
        }
        val metadataBlockKind =
            when (metadataBlockCh) {
                '-' -> MetadataBlockKind.YamlStyle
                '+' -> MetadataBlockKind.PlusesStyle
                else -> error("Erroneous metadata block character")
            }
        var ix = startIx + 3 + scanNextLine(text, startIx + 3)
        tree.append(
            Item(
                start = startIx,
                end = 0,
                body = ItemBody.MetadataBlock(metadataBlockKind),
            ),
        )
        tree.push()
        while (true) {
            val lineStart = LineStart(text, ix)
            val nContainers = scanContainers(tree, lineStart, options.hasGfmFootnotes())
            if (nContainers < tree.spineLen()) {
                break
            }
            val (_, indentLevel) = calcIndent(text, ix, 4)
            if (indentLevel == 0) {
                val n = scanClosingMetadataBlock(text, ix, metadataBlockCh)
                if (n != null) {
                    ix += n
                    break
                }
            }
            val remainingSpace = lineStart.remainingSpace()
            ix += lineStart.bytesScanned
            val nextIx = ix + scanNextLine(text, ix)
            appendCodeText(remainingSpace, ix, nextIx)
            ix = nextIx
        }

        pop(ix)
        return ix + (scanBlankLine(text, ix) ?: 0)
    }

    private fun appendCodeText(remainingSpace: Int, start: Int, end: Int) {
        if (remainingSpace > 0) {
            val spaces = "   ".substring(0, remainingSpace)
            val cowIx = allocs.allocateCow(CowStr.from(spaces))
            tree.append(
                Item(
                    start = start,
                    end = start,
                    body = ItemBody.SynthesizeText(cowIx),
                ),
            )
        }
        if (end >= 2 && text[end - 2] == '\r') {
            tree.appendText(start, end - 2, false)
            tree.appendText(end - 1, end, false)
        } else {
            tree.appendText(start, end, false)
        }
    }

    private fun appendHtmlLine(remainingSpace: Int, start: Int, end: Int) {
        if (remainingSpace > 0) {
            val spaces = "   ".substring(0, remainingSpace)
            val cowIx = allocs.allocateCow(CowStr.from(spaces))
            tree.append(
                Item(
                    start = start,
                    end = start,
                    body = ItemBody.SynthesizeText(cowIx),
                ),
            )
        }
        if (end >= 2 && text[end - 2] == '\r') {
            tree.append(Item(start = start, end = end - 2, body = ItemBody.Html))
            tree.append(Item(start = end - 1, end = end, body = ItemBody.Html))
        } else {
            tree.append(Item(start = start, end = end, body = ItemBody.Html))
        }
    }

    private fun pop(ix: Int) {
        val curIx = tree.pop() ?: return
        tree[curIx].item.end = ix
        if (tree[curIx].item.body is ItemBody.List && (tree[curIx].item.body as ItemBody.List).isTight) {
            surgerizeTightList(tree, curIx)
            beginListItem = null
        }
    }

    private fun finishList(ix: Int) {
        finishEmptyListItem()
        val nodeIx = tree.peekUp()
        if (nodeIx != null && tree[nodeIx].item.body is ItemBody.List) {
            pop(ix)
        }
        if (lastLineBlank) {
            val grandparent = tree.peekGrandparent()
            if (grandparent != null) {
                val body = tree[grandparent].item.body
                if (body is ItemBody.List) {
                    tree[grandparent].item.body = ItemBody.List(false, body.listChar, body.listStart)
                }
            }
            lastLineBlank = false
        }
    }

    private fun finishEmptyListItem() {
        val beginItem = beginListItem
        if (beginItem != null && lastLineBlank) {
            val nodeIx = tree.peekUp()
            if (nodeIx != null && tree[nodeIx].item.body is ItemBody.ListItem) {
                pop(beginItem)
            }
        }
        beginListItem = null
    }

    private fun continueList(start: Int, ch: Char, index: Long) {
        finishEmptyListItem()
        val nodeIx = tree.peekUp()
        if (nodeIx != null) {
            val body = tree[nodeIx].item.body
            if (body is ItemBody.List && body.listChar == ch) {
                if (lastLineBlank) {
                    tree[nodeIx].item.body = ItemBody.List(false, body.listChar, body.listStart)
                    lastLineBlank = false
                }
                return
            }
            finishList(start)
        }
        tree.append(
            Item(
                start = start,
                end = 0,
                body = ItemBody.List(true, ch, index),
            ),
        )
        tree.push()
        lastLineBlank = false
    }

    private fun parseHrule(hruleSize: Int, ix: Int): Int {
        tree.append(
            Item(
                start = ix,
                end = ix + hruleSize,
                body = ItemBody.Rule,
            ),
        )
        return ix + hruleSize
    }

    private fun parseAtxHeading(start: Int, atxLevel: HeadingLevel): Int {
        var ix = start
        val headingIx =
            tree.append(
                Item(
                    start = start,
                    end = 0,
                    body = ItemBody.Root,
                ),
            )
        ix += atxLevel.level
        val eolBytes = scanEol(text, ix)
        if (eolBytes != null) {
            tree[headingIx].item.end = ix + eolBytes
            tree[headingIx].item.body = ItemBody.Heading(atxLevel, null)
            return ix + eolBytes
        }
        val skipSpaces = scanWhitespaceNoNl(text, ix)
        ix += skipSpaces

        val headerStart = ix
        val headerNodeIdx = tree.push()

        val (end, contentEnd, attrs) =
            if (options.contains(Options.ENABLE_HEADING_ATTRIBUTES)) {
                val headerEnd = headerStart + scanNextLine(text, headerStart)
                val (cEnd, attrsParsed) = extractAndParseHeadingAttributeBlock(headerStart, headerEnd)
                parseLine(ix, cEnd, TableParseMode.Disabled)
                Triple(headerEnd, cEnd, attrsParsed)
            } else {
                val (lineIx, lineBrk) = parseLine(ix, null, TableParseMode.Disabled)
                ix = lineIx
                if (lineBrk != null && lineBrk.body is ItemBody.HardBreak && (lineBrk.body as ItemBody.HardBreak).isBackslash) {
                    tree.appendText(lineBrk.start, lineBrk.end, false)
                }
                Triple(ix, ix, null)
            }
        tree[headerNodeIdx].item.end = end

        var emptyTextNode = false
        val curIx = tree.cur
        if (curIx != null) {
            var limit = 0
            for (idx in (contentEnd - 1) downTo headerStart) {
                val b = text[idx]
                if (b != '\n' && b != '\r' && b != ' ') {
                    limit = idx - headerStart + 1
                    break
                }
            }
            var closer = 0
            for (idx in (headerStart + limit - 1) downTo headerStart) {
                if (text[idx] != '#') {
                    closer = idx - headerStart + 1
                    break
                }
            }
            if (closer == 0) {
                limit = closer
            } else {
                val spaces = scanRevWhile(text, headerStart, headerStart + closer) { it == ' ' }
                if (spaces > 0) {
                    limit = closer - spaces
                }
            }
            tree[curIx].item.end = limit + headerStart
            if (limit == 0) {
                emptyTextNode = true
            }
        }

        if (emptyTextNode) {
            tree.removeNode()
        } else {
            tree.pop()
        }
        tree[headingIx].item.body =
            ItemBody.Heading(
                atxLevel,
                attrs?.let { allocs.allocateHeading(it) },
            )

        return end
    }

    private fun parseFootnote(start: Int): Int? {
        if (start + 1 >= text.length || text[start] != '[' || text[start + 1] != '^') {
            return null
        }
        val labelRes: Pair<Int, CowStr>? =
            if (options.hasGfmFootnotes()) {
                scanLinkLabelRest(text.substring(start + 2), { null }, tree.isInTable())
            } else {
                parseRefdefLabel(start + 2)
            }
        val (labelLen, label) = labelRes ?: return null
        if (options.hasGfmFootnotes() && (label.asString().contains('\r') || label.asString().contains('\n'))) {
            return null
        }
        var i = labelLen + 2
        if (scanCh(text, start + i, ':') == 0) {
            return null
        }
        i += 1
        finishList(start)
        if (options.hasGfmFootnotes()) {
            val nodeIx = tree.peekUp()
            if (nodeIx != null && tree[nodeIx].item.body is ItemBody.FootnoteDefinition) {
                pop(start)
            }
            i += scanWhitespaceNoNl(text, start + i)
        }
        allocs.footdefs.map[UniCase(label.clone())] = FootnoteDef(0)
        tree.append(
            Item(
                start = start,
                end = 0,
                body = ItemBody.FootnoteDefinition(allocs.allocateCow(label)),
            ),
        )
        tree.push()
        return i
    }

    private fun parseRefdefLabel(start: Int): Pair<Int, CowStr>? =
        scanLinkLabelRest(
            text.substring(start),
            { b ->
                val lineStart = LineStart(b, 0)
                val currentContainer = scanContainers(tree, lineStart, options.hasGfmFootnotes()) == tree.spineLen()
                if (lineStart.scanSpace(4)) {
                    lineStart.bytesScanned
                } else {
                    val bytesScanned = lineStart.bytesScanned
                    if (scanParagraphInterrupt(b, bytesScanned, currentContainer) ||
                        (currentContainer && scanSetextHeading(b, bytesScanned) != null)
                    ) {
                        null
                    } else {
                        bytesScanned
                    }
                }
            },
            tree.isInTable(),
        )

    private fun parseRefdefTotal(start: Int): Triple<Int, UniCase, LinkDef>? {
        if (scanCh(text, start, '[') == 0) {
            return null
        }
        val (labelLen, label) = parseRefdefLabel(start + 1) ?: return null
        var i = labelLen + 1
        if (scanCh(text, start + i, ':') == 0) {
            return null
        }
        i += 1
        val (bytecount, linkDef) = scanRefdef(start, start + i) ?: return null
        return Triple(bytecount + i, UniCase(label), linkDef)
    }

    private fun scanRefdefSpace(iParam: Int): Pair<Int, Int>? {
        var i = iParam
        var newlines = 0
        while (true) {
            val whitespaces = scanWhitespaceNoNl(text, i)
            i += whitespaces
            val eolBytes = scanEol(text, i)
            if (eolBytes != null) {
                i += eolBytes
                newlines += 1
                if (newlines > 1) {
                    return null
                }
            } else {
                break
            }
            val lineStart = LineStart(text, i)
            val currentContainer = scanContainers(tree, lineStart, options.hasGfmFootnotes()) == tree.spineLen()
            if (!lineStart.scanSpace(4)) {
                val scanned = lineStart.bytesScanned
                if (scanParagraphInterrupt(i + scanned, currentContainer) ||
                    scanSetextHeading(text, i + scanned) != null
                ) {
                    return null
                }
            }
            i += lineStart.bytesScanned
        }
        return Pair(i, newlines)
    }

    private fun scanRefdefTitle(textSub: String): Pair<Int, CowStr>? {
        if (textSub.isEmpty()) return null
        val firstChar = textSub[0]
        val closingDelim =
            when (firstChar) {
                '\'' -> '\''
                '"' -> '"'
                '(' -> ')'
                else -> return null
            }
        var bytecount = 1
        var linestart = 1
        var linebuf: StringBuilder? = null

        while (bytecount < textSub.length) {
            val c = textSub[bytecount]
            when {
                c == '(' && closingDelim == ')' -> return null
                c == '\n' || c == '\r' -> {
                    if (linebuf == null) linebuf = StringBuilder()
                    linebuf.append(textSub.substring(linestart, bytecount))
                    linebuf.append('\n')
                    bytecount += 1
                    if (c == '\r' && bytecount < textSub.length && textSub[bytecount] == '\n') {
                        bytecount += 1
                    }
                    val lineStart = LineStart(textSub, bytecount)
                    val currentContainer = scanContainers(tree, lineStart, options.hasGfmFootnotes()) == tree.spineLen()
                    if (!lineStart.scanSpace(4)) {
                        val scanned = lineStart.bytesScanned
                        if (scanParagraphInterrupt(textSub, bytecount + scanned, currentContainer) ||
                            scanSetextHeading(textSub, bytecount + scanned) != null
                        ) {
                            return null
                        }
                    }
                    lineStart.scanAllSpace()
                    bytecount += lineStart.bytesScanned
                    linestart = bytecount
                    if (scanBlankLine(textSub, bytecount) != null) {
                        return null
                    }
                }
                c == '\\' -> {
                    bytecount += 1
                    if (bytecount < textSub.length) {
                        val nextC = textSub[bytecount]
                        if (nextC != '\r' && nextC != '\n') {
                            bytecount += 1
                        }
                    }
                }
                c == closingDelim -> {
                    val cow =
                        if (linebuf != null) {
                            linebuf.append(textSub.substring(linestart, bytecount))
                            CowStr.from(linebuf.toString())
                        } else {
                            CowStr.from(textSub.substring(linestart, bytecount))
                        }
                    return Pair(bytecount + 1, cow)
                }
                else -> bytecount += 1
            }
        }
        return null
    }

    private fun scanRefdef(spanStart: Int, start: Int): Pair<Int, LinkDef>? {
        val spaceRes = scanRefdefSpace(start) ?: return null
        var (i, _) = spaceRes

        val destRes = scanLinkDest(text, i, LINK_MAX_NESTED_PARENS) ?: return null
        val (destLength, dest) = destRes
        if (destLength == 0) return null
        val destUnescaped = unescape(dest, tree.isInTable())
        i += destLength

        var backup =
            Pair(
                i - start,
                LinkDef(
                    dest = CowStr.from(destUnescaped),
                    title = null,
                    span = spanStart until i,
                ),
            )

        val nextSpaceRes = scanRefdefSpace(i)
        val newlines: Int
        if (nextSpaceRes != null) {
            val (newI, nl) = nextSpaceRes
            var effectiveNl = nl
            if (i == text.length) effectiveNl += 1
            if (newI == i && effectiveNl == 0) return null
            if (effectiveNl > 1) return backup
            i = newI
            newlines = effectiveNl
        } else {
            return backup
        }

        if (i < text.length) {
            val titleRes = scanRefdefTitle(text.substring(i))
            if (titleRes != null) {
                val (titleLength, title) = titleRes
                i += titleLength
                if (scanBlankLine(text, i) != null) {
                    backup =
                        Pair(
                            i - start,
                            LinkDef(
                                dest = CowStr.from(destUnescaped),
                                title = CowStr.from(unescape(title.asString(), tree.isInTable())),
                                span = spanStart until i,
                            ),
                        )
                    return backup
                }
            }
        }

        return if (newlines > 0) backup else null
    }

    private fun scanParagraphInterrupt(offset: Int, currentContainer: Boolean): Boolean = scanParagraphInterrupt(text, offset, currentContainer)

    private fun scanParagraphInterrupt(textParam: String, offset: Int, currentContainer: Boolean): Boolean {
        val gfmFootnote = options.hasGfmFootnotes()
        if (scanParagraphInterruptNoTable(textParam, offset, currentContainer, gfmFootnote, tree)) {
            return true
        }
        if (!options.contains(Options.ENABLE_TABLES) || offset >= textParam.length || textParam[offset] != '|') {
            return false
        }

        var pipes = 0
        var nextLineIx = 0
        var bsesc = false
        var lastPipeIx = 0
        for (idx in offset until textParam.length) {
            val c = textParam[idx]
            when (c) {
                '\\' -> {
                    bsesc = true
                    continue
                }
                '|' -> {
                    if (!bsesc) {
                        pipes += 1
                        lastPipeIx = idx - offset
                    }
                }
                '\r', '\n' -> {
                    val eol = scanEol(textParam, idx) ?: 1
                    nextLineIx = (idx - offset) + eol
                    break
                }
            }
            bsesc = false
        }

        if (nextLineIx == 0) return false

        val lineStart = LineStart(textParam, offset + nextLineIx)
        if (scanContainers(tree, lineStart, options.hasGfmFootnotes()) != tree.spineLen()) {
            return false
        }
        val tableHeadIx = offset + nextLineIx + lineStart.bytesScanned
        val (tableHeadBytes, alignment) = scanTableHead(textParam, tableHeadIx)
        if (tableHeadBytes == 0) return false

        val headerCount = countHeaderCols(textParam, pipes, offset, offset + lastPipeIx)
        return alignment.size == headerCount
    }

    private fun extractAndParseHeadingAttributeBlock(
        headerStart: Int,
        headerEnd: Int,
    ): Pair<Int, HeadingAttributes?> {
        if (!options.contains(Options.ENABLE_HEADING_ATTRIBUTES)) {
            return Pair(headerEnd, null)
        }
        val headerText = text.substring(headerStart, headerEnd)
        val (contentLen, attrRange) = extractAttributeBlockContentFromHeaderText(headerText)
        val contentEnd = headerStart + contentLen
        val attrs =
            if (attrRange != null) {
                val sub = text.substring(headerStart + attrRange.first, headerStart + attrRange.last + 1)
                parseInsideAttributeBlock(sub)
            } else {
                null
            }
        return Pair(contentEnd, attrs)
    }
}

internal enum class TableParseMode {
    Scan,
    Active,
    Disabled,
}

internal fun countHeaderCols(
    text: String,
    pipesParam: Int,
    startParam: Int,
    lastPipeIx: Int,
): Int {
    var pipes = pipesParam
    var start = startParam
    start += scanWhitespaceNoNl(text, start)
    if (start < text.length && text[start] == '|') {
        pipes -= 1
    }
    return if (scanBlankLine(text, lastPipeIx + 1) != null) {
        pipes
    } else {
        pipes + 1
    }
}

internal fun scanParagraphInterruptNoTable(
    text: String,
    offset: Int,
    currentContainer: Boolean,
    gfmFootnote: Boolean,
    tree: Tree<Item>,
): Boolean {
    if (scanEol(text, offset) != null) return true
    val hr = scanHrule(text, offset)
    if (hr != null && hr > 0) return true
    if (scanAtxHeading(text, offset) != null) return true
    if (scanCodeFence(text, offset) != null) return true
    if (scanBlockquoteStart(text, offset) != null) return true

    val listitem = scanListItem(text, offset)
    if (listitem != null) {
        val (ix, delim, index, _) = listitem
        val allowed =
            !currentContainer ||
                tree.isInTable() ||
                (
                    (delim == '*' || delim == '-' || delim == '+' || index == 1L) &&
                        scanBlankLine(text, offset + ix) == null
                )
        if (allowed) return true
    }

    if (offset < text.length && text[offset] == '<') {
        if (getHtmlEndTag(text, offset + 1) != null || startsHtmlBlockType6(text, offset + 1)) {
            return true
        }
    }

    if (gfmFootnote && offset + 1 < text.length && text[offset] == '[' && text[offset + 1] == '^') {
        val subStr = text.substring(offset + 2)
        val linkRest = scanLinkLabelRest(subStr, { null }, tree.isInTable())
        if (linkRest != null) {
            val labelLen = linkRest.first
            if (offset + 2 + labelLen < text.length && text[offset + 2 + labelLen] == ':') {
                return true
            }
        }
    }

    return false
}

private val BEGIN_TAGS = arrayOf("pre", "style", "script", "textarea")
private val END_TAGS = arrayOf("</pre>", "</style>", "</script>", "</textarea>")
private val ST_BEGIN_TAGS = arrayOf("!--", "?", "![CDATA[")
private val ST_END_TAGS = arrayOf("-->", "?>", "]]>")

internal fun getHtmlEndTag(text: String, offset: Int): String? {
    val remaining = text.length - offset
    if (remaining <= 0) return null

    for (i in BEGIN_TAGS.indices) {
        val begTag = BEGIN_TAGS[i]
        val endTag = END_TAGS[i]
        val tagLen = begTag.length

        if (remaining < tagLen) continue

        if (!text.regionMatches(offset, begTag, 0, tagLen, ignoreCase = true)) {
            continue
        }

        if (remaining == tagLen) return endTag

        val s = text[offset + tagLen]
        if (isAsciiWhitespace(s) || s == '>') return endTag
    }

    for (i in ST_BEGIN_TAGS.indices) {
        val begTag = ST_BEGIN_TAGS[i]
        val endTag = ST_END_TAGS[i]
        if (remaining >= begTag.length) {
            if (text.regionMatches(offset, begTag, 0, begTag.length)) {
                return endTag
            }
        }
    }

    if (remaining > 1 && text[offset] == '!' && text[offset + 1].isLetter()) {
        return ">"
    }

    return null
}

internal fun surgerizeTightList(tree: Tree<Item>, listIx: TreeIndex) {
    var listItem = tree[listIx].child
    while (listItem != null) {
        val listitemIx = listItem
        val listItemFirstborn = tree[listitemIx].child

        if (listItemFirstborn != null) {
            val firstbornIx = listItemFirstborn
            if (tree[firstbornIx].item.body is ItemBody.Paragraph) {
                tree[listitemIx].child = tree[firstbornIx].child
            }

            var listItemChild: TreeIndex? = firstbornIx
            var nodeToRepoint: TreeIndex? = null
            while (listItemChild != null) {
                val childIx = listItemChild
                val repointIx =
                    if (tree[childIx].item.body is ItemBody.Paragraph) {
                        val childFirstborn = tree[childIx].child
                        if (childFirstborn != null) {
                            if (nodeToRepoint != null) {
                                tree[nodeToRepoint].next = childFirstborn
                            }
                            var childLastborn = childFirstborn
                            var nextNode = tree[childLastborn].next
                            while (nextNode != null) {
                                childLastborn = nextNode
                                nextNode = tree[childLastborn].next
                            }
                            childLastborn
                        } else {
                            childIx
                        }
                    } else {
                        childIx
                    }

                nodeToRepoint = repointIx
                tree[repointIx].next = tree[childIx].next
                listItemChild = tree[childIx].next
            }
        }

        listItem = tree[listitemIx].next
    }
}

internal fun delimRunCanOpen(
    s: String,
    suffix: String,
    runLen: Int,
    ix: Int,
    mode: TableParseMode,
): Boolean {
    if (runLen >= suffix.length) return false
    val nextChar = suffix[runLen]
    if (nextChar.isWhitespace()) return false
    if (ix == 0) return true
    if (mode == TableParseMode.Active) {
        val prevSub = s.substring(0, ix)
        if (prevSub.endsWith('|') && !prevSub.endsWith("""\|""")) return true
        if (nextChar == '|') return false
    }
    val delim = suffix[0]
    if (delim == '*' && !isPunctuation(nextChar)) return true
    if (delim == '~' && runLen > 1) return true
    val prevChar = s.substring(0, ix).last()
    if (delim == '~' && prevChar == '~' && !isPunctuation(nextChar)) return true

    return prevChar.isWhitespace() || (isPunctuation(prevChar) && (delim != '\'' || (prevChar != ']' && prevChar != ')')))
}

internal fun delimRunCanClose(
    s: String,
    suffix: String,
    runLen: Int,
    ix: Int,
    mode: TableParseMode,
): Boolean {
    if (ix == 0) return false
    val prevChar = s.substring(0, ix).last()
    if (prevChar.isWhitespace()) return false
    if (runLen >= suffix.length) return true
    val nextChar = suffix[runLen]
    if (mode == TableParseMode.Active) {
        val prevSub = s.substring(0, ix)
        if (prevSub.endsWith('|') && !prevSub.endsWith("""\|""")) return false
        if (nextChar == '|') return true
    }
    val delim = suffix[0]
    if ((delim == '*' || (delim == '~' && runLen > 1)) && !isPunctuation(prevChar)) return true
    if (delim == '~' && prevChar == '~') return true

    return nextChar.isWhitespace() || isPunctuation(nextChar)
}

internal fun createLut(options: Options): BooleanArray = specialBytes(options)

internal fun specialBytes(options: Options): BooleanArray {
    val bytes = BooleanArray(256)
    val standardChars = charArrayOf('\n', '\r', '*', '_', '&', '\\', '[', ']', '<', '!', '`')
    for (c in standardChars) {
        bytes[c.code and 0xFF] = true
    }
    if (options.contains(Options.ENABLE_TABLES)) {
        bytes['|'.code and 0xFF] = true
    }
    if (options.contains(Options.ENABLE_STRIKETHROUGH)) {
        bytes['~'.code and 0xFF] = true
    }
    if (options.contains(Options.ENABLE_SMART_PUNCTUATION)) {
        val smart = charArrayOf('.', '-', '"', '\'')
        for (c in smart) {
            bytes[c.code and 0xFF] = true
        }
    }
    return bytes
}

internal sealed class LoopInstruction<out T> {
    data class ContinueAndSkip(
        val skip: Int,
    ) : LoopInstruction<Nothing>()

    data class BreakAtWith<T>(
        val ix: Int,
        val value: T,
    ) : LoopInstruction<T>()
}

internal fun iterateSpecialChars(
    lut: BooleanArray,
    text: String,
    startIx: Int,
    textEnd: Int,
    callback: (Int, Char) -> LoopInstruction<Item?>,
): Pair<Int, Item?> {
    var ix = startIx
    while (ix < textEnd) {
        val c = text[ix]
        val code = c.code
        if (code < 256 && lut[code]) {
            when (val instruction = callback(ix, c)) {
                is LoopInstruction.ContinueAndSkip -> ix += instruction.skip
                is LoopInstruction.BreakAtWith -> return Pair(instruction.ix, instruction.value)
            }
        }
        ix += 1
    }
    return Pair(textEnd, null)
}

internal inline fun scanRevWhile(text: String, minIx: Int, startIx: Int, predicate: (Char) -> Boolean): Int {
    var count = 0
    var i = startIx - 1
    while (i >= minIx && predicate(text[i])) {
        count++
        i--
    }
    return count
}

internal fun extractAttributeBlockContentFromHeaderText(heading: String): Pair<Int, IntRange?> {
    val headingLen = heading.length
    var ix = headingLen
    ix -=
        scanRevWhile(heading, 0, headingLen) { c ->
            c == '\n' || c == '\r' || c == ' ' || c == '\t'
        }
    if (ix == 0) return Pair(headingLen, null)

    val attrBlockClose = ix - 1
    if (heading[attrBlockClose] != '}') return Pair(headingLen, null)
    ix -= 1

    ix -=
        scanRevWhile(heading, 0, ix) { c ->
            c != '{' && c != '}' && c != '<' && c != '>' && c != '\\' && c != '\n' && c != '\r'
        }
    if (ix == 0) return Pair(headingLen, null)
    val attrBlockOpen = ix - 1
    if (heading[attrBlockOpen] != '{') return Pair(headingLen, null)

    return Pair(attrBlockOpen, ix until attrBlockClose)
}

internal fun parseInsideAttributeBlock(insideAttrBlock: String): HeadingAttributes? {
    var id: CowStr? = null
    val classes = mutableListOf<CowStr>()
    val attrs = mutableListOf<HeadingAttribute>()

    val tokens = insideAttrBlock.split(Regex("\\s+")).filter { it.isNotEmpty() }
    for (attr in tokens) {
        if (attr.length > 1) {
            when (attr[0]) {
                '#' -> id = CowStr.from(attr.substring(1))
                '.' -> classes.add(CowStr.from(attr.substring(1)))
                else -> {
                    val eqIndex = attr.indexOf('=')
                    if (eqIndex >= 0) {
                        val key = attr.substring(0, eqIndex)
                        val value = attr.substring(eqIndex + 1)
                        attrs.add(HeadingAttribute(CowStr.from(key), CowStr.from(value)))
                    } else {
                        attrs.add(HeadingAttribute(CowStr.from(attr), null))
                    }
                }
            }
        }
    }
    return HeadingAttributes(id, classes, attrs)
}
