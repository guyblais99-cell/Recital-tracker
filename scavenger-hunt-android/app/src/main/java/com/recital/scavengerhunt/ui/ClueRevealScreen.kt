package com.recital.scavengerhunt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recital.scavengerhunt.ScavengerViewModel
import com.recital.scavengerhunt.ui.theme.HuntColors
import com.recital.scavengerhunt.util.SpeechHelper

@Composable
fun ClueRevealScreen(vm: ScavengerViewModel) {
    val checkpoint by vm.activeCheckpoint.collectAsState()
    val clue by vm.revealedClue.collectAsState()
    val context = LocalContext.current
    val speech = remember { SpeechHelper(context) }

    DisposableEffect(Unit) {
        onDispose { speech.shutdown() }
    }

    LaunchedEffect(clue) {
        clue?.let { speech.speak(it) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Checkpoint complete! ✓",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = HuntColors.success
        )
        Text(
            checkpoint?.title ?: "",
            color = MaterialTheme.colorScheme.secondary
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("Reward clue", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                Text(
                    clue ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
                clue?.takeIf { it.isNotBlank() }?.let { rewardText ->
                    OutlinedButton(
                        onClick = { speech.speak(rewardText) },
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Read again", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Button(
            onClick = { vm.dismissClueReveal() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue hunt")
        }
    }
}
