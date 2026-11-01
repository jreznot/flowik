package flowik.core

/**
 * Above this many edits in the part of the lists that differs, [matchedPairs]
 * gives up on a minimal script: its bookkeeping grows with the square of the
 * edit count, and lists that far apart are better replaced than patched.
 */
private const val MAX_DIFF_EDITS = 1_000

/**
 * The positions of [old] and [new] that a minimal edit script keeps, in
 * increasing order: each pair is an `(oldIndex, newIndex)` of equal elements.
 *
 * Common prefix and suffix are matched directly; the middle goes through
 * Myers' O((N+M)·D) algorithm, or is matched not at all when it needs more than
 * [MAX_DIFF_EDITS] edits.
 */
internal fun <T> matchedPairs(old: List<T>, new: List<T>): List<IntArray> {
    var prefix = 0
    while (prefix < old.size && prefix < new.size && old[prefix] == new[prefix]) prefix++
    var suffix = 0
    while (suffix < old.size - prefix && suffix < new.size - prefix &&
        old[old.size - 1 - suffix] == new[new.size - 1 - suffix]
    ) suffix++

    val pairs = ArrayList<IntArray>(prefix + suffix)
    for (i in 0 until prefix) pairs.add(intArrayOf(i, i))
    val middle = myers(old.subList(prefix, old.size - suffix), new.subList(prefix, new.size - suffix))
    middle?.forEach { (x, y) -> pairs.add(intArrayOf(prefix + x, prefix + y)) }
    for (i in suffix downTo 1) pairs.add(intArrayOf(old.size - i, new.size - i))
    return pairs
}

/**
 * Myers' diff: the matched `(aIndex, bIndex)` pairs of a shortest edit script
 * turning [a] into [b], or `null` when that script exceeds [MAX_DIFF_EDITS].
 */
private fun <T> myers(a: List<T>, b: List<T>): List<IntArray>? {
    val n = a.size
    val m = b.size
    if (n == 0 || m == 0) return emptyList()

    val max = n + m
    val offset = max + 1
    // v[offset + k] = furthest x reached on diagonal k
    val v = IntArray(2 * max + 3)
    // trace[d] = v before step d, for diagonals -d..d (index k + d)
    val trace = ArrayList<IntArray>()
    var found = -1

    outer@ for (d in 0..max) {
        if (d > MAX_DIFF_EDITS) return null
        trace.add(IntArray(2 * d + 1) { v[offset - d + it] })
        for (k in -d..d step 2) {
            var x = if (k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1])) {
                v[offset + k + 1]
            } else {
                v[offset + k - 1] + 1
            }
            var y = x - k
            while (x < n && y < m && a[x] == b[y]) {
                x++
                y++
            }
            v[offset + k] = x
            if (x >= n && y >= m) {
                found = d
                break@outer
            }
        }
    }

    val pairs = ArrayList<IntArray>()
    var x = n
    var y = m
    for (d in found downTo 1) {
        val previous = trace[d]
        val k = x - y
        val prevK = if (k == -d || (k != d && previous[k - 1 + d] < previous[k + 1 + d])) k + 1 else k - 1
        val prevX = previous[prevK + d]
        val prevY = prevX - prevK
        // The snake ends at (x, y) and starts right after the single edit from (prevX, prevY).
        val startX = if (prevK == k + 1) prevX else prevX + 1
        val startY = if (prevK == k + 1) prevY + 1 else prevY
        while (x > startX && y > startY) {
            x--
            y--
            pairs.add(intArrayOf(x, y))
        }
        x = prevX
        y = prevY
    }
    while (x > 0 && y > 0) {
        x--
        y--
        pairs.add(intArrayOf(x, y))
    }
    pairs.reverse()
    return pairs
}
