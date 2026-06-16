package com.hkmixedkeyboard.decoder

class SortedPrefixIndex(keys: Collection<String>) {
    private val sortedKeys = keys.distinct().sorted()

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
