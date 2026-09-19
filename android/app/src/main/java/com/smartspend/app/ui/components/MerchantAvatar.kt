package com.smartspend.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartspend.app.ui.theme.SmartSpendTheme
import java.util.Locale

/**
 * The single way a transaction is represented visually, resolved in three tiers:
 *
 *  1. a brand mark, when the merchant is one we recognise,
 *  2. the category glyph, when it is not,
 *  3. the merchant's initial, when there is no glyph either.
 *
 * Every tier renders in the category's accent, so a row stays colour-coded by category
 * even when the merchant is unknown — the tier only changes what sits inside the chip.
 */
@Composable
fun MerchantAvatar(
    category: String?,
    modifier: Modifier = Modifier,
    merchant: String? = null,
    size: Dp = 44.dp
) {
    val colors = SmartSpendTheme.categories[category]
    val brand = rememberBrandMark(merchant)
    val glyph = categoryGlyph(category)

    Box(
        modifier = modifier
            .size(size)
            // A squircle rather than a circle: it sits better in a chunky card stack,
            // and keeps square brand marks from being clipped at the corners.
            .clip(RoundedCornerShape(percent = 30))
            .background(colors.container),
        contentAlignment = Alignment.Center
    ) {
        when {
            brand != null -> Icon(
                imageVector = brand,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(size * 0.55f)
            )

            glyph != null -> Icon(
                imageVector = glyph,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(size * 0.55f)
            )

            else -> Text(
                text = initialFor(merchant, category),
                color = colors.accent,
                fontWeight = FontWeight.Black,
                // Scaled off the chip, not a fixed sp, so the same component works at
                // 28dp in a dense list and 64dp on a detail header.
                fontSize = (size.value * 0.42f).sp
            )
        }
    }
}

private fun initialFor(merchant: String?, category: String?): String {
    val source = merchant?.trim()?.takeIf { it.isNotEmpty() }
        ?: category?.trim()?.takeIf { it.isNotEmpty() }
        ?: return "?"
    // Skip leading punctuation from raw SMS merchant strings ("*AMAZON", "@swiggy").
    val firstLetter = source.firstOrNull { it.isLetterOrDigit() } ?: return "?"
    return firstLetter.uppercase(Locale.ROOT)
}

/**
 * Brand-mark lookup for recognised merchants.
 *
 * Returns null until real marks are supplied, which makes every transaction fall through
 * to the category glyph or monogram — correct, just less specific.
 *
 * Note before wiring this up: third-party logos are trademarks. Bundling Swiggy/Amazon/Uber
 * marks in a shipped APK needs a deliberate call on usage rights, so this stays empty rather
 * than shipping assets that were never cleared.
 */
@Composable
private fun rememberBrandMark(merchant: String?): ImageVector? {
    if (merchant.isNullOrBlank()) return null
    return null
}

/**
 * Category glyphs. Returns null until the icon set is drawn, so callers fall back to the
 * monogram chip. This is the single place the artwork drops in — every screen that shows
 * a transaction goes through [MerchantAvatar], so nothing else needs to change.
 */
@Composable
private fun categoryGlyph(category: String?): ImageVector? {
    if (category.isNullOrBlank()) return null
    return null
}
