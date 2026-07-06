package com.recital.scavengerhunt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recital.scavengerhunt.ScavengerViewModel
import com.recital.scavengerhunt.ui.theme.HuntColors

@Composable
fun HomeScreen(vm: ScavengerViewModel) {
    val status by vm.status.collectAsState()
    val busy by vm.busy.collectAsState()
    val email by vm.userEmail.collectAsState()
    val canResume = vm.hasSavedPlayerSession()
    var joinCode by remember { mutableStateOf("") }
    var teamName by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "JOIN THE HUNT",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = HuntColors.gold,
            letterSpacing = 1.sp
        )
        Text(
            "Enter your code, pick a team name, and start exploring.",
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodyMedium
        )

        email?.let { signedIn ->
            Text(
                "Signed in as $signedIn",
                color = HuntColors.cyan,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        if (canResume) {
            Button(
                onClick = { vm.resumePlayerHunt() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HuntColors.accent,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Continue your hunt →", fontWeight = FontWeight.Bold)
            }
        }

        OutlinedTextField(
            value = joinCode,
            onValueChange = { joinCode = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(12) },
            label = { Text("Join code") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
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
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = HuntColors.cyan,
                contentColor = MaterialTheme.colorScheme.onSecondary
            )
        ) {
            Text("Start hunting!", fontWeight = FontWeight.Black, fontSize = 18.sp)
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "Hosting a hunt?",
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodySmall
        )
        TextButton(
            onClick = { vm.openMyHunts() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Create or edit hunts →",
                fontWeight = FontWeight.Medium,
                color = HuntColors.accent,
                textAlign = TextAlign.Center
            )
        }

        TextButton(onClick = { vm.signOut() }, modifier = Modifier.padding(top = 4.dp)) {
            Text("Sign out", color = MaterialTheme.colorScheme.secondary)
        }
    }
}
