package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark
  )

private val LightColorScheme =
  lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight
  )

private fun getDarkColorSchemeForAccent(accent: String): ColorScheme {
  return when (accent) {
    "NeonBlue" -> darkColorScheme(
      primary = Color(0xFF90CAF9),
      onPrimary = Color(0xFF0D47A1),
      primaryContainer = Color(0xFF1E88E5),
      onPrimaryContainer = Color(0xFFE3F2FD),
      secondary = Color(0xFFB0BEC5),
      onSecondary = Color(0xFF37474F),
      background = Color(0xFF121212),
      onBackground = Color(0xFFE0E0E0),
      surface = Color(0xFF191C20),
      onSurface = Color(0xFFE0E0E0),
      surfaceVariant = Color(0xFF2B2D31),
      onSurfaceVariant = Color(0xFFC0C0C0)
    )
    "Emerald" -> darkColorScheme(
      primary = Color(0xFF81C784),
      onPrimary = Color(0xFF1B5E20),
      primaryContainer = Color(0xFF43A047),
      onPrimaryContainer = Color(0xFFE8F5E9),
      secondary = Color(0xFFB0BEC5),
      onSecondary = Color(0xFF37474F),
      background = Color(0xFF121212),
      onBackground = Color(0xFFE0E0E0),
      surface = Color(0xFF1A1D1A),
      onSurface = Color(0xFFE0E0E0),
      surfaceVariant = Color(0xFF2C2F2C),
      onSurfaceVariant = Color(0xFFC0C0C0)
    )
    "Sunset" -> darkColorScheme(
      primary = Color(0xFFFFB866),
      onPrimary = Color(0xFF5D3000),
      primaryContainer = Color(0xFFC66900),
      onPrimaryContainer = Color(0xFFFFE0B2),
      secondary = Color(0xFFB0BEC5),
      onSecondary = Color(0xFF37474F),
      background = Color(0xFF121212),
      onBackground = Color(0xFFE0E0E0),
      surface = Color(0xFF1D1C16),
      onSurface = Color(0xFFE0E0E0),
      surfaceVariant = Color(0xFF2E2D27),
      onSurfaceVariant = Color(0xFFC0C0C0)
    )
    "Rose" -> darkColorScheme(
      primary = Color(0xFFFF8A80),
      onPrimary = Color(0xFF4A0007),
      primaryContainer = Color(0xFFC62828),
      onPrimaryContainer = Color(0xFFFFEBEE),
      secondary = Color(0xFFB0BEC5),
      onSecondary = Color(0xFF37474F),
      background = Color(0xFF121212),
      onBackground = Color(0xFFE0E0E0),
      surface = Color(0xFF1E1A1A),
      onSurface = Color(0xFFE0E0E0),
      surfaceVariant = Color(0xFF302B2B),
      onSurfaceVariant = Color(0xFFC0C0C0)
    )
    else -> DarkColorScheme // Purple
  }
}

private fun getLightColorSchemeForAccent(accent: String): ColorScheme {
  return when (accent) {
    "NeonBlue" -> lightColorScheme(
      primary = Color(0xFF1E88E5),
      onPrimary = Color(0xFFFFFFFF),
      primaryContainer = Color(0xFFBBDEFB),
      onPrimaryContainer = Color(0xFF0D47A1),
      secondary = Color(0xFF546E7A),
      onSecondary = Color(0xFFFFFFFF),
      background = Color(0xFFFAFAFA),
      onBackground = Color(0xFF212121),
      surface = Color(0xFFFAFAFA),
      onSurface = Color(0xFF212121),
      surfaceVariant = Color(0xFFECEFF1),
      onSurfaceVariant = Color(0xFF37474F)
    )
    "Emerald" -> lightColorScheme(
      primary = Color(0xFF43A047),
      onPrimary = Color(0xFFFFFFFF),
      primaryContainer = Color(0xFFC8E6C9),
      onPrimaryContainer = Color(0xFF1B5E20),
      secondary = Color(0xFF558B2F),
      onSecondary = Color(0xFFFFFFFF),
      background = Color(0xFFFAFAFA),
      onBackground = Color(0xFF212121),
      surface = Color(0xFFFAFAFA),
      onSurface = Color(0xFF212121),
      surfaceVariant = Color(0xFFF1F8E9),
      onSurfaceVariant = Color(0xFF33691E)
    )
    "Sunset" -> lightColorScheme(
      primary = Color(0xFFF57C00),
      onPrimary = Color(0xFFFFFFFF),
      primaryContainer = Color(0xFFFFE0B2),
      onPrimaryContainer = Color(0xFFE65100),
      secondary = Color(0xFF8D6E63),
      onSecondary = Color(0xFFFFFFFF),
      background = Color(0xFFFAFAFA),
      onBackground = Color(0xFF212121),
      surface = Color(0xFFFAFAFA),
      onSurface = Color(0xFF212121),
      surfaceVariant = Color(0xFFFFF3E0),
      onSurfaceVariant = Color(0xFF5D4037)
    )
    "Rose" -> lightColorScheme(
      primary = Color(0xFFD32F2F),
      onPrimary = Color(0xFFFFFFFF),
      primaryContainer = Color(0xFFFFCDD2),
      onPrimaryContainer = Color(0xFFB71C1C),
      secondary = Color(0xFF757575),
      onSecondary = Color(0xFFFFFFFF),
      background = Color(0xFFFAFAFA),
      onBackground = Color(0xFF212121),
      surface = Color(0xFFFAFAFA),
      onSurface = Color(0xFF212121),
      surfaceVariant = Color(0xFFF5F5F5),
      onSurfaceVariant = Color(0xFF212121)
    )
    else -> LightColorScheme // Purple
  }
}

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false,
  accent: String = "Purple",
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> getDarkColorSchemeForAccent(accent)
      else -> getLightColorSchemeForAccent(accent)
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
