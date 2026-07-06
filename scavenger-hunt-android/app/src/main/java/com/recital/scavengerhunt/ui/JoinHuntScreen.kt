package com.recital.scavengerhunt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recital.scavengerhunt.ScavengerViewModel

@Composable
fun JoinHuntScreen(vm: ScavengerViewModel) {
    val status by vm.status.collectAsState()
    val busy by vm.busy.collectAsState()
    var joinCode by remember { mutableStateOf("") }
    var teamName by remember { mutableStateOf("") }

    HuntScaffold(
        title = "Join",
        onBack = { vm.goHome() },
        backLabel = "Home"
    ) { modifier ->
        Column(
            modifier
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Enter the join code from your hunt host.",
                color = MaterialTheme.colorScheme.secondary
            )

            OutlinedTextField(
                value = joinCode,
                onValueChange = { joinCode = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(12) },
                label = { Text("Join code") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = teamName,
                onValueChange = { teamName = it },
                label = { Text("Team / player name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (status.isNotBlank()) {
                Text(status, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { vm.joinHunt(joinCode, teamName) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = com.recital.scavengerhunt.ui.theme.HuntColors.cyan,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text("Join hunt", fontWeight = FontWeight.Bold)
            }
        }
    }
}
