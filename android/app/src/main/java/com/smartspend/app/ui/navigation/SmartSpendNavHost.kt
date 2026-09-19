package com.smartspend.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smartspend.app.ui.account.AccountScreen
import com.smartspend.app.ui.addtransaction.AddTransactionScreen
import com.smartspend.app.ui.auth.AuthScreen
import com.smartspend.app.ui.budget.BudgetScreen
import com.smartspend.app.ui.categories.CategoriesScreen
import com.smartspend.app.ui.home.HomeScreen
import com.smartspend.app.ui.permission.SmsConsentScreen
import com.smartspend.app.ui.trends.TrendsScreen

/**
 * A tap that lands while the current entry is mid-transition would otherwise push the
 * destination twice, leaving a duplicate on the back stack that the user has to dismiss
 * two times. Dropping events from a non-resumed entry is the standard guard.
 */
private fun NavHostController.navigateTo(destination: Destination) {
    val isResumed = currentBackStackEntry?.lifecycle?.currentState
        ?.isAtLeast(Lifecycle.State.RESUMED) == true
    if (!isResumed) return
    navigate(destination.route) { launchSingleTop = true }
}

@Composable
fun SmartSpendNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: Destination = Destination.Home
) {
    NavHost(
        navController = navController,
        startDestination = startDestination.route,
        modifier = modifier
    ) {
        composable(Destination.Auth.route) {
            AuthScreen(
                onAuthenticated = {
                    navController.navigate(Destination.Home.route) {
                        popUpTo(Destination.Auth.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Destination.Home.route) {
            HomeScreen(
                onAddTransaction = { navController.navigateTo(Destination.AddTransaction) },
                onBudget = { navController.navigateTo(Destination.Budget) },
                onCategories = { navController.navigateTo(Destination.Categories) },
                onTrends = { navController.navigateTo(Destination.Trends) },
                onAccount = { navController.navigateTo(Destination.Account) },
                onEnableAutoSync = { navController.navigateTo(Destination.SmsConsent) }
            )
        }

        composable(Destination.AddTransaction.route) {
            AddTransactionScreen(onBack = { navController.navigateUp() })
        }

        composable(Destination.Budget.route) {
            BudgetScreen(onBack = { navController.navigateUp() })
        }

        composable(Destination.Categories.route) {
            CategoriesScreen(onBack = { navController.navigateUp() })
        }

        composable(Destination.Trends.route) {
            TrendsScreen(
                onBack = { navController.navigateUp() },
                onBudget = { navController.navigateTo(Destination.Budget) }
            )
        }

        composable(Destination.Account.route) {
            AccountScreen(
                onBack = { navController.navigateUp() },
                onLogout = {
                    // AccountScreen already cleared the stored session before calling this.
                    // Nothing from the signed-in session may stay reachable via back.
                    navController.navigate(Destination.Auth.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Destination.SmsConsent.route) {
            // Every outcome lands back on Home with the consent screen dropped from the
            // back stack — pressing back from Home must not re-enter the disclosure flow.
            val returnHome = {
                navController.popBackStack(Destination.Home.route, inclusive = false)
                Unit
            }
            SmsConsentScreen(
                onBack = { navController.navigateUp() },
                onAutoSyncReady = returnHome,
                onManualEntry = returnHome
            )
        }
    }
}
