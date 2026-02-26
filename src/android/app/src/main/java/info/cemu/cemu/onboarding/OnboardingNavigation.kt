package info.cemu.cemu.onboarding

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
object StorageOnboardingRoute

fun NavGraphBuilder.onboardingNavigation(onComplete: () -> Unit) {
    composable<StorageOnboardingRoute> {
        StorageOnboardingScreen(onComplete = onComplete)
    }
}
