package com.musornibak.pocketclaw.ui.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.musornibak.pocketclaw.R
import com.musornibak.pocketclaw.ui.a11y.A11yScreen
import com.musornibak.pocketclaw.ui.chat.ChatScreen
import com.musornibak.pocketclaw.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNav() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val closeAndGo: (String) -> Unit = { route ->
        scope.launch { drawerState.close() }
        if (route != currentRoute) {
            navController.navigate(route) {
                popUpTo(TopDestination.Chat.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = { AppDrawer(currentRoute = currentRoute, onPick = closeAndGo) }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = TopDestination.Chat.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(TopDestination.Chat.route) {
                    ChatScreen(onOpenDrawer = openDrawer)
                }
                composable(TopDestination.Api.route) {
                    SettingsScreen(onOpenDrawer = openDrawer)
                }
                composable(TopDestination.A11y.route) {
                    A11yScreen(onOpenDrawer = openDrawer)
                }
            }
        }
    }
}

private data class DrawerEntry(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
)

@Composable
private fun AppDrawer(
    currentRoute: String?,
    onPick: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val entries = listOf(
        DrawerEntry(TopDestination.Chat.route, TopDestination.Chat.labelRes, TopDestination.Chat.icon),
        DrawerEntry(TopDestination.Api.route, TopDestination.Api.labelRes, TopDestination.Api.icon),
        DrawerEntry(TopDestination.A11y.route, TopDestination.A11y.labelRes, TopDestination.A11y.icon)
    )
    ModalDrawerSheet(
        drawerContainerColor = cs.surface,
        drawerContentColor = cs.onSurface
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )
        Text(
            stringResource(R.string.drawer_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
        ) {
            entries.forEach { e ->
                NavigationDrawerItem(
                    selected = currentRoute == e.route,
                    onClick = { onPick(e.route) },
                    icon = { Icon(e.icon, contentDescription = null) },
                    label = { Text(stringResource(e.labelRes)) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = cs.surfaceContainerHigh,
                        unselectedContainerColor = cs.surface,
                        selectedIconColor = cs.onSurface,
                        unselectedIconColor = cs.onSurfaceVariant,
                        selectedTextColor = cs.onSurface,
                        unselectedTextColor = cs.onSurfaceVariant
                    )
                )
            }
        }
    }
}
