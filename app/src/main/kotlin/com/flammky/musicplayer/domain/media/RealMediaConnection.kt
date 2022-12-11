package com.flammky.musicplayer.domain.media

import android.os.Handler
import android.os.Looper
import com.flammky.android.medialib.common.mediaitem.MediaItem
import com.flammky.android.medialib.common.mediaitem.MediaMetadata
import com.flammky.android.medialib.player.Player
import com.flammky.common.kotlin.coroutines.safeCollect
import com.flammky.kotlin.common.sync.sync
import com.flammky.musicplayer.base.media.mediaconnection.MediaConnectionDelegate
import com.flammky.musicplayer.base.media.mediaconnection.MediaConnectionPlayback
import com.flammky.musicplayer.media.playback.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.*
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import kotlin.time.Duration

class RealMediaConnection(
	private val delegate: MediaConnectionDelegate
) : MediaConnection, /* Temp */ PlaybackController {

	private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

	override val playback: MediaConnection.Playback = Playback()
	override val repository: MediaConnection.Repository = Repository()

	//
	// Temp Start
	//

	val playbackSession: PlaybackSession = PlaybackSession("DEBUG", this)

	override val looper: Looper
		get() = delegate.playback.publicLooper

	override val playing: Boolean
		get() = delegate.playback.playing

	override val playWhenReady: Boolean
		get() = delegate.playback.playWhenReady

	override val queue: PlaybackQueue
		get() = PlaybackQueue(
			list = delegate.playback.getPlaylist().map { it.mediaId }.toPersistentList(),
			current = delegate.playback.index
		)

	override val repeatMode: RepeatMode
		get() = when (delegate.playback.repeatMode) {
			Player.RepeatMode.OFF -> RepeatMode.OFF
			Player.RepeatMode.ONE -> RepeatMode.ONE
			Player.RepeatMode.ALL -> RepeatMode.ALL
		}

	override val shuffleMode: ShuffleMode
		get() = when (delegate.playback.shuffleEnabled) {
			true -> ShuffleMode.ON
			else -> ShuffleMode.OFF
		}

	override val progress: Duration
		get() = delegate.playback.position()

	override val bufferedProgress: Duration
		get() = delegate.playback.bufferedPosition()

	override val duration: Duration
		get() = delegate.playback.duration()

	override val playbackSpeed: Float
		get() = delegate.playback.playbackSpeed()

	override fun setQueue(queue: PlaybackQueue): Boolean {
		TODO("Not yet implemented")
	}

	override fun setRepeatMode(mode: RepeatMode): Boolean {
		delegate.playback.setRepeatMode(
			when (mode) {
				RepeatMode.ALL -> Player.RepeatMode.ALL
				RepeatMode.OFF -> Player.RepeatMode.OFF
				RepeatMode.ONE -> Player.RepeatMode.ONE
			}
		)
		return true
	}

	override fun setShuffleMode(mode: ShuffleMode): Boolean {
		delegate.playback.setShuffleMode(
			when (mode) {
				ShuffleMode.OFF -> false
				ShuffleMode.ON -> true
			}
		)
		return true
	}

	override fun seekProgress(progress: Duration): Boolean {
		return delegate.playback.seekToPosition(progress.inWholeMilliseconds)
	}

	override fun seekIndex(index: Int, progress: Duration): Boolean {
		return delegate.playback.index != index && delegate.playback.seekToIndex(index, progress.inWholeMilliseconds)
	}

	override fun setPlayWhenReady(playWhenReady: Boolean): Boolean {
		delegate.playback.playWhenReady = playWhenReady
		return true
	}

	private inner class OBS() : PlaybackController.Observer {

		private val dispatcher = Handler(delegate.playback.publicLooper).asCoroutineDispatcher()
		private val scope = CoroutineScope(dispatcher + SupervisorJob())


		override fun getAndObserveRepeatModeChange(
			onRepeatModeChange: (PlaybackEvent.RepeatModeChange) -> Unit
		): RepeatMode {
			scope.launch {
				delegate.playback.observeRepeatModeChange().distinctUntilChanged().collect {
					onRepeatModeChange(
						PlaybackEvent.RepeatModeChange(RepeatMode.OFF, repeatMode, RepeatModeChangeReason.UNKNOWN)
					)
				}
			}
			return repeatMode
		}

		override fun getAndObserveShuffleModeChange(
			onShuffleModeChange: (PlaybackEvent.ShuffleModeChange) -> Unit
		): ShuffleMode {
			scope.launch {
				delegate.playback.observeShuffleEnabledChange().distinctUntilChanged().collect {
					onShuffleModeChange(PlaybackEvent.ShuffleModeChange(ShuffleMode.OFF, shuffleMode, ShuffleModeChangeReason.UNKNOWN))
				}
			}
			return shuffleMode
		}

		override fun getAndObserveQueueChange(
			onQueueChange: (PlaybackEvent.QueueChange) -> Unit
		): PlaybackQueue {
			scope.launch {
				playback.observePlaylistStream().collect {
					onQueueChange(PlaybackEvent.QueueChange(old = PlaybackQueue.UNSET, new = queue, PlaybackQueueChangeReason.UNKNOWN))
				}
			}
			return queue
		}

		override fun getAndObserveIsPlayingChange(
			onIsPlayingChange: (PlaybackEvent.IsPlayingChange) -> Unit
		): Boolean {
			scope.launch {
				delegate.playback.observeIsPlayingChange().distinctUntilChanged().collect {
					onIsPlayingChange(PlaybackEvent.IsPlayingChange(false, playing, IsPlayingChangeReason.UNKNOWN))
				}
			}
			return playing
		}

		override fun getAndObservePlaybackSpeed(
			onPlaybackSpeedChange: (PlaybackEvent.PlaybackSpeedChange) -> Unit
		): Float {
			return 1f
		}

		override fun observeDiscontinuity(
			onDiscontinuity: (PlaybackEvent.ProgressDiscontinuity) -> Unit
		) {
			scope.launch {
				delegate.playback.observeDiscontinuityEvent().collect {
					onDiscontinuity(PlaybackEvent.ProgressDiscontinuity(it.oldPosition, it.newPosition, ProgressDiscontinuityReason.UNKNOWN))
				}
			}
		}

		override fun getAndObserveDurationChange(onDurationChange: (Duration) -> Unit): Duration {
			scope.launch {

				delegate.playback.joinDispatcher {
					observeDurationChange().collect {
						onDurationChange(it)
					}
				}
			}

			scope.launch {

				delegate.playback.joinDispatcher {
					observeIsPlayingChange().collect {
						onDurationChange(duration)
					}
				}
			}
			return duration
		}

		override fun release() {
			scope.cancel()
		}
	}

	private val owners = mutableMapOf<Any, PlaybackController.Observer>()

	override fun acquireObserver(owner: Any): PlaybackController.Observer {
		return owners.sync { getOrPut(owner) { OBS() } }
	}

	override fun releaseObserver(owner: Any) {
		return owners.sync { remove(owner)?.release() }
	}

	override fun inLooper(): Boolean {
		return Looper.myLooper() == delegate.playback.publicLooper
	}

	override suspend fun <R> withContext(block: PlaybackController.() -> R): R {
		return delegate.playback.joinDispatcher { block() }
	}

	//
	// Temp end
	//

	private inner class Playback : MediaConnection.Playback {

		override val currentIndex: Int
			get() = delegate.playback.index

		override val currentPosition: Duration
			get() = delegate.playback.position

		override val currentDuration: Duration
			get() = delegate.playback.duration()

		override val mediaItemCount: Int
			get() = delegate.playback.mediaItemCount

		override val hasNextMediaItem: Boolean
			get() = delegate.playback.hasNextMediaItem

		override val hasPreviousMediaItem: Boolean
			get() = delegate.playback.hasPreviousMediaItem

		override fun setMediaItems(items: List<MediaItem>, startIndex: Int, startPosition: Duration) {
			delegate.playback.setMediaItems(items, startIndex, startPosition)
		}

		override fun setRepeatMode(repeatMode: Player.RepeatMode) {
			delegate.playback.setRepeatMode(repeatMode)
		}

		override fun setShuffleMode(enabled: Boolean) {
			delegate.playback.setShuffleMode(enabled)
		}

		override fun seekNext() {
			delegate.playback.seekNext()
		}

		override fun seekNextMedia() {
			delegate.playback.seekNextMedia()
		}

		override fun seekPrevious() {
			delegate.playback.seekPrevious()
		}

		override fun seekPreviousMedia() {
			delegate.playback.seekPreviousMedia()
		}

		override fun stop() {
			delegate.playback.stop()
		}

		override suspend fun <R> joinSuspend(block: suspend MediaConnection.Playback.() -> R): R {
			return delegate.playback.joinDispatcher { block() }
		}

		override fun prepare() = delegate.playback.prepare()
		override fun playWhenReady() = delegate.play()
		override fun pause() = delegate.pause()

		override fun seekIndex(index: Int, startPosition: Long): Boolean {
			return delegate.playback.index != index && delegate.playback.seekToIndex(index, startPosition)
		}

		override fun postSeekPosition(position: Long) {
			delegate.playback.postSeekToPosition(position)
		}

		override fun seekToPosition(position: Long): Boolean {
			return delegate.playback.seekToPosition(position)
		}

		override fun observeInfo(): Flow<MediaConnection.PlaybackInfo> {
			return callbackFlow {

				suspend fun sendNew() {
					val new = delegate.playback.joinDispatcher {
						MediaConnection.PlaybackInfo(
							id = delegate.playback.getCurrentMediaItem()?.mediaId ?: "",
							playWhenReady = delegate.playback.playWhenReady,
							playing = delegate.playback.playing
						)
					}
					send(new)
				}

				sendNew()

				val job1 = ioScope.launch {
					delegate.playback.observeMediaItemTransition().safeCollect { sendNew() }
				}

				val job2 = ioScope.launch {
					delegate.playback.observePlayWhenReadyChange().safeCollect { sendNew() }
				}

				val job3 = ioScope.launch {
					delegate.playback.observeIsPlayingChange().safeCollect { sendNew() }
				}

				awaitClose {
					job1.cancel();job2.cancel();job3.cancel()
				}
			}
		}

		override fun observePositionStream(interval: Duration): Flow<MediaConnection.Playback.PositionStream> = callbackFlow {

			var positionCollectorJob: Job? = null
			val mutex = Mutex()

			suspend fun sendUpdatedPositions(
				reason: MediaConnection.Playback.PositionStream.PositionChangeReason
			) {
				val new = delegate.playback.joinDispatcher {
					MediaConnection.Playback.PositionStream(
						positionChangeReason = reason,
						position = delegate.playback.position(),
						bufferedPosition = delegate.playback.bufferedPosition(),
						duration = delegate.playback.duration()
					)
				}
				send(new)
			}

			suspend fun collectPosition(
				startReason: MediaConnection.Playback.PositionStream.PositionChangeReason
			) {
				if (isActive) {
					sendUpdatedPositions(startReason)
					while (isActive) {
						sendUpdatedPositions(
							reason = MediaConnection.Playback.PositionStream.PositionChangeReason.PERIODIC
						)
						delay(interval.inWholeMilliseconds)
					}
				}
			}

			fun stopPositionCollector() = positionCollectorJob?.cancel()

			suspend fun startPositionCollector(
				reason: MediaConnection.Playback.PositionStream.PositionChangeReason
			) = mutex.withLock {
				if (positionCollectorJob?.isActive != true) {
					positionCollectorJob = launch { collectPosition(reason) }
				}
			}

			suspend fun restartPositionCollector(
				reason: MediaConnection.Playback.PositionStream.PositionChangeReason
			) {
				stopPositionCollector()
				send(MediaConnection.Playback.PositionStream(reason))
				startPositionCollector(MediaConnection.Playback.PositionStream.PositionChangeReason.PERIODIC)
			}

			sendUpdatedPositions(MediaConnection.Playback.PositionStream.PositionChangeReason.PERIODIC)

			val transitionObserverJob = launch {
				delegate.playback.observeMediaItemTransition().safeCollect {
					restartPositionCollector(MediaConnection.Playback.PositionStream.PositionChangeReason.AUTO)
				}
			}

			val timelineObserverJob = launch {
				delegate.playback.observeTimelineChange().safeCollect {
					sendUpdatedPositions(MediaConnection.Playback.PositionStream.PositionChangeReason.AUTO)
				}
			}

			val discontinuityJob = launch {
				delegate.playback.observeDiscontinuityEvent().safeCollect {
					val updateReason =
						if (it.reason == MediaConnectionPlayback.Events.Discontinuity.Reason.USER_SEEK) {
							MediaConnection.Playback.PositionStream.PositionChangeReason.USER_SEEK
						} else {
							MediaConnection.Playback.PositionStream.PositionChangeReason.UNKN0WN
						}
					sendUpdatedPositions(updateReason)
				}
			}

			val isPlayingJob = launch {
				delegate.playback.observeIsPlayingChange().safeCollect {
					if (!it) {
						stopPositionCollector()
						sendUpdatedPositions(MediaConnection.Playback.PositionStream.PositionChangeReason.UNKN0WN)
					} else {
						startPositionCollector(MediaConnection.Playback.PositionStream.PositionChangeReason.UNKN0WN)
					}
				}
			}

			if (delegate.playback.playing) {
				startPositionCollector(MediaConnection.Playback.PositionStream.PositionChangeReason.PERIODIC)
			}

			awaitClose {
				transitionObserverJob.cancel()
				discontinuityJob.cancel()
				timelineObserverJob.cancel()
				positionCollectorJob?.cancel()
				isPlayingJob.cancel()
			}
		}

		override fun observePlaylistStream(): Flow<MediaConnection.Playback.TracksInfo> = callbackFlow {

			suspend fun sendNew(
				reason: Int,
				currentIndex: Int? = null,
				currentList: ImmutableList<String>? = null
			) {
				val new = delegate.playback.joinDispatcher {
					MediaConnection.Playback.TracksInfo(
						changeReason = reason,
						currentIndex = currentIndex ?: this.index,
						list = currentList ?: this.getPlaylist().map { it.mediaId }.toPersistentList()
					)
				}
				send(new)
				Timber.d("observePlaylistStream sent $new")
			}

			sendNew(0)

			val playlistObserver = ioScope.launch {
				delegate.playback.observePlaylistChange().safeCollect {
					if (it.isEmpty()) {
						Timber.d("onPlaylistChange safeCollect $it")
						return@safeCollect send(MediaConnection.Playback.TracksInfo())
					}
					sendNew(1, currentList = it.toPersistentList())
				}
			}

			val currentObserver = ioScope.launch {
				delegate.playback.observeMediaItemTransition().safeCollect {
					if (it == null || it is MediaItem.UNSET) {
						Timber.d("onMediaItemTransition safeCollect $it")
						return@safeCollect send(MediaConnection.Playback.TracksInfo())
					}
					sendNew(0)
				}
			}
			awaitClose {
				currentObserver.cancel()
				playlistObserver.cancel()
			}
		}

		override fun observePropertiesInfo(): Flow<MediaConnection.Playback.PropertiesInfo> = callbackFlow {

			suspend fun sendCurrent() {
				val new = delegate.playback.joinDispatcher {
					MediaConnection.Playback.PropertiesInfo(
						playWhenReady = playWhenReady,
						playing = playing,
						shuffleEnabled = shuffleEnabled,
						hasNextMediaItem = hasNextMediaItem,
						hasPreviousMediaItem = hasPreviousMediaItem,
						repeatMode = repeatMode,
						playerState = playerState
					)
				}
				send(new)
			}

			sendCurrent()

			val job1 = ioScope.launch { delegate.playback.observePlayWhenReadyChange().safeCollect { sendCurrent() } }
			val job2 = ioScope.launch { delegate.playback.observeIsPlayingChange().safeCollect { sendCurrent() } }
			val job3 = ioScope.launch { delegate.playback.observeShuffleEnabledChange().safeCollect { sendCurrent() } }
			val job4 = ioScope.launch { delegate.playback.observeMediaItemTransition().safeCollect { sendCurrent() } }
			val job5 = ioScope.launch { delegate.playback.observePlaylistChange().safeCollect { sendCurrent() } }
			val job6 = ioScope.launch { delegate.playback.observeRepeatModeChange().safeCollect { sendCurrent() } }
			val job7 = ioScope.launch { delegate.playback.observePlayerStateChange().safeCollect { sendCurrent() } }

			awaitClose {
				job1.cancel();job2.cancel();job3.cancel();job4.cancel();job5.cancel();job6.cancel();job7.cancel()
			}
		}
	}



	private inner class Repository : MediaConnection.Repository {
		override suspend fun getMetadata(id: String): MediaMetadata? = delegate.repository.getMetadata(id)
		override suspend fun getArtwork(id: String): Any? = delegate.repository.getArtwork(id)
		override suspend fun observeMetadata(id: String): Flow<MediaMetadata?> = delegate.repository.observeMetadata(id)
		override suspend fun observeArtwork(id: String): Flow<Any?> = delegate.repository.observeArtwork(id)
		override suspend fun provideMetadata(id: String, metadata: MediaMetadata) = delegate.repository.provideMetadata(id, metadata)
		override suspend fun provideArtwork(id: String, artwork: Any?) = delegate.repository.provideArtwork(id, artwork)
	}
}
