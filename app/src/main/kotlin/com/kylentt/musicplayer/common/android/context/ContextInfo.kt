package com.kylentt.musicplayer.common.android.context

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.kylentt.musicplayer.common.android.intent.AndroidCommonIntent
import com.kylentt.musicplayer.core.app.permission.AndroidPermission
import com.kylentt.musicplayer.core.app.permission.AndroidPermissionInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject


class ContextInfo @Inject constructor (
	@ApplicationContext private val context: Context
) {

	val commonIntent = AndroidCommonIntent(context)
	val permission = AndroidPermissionInfo(context)
	fun isPermissionGranted(permission: AndroidPermission): Boolean {
		return this.permission.isPermissionGranted(permission)
	}

	companion object {

		val current: ContextInfo
			@ReadOnlyComposable
			@Composable
			get() = LocalContextInfo.current

		@Composable
		fun Provide(content: @Composable () -> Unit) {
			Provide(contextInfo = rememberContextInfo(), content = content)
		}

		@Composable
		fun Provide(contextInfo: ContextInfo, content: @Composable () -> Unit) {
			CompositionLocalProvider(LocalContextInfo provides contextInfo, content = content)
		}

		private val LocalContextInfo = compositionLocalOf<ContextInfo> {
			error("no ContextInfo Provided")
		}
	}
}

@Composable
fun rememberContextInfo(): ContextInfo {
	val context = requireNotNull(LocalContext.current)
	return remember(context) { ContextInfo(context) }
}
