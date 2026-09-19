package com.smartspend.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── Brand ──────────────────────────────────────────────────────────────────
// Violet carries the brand; tangerine is the "act on this" accent. Deliberately
// not banking-blue. Dark variants are lightened so they keep contrast on ink.
val Violet = Color(0xFF6C4CF1)
val VioletDark = Color(0xFF9B84FF)
val Tangerine = Color(0xFFFF6B35)
val TangerineDark = Color(0xFFFF8A5F)

/** Hero fill on dark: deep enough to sit inside a dark UI, saturated enough to stay brand. */
val VioletDeep = Color(0xFF2F2168)

val VioletContainer = Color(0xFFEDE7FF)
val OnVioletContainer = Color(0xFF2A1B6B)
val VioletContainerDark = Color(0xFF2E2352)
val OnVioletContainerDark = Color(0xFFDCD2FF)

// Deep tints used as `on*` colors against light-on-dark brand fills.
val VioletInk = Color(0xFF1A1040)
val TangerineInk = Color(0xFF2B1206)
val PositiveInk = Color(0xFF072116)
val NegativeInk = Color(0xFF2B0B0D)

// ── Neutrals ───────────────────────────────────────────────────────────────
// Warm cream rather than sterile white, warm plum-ink rather than pure black:
// pure #FFF/#000 is what makes a finance app feel like a spreadsheet.
val Cream = Color(0xFFFFF8F0)
val PaperWhite = Color(0xFFFFFFFF)
val Sand = Color(0xFFF3EDE4)
val SandBorder = Color(0xFFE4DCD1)
val Ink = Color(0xFF1A1523)
val InkMuted = Color(0xFF6B6478)

val NightBase = Color(0xFF14111C)
val NightSurface = Color(0xFF1E1A2A)
val NightMuted = Color(0xFF2A2438)
val NightBorder = Color(0xFF352E45)
val Moon = Color(0xFFF5F1EA)
val MoonMuted = Color(0xFFA79FB5)

// ── Status ─────────────────────────────────────────────────────────────────
val Positive = Color(0xFF0E9F6E)
val PositiveDark = Color(0xFF3DD68C)
val Negative = Color(0xFFE5484D)
val NegativeDark = Color(0xFFFF6369)
val Caution = Color(0xFFF5A524)
val CautionDark = Color(0xFFFFC53D)

// ── Category accents ───────────────────────────────────────────────────────
// One hue per canonical category, chosen to stay distinguishable side by side
// in a donut chart. Credit-side categories (Salary/Refund/Investment) sit in
// the green-to-indigo range so income reads as a family at a glance.
val CatFood = Color(0xFFFF6B35)
val CatTransport = Color(0xFFF5A524)
val CatShopping = Color(0xFFEC4899)
val CatEntertainment = Color(0xFF8B5CF6)
val CatUtilities = Color(0xFF06B6D4)
val CatHealthcare = Color(0xFFEF4444)
val CatEducation = Color(0xFF3B82F6)
val CatTravel = Color(0xFF14B8A6)
val CatRent = Color(0xFFB45309)
val CatTransfer = Color(0xFF64748B)
val CatInvestment = Color(0xFF4F46E5)
val CatSalary = Color(0xFF10B981)
val CatRefund = Color(0xFF84CC16)
val CatOther = Color(0xFF78716C)
