package com.recital.scavengerhunt.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recital.scavengerhunt.ScavengerViewModel
import com.recital.scavengerhunt.ui.theme.HuntColors

@Composable
fun MyHuntsScreen(vm: ScavengerViewModel) {
    val hostedHunts by vm.hostedHunts.collectAsState()
    val status by vm.status.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.refreshHostedHunts() }

    HuntScaffold(
        title = "Create",
        onBack = { vm.goHome() },
        backLabel = "Home"
    ) { modifier ->
        Column(
            modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { vm.openCreateHunt() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HuntColors.accent,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Create new hunt", fontWeight = FontWeight.Bold)
            }

            Text("Your hunts", fontWeight = FontWeight.SemiBold, color = HuntColors.cyan)
            Text(
                "Edit checkpoints, re-pin GPS, copy join codes, or delete a hunt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )

            if (hostedHunts.isEmpty()) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = HuntColors.cardDeep)
                ) {
                    Text(
                        "No hunts yet — create one above and it will show here.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            } else {
                hostedHunts.forEach { hosted ->
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = HuntColors.card)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(hosted.name, fontWeight = FontWeight.Bold)
                            if (hosted.joinCode.isNotBlank()) {
                                Text(
                                    hosted.joinCode,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HuntColors.gold,
                                    letterSpacing = 2.sp
                                )
                            }
                            Button(
                                onClick = { vm.openEditHunt(hosted.huntId) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = HuntColors.cyan,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                )
                            ) {
                                Text("Edit hunt", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Join code", hosted.joinCode))
                                    Toast.makeText(context, "Copied ${hosted.joinCode}", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = hosted.joinCode.isNotBlank()
                            ) {
                                Text("Copy join code")
                            }
                        }
                    }
                }
            }

            if (status.isNotBlank()) {
                Text(
                    status,
                    color = if (status.contains("deleted", ignoreCase = true)) {
                        HuntColors.success
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text("", modifier = Modifier.padding(bottom = 16.dp))
        }
    }
}
