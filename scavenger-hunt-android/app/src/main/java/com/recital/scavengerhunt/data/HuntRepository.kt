package com.recital.scavengerhunt.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class HuntRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance()
    private val huntsRef = db.getReference("recitals")
    private val joinCodesRef = db.getReference("joinCodes")

    val currentUser get() = auth.currentUser

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun signUp(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email.trim(), password).await()
    }

    fun signOut() {
        auth.signOut()
    }

    fun observeAuth(): Flow<com.google.firebase.auth.FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun createHunt(
        name: String,
        checkpoints: List<DraftCheckpoint>,
        customJoinCode: String? = null
    ): String {
        val user = auth.currentUser ?: error("Sign in required")
        val joinCode = resolveJoinCodeForCreate(customJoinCode)
        val huntId = huntsRef.push().key ?: error("Could not create hunt")
        val meta = HuntMeta(
            name = name.trim(),
            joinCode = joinCode,
            hostUid = user.uid,
            hostEmail = user.email ?: "",
            createdAt = System.currentTimeMillis(),
            status = "open"
        )
        val checkpointMaps = checkpoints.mapIndexed { index, draft ->
            val id = "cp${index + 1}"
            id to Checkpoint(
                id = id,
                order = index,
                title = draft.title.trim(),
                clueText = draft.clueText.trim(),
                hintText = draft.hintText.trim(),
                imageBase64 = draft.imageBase64,
                latitude = draft.latitude,
                longitude = draft.longitude,
                gpsPinAccuracyM = draft.gpsPinAccuracyM
            ).toMap()
        }.toMap()

        huntsRef.child(huntId).child("meta").setValue(meta.toMap()).await()
        if (checkpointMaps.isNotEmpty()) {
            huntsRef.child(huntId).child("checkpoints").setValue(checkpointMaps).await()
        }
        joinCodesRef.child(joinCode).setValue(huntId).await()
        indexHostedHunt(user.uid, huntId, meta.name, joinCode, meta.createdAt)
        return huntId
    }

    suspend fun indexHostedHunt(uid: String, huntId: String, name: String, joinCode: String, createdAt: Long) {
        db.getReference("hostHunts").child(uid).child(huntId).setValue(
            mapOf(
                "name" to name,
                "joinCode" to joinCode,
                "createdAt" to createdAt
            )
        ).await()
    }

    suspend fun listHostedHunts(): List<HostedHuntSummary> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        val snap = db.getReference("hostHunts").child(uid).get().await()
        if (!snap.exists()) return emptyList()
        return snap.children.mapNotNull { child ->
            val map = child.value as? Map<*, *> ?: return@mapNotNull null
            HostedHuntSummary(
                huntId = child.key ?: return@mapNotNull null,
                name = map["name"] as? String ?: "Hunt",
                joinCode = map["joinCode"] as? String ?: "",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L
            )
        }.sortedByDescending { it.createdAt }
    }

    suspend fun fetchHuntMeta(huntId: String): HuntMeta? {
        val snap = huntsRef.child(huntId).child("meta").get().await()
        if (!snap.exists()) return null
        return huntMetaFromMap(snap.value as? Map<*, *>)
    }

    suspend fun fetchJoinCode(huntId: String): String? {
        val snap = huntsRef.child(huntId).child("meta").child("joinCode").get().await()
        return snap.getValue(String::class.java)
    }

    suspend fun resolveJoinCode(code: String): String? {
        val snap = joinCodesRef.child(code.uppercase()).get().await()
        return snap.getValue(String::class.java)
    }

    suspend fun joinHunt(huntId: String, teamName: String): String {
        val user = auth.currentUser ?: error("Sign in required")
        val teamsRef = huntsRef.child(huntId).child("teams")
        val normalizedName = teamName.trim()
        val existing = teamsRef.get().await()
        for (child in existing.children) {
            val map = child.value as? Map<*, *> ?: continue
            val uid = map["playerUid"] as? String
            val name = (map["teamName"] as? String)?.trim() ?: ""
            // Same account + same team name → resume that run
            if (uid == user.uid && name.equals(normalizedName, ignoreCase = true)) {
                return child.key ?: error("Invalid team entry")
            }
        }
        val teamId = teamsRef.push().key ?: error("Could not join")
        val team = TeamProgress(
            teamId = teamId,
            teamName = normalizedName,
            playerUid = user.uid,
            playerEmail = user.email ?: "",
            joinedAt = System.currentTimeMillis()
        )
        teamsRef.child(teamId).setValue(team.toMap()).await()
        return teamId
    }

    suspend fun findTeamForPlayer(huntId: String, uid: String): TeamProgress? {
        val snap = huntsRef.child(huntId).child("teams").get().await()
        return snap.children.mapNotNull { child ->
            val map = child.value as? Map<*, *> ?: return@mapNotNull null
            if (map["playerUid"] as? String != uid) return@mapNotNull null
            teamFromMap(child.key ?: return@mapNotNull null, map)
        }.maxByOrNull { it.joinedAt }
    }

    private fun parseCheckpoints(snapshot: DataSnapshot): List<Checkpoint> {
        val parsed = mutableListOf<Checkpoint>()
        val node = snapshot.child("checkpoints")
        for (child in node.children) {
            val key = child.key ?: continue
            when (val raw = child.value) {
                is Map<*, *> -> parsed.add(checkpointFromMap(key, raw))
            }
        }
        if (parsed.isEmpty()) {
            when (val raw = node.value) {
                is Map<*, *> -> raw.forEach { (k, v) ->
                    if (k is String && v is Map<*, *>) parsed.add(checkpointFromMap(k, v))
                }
            }
        }
        return parsed.sortedBy { it.order }
    }

    fun observeHunt(huntId: String): Flow<HuntSnapshot?> = callbackFlow {
        val ref = huntsRef.child(huntId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    trySend(null)
                    return
                }
                val meta = huntMetaFromMap(snapshot.child("meta").value as? Map<*, *>)
                val checkpoints = parseCheckpoints(snapshot)
                val teams = snapshot.child("teams").children.mapNotNull { child ->
                    val map = child.value as? Map<*, *> ?: return@mapNotNull null
                    teamFromMap(child.key ?: return@mapNotNull null, map)
                }.sortedByDescending { it.completedCount() }
                trySend(HuntSnapshot(huntId, meta, checkpoints, teams))
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun updateCheckpointGps(
        huntId: String,
        checkpointId: String,
        lat: Double,
        lon: Double,
        accuracyM: Float
    ) {
        huntsRef.child(huntId).child("checkpoints").child(checkpointId).updateChildren(
            mapOf(
                "latitude" to lat,
                "longitude" to lon,
                "gpsPinAccuracyM" to accuracyM
            )
        ).await()
    }

    suspend fun saveCheckpoint(huntId: String, checkpoint: Checkpoint) {
        huntsRef.child(huntId).child("checkpoints").child(checkpoint.id)
            .setValue(checkpoint.toMap()).await()
    }

    suspend fun addCheckpoint(huntId: String, checkpoint: Checkpoint) {
        saveCheckpoint(huntId, checkpoint)
    }

    suspend fun completeCheckpoint(huntId: String, teamId: String, checkpointId: String) {
        val updates = mapOf(
            "completedCheckpointIds/$checkpointId" to true,
            "lastCompletedAt" to System.currentTimeMillis()
        )
        huntsRef.child(huntId).child("teams").child(teamId).updateChildren(updates).await()
    }

    suspend fun deleteHunt(huntId: String, joinCode: String?) {
        huntsRef.child(huntId).removeValue().await()
        if (!joinCode.isNullOrBlank()) {
            joinCodesRef.child(joinCode.uppercase()).removeValue().await()
        }
        auth.currentUser?.uid?.let { uid ->
            db.getReference("hostHunts").child(uid).child(huntId).removeValue().await()
        }
    }

    private suspend fun resolveJoinCodeForCreate(customJoinCode: String?): String {
        if (!customJoinCode.isNullOrBlank()) {
            val normalized = normalizeJoinCode(customJoinCode)
            validateJoinCode(normalized)?.let { error(it) }
            if (resolveJoinCode(normalized) != null) {
                error("Join code \"$normalized\" is already taken — pick another")
            }
            return normalized
        }
        repeat(24) {
            val code = generateJoinCode()
            if (resolveJoinCode(code) == null) return code
        }
        error("Could not generate a unique join code — try again")
    }

    fun normalizeJoinCode(raw: String): String =
        raw.trim().uppercase().filter { it.isLetterOrDigit() }

    fun validateJoinCode(code: String): String? = when {
        code.length < 3 -> "Join code needs at least 3 characters"
        code.length > 12 -> "Join code can be at most 12 characters"
        !code.all { it.isLetterOrDigit() } -> "Use letters and numbers only"
        else -> null
    }

    private fun generateJoinCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}
