// port-lint: source html.rs
package io.github.kotlinmania.pulldowncmark

private enum class TableState {
    Head,
    Body,
}

/**
 * HTML renderer converting a stream of markdown events into HTML.
 */
internal class HtmlWriter(
    private val iter: Iterator<Event>,
    private val out: StringBuilder,
) {
    private var endNewline: Boolean = true
    private var inNonWritingBlock: Boolean = false
    private var tableState: TableState = TableState.Head
    private var tableAlignments: List<Alignment> = emptyList()
    private var tableCellIndex: Int = 0
    private val numbers = HashMap<CowStr, Int>()

    private fun writeNewline() {
        endNewline = true
        out.append('\n')
    }

    private fun write(s: String) {
        out.append(s)
        if (s.isNotEmpty()) {
            endNewline = s.endsWith('\n')
        }
    }

    fun run() {
        while (iter.hasNext()) {
            val event = iter.next()
            when (event) {
                is Event.Start -> startTag(event.tag)
                is Event.End -> endTag(event.tagEnd)
                is Event.Text -> {
                    if (!inNonWritingBlock) {
                        escapeHtmlBodyText(out, event.text.value)
                        endNewline = event.text.value.endsWith('\n')
                    }
                }
                is Event.Code -> {
                    write("<code>")
                    escapeHtmlBodyText(out, event.text.value)
                    write("</code>")
                }
                is Event.Html, is Event.InlineHtml -> {
                    val htmlVal = if (event is Event.Html) event.text.value else (event as Event.InlineHtml).text.value
                    write(htmlVal)
                }
                is Event.SoftBreak -> writeNewline()
                is Event.HardBreak -> write("<br />\n")
                is Event.Rule -> {
                    if (endNewline) {
                        write("<hr />\n")
                    } else {
                        write("\n<hr />\n")
                    }
                }
                is Event.FootnoteReference -> {
                    val len = numbers.size + 1
                    write("<sup class=\"footnote-reference\"><a href=\"#")
                    escapeHtml(out, event.label.value)
                    write("\">")
                    val number = numbers.getOrPut(event.label) { len }
                    write(number.toString())
                    write("</a></sup>")
                }
                is Event.TaskListMarker -> {
                    if (event.checked) {
                        write("<input disabled=\"\" type=\"checkbox\" checked=\"\"/>\n")
                    } else {
                        write("<input disabled=\"\" type=\"checkbox\"/>\n")
                    }
                }
            }
        }
    }

    private fun startTag(tag: Tag) {
        when (tag) {
            is Tag.HtmlBlock -> {}
            is Tag.Paragraph -> {
                if (endNewline) {
                    write("<p>")
                } else {
                    write("\n<p>")
                }
            }
            is Tag.Heading -> {
                if (endNewline) {
                    endNewline = false
                    write("<")
                } else {
                    write("\n<")
                }
                write(tag.level.toString())
                if (tag.id != null) {
                    write(" id=\"")
                    escapeHtml(out, tag.id.value)
                    write("\"")
                }
                if (tag.classes.isNotEmpty()) {
                    write(" class=\"")
                    escapeHtml(out, tag.classes[0].value)
                    for (i in 1 until tag.classes.size) {
                        write(" ")
                        escapeHtml(out, tag.classes[i].value)
                    }
                    write("\"")
                }
                for (attr in tag.attrs) {
                    write(" ")
                    escapeHtml(out, attr.key.value)
                    if (attr.value != null) {
                        write("=\"")
                        escapeHtml(out, attr.value.value)
                        write("\"")
                    } else {
                        write("=\"\"")
                    }
                }
                write(">")
            }
            is Tag.Table -> {
                tableAlignments = tag.alignments
                write("<table>")
            }
            is Tag.TableHead -> {
                tableState = TableState.Head
                tableCellIndex = 0
                write("<thead><tr>")
            }
            is Tag.TableRow -> {
                tableCellIndex = 0
                write("<tr>")
            }
            is Tag.TableCell -> {
                when (tableState) {
                    TableState.Head -> write("<th")
                    TableState.Body -> write("<td")
                }
                when (tableAlignments.getOrNull(tableCellIndex)) {
                    Alignment.Left -> write(" style=\"text-align: left\">")
                    Alignment.Center -> write(" style=\"text-align: center\">")
                    Alignment.Right -> write(" style=\"text-align: right\">")
                    else -> write(">")
                }
            }
            is Tag.BlockQuote -> {
                if (endNewline) {
                    write("<blockquote>\n")
                } else {
                    write("\n<blockquote>\n")
                }
            }
            is Tag.CodeBlock -> {
                if (!endNewline) {
                    writeNewline()
                }
                when (val kind = tag.kind) {
                    is CodeBlockKind.Fenced -> {
                        val lang =
                            kind.info.value
                                .split(' ')
                                .firstOrNull()
                                .orEmpty()
                        if (lang.isEmpty()) {
                            write("<pre><code>")
                        } else {
                            write("<pre><code class=\"language-")
                            escapeHtml(out, lang)
                            write("\">")
                        }
                    }
                    is CodeBlockKind.Indented -> write("<pre><code>")
                }
            }
            is Tag.List -> {
                val start = tag.startNumber
                if (start == null) {
                    if (endNewline) {
                        write("<ul>\n")
                    } else {
                        write("\n<ul>\n")
                    }
                } else if (start == 1L) {
                    if (endNewline) {
                        write("<ol>\n")
                    } else {
                        write("\n<ol>\n")
                    }
                } else {
                    if (endNewline) {
                        write("<ol start=\"$start\">\n")
                    } else {
                        write("\n<ol start=\"$start\">\n")
                    }
                }
            }
            is Tag.Item -> {
                if (endNewline) {
                    write("<li>")
                } else {
                    write("\n<li>")
                }
            }
            is Tag.Emphasis -> write("<em>")
            is Tag.Strong -> write("<strong>")
            is Tag.Strikethrough -> write("<del>")
            is Tag.Link -> {
                if (tag.linkType == LinkType.Email) {
                    write("<a href=\"mailto:")
                    escapeHref(out, tag.destUrl.value)
                } else {
                    write("<a href=\"")
                    escapeHref(out, tag.destUrl.value)
                }
                if (tag.title.isNotEmpty()) {
                    write("\" title=\"")
                    escapeHtml(out, tag.title.value)
                }
                write("\">")
            }
            is Tag.Image -> {
                write("<img src=\"")
                escapeHref(out, tag.destUrl.value)
                write("\" alt=\"")
                rawText()
                if (tag.title.isNotEmpty()) {
                    write("\" title=\"")
                    escapeHtml(out, tag.title.value)
                }
                write("\" />")
            }
            is Tag.FootnoteDefinition -> {
                if (endNewline) {
                    write("<div class=\"footnote-definition\" id=\"")
                } else {
                    write("\n<div class=\"footnote-definition\" id=\"")
                }
                escapeHtml(out, tag.label.value)
                write("\"><sup class=\"footnote-definition-label\">")
                val len = numbers.size + 1
                val number = numbers.getOrPut(tag.label) { len }
                write(number.toString())
                write("</sup>")
            }
            is Tag.MetadataBlock -> {
                inNonWritingBlock = true
            }
        }
    }

    private fun endTag(tag: TagEnd) {
        when (tag) {
            is TagEnd.HtmlBlock -> {}
            is TagEnd.Paragraph -> write("</p>\n")
            is TagEnd.Heading -> {
                write("</")
                write(tag.level.toString())
                write(">\n")
            }
            is TagEnd.Table -> write("</tbody></table>\n")
            is TagEnd.TableHead -> {
                write("</tr></thead><tbody>\n")
                tableState = TableState.Body
            }
            is TagEnd.TableRow -> write("</tr>\n")
            is TagEnd.TableCell -> {
                when (tableState) {
                    TableState.Head -> write("</th>")
                    TableState.Body -> write("</td>")
                }
                tableCellIndex++
            }
            is TagEnd.BlockQuote -> write("</blockquote>\n")
            is TagEnd.CodeBlock -> write("</code></pre>\n")
            is TagEnd.List -> {
                if (tag.isOrdered) {
                    write("</ol>\n")
                } else {
                    write("</ul>\n")
                }
            }
            is TagEnd.Item -> write("</li>\n")
            is TagEnd.Emphasis -> write("</em>")
            is TagEnd.Strong -> write("</strong>")
            is TagEnd.Strikethrough -> write("</del>")
            is TagEnd.Link -> write("</a>")
            is TagEnd.Image -> {}
            is TagEnd.FootnoteDefinition -> write("</div>\n")
            is TagEnd.MetadataBlock -> {
                inNonWritingBlock = false
            }
        }
    }

    private fun rawText() {
        var nest = 0
        while (iter.hasNext()) {
            val event = iter.next()
            when (event) {
                is Event.Start -> nest++
                is Event.End -> {
                    if (nest == 0) break
                    nest--
                }
                is Event.Html -> {}
                is Event.InlineHtml -> escapeHtml(out, event.text.value)
                is Event.Code -> escapeHtml(out, event.text.value)
                is Event.Text -> {
                    escapeHtml(out, event.text.value)
                    endNewline = event.text.value.endsWith('\n')
                }
                is Event.SoftBreak, is Event.HardBreak, is Event.Rule -> write(" ")
                is Event.FootnoteReference -> {
                    val len = numbers.size + 1
                    val number = numbers.getOrPut(event.label) { len }
                    write("[$number]")
                }
                is Event.TaskListMarker -> {
                    if (event.checked) write("[x]") else write("[ ]")
                }
            }
        }
    }
}

/**
 * Iterate over an `Iterator` of `Event`s, generate HTML for each `Event`, and push it to a `StringBuilder`.
 */
fun pushHtml(out: StringBuilder, iter: Iterator<Event>) {
    HtmlWriter(iter, out).run()
}

/**
 * Convert markdown string to HTML using default options.
 */
fun renderHtml(markdown: String, options: Options = Options.NONE): String {
    val parser = Parser(markdown, options)
    val sb = StringBuilder()
    pushHtml(sb, parser)
    return sb.toString()
}
