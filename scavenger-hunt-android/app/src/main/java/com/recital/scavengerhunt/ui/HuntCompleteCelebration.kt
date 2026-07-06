package com.recital.scavengerhunt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recital.scavengerhunt.data.HuntSnapshot
import com.recital.scavengerhunt.data.TeamProgress
import com.recital.scavengerhunt.data.elapsedFinishMs
import com.recital.scavengerhunt.data.formatHuntDuration
import com.recital.scavengerhunt.data.hasFinishedHunt
import com.recital.scavengerhunt.ui.theme.HuntColors

@Composable
fun HuntCompleteCelebration(
    hunt: HuntSnapshot,
    myTeam: TeamProgress?,
    modifier: Modifier = Modifier
) {
    val total = hunt.checkpoints.size
    val myTime = myTeam?.elapsedFinishMs()
    val finishedTeams = hunt.teams
        .filter { it.hasFinishedHunt(total) }
        .sortedBy { it.elapsedFinishMs() ?: Long.MAX_VALUE }

    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("🎉", fontSize = 56.sp)
        Text(
            "HUNT\nCOMPLETE!",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = HuntColors.gold,
            textAlign = TextAlign.Center,
            lineHeight = 44.sp,
            letterSpacing = 2.sp
        )
        Text(
            hunt.meta.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = HuntColors.cyan,
            textAlign = TextAlign.Center
        )

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = HuntColors.card)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Your stats", fontWeight = FontWeight.Bold, color = HuntColors.accent)
                Text(
                    myTeam?.teamName ?: "Your team",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "$total / $total checkpoints",
                    color = HuntColors.success,
                    fontWeight = FontWeight.Bold
                )
                myTime?.let {
                    Text(
                        "Finish time: ${formatHuntDuration(it)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = HuntColors.gold
                    )
                }
            }
        }

        if (finishedTeams.size > 1) {
            Text(
                "Leaderboard",
                fontWeight = FontWeight.Bold,
                color = HuntColors.cyan,
                modifier = Modifier.fillMaxWidth()
            )
            finishedTeams.forEachIndexed { rank, team ->
                val time = team.elapsedFinishMs()
                val isMe = team.teamId == myTeam?.teamId
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isMe) HuntColors.card else HuntColors.cardDeep
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            buildString {
                                append("#${rank + 1}  ")
                                append(team.teamName)
                                if (isMe) append("  (you)")
                            },
                            fontWeight = if (isMe) FontWeight.Bold else FontWeight.Medium,
                            color = if (rank == 0) HuntColors.gold else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            time?.let { formatHuntDuration(it) } ?: "—",
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        } else if (finishedTeams.size == 1) {
            Text(
                "You're the first team to finish — nice work!",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
