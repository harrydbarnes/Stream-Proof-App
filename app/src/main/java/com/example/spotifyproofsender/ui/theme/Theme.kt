package com.example.spotifyproofsender.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColours = lightColorScheme(
    primary = Color(0xFF006C4C),
    secondary = Color(0xFF4F6358),
    tertiary = Color(0xFF3F6374),
)

private val DarkColours = darkColorScheme(
    primary = Color(0xFF80DBB2),
    secondary = Color(0xFFB3CCBC),
    tertiary = Color(0xFFA4CDDF),
)

@Composable
fun StreamProofAppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val colourScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        darkTheme -> DarkColours
        else -> LightColours
    }

    MaterialTheme(
        colorScheme = colourScheme,
        typography = Typography(),
        content = content,
    )
}
