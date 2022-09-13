package com.kylentt.mediaplayer.domain.viewmodels

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.kylentt.mediaplayer.helper.external.IntentWrapper
import com.kylentt.mediaplayer.helper.external.MediaIntentHandler
import com.kylentt.mediaplayer.helper.image.CoilHelper
import com.kylentt.musicplayer.common.android.environment.DeviceInfo
import com.flammky.common.kotlin.coroutines.AndroidCoroutineDispatchers
import com.kylentt.musicplayer.common.coroutines.safeCollect
import com.flammky.common.kotlin.coroutine.checkCancellation
import com.flammky.common.kotlin.collection.mutable.forEachClear
import com.flammky.android.app.AppDelegate
import com.kylentt.musicplayer.domain.musiclib.core.MusicLibrary
import com.kylentt.musicplayer.domain.musiclib.media3.mediaitem.MediaItemHelper
import com.kylentt.musicplayer.ui.main.compose.screens.root.PlaybackControlModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.await
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import kotlin.time.ExperimentalTime

@HiltViewModel
class MediaViewModel @Inject constructor(
	private val coilHelper: CoilHelper,
	private val deviceInfo: DeviceInfo,
	private val dispatchers: AndroidCoroutineDispatchers,
	private val itemHelper: MediaItemHelper,
	private val intentHandler: MediaIntentHandler,
) : ViewModel() {

	private val cacheManager = AppDelegate.cacheManager

	private val ioScope = viewModelScope + dispatchers.io
	private val computationScope = viewModelScope + dispatchers.computation

	private val positionStateFlow = MusicLibrary.api.localAgent.session.info.playbackPosition
	private val bufferedPositionStateFlow = MusicLibrary.api.localAgent.session.info.playbackBufferedPosition

	private val player = MusicLibrary.api.localAgent.session.player

	private var positionCollectorJob = Job().job
	private var updateArtJob = Job().job

	val playbackControlModel = PlaybackControlModel()

	val pendingStorageIntent = mutableListOf<IntentWrapper>()
	init {
		viewModelScope.launch(dispatchers.main) {
			collectPlaybackState()
		}
		viewModelScope.launch(dispatchers.main) {
			positionStateFlow.safeCollect {
				Timber.d("positionStateFlow collected $it")
				playbackControlModel.updatePosition(it)
			}
		}
		viewModelScope.launch(dispatchers.main) {
			bufferedPositionStateFlow.safeCollect {
				playbackControlModel.updateBufferedPosition(it)
			}
		}
	}

	fun readPermissionGranted() {
		pendingStorageIntent.forEachClear(::handleMediaIntent)
	}

	fun play() = viewModelScope.launch {

		val player = if (!player.connected) {
			player.connectService().await()
		} else {
			player
		}

		when {
			player.playbackState.isIDLE() -> player.prepare()
			player.playbackState.isENDED() -> player.seekToDefaultPosition()
		}

		player.play()
	}
	fun pause() = player.pause()

  fun handleMediaIntent(intent: IntentWrapper) {
    viewModelScope.launch(dispatchers.computation) {
			if (intent.shouldHandleIntent) intentHandler.handleMediaIntent(intent)
		}
  }

  private suspend fun collectPlaybackState() {
    MusicLibrary.api.localAgent.session.info.playbackState.safeCollect { playbackState ->
			Timber.d("collectPlaybackState")

			val get = playbackControlModel.mediaItem

			if (get !== playbackState.mediaItem) {
				playbackControlModel.updateArt(null)
				dispatchUpdateItemBitmap(playbackState.mediaItem)
			}

			playbackControlModel.updateBy(playbackState)
    }
  }

  @OptIn(ExperimentalTime::class)
	@MainThread
  suspend fun dispatchUpdateItemBitmap(item: MediaItem) {
		updateArtJob.cancel()
    updateArtJob = ioScope.launch {
			ensureActive()

			val lru = com.flammky.android.medialib.temp.MediaLibrary.API.imageRepository.sharedBitmapLru
			val cached = lru.get(item.mediaId) ?: lru.get(item.mediaId + "500")

			if (cached != null) {
				return@launch playbackControlModel.updateArt(cached)
			}

			try {

				Timber.d("itemHasExtra: ${item.mediaMetadata.extras}")

				val bitmap = item.mediaMetadata.extras?.getString("cachedArtwork")?.let { file ->
					coilHelper.loadBitmap(File(file), 500 ,500)
				} ?: itemHelper.getEmbeddedPicture(item)?.let { bytes ->
					coilHelper.loadBitmap(bytes, 500 ,500)
				}

				checkCancellation {
					bitmap?.recycle()
				}

				if (bitmap != null) lru.putIfKeyAbsent(item.mediaId + "500", bitmap)

				withContext(dispatchers.main) {
					playbackControlModel.updateArt(bitmap)
				}

			} catch (_: OutOfMemoryError) {}
		}
	}
}
