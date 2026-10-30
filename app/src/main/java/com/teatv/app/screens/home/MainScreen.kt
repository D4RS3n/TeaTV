package com.teatv.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.teatv.app.MainTab
import com.teatv.app.R
import com.teatv.app.data.ChannelInfo
import com.teatv.app.data.FollowedCategory
import com.teatv.app.data.SearchBundle
import com.teatv.app.data.SearchChannel
import com.teatv.app.data.StreamerItem
import com.teatv.app.data.TwitchUser
import com.teatv.app.ui.theme.TeaColors

@Composable
fun MainScreen(
    user: TwitchUser?,
    channel: ChannelInfo?,
    streamers: List<StreamerItem>,
    favorites: List<StreamerItem>,
    categories: List<FollowedCategory>,
    tab: MainTab,
    onTab: (MainTab) -> Unit,
    onLogout: () -> Unit,
    onOpenStream: (StreamerItem) -> Unit,
    onOpenChannel: (StreamerItem) -> Unit,
    onToggleFavorite: (StreamerItem) -> Unit,
    onToggleBot: (StreamerItem) -> Unit,
    isBotForChannel: (String) -> Boolean,
    onOpenCategory: (FollowedCategory) -> Unit,
    modifier: Modifier = Modifier,
    searchQuery: String,
    searchResults: SearchBundle,
    searchLoading: Boolean,
    onSearchQuery: (String) -> Unit,
    onOpenChannelFromSearch: (SearchChannel) -> Unit,
    onOpenAccount: () -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    bufferSeconds: Int = 15,
    onBufferChange: (Int) -> Unit = {},
    modModeEnabled: Boolean = true,
    onModModeChange: (Boolean) -> Unit = {},
    modSwipeRequiresSelection: Boolean = true,
    onModSwipeRequiresSelectionChange: (Boolean) -> Unit = {},
    splitMessagesEnabled: Boolean = true,
    onSplitMessagesChange: (Boolean) -> Unit = {},
    onOpenEmoteOrder: () -> Unit = {}
) {
    Scaffold(
        modifier = modifier,
        containerColor = TeaColors.Background,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TeaColors.Surface)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockButton(MainTab.View, R.drawable.tab_view, tab, onTab)
                DockButton(MainTab.Search, R.drawable.tab_search, tab, onTab)
                DockButton(MainTab.Tools, R.drawable.tab_tools, tab, onTab)
            }
        }
    ) { padding ->
        when (tab) {
            MainTab.View -> HomeScreen(
                user = user,
                channel = channel,
                streamers = streamers,
                favorites = favorites,
                categories = categories,
                onOpenStream = onOpenStream,
                onToggleFavorite = onToggleFavorite,
                onToggleBot = onToggleBot,
                isBotForChannel = isBotForChannel,
                onOpenCategory = onOpenCategory,
                onOpenAccount = onOpenAccount,
                onOpenChannel = onOpenChannel,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.padding(padding)
            )
            MainTab.Search -> SearchScreen(
                query = searchQuery,
                results = searchResults,
                loading = searchLoading,
                onQuery = onSearchQuery,
                onOpenChannel = onOpenChannelFromSearch,
                onOpenCategory = onOpenCategory,
                modifier = Modifier.padding(padding)
            )
            MainTab.Tools -> ToolsScreen(
                modModeEnabled = modModeEnabled,
                onModModeChange = onModModeChange,
                modSwipeRequiresSelection = modSwipeRequiresSelection,
                onModSwipeRequiresSelectionChange = onModSwipeRequiresSelectionChange,
                splitMessagesEnabled = splitMessagesEnabled,
                onSplitMessagesChange = onSplitMessagesChange,
                onOpenEmoteOrder = onOpenEmoteOrder,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun DockButton(
    tab: MainTab,
    iconRes: Int,
    current: MainTab,
    onTab: (MainTab) -> Unit
) {
    val selected = current == tab
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(
                if (selected) TeaColors.Accent.copy(alpha = 0.15f)
                else Color.Transparent
            )
            .clickable { onTab(tab) },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = if (selected) TeaColors.Accent else TeaColors.TextSecondary,
            modifier = Modifier.size(24.dp)
        )
    }
}