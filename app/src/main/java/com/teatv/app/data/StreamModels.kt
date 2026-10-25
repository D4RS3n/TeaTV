package com.teatv.app.data

/** Подписка (GET /helix/channels/followed). */
data class FollowedChannel(
    val broadcasterId: String,
    val broadcasterLogin: String,
    val broadcasterName: String,
    val followedAt: String
)

/** Живой стрим (GET /helix/streams/followed). */
data class LiveStream(
    val userId: String,
    val userName: String,
    val gameId: String,
    val gameName: String,
    val title: String,
    val viewerCount: Int,
    val thumbnailUrl: String,
    val startedAt: String = ""
)

/** Данные канала (GET /helix/channels). */
data class ChannelInfo(
    val broadcasterId: String,
    val title: String,
    val gameName: String,
    val language: String
)

/** Элемент списка: подписка + жива ли сейчас. */
data class StreamerItem(
    val id: String,
    val login: String,
    val name: String,
    val avatarUrl: String,
    val isLive: Boolean,
    val stream: LiveStream?
)

/** Категория, добавленная пользователем на локальную полку. */
data class FollowedCategory(
    val id: String,
    val name: String,
    val boxArtUrl: String
)

/** Детали категории для экрана категории. */
data class CategoryDetails(
    val id: String,
    val name: String,
    val boxArtUrl: String,
    val viewersCount: Int,
    val followersCount: Int,
    val tags: List<String>,
    val isFollowed: Boolean
)

/** Страница стримов по игре с курсором для пагинации. */
data class StreamsPage(
    val streams: List<StreamerItem>,
    val cursor: String?,
    val hasMore: Boolean
)

/** Сырая страница стримов игры из Helix (до обогащения аватарами). */
data class LiveStreamPage(
    val streams: List<LiveStream>,
    val cursor: String?,
    val hasMore: Boolean
)

/** Канал из поиска Helix. */
data class SearchChannel(
    val id: String,
    val login: String,
    val displayName: String,
    val gameName: String,
    val title: String,
    val isLive: Boolean,
    val thumbnailUrl: String
) {
    val name: String = displayName
}

/** Категория из поиска Helix. */
data class SearchCategory(
    val id: String,
    val name: String,
    val boxArtUrl: String
)

/** Пакет результатов поиска. */
data class SearchBundle(
    val channels: List<SearchChannel>,
    val categories: List<SearchCategory>,
    val streams: List<LiveStream>
) {
    companion object {
        val EMPTY = SearchBundle(emptyList(), emptyList(), emptyList())
    }
}

data class ChannelPage(
    val bannerUrl: String,
    val description: String,
    val socials: List<SocialLink>,
    val panels: List<ChannelPanel> = emptyList(),
    val followersCount: Int = -1,
    val brandColor: String = ""
)

/** Клип канала. */
data class ChannelClip(
    val id: String,
    val title: String,
    val viewCount: Int,
    val thumbnailUrl: String,
    val url: String,
    val durationSeconds: Int,
    val createdAt: String
)

data class ChannelPanel(
    val title: String,
    val description: String,
    val imageUrl: String,
    val linkUrl: String
)

/** Ссылка на стороннее медиа канала. */
data class SocialLink(val label: String, val url: String)

/** Прошлая трансляция канала (архив). */
data class ChannelVideo(
    val id: String,
    val title: String,
    val createdAt: String,
    val duration: String,
    val viewCount: Int,
    val thumbnailUrl: String,
    val url: String
)

/** Активная подписка пользователя на стримера. */
data class MySubscription(
    val broadcasterId: String,
    val broadcasterLogin: String,
    val broadcasterName: String,
    val tier: String,
    val planName: String,
    val createdAt: String?
)