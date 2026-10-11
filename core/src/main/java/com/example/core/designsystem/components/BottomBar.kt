package com.example.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.SwissColors
import com.example.core.designsystem.SwissType

data class BottomBarItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val badge: Boolean = false,
)

/** The bottom navigation bar from the Figma (5 tabs in Swissquote, 4 in SwissWallet). */
@Composable
fun SwissBottomBar(
    items: List<BottomBarItem>,
    selectedKey: String?,
    onSelect: (BottomBarItem) -> Unit,
    modifier: Modifier = Modifier,
    inactiveColor: Color = SwissColors.TextSoft,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SwissColors.NavBar)
            .navigationBarsPadding()
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(SwissColors.Line))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEach { item ->
                val selected = item.key == selectedKey
                val color = if (selected) SwissColors.Orange else inactiveColor
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(role = Role.Tab) { onSelect(item) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box {
                        Icon(item.icon, contentDescription = item.label, tint = color, modifier = Modifier.size(24.dp))
                        if (item.badge) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 4.dp, y = (-2).dp)
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(SwissColors.NavBar)
                            ) {
                                Box(
                                    Modifier
                                        .align(Alignment.Center)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(SwissColors.Green)
                                )
                            }
                        }
                    }
                    Text(
                        text = item.label,
                        style = SwissType.Tab,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = color,
                        modifier = Modifier.offset(y = 3.dp),
                    )
                }
            }
        }
    }
}
