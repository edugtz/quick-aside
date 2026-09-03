package com.edu.quickaside.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppDestination(
    val label: String,
    val icon: ImageVector,
) {
    Inicio("Inicio", Icons.Outlined.Home),
    Pendientes("Pendientes", Icons.Outlined.CheckCircle),
    Listas("Listas", Icons.AutoMirrored.Outlined.List),
    Memoria("Memoria", Icons.Outlined.Search),
}
