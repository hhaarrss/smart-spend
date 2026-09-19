package com.smartspend.app.ui.auth

import android.content.Context

private const val PREFS_NAME = "smart_spend_prefs"

/**
 * Whether a session token exists locally. Not a validity check — an expired or otherwise
 * rejected token still routes past Auth on launch and only surfaces as a 401 from the
 * backend, same as every other authenticated call in this app.
 */
fun hasStoredSession(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return !prefs.getString("jwt_token", null).isNullOrEmpty()
}
