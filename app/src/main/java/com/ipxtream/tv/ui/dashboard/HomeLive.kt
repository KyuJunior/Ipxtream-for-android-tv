package com.ipxtream.tv.ui.dashboard

import com.ipxtream.tv.data.local.LibraryItem
import com.ipxtream.tv.data.model.StreamItem

/** Home's live row: the user's own channels first, so it fits any provider. */
object HomeLive {

    /**
     * Favourite live channels, then recently watched ones (newest first), as they are in the
     * current channel list (removed channels drop out). With neither, the provider's first channels.
     */
    fun highlights(
        channels:  List<StreamItem>,
        favorites: List<LibraryItem>,
        history:   List<LibraryItem>,
        limit:     Int = 15
    ): List<StreamItem> {
        val byId = channels.associateBy { it.streamId.toString() }
        val mine = (favorites.filter { it.type == "live" } + history.filter { it.type == "live" }.sortedByDescending { it.timestamp })
            .mapNotNull { byId[it.id] }
            .distinctBy { it.streamId }
            .take(limit)
        return mine.ifEmpty { channels.take(limit) }
    }
}
