package com.smartspend.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colors Material3's [androidx.compose.material3.ColorScheme] has no slot for.
 *
 * Money direction and budget health are the two things this app colors by, and both need
 * to mean the same thing on every screen — a screen picking its own green for income is
 * how the palette drifts. Read these through [SmartSpendTheme.colors], never as literals.
 */
@Immutable
data class SmartSpendColors(
    /** Money in: credits, income, refunds. */
    val positive: Color,
    val positiveContainer: Color,
    /** Money out, and budgets that have blown their limit. */
    val negative: Color,
    val negativeContainer: Color,
    /** Approaching a budget limit — warn, don't alarm. */
    val caution: Color,
    val cautionContainer: Color,
    /** Secondary brand accent, for the one action a screen most wants. */
    val accent: Color,
    val onAccent: Color,
    /**
     * The hero card's fill. Separate from `colorScheme.primary` on purpose: in dark mode
     * primary is the *lightened* violet meant for accents and text, and painting a large
     * surface with it produces a glaring pale block brighter than the light theme's hero.
     * Both variants stay dark enough to carry white text, so hero content never restyles.
     */
    val heroSurface: Color,
    val onHeroSurface: Color,
    /** Muted body text — the "₹420 • 12 Mar" line under a merchant name. */
    val inkMuted: Color,
    /** Fills behind icon chips and inert chart tracks. */
    val subtleSurface: Color,
    val hairline: Color
)

val LightSmartSpendColors = SmartSpendColors(
    positive = Positive,
    positiveContainer = Color(0xFFDCFCE7),
    negative = Negative,
    negativeContainer = Color(0xFFFFE4E4),
    caution = Caution,
    cautionContainer = Color(0xFFFFF1D6),
    accent = Tangerine,
    onAccent = Color.White,
    heroSurface = Violet,
    onHeroSurface = Color.White,
    inkMuted = InkMuted,
    subtleSurface = Sand,
    hairline = SandBorder
)

val DarkSmartSpendColors = SmartSpendColors(
    positive = PositiveDark,
    positiveContainer = Color(0xFF10301F),
    negative = NegativeDark,
    negativeContainer = Color(0xFF3A1A1C),
    caution = CautionDark,
    cautionContainer = Color(0xFF3A2C10),
    accent = TangerineDark,
    onAccent = TangerineInk,
    heroSurface = VioletDeep,
    onHeroSurface = Moon,
    inkMuted = MoonMuted,
    subtleSurface = NightMuted,
    hairline = NightBorder
)

/**
 * Static rather than dynamic: the whole set swaps at once on a theme change, so tracking
 * reads individually would cost recompositions for a value that changes about twice a day.
 */
internal val LocalSmartSpendColors = staticCompositionLocalOf { LightSmartSpendColors }
