package dev.catsradar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val BaseTypography = Typography()

// Heavier than Material's defaults, so numbers and titles carry the screen without a custom font.
private val CatsRadarTypography = BaseTypography.copy(
    displayLarge = BaseTypography.displayLarge.copy(fontWeight = FontWeight.Bold),
    displayMedium = BaseTypography.displayMedium.copy(fontWeight = FontWeight.Bold),
    displaySmall = BaseTypography.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineLarge = BaseTypography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val CatsRadarShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun CatsRadarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) CatsRadarDarkColors else CatsRadarLightColors,
        shapes = CatsRadarShapes,
        typography = CatsRadarTypography,
        content = content,
    )
}
