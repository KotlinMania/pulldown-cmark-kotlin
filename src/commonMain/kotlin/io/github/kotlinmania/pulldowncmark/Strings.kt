// port-lint: source strings.rs
package io.github.kotlinmania.pulldowncmark

import kotlin.jvm.JvmInline

/**
 * An inline string abstraction for short strings.
 */
@JvmInline
value class InlineStr(
    val value: String,
) : CharSequence by value,
    Comparable<InlineStr> {
    override fun compareTo(other: InlineStr): Int = value.compareTo(other.value)

    override fun toString(): String = value

    companion object {
        fun fromChar(c: Char): InlineStr = InlineStr(c.toString())

        fun tryFrom(s: String): InlineStr? = if (s.length <= 22) InlineStr(s) else null
    }
}

/**
 * A copy-on-write string abstraction.
 */
class CowStr(
    val value: String,
) : CharSequence by value,
    Comparable<CowStr> {
    override fun compareTo(other: CowStr): Int = value.compareTo(other.value)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CowStr) return false
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    fun intoString(): String = value

    fun asStr(): String = value

    fun asString(): String = value

    fun clone(): CowStr = CowStr(value)

    fun isEmpty(): Boolean = value.isEmpty()

    fun isNotEmpty(): Boolean = value.isNotEmpty()

    companion object {
        val EMPTY: CowStr = CowStr("")

        fun borrowed(s: String): CowStr = CowStr(s)

        fun boxed(s: String): CowStr = CowStr(s)

        fun inlined(s: InlineStr): CowStr = CowStr(s.value)

        fun from(s: String): CowStr = CowStr(s)

        fun from(c: Char): CowStr = CowStr(c.toString())

        fun from(cow: CowStr): CowStr = CowStr(cow.value)
    }
}

fun String.toCowStr(): CowStr = CowStr(this)

fun Char.toCowStr(): CowStr = CowStr(this.toString())
