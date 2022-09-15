package com.flammky.musicplayer.domain.musiclib.session

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.flammky.common.kotlin.comparable.clamp
import com.flammky.musicplayer.domain.musiclib.entity.PlaybackState
import com.flammky.musicplayer.domain.musiclib.interactor.LibraryAgent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.coroutineContext

class MusicSession(private val agent: LibraryAgent) {

	val player: com.flammky.android.medialib.temp.api.player.MediaController = com.flammky.android.medialib.temp.MediaLibrary.API.sessions.manager.findSessionById("DEBUG")!!.mediaController

	val sessionInfo = object : SessionInfo {
		val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

		private val mLoading = MutableStateFlow(false)
		private val mPlaybackPosition = MutableStateFlow(-1L)
		private val mPlaybackDuration = MutableStateFlow(-1L)
		private val mPlaybackBufferedPosition = MutableStateFlow(-1L)

		private var positionCollectorJob = Job().apply { complete() }.job
			set(value) {
				if (field.isActive) throw IllegalStateException()
				field = value
			}

		private var bufferedPositionCollectorJob = Job().apply { complete() }.job
			set(value) {
				if (field.isActive) throw IllegalStateException()
				field = value
			}

		override val playbackState: StateFlow<PlaybackState> = PlaybackState.StateFlow(player)

		override val playbackPosition: StateFlow<Long>
			get() = mPlaybackPosition

		override val playbackBufferedPosition: StateFlow<Long>
			get() = mPlaybackBufferedPosition

		private val playerListener = object :
			com.flammky.android.medialib.temp.player.event.LibraryPlayerEventListener {


			override fun onMediaItemTransition(
				old: MediaItem?,
				new: MediaItem?,
				reason: com.flammky.android.medialib.temp.player.event.MediaItemTransitionReason
			) {
				cancelPositionCollector()
				cancelBufferedPositionCollector()

				if (new != null) {
					if (!positionCollectorJob.isActive) {
						startPositionCollector(0L)
					}
					if (!bufferedPositionCollectorJob.isActive) {
						startBufferedPositionCollector(0L)
					}
				} else {
					updatePlaybackPosition(0L)
					updatePlaybackBufferedPosition(0L)
				}
			}

			override fun onIsPlayingChanged(isPlaying: Boolean, reason: com.flammky.android.medialib.temp.player.event.IsPlayingChangedReason) {
				if (isPlaying && !positionCollectorJob.isActive) startPositionCollector()
			}

			override fun onIsLoadingChanged(isLoading: Boolean) {
				updatePlaybackBufferedPosition(player.bufferedPositionMs)
				mLoading.value = isLoading

				if (!bufferedPositionCollectorJob.isActive) {
					startBufferedPositionCollector()
				}
			}

			override fun onPositionDiscontinuity(
				oldPos: Player.PositionInfo,
				newPos: Player.PositionInfo,
				reason: Int
			) {
				updatePlaybackPosition(newPos.positionMs)
			}
		}

		/*private val playerListener = object : Player.Listener {

			override fun onPositionDiscontinuity(
				oldPosition: Player.PositionInfo,
				newPosition: Player.PositionInfo,
				reason: Int
			) {
				updatePlaybackPosition(newPosition.positionMs)
			}

			override fun onIsPlayingChanged(isPlaying: Boolean) {
				if (isPlaying && !positionCollectorJob.isActive) startPositionCollector()
			}

			override fun onIsLoadingChanged(isLoading: Boolean) {
				updatePlaybackBufferedPosition(player.bufferedPosition)
				mLoading.value = isLoading

				if (!bufferedPositionCollectorJob.isActive) {
					startBufferedPositionCollector()
				}
			}

			override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
				cancelPositionCollector()
				cancelBufferedPositionCollector()

				if (mediaItem != null) {
					if (!positionCollectorJob.isActive) {
						startPositionCollector(0L)
					}
					if (!bufferedPositionCollectorJob.isActive) {
						startBufferedPositionCollector(0L)
					}
				} else {
					updatePlaybackPosition(0L)
					updatePlaybackBufferedPosition(0L)
				}
			}

			override fun onTimelineChanged(timeline: Timeline, reason: Int) {
			}
		}*/

		init {
			player.addListener(playerListener)
		}

		private fun updatePlaybackPosition(pos: Long = player.positionMs) {
			mPlaybackPosition.value = pos.clamp(0L, playbackState.value.duration)
		}

		private fun updatePlaybackBufferedPosition(pos: Long = player.bufferedPositionMs) {
			mPlaybackBufferedPosition.value = pos.clamp(0L, playbackState.value.duration)
		}

		private fun startPositionCollector(startPosition: Long = player.positionMs) {
			updatePlaybackPosition(startPosition)
			positionCollectorJob = mainScope.launch {
				delay(1000)
				collectPosition()
			}
		}

		private fun cancelPositionCollector() {
			positionCollectorJob.cancel()
		}

		private fun startBufferedPositionCollector(startPosition: Long = player.bufferedPositionMs) {
			updatePlaybackBufferedPosition(startPosition)
			bufferedPositionCollectorJob = mainScope.launch {
				delay(500)
				collectBufferedPosition()
			}
		}

		private fun cancelBufferedPositionCollector() {
			bufferedPositionCollectorJob.cancel()
		}

		private suspend fun collectPosition() {
			while (coroutineContext.isActive) {
				updatePlaybackPosition(player.positionMs)
				if (playbackPosition.value >= playbackState.value.duration) break
				delay(1000)
			}
		}

		private suspend fun collectBufferedPosition() {
			while (coroutineContext.isActive) {
				updatePlaybackBufferedPosition(player.bufferedPositionMs)
				if (playbackBufferedPosition.value >= playbackState.value.duration) break
				delay(500)
			}
		}
	}

	interface SessionInfo {
		val playbackState: StateFlow<PlaybackState>
		val playbackPosition: StateFlow<Long>
		val playbackBufferedPosition: StateFlow<Long>
	}
}