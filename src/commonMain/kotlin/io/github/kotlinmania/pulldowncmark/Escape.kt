package io.github.kotlinmania.pulldowncmark

fun escapeHtml(out: StringBuilder, text: String) {
    for (c in text) {
        when (c) {
            '&' -> out.append("&amp;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            '"' -> out.append("&quot;")
            '\'' -> out.append("&#39;")
            else -> out.append(c)
        }
    }
}

fun escapeHtmlBodyText(out: StringBuilder, text: String) {
    for (c in text) {
        when (c) {
            '&' -> out.append("&amp;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            else -> out.append(c)
        }
    }
}

fun escapeHref(out: StringBuilder, href: String) {
    for (c in href) {
        when (c) {
            '&' -> out.append("&amp;")
            '"' -> out.append("&quot;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            '\'' -> out.append("&#39;")
            ' ' -> out.append("%20")
            else -> out.append(c)
        }
    }
}

