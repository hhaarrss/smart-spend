package com.smartspend.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import java.util.Locale

/**
 * The accent a single category is drawn with: [accent] for the icon glyph, chart slice and
 * emphasis text, [container] for the chip fill behind it.
 */
@Immutable
data class CategoryColors(
    val accent: Color,
    val container: Color
)

/**
 * Category colors are looked up by name because the category set is server-driven — the
 * backend's CANONICAL_CATEGORY_MAP can add a category without an app release, so an
 * unmatched name has to degrade to a usable color rather than crash or render invisible.
 */
@Immutable
class CategoryPalette(
    private val byName: Map<String, CategoryColors>,
    private val fallback: CategoryColors
) {
    operator fun get(category: String?): CategoryColors {
        val key = category?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return byName[key] ?: fallback
    }
}

private fun colorsFor(accent: Color, dark: Boolean): CategoryColors {
    // On light the chip is a pale wash of the accent; on dark a low-luminance version of
    // it, so the chip reads as tinted rather than as a bright blob against the surface.
    val container = if (dark) accent.copy(alpha = 0.22f) else accent.copy(alpha = 0.14f)
    return CategoryColors(accent = accent, container = container)
}

/**
 * Saturated mid-tones lose contrast against a dark surface, so each accent is lifted
 * toward white on dark. Anything already light enough is left alone.
 */
private fun Color.liftForDark(): Color =
    if (luminance() < 0.35f) lerpToWhite(0.30f) else this

private fun Color.lerpToWhite(fraction: Float): Color = Color(
    red = red + (1f - red) * fraction,
    green = green + (1f - green) * fraction,
    blue = blue + (1f - blue) * fraction,
    alpha = alpha
)

internal fun categoryPalette(dark: Boolean): CategoryPalette {
    val base = listOf(
        "food" to CatFood,
        "transport" to CatTransport,
        "shopping" to CatShopping,
        "entertainment" to CatEntertainment,
        "utilities" to CatUtilities,
        "healthcare" to CatHealthcare,
        "education" to CatEducation,
        "travel" to CatTravel,
        "rent" to CatRent,
        "transfer" to CatTransfer,
        "investment" to CatInvestment,
        "salary" to CatSalary,
        "refund" to CatRefund,
        "other" to CatOther
    )
    val byName = base.associate { (name, accent) ->
        name to colorsFor(if (dark) accent.liftForDark() else accent, dark)
    }
    val fallback = colorsFor(if (dark) CatOther.liftForDark() else CatOther, dark)
    return CategoryPalette(byName, fallback)
}

internal val LocalCategoryPalette = staticCompositionLocalOf { categoryPalette(dark = false) }
