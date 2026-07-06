package com.recital.scavengerhunt.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.recital.scavengerhunt.ScavengerViewModel
import com.recital.scavengerhunt.data.DraftCheckpoint
import com.recital.scavengerhunt.location.GeoUtils
import com.recital.scavengerhunt.location.HOST_PIN_IDEAL_ACCURACY_M
import com.recital.scavengerhunt.location.HOST_PIN_MAX_ACCURACY_M
import com.recital.scavengerhunt.ui.theme.HuntColors
import com.recital.scavengerhunt.util.ImageUtils
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun CreateHuntScreen(vm: ScavengerViewModel) {
    val status by vm.status.collectAsState()
    val busy by vm.busy.collectAsState()
    var huntName by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    val drafts = remember { mutableStateListOf(DraftCheckpoint()) }

    HuntScaffold(
        title = "New hunt",
        onBack = { vm.openMyHunts() },
        backLabel = "Back"
    ) { modifier ->
        Column(
            modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Each stop: direction clue, GPS pin, ghost photo, reward clue.",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedTextField(
                value = huntName,
                onValueChange = { huntName = it },
                label = { Text("Hunt name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = joinCode,
                onValueChange = { joinCode = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(12) },
                label = { Text("Join code (optional)") },
                supportingText = {
                    Text("3–12 letters/numbers, e.g. SMITH or BDAY2024. Leave blank for a random code.")
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            drafts.forEachIndexed { index, _ ->
                key(index) {
                    CheckpointDraftCard(
                        index = index,
                        draft = drafts[index],
                        onUpdate = { transform -> drafts[index] = transform(drafts[index]) },
                        onRemove = { if (drafts.size > 1) drafts.removeAt(index) },
                        canRemove = drafts.size > 1
                    )
                }
            }

            OutlinedButton(
                onClick = { drafts.add(DraftCheckpoint()) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Add checkpoint")
            }

            if (status.isNotBlank()) {
                Text(status, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { vm.publishHunt(huntName, drafts.toList(), joinCode) },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text("Publish hunt")
            }
        }
    }
}

@Composable
private fun CheckpointDraftCard(
    index: Int,
    draft: DraftCheckpoint,
    onUpdate: ((DraftCheckpoint) -> DraftCheckpoint) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var gpsLoading by remember { mutableStateOf(false) }
    var gpsMessage by remember { mutableStateOf<String?>(null) }

    var hasLocation by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasLocation = granted }

    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var launchCameraAfterPermission by remember { mutableStateOf(false) }

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val b64 = ImageUtils.uriToBase64(context, uri)
        if (b64 != null) {
            onUpdate { it.copy(imageBase64 = b64) }
        } else {
            Toast.makeText(context, "Could not load that photo — try another", Toast.LENGTH_SHORT).show()
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingPhotoUri
        if (success && uri != null) {
            val b64 = ImageUtils.uriToBase64(context, uri)
            if (b64 != null) {
                onUpdate { it.copy(imageBase64 = b64) }
            } else {
                Toast.makeText(context, "Could not save photo — try again", Toast.LENGTH_SHORT).show()
            }
        }
        pendingPhotoUri = null
    }

    fun startCameraCapture() {
        val file = File(context.cacheDir, "ghost_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingPhotoUri = uri
        takePicture.launch(uri)
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCamera = granted
        if (granted) launchCameraAfterPermission = true
    }

    LaunchedEffect(launchCameraAfterPermission) {
        if (launchCameraAfterPermission) {
            launchCameraAfterPermission = false
            startCameraCapture()
        }
    }

    fun requestGhostPhotoFromCamera() {
        if (hasCamera) {
            startCameraCapture()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    val preview = remember(draft.imageBase64) { ImageUtils.base64ToBitmap(draft.imageBase64) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Checkpoint ${index + 1}", fontWeight = FontWeight.SemiBold)
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove")
                    }
                }
            }

            OutlinedTextField(
                value = draft.title,
                onValueChange = { onUpdate { d -> d.copy(title = it) } },
                label = { Text("Stop name (private label)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = draft.hintText,
                onValueChange = { onUpdate { d -> d.copy(hintText = it) } },
                label = { Text("Direction clue — where to go") },
                supportingText = { Text("Players see this while searching") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            OutlinedTextField(
                value = draft.clueText,
                onValueChange = { onUpdate { d -> d.copy(clueText = it) } },
                label = { Text("Reward clue — after photo match") },
                supportingText = { Text("Unlocked when they align the ghost image") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            LiveGpsAccuracyCard(enabled = hasLocation)

            OutlinedButton(
                onClick = {
                    if (!hasLocation) {
                        locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        return@OutlinedButton
                    }
                    gpsLoading = true
                    gpsMessage = "Waiting for accurate GPS…"
                    scope.launch {
                        val loc = GeoUtils.capturePinLocation(context)
                        gpsLoading = false
                        if (loc == null) {
                            gpsMessage = "Could not get GPS — try outdoors"
                            return@launch
                        }
                        if (!GeoUtils.isPinAccurateEnough(loc.accuracyM)) {
                            gpsMessage =
                                "GPS still fuzzy (±${loc.accuracyM.roundToInt()} m). " +
                                "Stand still outside and tap again (need ±${HOST_PIN_MAX_ACCURACY_M.toInt()} m or better)."
                            return@launch
                        }
                        gpsMessage = if (!GeoUtils.isPinIdeal(loc.accuracyM)) {
                            "Pin saved with fuzzy GPS (±${loc.accuracyM.roundToInt()} m). " +
                                "Hot/cold may be loose — retry for ±${HOST_PIN_IDEAL_ACCURACY_M.toInt()} m if you can."
                        } else {
                            null
                        }
                        onUpdate { d ->
                            d.copy(
                                latitude = loc.lat,
                                longitude = loc.lon,
                                gpsPinAccuracyM = loc.accuracyM
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (gpsLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                } else {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                }
                Text(
                    when {
                        gpsLoading -> "Locking GPS…"
                        draft.latitude != null -> "GPS set ✓ (tap to update)"
                        else -> "Set GPS here (stand at this stop)"
                    }
                )
            }
            when {
                gpsMessage != null -> Text(
                    gpsMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                draft.latitude != null && draft.longitude != null -> {
                    val acc = draft.gpsPinAccuracyM
                    Text(
                        buildString {
                            append("Pin: ${"%.5f".format(draft.latitude)}, ${"%.5f".format(draft.longitude)}")
                            if (acc != null) append(" (±${acc.roundToInt()} m when set)")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            acc == null -> MaterialTheme.colorScheme.secondary
                            acc > HOST_PIN_MAX_ACCURACY_M -> MaterialTheme.colorScheme.error
                            acc > HOST_PIN_IDEAL_ACCURACY_M -> HuntColors.gold
                            else -> MaterialTheme.colorScheme.secondary
                        }
                    )
                }
                else -> Text(
                    "Stand outdoors at the spot. Ideal ±${HOST_PIN_IDEAL_ACCURACY_M.toInt()} m; up to ±${HOST_PIN_MAX_ACCURACY_M.toInt()} m accepted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (draft.latitude == null) {
                Text(
                    "No GPS — hot/cold won't work for this stop",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text(
                "Ghost photo — take it here after setting GPS (same spot & angle players will use).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { requestGhostPhotoFromCamera() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Text(" Take photo", modifier = Modifier.padding(start = 4.dp))
                }
                OutlinedButton(
                    onClick = { galleryPicker.launch("image/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Text(" Gallery", modifier = Modifier.padding(start = 4.dp))
                }
            }
            preview?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}
