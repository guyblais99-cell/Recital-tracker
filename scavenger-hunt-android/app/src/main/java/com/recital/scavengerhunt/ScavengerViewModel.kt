package com.recital.scavengerhunt

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recital.scavengerhunt.data.Checkpoint
import com.recital.scavengerhunt.data.DraftCheckpoint
import com.recital.scavengerhunt.data.HostedHuntSummary
import com.recital.scavengerhunt.data.HuntRepository
import com.recital.scavengerhunt.data.HuntSnapshot
import com.recital.scavengerhunt.data.TeamProgress
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppScreen {
    AUTH,
    HOME,
    MY_HUNTS,
    CREATE_HUNT,
    JOIN_HUNT,
    HOST_DASHBOARD,
    PLAYER_HUNT,
    ALIGN_CHECKPOINT,
    CLUE_REVEAL
}

data class PlayerUiState(
    val hunt: HuntSnapshot? = null,
    val team: TeamProgress? = null,
    val nextCheckpoint: Checkpoint? = null,
    val huntComplete: Boolean = false,
    val teamLinked: Boolean = false
)

class ScavengerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = HuntRepository()
    private val prefs = app.getSharedPreferences("scavenger", 0)

    private val _screen = MutableStateFlow(AppScreen.AUTH)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()

    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _hunt = MutableStateFlow<HuntSnapshot?>(null)
    val hunt: StateFlow<HuntSnapshot?> = _hunt.asStateFlow()

    private val _playerUi = MutableStateFlow(PlayerUiState())
    val playerUi: StateFlow<PlayerUiState> = _playerUi.asStateFlow()

    private val _hostedHunts = MutableStateFlow<List<HostedHuntSummary>>(emptyList())
    val hostedHunts: StateFlow<List<HostedHuntSummary>> = _hostedHunts.asStateFlow()

    private val _activeHuntId = MutableStateFlow<String?>(null)
    val activeHuntId: StateFlow<String?> = _activeHuntId.asStateFlow()

    private val _activeTeamId = MutableStateFlow<String?>(null)
    val activeTeamId: StateFlow<String?> = _activeTeamId.asStateFlow()

    private val _activeCheckpoint = MutableStateFlow<Checkpoint?>(null)
    val activeCheckpoint: StateFlow<Checkpoint?> = _activeCheckpoint.asStateFlow()

    private val _revealedClue = MutableStateFlow<String?>(null)
    val revealedClue: StateFlow<String?> = _revealedClue.asStateFlow()

    private var huntJob: Job? = null

    init {
        viewModelScope.launch {
            repo.observeAuth().collect { user ->
                _userEmail.value = user?.email
                if (user?.email != null && _screen.value == AppScreen.AUTH) {
                    _screen.value = AppScreen.HOME
                    refreshHostedHunts()
                    restorePlayerSessionIfAny()
                } else if (user == null) {
                    stopHuntListener()
                    _hostedHunts.value = emptyList()
                    _screen.value = AppScreen.AUTH
                }
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _busy.value = true
            _status.value = ""
            try {
                repo.signIn(email, password)
            } catch (e: Exception) {
                _status.value = e.message ?: "Sign in failed"
            } finally {
                _busy.value = false
            }
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _busy.value = true
            _status.value = ""
            try {
                repo.signUp(email, password)
            } catch (e: Exception) {
                _status.value = e.message ?: "Sign up failed"
            } finally {
                _busy.value = false
            }
        }
    }

    fun signOut() {
        repo.signOut()
        _activeHuntId.value = null
        _activeTeamId.value = null
        _hostedHunts.value = emptyList()
        clearPlayerSession()
        stopHuntListener()
        _screen.value = AppScreen.AUTH
    }

    fun goHome() {
        stopHuntListener()
        _activeHuntId.value = null
        _activeTeamId.value = null
        _hunt.value = null
        _status.value = ""
        _screen.value = AppScreen.HOME
    }

    fun leavePlayerHunt() {
        clearPlayerSession()
        goHome()
    }

    fun openMyHunts() {
        stopHuntListener()
        _activeHuntId.value = null
        _hunt.value = null
        _status.value = ""
        _screen.value = AppScreen.MY_HUNTS
        refreshHostedHunts()
    }

    fun openCreateHunt() {
        _status.value = ""
        _screen.value = AppScreen.CREATE_HUNT
    }

    fun openJoinHunt() {
        _status.value = ""
        _screen.value = AppScreen.JOIN_HUNT
    }

    fun openEditHunt(huntId: String) {
        _activeHuntId.value = huntId
        listenToHunt(huntId)
        _screen.value = AppScreen.HOST_DASHBOARD
    }

    fun openHostDashboard(huntId: String) = openEditHunt(huntId)

    fun updateCheckpointPin(checkpointId: String, lat: Double, lon: Double, accuracyM: Float) {
        val huntId = _activeHuntId.value ?: return
        viewModelScope.launch {
            _status.value = ""
            try {
                repo.updateCheckpointGps(huntId, checkpointId, lat, lon, accuracyM)
                _status.value = "GPS pin updated (±${accuracyM.toInt()} m)"
            } catch (e: Exception) {
                _status.value = e.message ?: "Could not update GPS pin"
            }
        }
    }

    fun saveCheckpointDetails(checkpoint: Checkpoint) {
        val huntId = _activeHuntId.value ?: return
        viewModelScope.launch {
            _status.value = ""
            try {
                repo.saveCheckpoint(
                    huntId,
                    checkpoint.copy(
                        title = checkpoint.title.trim(),
                        hintText = checkpoint.hintText.trim(),
                        clueText = checkpoint.clueText.trim()
                    )
                )
                _status.value = "Checkpoint saved"
            } catch (e: Exception) {
                _status.value = e.message ?: "Could not save checkpoint"
            }
        }
    }

    fun addCheckpointToHunt(checkpoint: Checkpoint) {
        val huntId = _activeHuntId.value ?: return
        viewModelScope.launch {
            _status.value = ""
            try {
                repo.addCheckpoint(
                    huntId,
                    checkpoint.copy(
                        title = checkpoint.title.trim(),
                        hintText = checkpoint.hintText.trim(),
                        clueText = checkpoint.clueText.trim()
                    )
                )
                _status.value = "Checkpoint added"
            } catch (e: Exception) {
                _status.value = e.message ?: "Could not add checkpoint"
            }
        }
    }

    fun deleteActiveHunt() {
        val huntId = _activeHuntId.value ?: return
        val joinCode = _hunt.value?.meta?.joinCode
        viewModelScope.launch {
            _busy.value = true
            _status.value = ""
            try {
                repo.deleteHunt(huntId, joinCode)
                removeHostedHuntLocally(huntId)
                stopHuntListener()
                _activeHuntId.value = null
                _hunt.value = null
                refreshHostedHunts()
                _status.value = "Hunt deleted"
                _screen.value = AppScreen.MY_HUNTS
            } catch (e: Exception) {
                _status.value = e.message ?: "Could not delete hunt"
            } finally {
                _busy.value = false
            }
        }
    }

    private fun removeHostedHuntLocally(huntId: String) {
        val ids = prefs.getStringSet(PREF_HOSTED_HUNT_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        ids.remove(huntId)
        prefs.edit().putStringSet(PREF_HOSTED_HUNT_IDS, ids).apply()
        if (prefs.getString(PREF_LAST_HUNT_ID, null) == huntId) {
            prefs.edit()
                .remove(PREF_LAST_HUNT_ID)
                .remove(PREF_LAST_JOIN_CODE)
                .remove(PREF_LAST_HUNT_NAME)
                .apply()
        }
        if (prefs.getString(PREF_PLAYER_HUNT_ID, null) == huntId) {
            clearPlayerSession()
        }
    }

    fun persistHostedHunt(snap: HuntSnapshot) {
        rememberHostedHuntId(snap.huntId)
        prefs.edit()
            .putString(PREF_LAST_HUNT_ID, snap.huntId)
            .putString(PREF_LAST_JOIN_CODE, snap.meta.joinCode)
            .putString(PREF_LAST_HUNT_NAME, snap.meta.name)
            .apply()
        viewModelScope.launch {
            val uid = repo.currentUser?.uid ?: return@launch
            repo.indexHostedHunt(
                uid,
                snap.huntId,
                snap.meta.name,
                snap.meta.joinCode,
                snap.meta.createdAt
            )
            refreshHostedHunts()
        }
    }

    fun refreshHostedHunts() {
        viewModelScope.launch {
            _status.value = ""
            val uid = repo.currentUser?.uid
            val email = repo.currentUser?.email ?: ""
            val merged = mutableMapOf<String, HostedHuntSummary>()

            try {
                repo.listHostedHunts().forEach { merged[it.huntId] = it }
            } catch (_: Exception) {
                // hostHunts index may be unavailable — fall back to local saved ids
            }

            addLocalHuntFallback(merged, uid)

            _hostedHunts.value = merged.values.sortedByDescending { it.createdAt }
        }
    }

    private suspend fun addLocalHuntFallback(merged: MutableMap<String, HostedHuntSummary>, uid: String?) {
        val localIds = prefs.getStringSet(PREF_HOSTED_HUNT_IDS, emptySet()) ?: emptySet()
        val lastId = prefs.getString(PREF_LAST_HUNT_ID, null)
        val savedName = prefs.getString(PREF_LAST_HUNT_NAME, null)
        val savedCode = prefs.getString(PREF_LAST_JOIN_CODE, null)
        val huntIds = (localIds + listOfNotNull(lastId)).distinct()

        for (huntId in huntIds) {
            if (merged.containsKey(huntId)) continue

            val meta = try {
                repo.fetchHuntMeta(huntId)
            } catch (_: Exception) {
                null
            }

            if (meta != null) {
                if (uid != null && meta.hostUid.isNotBlank() && meta.hostUid != uid) continue
                merged[huntId] = HostedHuntSummary(
                    huntId = huntId,
                    name = meta.name.ifBlank { "Your hunt" },
                    joinCode = meta.joinCode,
                    createdAt = meta.createdAt
                )
                if (uid != null) {
                    try {
                        repo.indexHostedHunt(uid, huntId, meta.name, meta.joinCode, meta.createdAt)
                    } catch (_: Exception) { }
                }
            } else if (huntId == lastId) {
                merged[huntId] = HostedHuntSummary(
                    huntId = huntId,
                    name = savedName ?: "Your hunt",
                    joinCode = savedCode ?: ""
                )
            }
        }
    }

    private fun persistLastHostedHunt(huntId: String, name: String, joinCode: String) {
        rememberHostedHuntId(huntId)
        prefs.edit()
            .putString(PREF_LAST_HUNT_ID, huntId)
            .putString(PREF_LAST_JOIN_CODE, joinCode)
            .putString(PREF_LAST_HUNT_NAME, name)
            .apply()
    }

    private fun rememberHostedHuntId(huntId: String) {
        val ids = prefs.getStringSet(PREF_HOSTED_HUNT_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        ids.add(huntId)
        prefs.edit().putStringSet(PREF_HOSTED_HUNT_IDS, ids).apply()
    }

    fun publishHunt(name: String, drafts: List<DraftCheckpoint>, customJoinCode: String = "") {
        if (name.isBlank()) {
            _status.value = "Give your hunt a name"
            return
        }
        val toPublish = drafts.filterNot { it.isBlankDraft() }
        if (toPublish.isEmpty()) {
            _status.value = "Add at least one checkpoint"
            return
        }
        toPublish.forEachIndexed { index, draft ->
            val missing = draft.missingForPublish()
            if (missing.isNotEmpty()) {
                _status.value = "Checkpoint ${index + 1}: add ${missing.joinToString(", ")}"
                return
            }
        }
        if (customJoinCode.isNotBlank()) {
            val code = repo.normalizeJoinCode(customJoinCode)
            repo.validateJoinCode(code)?.let {
                _status.value = it
                return
            }
        }
        viewModelScope.launch {
            _busy.value = true
            _status.value = ""
            try {
                val huntId = repo.createHunt(
                    name,
                    toPublish,
                    customJoinCode.trim().takeIf { it.isNotBlank() }
                )
                val meta = repo.fetchHuntMeta(huntId)
                if (meta != null) {
                    persistLastHostedHunt(huntId, meta.name, meta.joinCode)
                } else {
                    rememberHostedHuntId(huntId)
                }
                _activeHuntId.value = huntId
                _activeTeamId.value = null
                listenToHunt(huntId)
                _screen.value = AppScreen.HOST_DASHBOARD
            } catch (e: Exception) {
                _status.value = e.message ?: "Could not create hunt"
            } finally {
                _busy.value = false
            }
        }
    }

    fun joinHunt(joinCode: String, teamName: String) {
        if (joinCode.isBlank() || teamName.isBlank()) {
            _status.value = "Enter join code and team name"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            _status.value = ""
            try {
                val huntId = repo.resolveJoinCode(joinCode.trim()) ?: run {
                    _status.value = "Invalid join code"
                    return@launch
                }
                val teamId = repo.joinHunt(huntId, teamName)
                stopHuntListener()
                _activeHuntId.value = huntId
                _activeTeamId.value = teamId
                persistPlayerSession(huntId, teamId)
                listenToHunt(huntId)
                refreshPlayerUi()
                _screen.value = AppScreen.PLAYER_HUNT
            } catch (e: Exception) {
                _status.value = e.message ?: "Could not join hunt"
            } finally {
                _busy.value = false
            }
        }
    }

    fun resumePlayerHunt() {
        val huntId = _activeHuntId.value ?: prefs.getString(PREF_PLAYER_HUNT_ID, null)
        val teamId = _activeTeamId.value ?: prefs.getString(PREF_PLAYER_TEAM_ID, null)
        if (huntId != null && teamId != null) {
            _activeHuntId.value = huntId
            _activeTeamId.value = teamId
            listenToHunt(huntId)
            refreshPlayerUi()
        }
        _screen.value = AppScreen.PLAYER_HUNT
    }

    fun ensurePlayerReady() {
        viewModelScope.launch {
            val huntId = _activeHuntId.value ?: prefs.getString(PREF_PLAYER_HUNT_ID, null) ?: return@launch
            _activeHuntId.value = huntId
            if (_hunt.value?.huntId != huntId) {
                listenToHunt(huntId)
            }
            if (_activeTeamId.value == null) {
                _activeTeamId.value = prefs.getString(PREF_PLAYER_TEAM_ID, null)
            }
            syncPlayerTeam(_hunt.value)
            if (_activeTeamId.value == null) {
                val uid = repo.currentUser?.uid ?: return@launch
                repo.findTeamForPlayer(huntId, uid)?.let { team ->
                    _activeTeamId.value = team.teamId
                    persistPlayerSession(huntId, team.teamId)
                }
            }
            refreshPlayerUi()
        }
    }

    fun hasSavedPlayerSession(): Boolean =
        prefs.getString(PREF_PLAYER_HUNT_ID, null) != null &&
            prefs.getString(PREF_PLAYER_TEAM_ID, null) != null

    fun startCheckpoint(checkpoint: Checkpoint) {
        _activeCheckpoint.value = checkpoint
        _revealedClue.value = null
        _screen.value = AppScreen.ALIGN_CHECKPOINT
    }

    fun onCheckpointAligned() {
        val huntId = _activeHuntId.value ?: return
        val teamId = _activeTeamId.value ?: return
        val checkpoint = _activeCheckpoint.value ?: return
        viewModelScope.launch {
            try {
                repo.completeCheckpoint(huntId, teamId, checkpoint.id)
                _revealedClue.value = checkpoint.clueText
                _screen.value = AppScreen.CLUE_REVEAL
            } catch (e: Exception) {
                _status.value = e.message ?: "Could not save progress"
            }
        }
    }

    fun dismissClueReveal() {
        _activeCheckpoint.value = null
        _revealedClue.value = null
        _screen.value = AppScreen.PLAYER_HUNT
    }

    fun myTeam(): TeamProgress? = resolveTeam(_hunt.value)

    fun nextCheckpointForTeam(): Checkpoint? = computeNextCheckpoint(_hunt.value, resolveTeam(_hunt.value))

    fun isHuntComplete(): Boolean {
        val hunt = _hunt.value ?: return false
        val team = resolveTeam(hunt) ?: return false
        return hunt.checkpoints.isNotEmpty() &&
            hunt.checkpoints.all { team.hasCompleted(it.id) }
    }

    private fun resolveTeam(hunt: HuntSnapshot?): TeamProgress? {
        if (hunt == null) return null
        val teamId = _activeTeamId.value ?: prefs.getString(PREF_PLAYER_TEAM_ID, null)
        if (teamId != null) {
            return hunt.teams.find { it.teamId == teamId }
        }
        return null
    }

    private fun computeNextCheckpoint(hunt: HuntSnapshot?, team: TeamProgress?): Checkpoint? {
        if (hunt == null || hunt.checkpoints.isEmpty()) return null
        val completed = team?.completedCheckpointIds ?: emptyMap()
        return hunt.checkpoints.firstOrNull { checkpoint ->
            !isCheckpointDone(checkpoint.id, completed)
        }
    }

    private fun isCheckpointDone(checkpointId: String, completed: Map<String, Boolean>): Boolean {
        if (checkpointId.isBlank()) return false
        return completed[checkpointId] == true
    }

    private fun refreshPlayerUi() {
        val hunt = _hunt.value
        val team = resolveTeam(hunt)
        val next = computeNextCheckpoint(hunt, team)
        val complete = hunt != null && team != null && hunt.checkpoints.isNotEmpty() &&
            hunt.checkpoints.all { team.hasCompleted(it.id) }
        _playerUi.value = PlayerUiState(
            hunt = hunt,
            team = team,
            nextCheckpoint = next,
            huntComplete = complete,
            teamLinked = team != null && hunt?.teams?.any { it.teamId == team.teamId } == true
        )
    }

    private fun listenToHunt(huntId: String) {
        huntJob?.cancel()
        huntJob = viewModelScope.launch {
            repo.observeHunt(huntId).collect { snap ->
                _hunt.value = snap
                syncPlayerTeam(snap)
                refreshPlayerUi()
            }
        }
    }

    private fun syncPlayerTeam(snap: HuntSnapshot?) {
        if (snap == null) return
        val knownId = _activeTeamId.value ?: prefs.getString(PREF_PLAYER_TEAM_ID, null)
        if (knownId != null) {
            if (snap.teams.any { it.teamId == knownId }) {
                _activeTeamId.value = knownId
            }
            // Keep the chosen team id even if Firebase hasn't echoed it yet — don't swap to another team
            return
        }
        val uid = repo.currentUser?.uid ?: return
        snap.teams.filter { it.playerUid == uid }
            .maxByOrNull { it.joinedAt }
            ?.let { team ->
                _activeTeamId.value = team.teamId
                persistPlayerSession(snap.huntId, team.teamId)
                refreshPlayerUi()
            }
    }

    private fun stopHuntListener() {
        huntJob?.cancel()
        huntJob = null
    }

    private fun persistPlayerSession(huntId: String, teamId: String) {
        prefs.edit()
            .putString(PREF_PLAYER_HUNT_ID, huntId)
            .putString(PREF_PLAYER_TEAM_ID, teamId)
            .apply()
    }

    private fun clearPlayerSession() {
        prefs.edit()
            .remove(PREF_PLAYER_HUNT_ID)
            .remove(PREF_PLAYER_TEAM_ID)
            .apply()
    }

    private fun restorePlayerSessionIfAny() {
        val huntId = prefs.getString(PREF_PLAYER_HUNT_ID, null) ?: return
        val teamId = prefs.getString(PREF_PLAYER_TEAM_ID, null) ?: return
        _activeHuntId.value = huntId
        _activeTeamId.value = teamId
        listenToHunt(huntId)
    }

    companion object {
        private const val PREF_LAST_HUNT_ID = "last_hunt_id"
        private const val PREF_LAST_JOIN_CODE = "last_join_code"
        private const val PREF_LAST_HUNT_NAME = "last_hunt_name"
        private const val PREF_PLAYER_HUNT_ID = "player_hunt_id"
        private const val PREF_PLAYER_TEAM_ID = "player_team_id"
        private const val PREF_HOSTED_HUNT_IDS = "hosted_hunt_ids"
    }
}
