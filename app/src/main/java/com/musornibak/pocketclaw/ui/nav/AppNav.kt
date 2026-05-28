package com.musornibak.pocketclaw.ui.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.musornibak.pocketclaw.R
import com.musornibak.pocketclaw.ui.a11y.A11yScreen
import com.musornibak.pocketclaw.ui.chat.ChatScreen
import com.musornibak.pocketclaw.ui.confirm.ConfirmHost
import com.musornibak.pocketclaw.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

enum class Screen { Chat, Settings, A11y }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNav() {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var current by rememberSaveable { mutableStateOf(Screen.Chat) }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "PocketClaw",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Лапа агента",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    drawerItem(Screen.Chat, current, Icons.Outlined.Chat, R.string.nav_chat) {
                        current = it; scope.launch { drawer.close() }
                    }
                    drawerItem(Screen.Settings, current, Icons.Outlined.Cloud, R.string.nav_settings) {
                        current = it; scope.launch { drawer.close() }
                    }
                    drawerItem(Screen.A11y, current, Icons.Outlined.Build, R.string.nav_a11y) {
                        current = it; scope.launch { drawer.close() }
                    }
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (current) {
                Screen.Chat -> ChatScreen(onOpenDrawer = { scope.launch { drawer.open() } })
                Screen.Settings -> SettingsScreen(onOpenDrawer = { scope.launch { drawer.open() } })
                Screen.A11y -> A11yScreen(onOpenDrawer = { scope.launch { drawer.open() } })
            }
            ConfirmHost()
        }
    }
}

@Composable
private fun drawerItem(
    target: Screen,
    current: Screen,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    labelRes: Int,
    onClick: (Screen) -> Unit
) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(stringResource(labelRes)) },
        selected = current == target,
        onClick = { onClick(target) },
        shape = RoundedCornerShape(12.dp),
        colors = NavigationDrawerItemDefaults.colors()
    )
}
