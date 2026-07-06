package com.recital.scavengerhunt.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.recital.scavengerhunt.data.Checkpoint
import com.recital.scavengerhunt.util.ImageUtils
import java.io.File

@Composable
fun CheckpointEditDialog(
    title: String,
    initial: Checkpoint,
    onDismiss: () -> Unit,
    onSave: (Checkpoint) -> Unit
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    val context = LocalContext.current

    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var launchCameraAfterPermission by remember { mutableStateOf(false) }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCamera = granted
        if (granted) launchCameraAfterPermission = true
    }

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val b64 = ImageUtils.uriToBase64(context, uri)
        if (b64 != null) {
            draft = draft.copy(imageBase64 = b64)
        } else {
            Toast.makeText(context, "Could not load photo", Toast.LENGTH_SHORT).show()
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingPhotoUri
        if (success && uri != null) {
            val b64 = ImageUtils.uriToBase64(context, uri)
            if (b64 != null) {
                draft = draft.copy(imageBase64 = b64)
            } else {
                Toast.makeText(context, "Could not save photo", Toast.LENGTH_SHORT).show()
            }
        }
        pendingPhotoUri = null
    }

    fun startCamera() {
        val file = File(context.cacheDir, "ghost_edit_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingPhotoUri = uri
        takePicture.launch(uri)
    }

    LaunchedEffect(launchCameraAfterPermission) {
        if (launchCameraAfterPermission) {
            launchCameraAfterPermission = false
            startCamera()
        }
    }

    val preview = remember(draft.imageBase64) { ImageUtils.base64ToBitmap(draft.imageBase64) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { draft = draft.copy(title = it) },
                    label = { Text("Stop name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = draft.hintText,
                    onValueChange = { draft = draft.copy(hintText = it) },
                    label = { Text("Direction clue") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                OutlinedTextField(
                    value = draft.clueText,
                    onValueChange = { draft = draft.copy(clueText = it) },
                    label = { Text("Reward clue") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Text(
                    "Ghost photo",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (hasCamera) startCamera()
                            else cameraPermission.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Text(" Camera", modifier = Modifier)
                    }
                    OutlinedButton(
                        onClick = { galleryPicker.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Text(" Gallery", modifier = Modifier)
                    }
                }
                preview?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        draft.title.isBlank() -> Toast.makeText(context, "Add a stop name", Toast.LENGTH_SHORT).show()
                        draft.hintText.isBlank() -> Toast.makeText(context, "Add a direction clue", Toast.LENGTH_SHORT).show()
                        draft.clueText.isBlank() -> Toast.makeText(context, "Add a reward clue", Toast.LENGTH_SHORT).show()
                        draft.imageBase64.isBlank() -> Toast.makeText(context, "Add a ghost photo", Toast.LENGTH_SHORT).show()
                        else -> onSave(draft)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
