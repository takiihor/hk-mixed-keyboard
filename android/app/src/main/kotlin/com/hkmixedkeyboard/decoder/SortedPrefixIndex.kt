package com.hkmixedkeyboard.decoder

class SortedPrefixIndex(keys: Collection<String>) {
    private val sortedKeys = keys.distinct().sorted()

    // True if any key starts with [prefix]. O(log n) and allocation-free — replaces
    // materializing the set of every prefix of every key just to answer this.
    fun hasPrefix(prefix: String): Boolean {
        if (prefix.isEmpty()) return false
        val index = lowerBound(prefix)
        return index < sortedKeys.size && sortedKeys[index].startsWith(prefix)
    }

    fun matching(prefix: String, limit: Int = Int.MAX_VALUE): List<String> {
        if (prefix.isEmpty() || limit <= 0) return emptyList()
        val result = ArrayList<String>(minOf(limit, 16))
        var index = lowerBound(prefix)
        while (index < sortedKeys.size && result.size < limit) {
            val key = sortedKeys[index]
            if (!key.startsWith(prefix)) break
            result += key
            index++
        }
        return result
    }

    fun forEachMatching(prefix: String, action: (String) -> Unit) {
        if (prefix.isEmpty()) return
        var index = lowerBound(prefix)
        while (index < sortedKeys.size) {
            val key = sortedKeys[index]
            if (!key.startsWith(prefix)) break
            action(key)
            index++
        }
    }

    private fun lowerBound(target: String): Int {
        var low = 0
        var high = sortedKeys.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (sortedKeys[mid] < target) low = mid + 1 else high = mid
        }
        return low
    }
}
