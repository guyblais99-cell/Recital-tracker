package com.recital.scavengerhunt.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.recital.scavengerhunt.ScavengerViewModel
import com.recital.scavengerhunt.ui.theme.HuntColors
import com.recital.scavengerhunt.util.SpeechHelper

@Composable
fun PlayerHuntScreen(vm: ScavengerViewModel) {
    val playerUi by vm.playerUi.collectAsState()
    val snap = playerUi.hunt
    val team = playerUi.team
    val next = playerUi.nextCheckpoint
    val complete = playerUi.huntComplete

    LaunchedEffect(Unit) {
        vm.ensurePlayerReady()
    }

    val context = LocalContext.current
    val speech = remember { SpeechHelper(context) }
    DisposableEffect(Unit) {
        onDispose { speech.shutdown() }
    }

    HuntScaffold(
        title = if (complete) "Victory!" else "Your hunt",
        onBack = { vm.leavePlayerHunt() },
        backLabel = "Leave"
    ) { modifier ->
        Column(modifier.fillMaxSize()) {
            if (!complete) {
                GpsStrengthBar()
            }
            if (!complete && snap != null) {
                HuntNameBanner(
                    name = snap.meta.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            snap?.let { hunt ->
                if (complete) {
                    HuntCompleteCelebration(hunt = hunt, myTeam = team)
                } else {
                    val total = hunt.checkpoints.size.coerceAtLeast(1)
                    val done = team?.completedCount() ?: 0
                    LinearProgressIndicator(
                        progress = { done.toFloat() / total },
                        modifier = Modifier.fillMaxWidth(),
                        color = HuntColors.success,
                        trackColor = HuntColors.card
                    )
                    Text("$done of ${hunt.checkpoints.size} checkpoints complete", fontWeight = FontWeight.Medium)

                    when {
                        next != null -> {
                            val hasGps = next.latitude != null && next.longitude != null
                            var canAlign by remember(next.id) { mutableStateOf(!hasGps) }

                            Column(
                                Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(
                                    Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        "Direction clue",
                                        fontWeight = FontWeight.Bold,
                                        color = HuntColors.gold,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        next.hintText,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (next.hintText.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = { speech.speak(next.hintText) },
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            Icon(Icons.Default.VolumeUp, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Read clue aloud", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                HuntNavigatorCard(
                                    checkpoint = next,
                                    onArrivedChange = { arrived -> canAlign = arrived }
                                )

                                AnimatedVisibility(
                                    visible = canAlign,
                                    enter = fadeIn() + scaleIn(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                ) {
                                    Button(
                                        onClick = { vm.startCheckpoint(next) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(64.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = HuntColors.success,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        Text(
                                            "You're here — align photo!",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 20.sp
                                        )
                                    }
                                }

                            }
                        }
                        hunt.checkpoints.isEmpty() -> {
                            Text(
                                "This hunt has no checkpoints yet. Ask the host to publish stops.",
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        else -> {
                            Text(
                                "Could not find your next stop.",
                                color = MaterialTheme.colorScheme.secondary
                            )
                            OutlinedButton(
                                onClick = { vm.ensurePlayerReady() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }

                if (!complete) {
                    Text("Checkpoints", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                } else {
                    Text("Your route", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                }

                hunt.checkpoints.forEachIndexed { index, cp ->
                    val isDone = team?.hasCompleted(cp.id) == true
                    val isCurrent = !complete && !isDone && cp.id == next?.id
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                complete && isDone -> HuntColors.card
                                isCurrent -> HuntColors.card
                                else -> HuntColors.cardDeep
                            }
                        )
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = when {
                                    isDone -> HuntColors.success
                                    isCurrent -> HuntColors.accent
                                    else -> MaterialTheme.colorScheme.secondary
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Text("Stop ${index + 1}: ${cp.title}", fontWeight = FontWeight.Medium)
                                when {
                                    isDone -> Text(
                                        "Complete ✓",
                                        color = HuntColors.success,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    isCurrent -> Text(
                                        "Current stop",
                                        color = HuntColors.accent,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    else -> Text(
                                        "Locked",
                                        color = MaterialTheme.colorScheme.secondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            } ?: Text("Loading hunt…")

            Text("", modifier = Modifier.padding(bottom = 16.dp))
            }
        }
    }
}

@Composable
private fun HuntNameBanner(name: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = HuntColors.cardDeep),
        border = BorderStroke(2.dp, HuntColors.gold)
    ) {
        Text(
            text = name,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 16.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = HuntColors.gold
        )
    }
}
