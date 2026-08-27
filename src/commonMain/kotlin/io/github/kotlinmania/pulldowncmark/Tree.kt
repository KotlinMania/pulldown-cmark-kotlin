// port-lint: source tree.rs
package io.github.kotlinmania.pulldowncmark

import kotlin.jvm.JvmInline

/**
 * 1-based index into the tree node storage.
 */
@JvmInline
value class TreeIndex(
    val get: Int,
) : Comparable<TreeIndex> {
    override fun compareTo(other: TreeIndex): Int = get.compareTo(other.get)

    operator fun plus(offset: Int): TreeIndex = TreeIndex(get + offset)

    operator fun minus(offset: Int): TreeIndex = TreeIndex(get - offset)
}

/**
 * A node in the AST tree.
 */
class Node<T>(
    var child: TreeIndex? = null,
    var next: TreeIndex? = null,
    var item: T,
)

/**
 * A tree abstraction intended for fast building as a preorder traversal.
 */
class Tree<T>(
    private val defaultItem: () -> T,
    cap: Int = 128,
) {
    private val nodes = ArrayList<Node<T>>(cap.coerceAtLeast(16))
    private val spine = ArrayList<TreeIndex>()
    var cur: TreeIndex? = null
        internal set

    init {
        // Indices start at one, so place dummy value at index 0
        nodes.add(Node(item = defaultItem()))
    }

    fun append(item: T): TreeIndex {
        val ix = createNode(item)
        val thisIx = ix
        val currentCur = cur
        if (currentCur != null) {
            nodes[currentCur.get].next = thisIx
        } else {
            val parent = spine.lastOrNull()
            if (parent != null) {
                nodes[parent.get].child = thisIx
            }
        }
        cur = thisIx
        return ix
    }

    fun createNode(item: T): TreeIndex {
        val thisIdx = nodes.size
        nodes.add(Node(item = item))
        return TreeIndex(thisIdx)
    }

    fun push(): TreeIndex {
        val curIx = cur ?: throw IllegalStateException("Cannot push without current focus")
        spine.add(curIx)
        cur = nodes[curIx.get].child
        return curIx
    }

    fun pop(): TreeIndex? {
        if (spine.isEmpty()) return null
        val ix = spine.removeAt(spine.size - 1)
        cur = ix
        return ix
    }

    fun removeNode(): TreeIndex? {
        if (spine.isEmpty()) return null
        val ix = spine.removeAt(spine.size - 1)
        cur = ix
        if (nodes.isNotEmpty()) {
            nodes.removeAt(nodes.size - 1)
        }
        nodes[ix.get].child = null
        return ix
    }

    fun peekUp(): TreeIndex? = spine.lastOrNull()

    fun peekGrandparent(): TreeIndex? = if (spine.size >= 2) spine[spine.size - 2] else null

    fun isEmpty(): Boolean = nodes.size <= 1

    fun spineLen(): Int = spine.size

    fun reset() {
        cur = if (isEmpty()) null else TreeIndex(1)
        spine.clear()
    }

    fun walkSpine(): List<TreeIndex> = spine

    fun nextSibling(curIx: TreeIndex): TreeIndex? {
        cur = nodes[curIx.get].next
        return cur
    }

    operator fun get(ix: TreeIndex): Node<T> = nodes[ix.get]

    fun nodesCount(): Int = nodes.size
}

/**
 * Truncate preceding siblings for Item tree.
 */
internal fun Tree<Item>.truncateSiblings(endByteIx: Int) {
    val parentIx = peekUp() ?: return
    var nextChildIx = this[parentIx].child
    var prevChildIx: TreeIndex? = null

    while (nextChildIx != null) {
        val childIx = nextChildIx
        val childEnd = this[childIx].item.end
        if (childEnd < endByteIx) {
            prevChildIx = childIx
            nextChildIx = this[childIx].next
            continue
        } else if (childEnd == endByteIx) {
            this[childIx].next = null
            cur = childIx
        } else if (this[childIx].item.start == endByteIx) {
            val isPrevCharBackslash =
                when (this[childIx].item.body) {
                    is ItemBody.Text -> (this[childIx].item.body as ItemBody.Text).backslashEscaped
                    else -> false
                }
            if (isPrevCharBackslash) {
                val lastByteIx = endByteIx - 1
                this[childIx].item.start = lastByteIx
                this[childIx].item.end = endByteIx
                cur = childIx
            } else if (prevChildIx != null) {
                this[prevChildIx].next = null
                cur = prevChildIx
            } else {
                this[parentIx].child = null
                cur = null
            }
        } else {
            this[childIx].item.end = endByteIx
            this[childIx].next = null
            cur = childIx
        }
        break
    }
}
