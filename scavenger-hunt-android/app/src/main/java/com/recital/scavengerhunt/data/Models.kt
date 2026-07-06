package com.recital.scavengerhunt.data

data class HuntMeta(
    val name: String = "",
    val joinCode: String = "",
    val hostUid: String = "",
    val hostEmail: String = "",
    val createdAt: Long = 0L,
    val status: String = "open"
)

data class Checkpoint(
    val id: String = "",
    val order: Int = 0,
    val title: String = "",
    val clueText: String = "",
    val hintText: String = "",
    /** JPEG base64 (no data-uri prefix) for the ghost overlay image */
    val imageBase64: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** How accurate the host GPS was when the pin was set (meters). */
    val gpsPinAccuracyM: Float? = null
)

data class TeamProgress(
    val teamId: String = "",
    val teamName: String = "",
    val playerUid: String = "",
    val playerEmail: String = "",
    val joinedAt: Long = 0L,
    val completedCheckpointIds: Map<String, Boolean> = emptyMap(),
    val lastCompletedAt: Long = 0L
) {
    fun completedCount(): Int = completedCheckpointIds.count { (_, v) -> v }

    fun hasCompleted(checkpointId: String): Boolean =
        completedCheckpointIds[checkpointId] == true
}

fun TeamProgress.hasFinishedHunt(totalCheckpoints: Int): Boolean =
    totalCheckpoints > 0 && completedCount() >= totalCheckpoints

fun TeamProgress.elapsedFinishMs(): Long? {
    if (joinedAt <= 0L || lastCompletedAt <= 0L) return null
    return (lastCompletedAt - joinedAt).coerceAtLeast(0L)
}

fun formatHuntDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return if (min > 0) "${min}m ${sec}s" else "${sec}s"
}

data class HuntSnapshot(
    val huntId: String,
    val meta: HuntMeta,
    val checkpoints: List<Checkpoint>,
    val teams: List<TeamProgress>
)

data class HostedHuntSummary(
    val huntId: String,
    val name: String,
    val joinCode: String,
    val createdAt: Long = 0L
)

data class DraftCheckpoint(
    val title: String = "",
    /** Shown while searching — tells players where to go */
    val hintText: String = "",
    /** Shown after ghost photo match — reward / next lead */
    val clueText: String = "",
    val imageBase64: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** How accurate the host GPS was when the pin was set (meters). */
    val gpsPinAccuracyM: Float? = null
) {
    /** Extra blank row from "Add checkpoint" — safe to drop on publish. */
    fun isBlankDraft(): Boolean =
        title.isBlank() && hintText.isBlank() && clueText.isBlank() &&
            imageBase64.isBlank() && latitude == null && longitude == null

    fun missingForPublish(): List<String> = buildList {
        if (title.isBlank()) add("stop name")
        if (hintText.isBlank()) add("direction clue")
        if (clueText.isBlank()) add("reward clue")
        if (imageBase64.isBlank()) add("ghost photo")
    }
}

fun Checkpoint.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "order" to order,
    "title" to title,
    "clueText" to clueText,
    "hintText" to hintText,
    "imageBase64" to imageBase64,
    "latitude" to latitude,
    "longitude" to longitude,
    "gpsPinAccuracyM" to gpsPinAccuracyM
)

fun checkpointFromMap(id: String, map: Map<*, *>): Checkpoint {
    val storedId = (map["id"] as? String)?.takeIf { it.isNotBlank() }
    return Checkpoint(
        id = storedId ?: id,
        order = (map["order"] as? Number)?.toInt() ?: 0,
        title = map["title"] as? String ?: "",
        clueText = map["clueText"] as? String ?: "",
        hintText = map["hintText"] as? String ?: "",
        imageBase64 = map["imageBase64"] as? String ?: "",
        latitude = (map["latitude"] as? Number)?.toDouble(),
        longitude = (map["longitude"] as? Number)?.toDouble(),
        gpsPinAccuracyM = (map["gpsPinAccuracyM"] as? Number)?.toFloat()
    )
}

fun parseCompletedCheckpointIds(raw: Any?): Map<String, Boolean> {
    val map = raw as? Map<*, *> ?: return emptyMap()
    return map.mapNotNull { (k, v) ->
        val key = k as? String ?: return@mapNotNull null
        val done = when (v) {
            is Boolean -> v
            is Number -> v.toInt() != 0
            is String -> v.equals("true", ignoreCase = true)
            else -> false
        }
        key to done
    }.toMap()
}

fun HuntMeta.toMap(): Map<String, Any?> = mapOf(
    "name" to name,
    "joinCode" to joinCode,
    "hostUid" to hostUid,
    "hostEmail" to hostEmail,
    "createdAt" to createdAt,
    "status" to status
)

fun huntMetaFromMap(map: Map<*, *>?): HuntMeta {
    if (map == null) return HuntMeta()
    return HuntMeta(
        name = map["name"] as? String ?: "",
        joinCode = map["joinCode"] as? String ?: "",
        hostUid = map["hostUid"] as? String ?: "",
        hostEmail = map["hostEmail"] as? String ?: "",
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
        status = map["status"] as? String ?: "open"
    )
}

fun TeamProgress.toMap(): Map<String, Any?> = mapOf(
    "teamName" to teamName,
    "playerUid" to playerUid,
    "playerEmail" to playerEmail,
    "joinedAt" to joinedAt,
    "completedCheckpointIds" to completedCheckpointIds,
    "lastCompletedAt" to lastCompletedAt
)

fun teamFromMap(teamId: String, map: Map<*, *>?): TeamProgress {
    if (map == null) return TeamProgress(teamId = teamId)
    return TeamProgress(
        teamId = teamId,
        teamName = map["teamName"] as? String ?: "",
        playerUid = map["playerUid"] as? String ?: "",
        playerEmail = map["playerEmail"] as? String ?: "",
        joinedAt = (map["joinedAt"] as? Number)?.toLong() ?: 0L,
        completedCheckpointIds = parseCompletedCheckpointIds(map["completedCheckpointIds"]),
        lastCompletedAt = (map["lastCompletedAt"] as? Number)?.toLong() ?: 0L
    )
}
