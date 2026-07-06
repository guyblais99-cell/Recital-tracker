package com.recital.scavengerhunt.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.recital.scavengerhunt.AppScreen
import com.recital.scavengerhunt.ScavengerViewModel

@Composable
fun ScavengerApp(vm: ScavengerViewModel = viewModel()) {
    val screen by vm.screen.collectAsState()
    Box(Modifier.fillMaxSize()) {
        when (screen) {
            AppScreen.AUTH -> AuthScreen(vm)
            AppScreen.HOME -> HomeScreen(vm)
            AppScreen.MY_HUNTS -> MyHuntsScreen(vm)
            AppScreen.CREATE_HUNT -> CreateHuntScreen(vm)
            AppScreen.JOIN_HUNT -> JoinHuntScreen(vm)
            AppScreen.HOST_DASHBOARD -> HostDashboardScreen(vm)
            AppScreen.PLAYER_HUNT -> PlayerHuntScreen(vm)
            AppScreen.ALIGN_CHECKPOINT -> AlignCheckpointScreen(vm)
            AppScreen.CLUE_REVEAL -> ClueRevealScreen(vm)
        }
    }
}
