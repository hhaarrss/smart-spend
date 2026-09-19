package com.smartspend.app.ui.navigation

/**
 * Every navigable destination in the app.
 *
 * Route strings are persisted in the saved back stack across process death, so they are
 * part of the app's stored state — renaming one invalidates a restored back stack.
 */
enum class Destination(val route: String) {
    Home("home"),
    AddTransaction("add_transaction"),
    Budget("budget"),
    Categories("categories"),
    Trends("trends"),
    Account("account"),
    SmsConsent("sms_consent")
}
