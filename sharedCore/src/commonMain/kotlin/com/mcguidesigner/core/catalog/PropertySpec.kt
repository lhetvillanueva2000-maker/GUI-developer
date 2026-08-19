package com.mcguidesigner.core.catalog

import com.mcguidesigner.core.model.BoolValue
import com.mcguidesigner.core.model.ColorValue
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.EnumValue
import com.mcguidesigner.core.model.FloatValue
import com.mcguidesigner.core.model.IntValue
import com.mcguidesigner.core.model.ListValue
import com.mcguidesigner.core.model.PropValue
import com.mcguidesigner.core.model.StringValue
import com.mcguidesigner.core.model.TextureValue

/** Editor control to render for a property. */
enum class PropType {
    TEXT,
    MULTILINE_TEXT,
    INT,
    FLOAT,
    BOOL,
    COLOR,
    ENUM,
    TEXTURE,
    STRING_LIST,
}

/** Grouping used by the property inspector's collapsible sections. */
object PropGroup {
    const val CONTENT = "Content"
    const val APPEARANCE = "Appearance"
    const val LAYOUT = "Layout"
    const val BEHAVIOUR = "Behaviour"
    const val JAVA = "Java Edition"
    const val BEDROCK = "Bedrock Edition"
}

/**
 * Declarative description of one editable property.
 *
 * The inspector, the validator and the exporters are all driven from these -
 * adding a property to a widget never requires touching UI code.
 */
data class PropertySpec(
    val key: String,
    val label: String,
    val type: PropType,
    val default: PropValue,
    val group: String = PropGroup.CONTENT,
    val options: List<String> = emptyList(),
    val min: Float? = null,
    val max: Float? = null,
    val step: Float = 1f,
    /** Editions in which this property has any meaning. */
    val editions: Set<Edition> = BOTH_EDITIONS,
    /** May be overridden per [com.mcguidesigner.core.model.InteractionState]. */
    val stateAware: Boolean = false,
    val help: String = "",
) {
    fun supports(edition: Edition): Boolean = edition in editions

    /** True when [value] is structurally compatible with this spec. */
    fun accepts(value: PropValue): Boolean = when (type) {
        PropType.TEXT, PropType.MULTILINE_TEXT -> value is StringValue
        PropType.INT -> value is IntValue
        PropType.FLOAT -> value is FloatValue || value is IntValue
        PropType.BOOL -> value is BoolValue
        PropType.COLOR -> value is ColorValue
        PropType.ENUM -> value is EnumValue && value.value in options
        PropType.TEXTURE -> value is TextureValue
        PropType.STRING_LIST -> value is ListValue
    }

    /** Clamps numeric values into [min]..[max]; returns other types unchanged. */
    fun coerce(value: PropValue): PropValue = when {
        value is IntValue && (min != null || max != null) ->
            IntValue(value.value.coerceIn((min ?: Float.MIN_VALUE).toInt(), (max ?: Float.MAX_VALUE).toInt()))

        value is FloatValue && (min != null || max != null) ->
            FloatValue(value.value.coerceIn(min ?: -Float.MAX_VALUE, max ?: Float.MAX_VALUE))

        else -> value
    }
}

val BOTH_EDITIONS: Set<Edition> = setOf(Edition.JAVA, Edition.BEDROCK)
val JAVA_ONLY: Set<Edition> = setOf(Edition.JAVA)
val BEDROCK_ONLY: Set<Edition> = setOf(Edition.BEDROCK)

// --- Terse builders used by the catalog ------------------------------------

internal fun textProp(
    key: String,
    label: String,
    default: String = "",
    group: String = PropGroup.CONTENT,
    multiline: Boolean = false,
    editions: Set<Edition> = BOTH_EDITIONS,
    stateAware: Boolean = false,
    help: String = "",
) = PropertySpec(
    key, label,
    if (multiline) PropType.MULTILINE_TEXT else PropType.TEXT,
    StringValue(default), group, editions = editions, stateAware = stateAware, help = help,
)

internal fun intProp(
    key: String,
    label: String,
    default: Int,
    min: Int? = null,
    max: Int? = null,
    group: String = PropGroup.LAYOUT,
    editions: Set<Edition> = BOTH_EDITIONS,
    help: String = "",
) = PropertySpec(
    key, label, PropType.INT, IntValue(default), group,
    min = min?.toFloat(), max = max?.toFloat(), editions = editions, help = help,
)

internal fun floatProp(
    key: String,
    label: String,
    default: Float,
    min: Float? = null,
    max: Float? = null,
    step: Float = 0.01f,
    group: String = PropGroup.APPEARANCE,
    editions: Set<Edition> = BOTH_EDITIONS,
    help: String = "",
) = PropertySpec(
    key, label, PropType.FLOAT, FloatValue(default), group,
    min = min, max = max, step = step, editions = editions, help = help,
)

internal fun boolProp(
    key: String,
    label: String,
    default: Boolean,
    group: String = PropGroup.BEHAVIOUR,
    editions: Set<Edition> = BOTH_EDITIONS,
    stateAware: Boolean = false,
    help: String = "",
) = PropertySpec(
    key, label, PropType.BOOL, BoolValue(default), group,
    editions = editions, stateAware = stateAware, help = help,
)

internal fun colorProp(
    key: String,
    label: String,
    default: Long,
    group: String = PropGroup.APPEARANCE,
    editions: Set<Edition> = BOTH_EDITIONS,
    stateAware: Boolean = true,
    help: String = "",
) = PropertySpec(
    key, label, PropType.COLOR, ColorValue(default), group,
    editions = editions, stateAware = stateAware, help = help,
)

internal fun enumProp(
    key: String,
    label: String,
    options: List<String>,
    default: String = options.first(),
    group: String = PropGroup.APPEARANCE,
    editions: Set<Edition> = BOTH_EDITIONS,
    stateAware: Boolean = false,
    help: String = "",
) = PropertySpec(
    key, label, PropType.ENUM, EnumValue(default), group,
    options = options, editions = editions, stateAware = stateAware, help = help,
)

internal fun textureProp(
    key: String,
    label: String,
    group: String = PropGroup.APPEARANCE,
    editions: Set<Edition> = BOTH_EDITIONS,
    stateAware: Boolean = true,
    help: String = "",
) = PropertySpec(
    key, label, PropType.TEXTURE, TextureValue(null), group,
    editions = editions, stateAware = stateAware, help = help,
)

internal fun stringListProp(
    key: String,
    label: String,
    default: List<String> = emptyList(),
    group: String = PropGroup.CONTENT,
    editions: Set<Edition> = BOTH_EDITIONS,
    help: String = "",
) = PropertySpec(
    key, label, PropType.STRING_LIST,
    ListValue(default.map { StringValue(it) }), group, editions = editions, help = help,
)
