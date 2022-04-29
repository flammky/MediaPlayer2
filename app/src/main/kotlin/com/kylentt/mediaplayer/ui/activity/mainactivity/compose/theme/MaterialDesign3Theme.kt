package com.kylentt.mediaplayer.ui.activity.mainactivity.compose.theme

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.google.android.material.color.ColorRoles
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.kylentt.mediaplayer.helper.VersionHelper
import com.kylentt.mediaplayer.ui.activity.mainactivity.compose.theme.helper.ColorHelper
import timber.log.Timber

private val LightThemeColors = lightColorScheme(

    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    errorContainer = md_theme_light_errorContainer,
    onError = md_theme_light_onError,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    inverseOnSurface = md_theme_light_inverseOnSurface,
    inverseSurface = md_theme_light_inverseSurface,
    inversePrimary = md_theme_light_inversePrimary,
)
private val DarkThemeColors = darkColorScheme(

    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    errorContainer = md_theme_dark_errorContainer,
    onError = md_theme_dark_onError,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inverseSurface = md_theme_dark_inverseSurface,
    inversePrimary = md_theme_dark_inversePrimary,
)


@Composable
fun MaterialDesign3Theme(
    dynamic: Boolean = VersionHelper.hasSnowCone(),
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {

    val systemUiController = rememberSystemUiController()

    Timber.d("hasSnowCone? ${VersionHelper.hasSnowCone()}")

    if (dynamic && DynamicColors.isDynamicColorAvailable()) {
       return HarmonizedTheme(dynamic,useDarkTheme) {
           with(systemUiController) {
               setStatusBarColor(ColorHelper.getTonedSurface().copy(alpha = 0.5f))
               setNavigationBarColor(Color.Transparent)
               isNavigationBarContrastEnforced = false
               statusBarDarkContentEnabled = !useDarkTheme
               navigationBarDarkContentEnabled = !useDarkTheme
           }
           content()
       }
    }

    val colors = if (DynamicColors.isDynamicColorAvailable()) {
        if (useDarkTheme) {
            dynamicDarkColorScheme(LocalContext.current)
        } else {
            dynamicLightColorScheme(LocalContext.current)
        }
    } else {
        if (useDarkTheme) {
            DarkThemeColors
        } else {
            LightThemeColors
        }
    }



    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
    ) {
        with(systemUiController) {
            setStatusBarColor(ColorHelper.getTonedSurface().copy(alpha = 0.5f))
            setNavigationBarColor(Color.Transparent)
            isNavigationBarContrastEnforced = false
            statusBarDarkContentEnabled = !useDarkTheme
            navigationBarDarkContentEnabled = !useDarkTheme
        }
        content()
    }
}

data class CustomColor(val name:String, val color: Color, val harmonized: Boolean, var roles: ColorRoles)
data class ExtendedColors(val colors: Array<CustomColor>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ExtendedColors
        if (!colors.contentEquals(other.colors)) return false
        return true
    }

    override fun hashCode(): Int {
        return colors.contentHashCode()
    }
}


fun setupErrorColors(colorScheme: ColorScheme, isLight: Boolean): ColorScheme {
    val harmonizedError =
        MaterialColors.harmonize(error.toArgb(), colorScheme.primary.toArgb())
    val roles = MaterialColors.getColorRoles(harmonizedError, isLight)
    //returns a colorScheme with newly harmonized error colors
    return colorScheme.copy(
        error = Color(roles.accent),
        onError = Color(roles.onAccent),
        errorContainer = Color(roles.accentContainer),
        onErrorContainer = Color(roles.onAccentContainer)
    )
}
val initializeExtended = ExtendedColors(arrayOf())

fun setupCustomColors(
    colorScheme: ColorScheme,
    isLight: Boolean
): ExtendedColors {
    initializeExtended.colors.forEach {customColor ->
        // Retrieve record
        val shouldHarmonize = customColor.harmonized
        // Blend or not
        if (shouldHarmonize) {
            val blendedColor =
                MaterialColors.harmonize(customColor.color.toArgb(), colorScheme.primary.toArgb())
            customColor.roles = MaterialColors.getColorRoles(blendedColor, isLight)
        } else {
            customColor.roles = MaterialColors.getColorRoles(customColor.color.toArgb(), isLight)
        }
    }
    return initializeExtended
}

val LocalExtendedColors = staticCompositionLocalOf {
    initializeExtended
}


@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun HarmonizedTheme(
    isDynamic: Boolean = true,
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable() () -> Unit
) {

    val colors = if (isDynamic) {
        val context = LocalContext.current
        if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (useDarkTheme) DarkThemeColors else LightThemeColors
    }
    val colorsWithHarmonizedError = if (isDynamic) setupErrorColors(colors, !useDarkTheme) else colors
    val extendedColors = setupCustomColors(colors, !useDarkTheme)
    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorsWithHarmonizedError,
            typography = AppTypography,
            content = content
        )
    }
}

object Extended {}
