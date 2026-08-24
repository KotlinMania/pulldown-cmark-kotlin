package io.github.kotlinmania.pulldowncmark

// port-lint: source tmp/pulldown-cmark/src/utils.rs

/**
 * Merge consecutive `Event.Text` events into one.
 */
class TextMergeStream(private val iterator: Iterator<Event>) : Iterator<Event>, Iterable<Event> {
    private var lastEvent: Event? = null

    override fun hasNext(): Boolean {
        return lastEvent != null || iterator.hasNext()
    }

    override fun next(): Event {
        val saved = lastEvent
        val nextEv: Event = if (saved != null) {
            lastEvent = null
            saved
        } else if (iterator.hasNext()) {
            iterator.next()
        } else {
            throw NoSuchElementException()
        }

        if (nextEv is Event.Text) {
            val sb = StringBuilder(nextEv.text.value)
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                if (candidate is Event.Text) {
                    sb.append(candidate.text.value)
                } else {
                    lastEvent = candidate
                    break
                }
            }
            if (sb.isEmpty()) {
                return next()
            }
            return Event.Text(CowStr.from(sb.toString()))
        }

        return nextEv
    }

    override fun iterator(): Iterator<Event> = this

    companion object {
        fun new(iterator: Iterator<Event>): TextMergeStream = TextMergeStream(iterator)
    }
}

/**
 * Merge consecutive `Event.Text` events into one with offsets.
 */
class TextMergeWithOffset(
    private val source: String,
    options: Options = Options.NONE,
    callback: BrokenLinkCallback? = null,
) : Iterator<SpannedEvent>, Iterable<SpannedEvent> {

    private val parser: OffsetIter = Parser.newWithBrokenLinkCallback(source, options, callback).intoOffsetIter()
    private var peeked: SpannedEvent? = null

    private fun peek(): SpannedEvent? {
        if (peeked == null && parser.hasNext()) {
            peeked = parser.next()
        }
        return peeked
    }

    override fun hasNext(): Boolean {
        skipEmptyText()
        return peek() != null
    }

    private fun skipEmptyText() {
        while (true) {
            val p = peek() ?: break
            if (p.event is Event.Text && p.event.text.isEmpty()) {
                peeked = null // consume
            } else {
                break
            }
        }
    }

    override fun next(): SpannedEvent {
        skipEmptyText()
        val current = peek() ?: throw NoSuchElementException()
        peeked = null // consume

        if (current.event is Event.Text) {
            val start = current.range.first
            var end = current.range.last + 1
            while (true) {
                val nextP = peek()
                if (nextP != null && nextP.event is Event.Text) {
                    end = nextP.range.last + 1
                    peeked = null // consume
                } else {
                    break
                }
            }
            val textSlice = source.substring(start, end)
            return SpannedEvent(Event.Text(CowStr.from(textSlice)), start until end)
        }

        return current
    }

    override fun iterator(): Iterator<SpannedEvent> = this

    companion object {
        fun new(source: String): TextMergeWithOffset =
            TextMergeWithOffset(source, Options.NONE, null)

        fun newExt(source: String, options: Options): TextMergeWithOffset =
            TextMergeWithOffset(source, options, null)

        fun newExtWithBrokenLinkCallback(source: String, options: Options, callback: BrokenLinkCallback?): TextMergeWithOffset =
            TextMergeWithOffset(source, options, callback)
    }
}

