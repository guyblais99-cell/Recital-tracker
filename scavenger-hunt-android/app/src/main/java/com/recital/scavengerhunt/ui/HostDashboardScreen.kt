package com.recital.scavengerhunt.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.recital.scavengerhunt.ScavengerViewModel
import com.recital.scavengerhunt.data.Checkpoint
import com.recital.scavengerhunt.location.HOST_PIN_IDEAL_ACCURACY_M
import com.recital.scavengerhunt.location.HOST_PIN_MAX_ACCURACY_M
import com.recital.scavengerhunt.location.GeoUtils
import kotlinx.coroutines.launch
import com.recital.scavengerhunt.ui.theme.HuntColors
import kotlin.math.roundToInt

private fun nextCheckpointId(checkpoints: List<Checkpoint>): String {
    val maxNum = checkpoints.mapNotNull { cp ->
        Regex("cp(\\d+)", RegexOption.IGNORE_CASE).find(cp.id)?.groupValues?.get(1)?.toIntOrNull()
    }.maxOrNull() ?: checkpoints.size
    return "cp${maxNum + 1}"
}

private fun copyJoinCode(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Join code", code))
    Toast.makeText(context, "Copied $code", Toast.LENGTH_SHORT).show()
}

@Composable
fun HostDashboardScreen(vm: ScavengerViewModel) {
    val hunt by vm.hunt.collectAsState()
    val status by vm.status.collectAsState()
    val busy by vm.busy.collectAsState()
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var editingCheckpoint by remember { mutableStateOf<Checkpoint?>(null) }
    var addingCheckpoint by remember { mutableStateOf(false) }

    LaunchedEffect(hunt) {
        hunt?.let { vm.persistHostedHunt(it) }
    }

    HuntScaffold(
        title = "Edit hunt",
        onBack = { vm.openMyHunts() },
        backLabel = "Back"
    ) { modifier ->
        Column(
            modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            hunt?.let { snap ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = HuntColors.card)
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            snap.meta.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Share this code with players",
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            snap.meta.joinCode,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = HuntColors.gold,
                            letterSpacing = 4.sp
                        )
                        Button(
                            onClick = { copyJoinCode(context, snap.meta.joinCode) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Copy join code")
                        }
                        Text(
                            "${snap.checkpoints.size} checkpoints · ${snap.teams.size} team(s) joined",
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Text("Checkpoints", fontWeight = FontWeight.SemiBold)
                Text(
                    "Wrong hot/cold? Stand at the exact spot and tap Re-set GPS pin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                snap.checkpoints.forEachIndexed { i, cp ->
                    HostCheckpointCard(
                        index = i,
                        checkpoint = cp,
                        vm = vm,
                        onEdit = { editingCheckpoint = cp }
                    )
                }

                OutlinedButton(
                    onClick = { addingCheckpoint = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(" Add checkpoint", modifier = Modifier.padding(start = 4.dp))
                }

                if (status.isNotBlank()) {
                    Text(status, color = HuntColors.cyan, style = MaterialTheme.typography.bodySmall)
                }

                Text("Team progress", fontWeight = FontWeight.SemiBold)
                if (snap.teams.isEmpty()) {
                    Text("Waiting for players to join…", color = MaterialTheme.colorScheme.secondary)
                } else {
                    snap.teams.forEach { team ->
                        val total = snap.checkpoints.size.coerceAtLeast(1)
                        val done = team.completedCount()
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(team.teamName, fontWeight = FontWeight.Medium)
                                Text("$done / $total checkpoints", color = MaterialTheme.colorScheme.secondary)
                                LinearProgressIndicator(
                                    progress = { done.toFloat() / total },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }
            } ?: Text("Loading hunt…")

            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                enabled = !busy && hunt != null,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete hunt permanently")
            }

            Text("", modifier = Modifier.padding(bottom = 16.dp))
        }
    }

    editingCheckpoint?.let { cp ->
        CheckpointEditDialog(
            title = "Edit checkpoint",
            initial = cp,
            onDismiss = { editingCheckpoint = null },
            onSave = { updated ->
                vm.saveCheckpointDetails(updated)
                editingCheckpoint = null
            }
        )
    }

    if (addingCheckpoint) {
        hunt?.let { snap ->
            CheckpointEditDialog(
                title = "New checkpoint",
                initial = Checkpoint(
                    id = nextCheckpointId(snap.checkpoints),
                    order = snap.checkpoints.size
                ),
                onDismiss = { addingCheckpoint = false },
                onSave = { created ->
                    vm.addCheckpointToHunt(created)
                    addingCheckpoint = false
                }
            )
        } ?: run { addingCheckpoint = false }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this hunt?") },
            text = {
                Text(
                    "This permanently removes the hunt, join code, checkpoints, and all team progress. This cannot be undone.",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        vm.deleteActiveHunt()
                    }
                ) {
                    Text("Delete forever", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HostCheckpointCard(
    index: Int,
    checkpoint: Checkpoint,
    vm: ScavengerViewModel,
    onEdit: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var gpsLoading by remember { mutableStateOf(false) }
    var gpsError by remember { mutableStateOf<String?>(null) }

    var hasLocation by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasLocation = granted }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HuntColors.cardDeep)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${index + 1}. ${checkpoint.title}", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit checkpoint", tint = HuntColors.cyan)
                }
            }
            when {
                checkpoint.latitude == null || checkpoint.longitude == null -> {
                    Text("No GPS pin", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                checkpoint.gpsPinAccuracyM == null -> {
                    Text(
                        "Pin set (accuracy unknown — may be loose from older app)",
                        color = HuntColors.gold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                checkpoint.gpsPinAccuracyM!! > HOST_PIN_MAX_ACCURACY_M -> {
                    Text(
                        "Pin may be off (was ±${checkpoint.gpsPinAccuracyM!!.roundToInt()} m when set)",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                checkpoint.gpsPinAccuracyM!! > HOST_PIN_IDEAL_ACCURACY_M -> {
                    Text(
                        "Pin ±${checkpoint.gpsPinAccuracyM!!.roundToInt()} m — fuzzy but usable",
                        color = HuntColors.gold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                else -> {
                    Text(
                        "Pin ±${checkpoint.gpsPinAccuracyM!!.roundToInt()} m when set",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            LiveGpsAccuracyCard(enabled = hasLocation)

            OutlinedButton(
                onClick = {
                    if (!hasLocation) {
                        locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        return@OutlinedButton
                    }
                    gpsLoading = true
                    gpsError = null
                    scope.launch {
                        val loc = GeoUtils.capturePinLocation(context)
                        gpsLoading = false
                        if (loc == null) {
                            gpsError = "Could not get GPS"
                            return@launch
                        }
                        if (!GeoUtils.isPinAccurateEnough(loc.accuracyM)) {
                            gpsError =
                                "GPS fuzzy (±${loc.accuracyM.roundToInt()} m). Stand still outside and retry (need ±${HOST_PIN_MAX_ACCURACY_M.toInt()} m or better)."
                            return@launch
                        }
                        vm.updateCheckpointPin(checkpoint.id, loc.lat, loc.lon, loc.accuracyM)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !gpsLoading
            ) {
                if (gpsLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                } else {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                }
                Text("Re-set GPS pin here")
            }
            gpsError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
