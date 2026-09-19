package com.smartspend.app.ui.util

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity

/**
 * LocalContext is not guaranteed to be the Activity — it can be a ContextWrapper — so a
 * plain `as? Activity` cast silently yields null. Needed anywhere Compose code has to reach
 * an Activity for an API that requires one (permission-rationale checks, Firebase Phone
 * Auth's verifyPhoneNumber).
 */
tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}
