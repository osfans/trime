package com.osfans.trime.util

object CollectionUtils {
    @JvmStatic
    fun <K, V> getOrDefault(map: Map<K, V>, key: K, defaultValue: V): V = map[key] ?: defaultValue
}
