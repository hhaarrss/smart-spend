package com.smartspend.app.ui.permission

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

internal val SMS_PERMISSIONS = arrayOf(
    Manifest.permission.RECEIVE_SMS,
    Manifest.permission.READ_SMS
)

internal fun smsPermissionsGranted(context: Context): Boolean =
    SMS_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

/**
 * LocalContext is not guaranteed to be the Activity — it can be a ContextWrapper — so a
 * plain `as? Activity` cast silently yields null and breaks permission-rationale checks.
 */
internal tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}

/**
 * Re-reads permission state on every resume. Granting from the system Settings screen
 * changes nothing Compose observes, so a one-shot check would keep reporting the stale
 * value after the user returns to the app.
 */
@Composable
internal fun rememberSmsPermissionsGranted(): Boolean {
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    var granted by remember { mutableStateOf(smsPermissionsGranted(context)) }

    DisposableEffect(activity) {
        val lifecycle = activity?.lifecycle ?: return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = smsPermissionsGranted(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    return granted
}
