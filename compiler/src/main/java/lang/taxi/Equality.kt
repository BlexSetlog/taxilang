package lang.taxi

import nl.pvdberg.hashkode.HashKode


class Equality<T : Any>(val target: T, vararg val properties: T.() -> Any?) {
    companion object {
        var DEFAULT_INITIAL_ODD_NUMBER = 17
        var DEFAULT_MULTIPLIER_PRIME = 37
    }
    fun isEqualTo(other: Any?): Boolean {
        if (other == null) return false
        if (other === this) return true
        if (other.javaClass != target.javaClass) return false
        return properties.all { it.invoke(target) == it.invoke(other as T) }
    }

    fun hash(): Int {
        val fields = properties.map { it.invoke(target)?.hashCode() ?: 0 }
        return fields.fold(HashKode.DEFAULT_INITIAL_ODD_NUMBER) { a, b -> HashKode.DEFAULT_MULTIPLIER_PRIME * a + b }
    }
}