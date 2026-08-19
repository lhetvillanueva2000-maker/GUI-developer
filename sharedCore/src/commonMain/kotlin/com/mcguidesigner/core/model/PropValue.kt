package com.mcguidesigner.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A dynamically typed property value.
 *
 * Elements store their configuration in a `Map<String, PropValue>` rather than
 * in a per-type data class.  That keeps the model open for extension (a new
 * widget type is a catalog entry, not a new serializer), lets the property
 * inspector be fully data-driven, and lets the validator reason about
 * edition-specific properties generically.
 */
@Serializable
sealed interface PropValue {
    /** Stable, human-readable rendering used by exporters and the code view. */
    fun asText(): String
}

@Serializable
@SerialName("s")
data class StringValue(val value: String) : PropValue {
    override fun asText() = value
}

@Serializable
@SerialName("i")
data class IntValue(val value: Int) : PropValue {
    override fun asText() = value.toString()
}

@Serializable
@SerialName("f")
data class FloatValue(val value: Float) : PropValue {
    override fun asText() = value.toString()
}

@Serializable
@SerialName("b")
data class BoolValue(val value: Boolean) : PropValue {
    override fun asText() = value.toString()
}

/** Packed 0xAARRGGBB colour. */
@Serializable
@SerialName("c")
data class ColorValue(val argb: Long) : PropValue {
    override fun asText() = toHex()

    fun toHex(includeAlpha: Boolean = true): String {
        val a = ((argb shr 24) and 0xFF).toInt()
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        return if (includeAlpha && a != 0xFF) {
            "#" + listOf(r, g, b, a).joinToString("") { it.toString(16).padStart(2, '0') }
        } else {
            "#" + listOf(r, g, b).joinToString("") { it.toString(16).padStart(2, '0') }
        }
    }

    val alphaFraction: Float get() = ((argb shr 24) and 0xFF) / 255f

    companion object {
        fun of(r: Int, g: Int, b: Int, a: Int = 0xFF): ColorValue =
            ColorValue(
                ((a.toLong() and 0xFF) shl 24) or
                    ((r.toLong() and 0xFF) shl 16) or
                    ((g.toLong() and 0xFF) shl 8) or
                    (b.toLong() and 0xFF),
            )

        /** Parses `#RGB`, `#RRGGBB` or `#RRGGBBAA`; returns null when malformed. */
        fun parse(text: String): ColorValue? {
            val hex = text.trim().removePrefix("#")
            val expanded = when (hex.length) {
                3 -> hex.map { "$it$it" }.joinToString("") + "ff"
                6 -> hex + "ff"
                8 -> hex
                else -> return null
            }
            val r = expanded.substring(0, 2).toIntOrNull(16) ?: return null
            val g = expanded.substring(2, 4).toIntOrNull(16) ?: return null
            val b = expanded.substring(4, 6).toIntOrNull(16) ?: return null
            val a = expanded.substring(6, 8).toIntOrNull(16) ?: return null
            return of(r, g, b, a)
        }
    }
}

/** One choice out of a [PropertySpec.options] list. */
@Serializable
@SerialName("e")
data class EnumValue(val value: String) : PropValue {
    override fun asText() = value
}

/** Reference to a [TextureAsset.id] stored in the project. */
@Serializable
@SerialName("tex")
data class TextureValue(val assetId: String?) : PropValue {
    override fun asText() = assetId ?: ""
}

@Serializable
@SerialName("l")
data class ListValue(val values: List<PropValue> = emptyList()) : PropValue {
    override fun asText() = values.joinToString(", ") { it.asText() }
}

// --- Convenience accessors -------------------------------------------------

fun Map<String, PropValue>.string(key: String, fallback: String = ""): String =
    when (val v = this[key]) {
        is StringValue -> v.value
        is EnumValue -> v.value
        null -> fallback
        else -> v.asText()
    }

fun Map<String, PropValue>.int(key: String, fallback: Int = 0): Int =
    when (val v = this[key]) {
        is IntValue -> v.value
        is FloatValue -> v.value.toInt()
        else -> fallback
    }

fun Map<String, PropValue>.float(key: String, fallback: Float = 0f): Float =
    when (val v = this[key]) {
        is FloatValue -> v.value
        is IntValue -> v.value.toFloat()
        else -> fallback
    }

fun Map<String, PropValue>.bool(key: String, fallback: Boolean = false): Boolean =
    (this[key] as? BoolValue)?.value ?: fallback

fun Map<String, PropValue>.color(key: String, fallback: Long): Long =
    (this[key] as? ColorValue)?.argb ?: fallback

fun Map<String, PropValue>.texture(key: String): String? =
    (this[key] as? TextureValue)?.assetId?.takeIf { it.isNotBlank() }

fun Map<String, PropValue>.list(key: String): List<PropValue> =
    (this[key] as? ListValue)?.values ?: emptyList()

fun Map<String, PropValue>.stringList(key: String): List<String> =
    list(key).map { it.asText() }
