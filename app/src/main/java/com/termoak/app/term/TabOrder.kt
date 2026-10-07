package com.termoak.app.term

/**
 * Order of the terminal tabs (the tab bar of wide windows): moving one with
 * the tab's menu (Move left / Move right) or by dragging it.
 */
object TabOrder {
    /** [list] with the item at [from] moved to [to] (clamped); the same list when nothing moves. */
    fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from !in list.indices) return list
        val target = to.coerceIn(0, list.lastIndex)
        if (target == from) return list
        return list.toMutableList().apply { add(target, removeAt(from)) }
    }

    /**
     * Where a tab dragged [offset] px away from its place at [index] goes:
     * past the middle of a neighbour it takes that neighbour's place (as many
     * times as needed). [widths] are those of the tabs in order and [gap] the
     * space between two of them. Returns the new index and the offset that
     * is left, measured from the new place, so the tab stays under the finger.
     */
    fun drag(index: Int, offset: Float, widths: List<Int>, gap: Float): Pair<Int, Float> {
        var i = index
        var left = offset
        while (true) {
            if (left > 0 && i < widths.lastIndex && left > widths[i + 1] / 2f) {
                left -= widths[i + 1] + gap
                i++
            } else if (left < 0 && i > 0 && -left > widths[i - 1] / 2f) {
                left += widths[i - 1] + gap
                i--
            } else {
                return i to left
            }
        }
    }
}
