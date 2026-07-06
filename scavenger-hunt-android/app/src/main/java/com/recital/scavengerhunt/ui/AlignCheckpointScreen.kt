package com.recital.scavengerhunt.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.recital.scavengerhunt.ScavengerViewModel
import com.recital.scavengerhunt.camera.ImageAlignMatcher
import com.recital.scavengerhunt.ui.theme.HuntColors
import com.recital.scavengerhunt.util.ImageUtils
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@Composable
fun AlignCheckpointScreen(vm: ScavengerViewModel) {
    val checkpoint by vm.activeCheckpoint.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasCamera = it }

    LaunchedEffect(Unit) {
        if (!hasCamera) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val reference = remember(checkpoint?.imageBase64) {
        checkpoint?.imageBase64?.let { ImageUtils.base64ToBitmap(it) }
    }
    var rawScore by remember { mutableFloatStateOf(0f) }
    var matchScore by remember { mutableFloatStateOf(0f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var holdStart by remember { mutableLongStateOf(0L) }
    var unlocked by remember { mutableStateOf(false) }

    LaunchedEffect(rawScore) {
        matchScore = matchScore * 0.5f + rawScore * 0.5f
    }

    LaunchedEffect(matchScore) {
        if (unlocked) return@LaunchedEffect
        if (matchScore >= ImageAlignMatcher.MATCH_THRESHOLD) {
            if (holdStart == 0L) holdStart = System.currentTimeMillis()
            if (System.currentTimeMillis() - holdStart >= ImageAlignMatcher.HOLD_MS) {
                unlocked = true
                vm.onCheckpointAligned()
            }
        } else {
            holdStart = 0L
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (hasCamera && reference != null && checkpoint != null) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { previewView ->
                            val executor = Executors.newSingleThreadExecutor()
                            val main = ContextCompat.getMainExecutor(ctx)
                            ProcessCameraProvider.getInstance(ctx).addListener({
                                val provider = ProcessCameraProvider.getInstance(ctx).get()
                                val rotation = previewView.display.rotation
                                val preview = Preview.Builder()
                                    .setTargetRotation(rotation)
                                    .build()
                                    .also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                val refBmp = reference
                                val analysis = ImageAnalysis.Builder()
                                    .setTargetRotation(rotation)
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also { ia ->
                                        ia.setAnalyzer(executor) { image ->
                                            try {
                                                val result = ImageAlignMatcher.match(image, refBmp)
                                                main.execute {
                                                    rawScore = result.score
                                                    panX = panX * 0.55f + result.panX * 0.45f
                                                    panY = panY * 0.55f + result.panY * 0.45f
                                                }
                                            } finally {
                                                image.close()
                                            }
                                        }
                                    }
                                try {
                                    provider.unbindAll()
                                    provider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        analysis
                                    )
                                } catch (_: Exception) {
                                }
                            }, main)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Full-screen ghost — same crop as the matcher (not a tiny inset box)
                Image(
                    bitmap = reference.asImageBitmap(),
                    contentDescription = "Ghost overlay",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.42f
                )

                AlignGuideOverlay(
                    panX = panX,
                    panY = panY,
                    matchScore = matchScore
                )
            } else if (!hasCamera) {
                Text("Camera permission required", Modifier.align(Alignment.Center))
            } else {
                Text("Missing reference photo", Modifier.align(Alignment.Center))
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .background(HuntColors.cardDeep)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = { vm.resumePlayerHunt() }) { Text("← Back") }
            Text(
                checkpoint?.title ?: "",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            val pct = (matchScore * 100).roundToInt()
            val need = (ImageAlignMatcher.MATCH_THRESHOLD * 100).roundToInt()
            val moveHint = ImageAlignMatcher.alignmentHintLabel(panX, panY, matchScore)
            Text(
                when {
                    unlocked -> "Matched! Unlocking clue…"
                    pct >= need -> "Hold steady… ${((System.currentTimeMillis() - holdStart).coerceAtMost(ImageAlignMatcher.HOLD_MS) * 100 / ImageAlignMatcher.HOLD_MS)}%"
                    moveHint != null -> moveHint
                    pct >= need - 10 -> "Almost there — $pct%"
                    else -> "Match the ghost to the scene — $pct%"
                },
                color = HuntColors.success,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            LinearProgressIndicator(
                progress = { matchScore.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = HuntColors.success,
                trackColor = HuntColors.card
            )
            if (unlocked) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = HuntColors.success
                )
            }
            if (!unlocked) {
                TextButton(
                    onClick = {
                        unlocked = true
                        vm.onCheckpointAligned()
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        "I have it matched",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}
