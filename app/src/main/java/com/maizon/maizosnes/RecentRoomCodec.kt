package com.maizon.maizosnes

import org.json.JSONArray
import org.json.JSONObject

/** Recently visited room bookmarks, stored on-device; no account or server DB. */
internal data class RecentRoom(
    val id: String,
    val game: String,
    val ownerName: String,
    val endpoint: String,
    val visitedAt: Long
)
internal object RecentRoomCodec {
    fun decode(raw: String?): List<RecentRoom> = runCatching {
        val array = JSONArray(raw ?: "[]")
        (0 until minOf(array.length(), 12)).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            val endpoint = item.optString("endpoint")
            if (!id.matches(Regex("[a-f0-9]{12,24}")) || endpoint.length > 300)
                return@mapNotNull null
            RecentRoom(id, item.optString("game").take(75),
                PlayerIdentity.sanitize(item.optString("ownerName")),
                endpoint, item.optLong("visitedAt"))
        }
    }.getOrDefault(emptyList())

    fun withVisited(previous: List<RecentRoom>, room: RecentRoom): List<RecentRoom> =
        (listOf(room) + previous.filterNot { it.id == room.id && it.endpoint == room.endpoint })
            .take(8)
    fun encode(rooms: List<RecentRoom>): String = JSONArray().apply {
        rooms.take(8).forEach { item ->
            put(JSONObject().apply {
                put("id", item.id)
                put("game", item.game)
                put("ownerName", PlayerIdentity.sanitize(item.ownerName))
                put("endpoint", item.endpoint)
                put("visitedAt", item.visitedAt)
            })
        }
    }.toString()
}
