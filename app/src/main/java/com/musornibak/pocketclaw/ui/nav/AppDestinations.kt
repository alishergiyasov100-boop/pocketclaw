package com.musornibak.pocketclaw.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.ui.graphics.vector.ImageVector
import com.musornibak.pocketclaw.R

sealed class TopDestination(val route: String, val labelRes: Int, val icon: ImageVector) {
    data object Chat : TopDestination("chat", R.string.nav_chat, Icons.Outlined.Chat)
    data object Api : TopDestination("api", R.string.nav_settings, Icons.Outlined.Lan)
    data object A11y : TopDestination("a11y", R.string.nav_a11y, Icons.Outlined.Accessibility)
}
