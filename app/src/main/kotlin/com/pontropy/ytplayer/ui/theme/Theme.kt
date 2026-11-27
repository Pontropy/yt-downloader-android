package com.pontropy.ytplayer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.pontropy.ytplayer.data.AppThemeMode

@Immutable
data class ExtraAppColors(
    val success: Color,
    val warning: Color,
    val isNothingOs: Boolean = false,
)

val LocalExtraAppColors = staticCompositionLocalOf {
    ExtraAppColors(
        success = Color(0xFF9ECE6A),
        warning = Color(0xFFE0AF68),
    )
}

private val DefaultShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

private val NothingShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun YTPlayerTheme(
    themeMode: AppThemeMode = AppThemeMode.TOKYO_NIGHT,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when (themeMode) {
        AppThemeMode.TOKYO_NIGHT -> TokyoNightColorScheme
        AppThemeMode.NOTHING_OS -> NothingOsColorScheme
        AppThemeMode.CRIMSON_BLACK -> CrimsonBlackColorScheme
        AppThemeMode.MATERIAL_3_DARK -> Material3DarkColorScheme
        AppThemeMode.MATERIAL_3_DYNAMIC -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(context)
            } else {
                Material3DarkColorScheme
            }
        }
        AppThemeMode.MATERIAL_3_LIGHT -> Material3LightColorScheme
        AppThemeMode.MATRIX -> MatrixColorScheme
    }

    val extraColors = when (themeMode) {
        AppThemeMode.TOKYO_NIGHT -> ExtraAppColors(
            success = Color(0xFF9ECE6A),
            warning = Color(0xFFE0AF68),
        )
        AppThemeMode.NOTHING_OS -> ExtraAppColors(
            success = Color(0xFF22C55E),
            warning = Color(0xFFF59E0B),
            isNothingOs = true,
        )
        AppThemeMode.CRIMSON_BLACK -> ExtraAppColors(
            success = Color(0xFF4ADE80),
            warning = Color(0xFFFBBF24),
        )
        AppThemeMode.MATERIAL_3_DARK, AppThemeMode.MATERIAL_3_DYNAMIC -> ExtraAppColors(
            success = Color(0xFFA8DAB5),
            warning = Color(0xFFFDE293),
        )
        AppThemeMode.MATERIAL_3_LIGHT -> ExtraAppColors(
            success = Color(0xFF146C2E),
            warning = Color(0xFF9A5B00),
        )
        AppThemeMode.MATRIX -> ExtraAppColors(
            success = Color(0xFF00E676),
            warning = Color(0xFFFFD54F),
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !themeMode.isDark
            insetsController.isAppearanceLightNavigationBars = !themeMode.isDark
        }
    }

    CompositionLocalProvider(LocalExtraAppColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = if (themeMode == AppThemeMode.NOTHING_OS) NothingShapes else DefaultShapes,
            content = content,
        )
    }
}
