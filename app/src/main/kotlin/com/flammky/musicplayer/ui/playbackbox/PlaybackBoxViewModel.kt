package com.flammky.musicplayer.ui.playbackbox

import androidx.annotation.MainThread
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flammky.android.kotlin.coroutine.AndroidCoroutineDispatchers
import com.flammky.android.medialib.common.Contract
import com.flammky.android.medialib.common.mediaitem.AudioFileMetadata
import com.flammky.android.medialib.common.mediaitem.AudioMetadata
import com.flammky.android.medialib.common.mediaitem.MediaMetadata
import com.flammky.android.medialib.providers.metadata.VirtualFileMetadata
import com.flammky.common.kotlin.coroutines.safeCollect
import com.flammky.musicplayer.base.media.mediaconnection.MediaConnectionDelegate
import com.flammky.musicplayer.domain.media.MediaConnection
import com.flammky.musicplayer.ui.playbackbox.PlaybackBoxPositions.Companion.asPlaybackBoxPosition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class PlaybackBoxViewModel @Inject constructor(
	private val dispatchers: AndroidCoroutineDispatchers,
	private val mediaConnection: MediaConnection,
	private val mediaConnectionDelegate: MediaConnectionDelegate
	// I think we should consider injecting observable instead ?
) : ViewModel() {

	private val _metadataStateMap = mutableMapOf<String, StateFlow<PlaybackBoxMetadata>>()

	private val playlistStreamFlow = mediaConnection.playback.observePlaylistStream()
	private val positionStreamFlow = mediaConnection.playback.observePositionStream(1.seconds)
	private val playbackPropertiesInfoFlow = mediaConnection.playback.observePropertiesInfo()

	// Localize
	val playlistStreamStateFlow = playlistStreamFlow
		.stateIn(viewModelScope, SharingStarted.Lazily, MediaConnection.Playback.TracksInfo())

	val positionStreamStateFlow = positionStreamFlow
		.map { it.asPlaybackBoxPosition }
		.stateIn(viewModelScope, SharingStarted.Lazily, PlaybackBoxPositions())

	// Localize
	val playbackPropertiesInfoStateFlow = playbackPropertiesInfoFlow
		.stateIn(viewModelScope, SharingStarted.Lazily, MediaConnection.Playback.PropertiesInfo())

	@OptIn(ExperimentalCoroutinesApi::class)
	val artworkFlow = playlistStreamStateFlow.mapLatest {
		val id = if (it.currentIndex >= 0 && it.list.isNotEmpty()) it.list[it.currentIndex] else "NO_ID"
		Timber.d("artworkFlow collected id $id, ${it.currentIndex}, ${it.list}")
		id
	}.distinctUntilChanged()
		.flatMapLatest { id -> observeArtwork(id) }
		.stateIn(viewModelScope, SharingStarted.Lazily, null)

	fun play() = mediaConnectionDelegate.play()
	fun pause() = mediaConnectionDelegate.pause()

	fun seekIndex(index: Int) = mediaConnection.playback.seekIndex(index, 0)

	suspend fun observeMetadata(id: String): Flow<MediaMetadata?> {
		return mediaConnectionDelegate.repository.observeMetadata(id)
	}

	suspend fun observeArtwork(id: String): Flow<Any?> {
		return mediaConnectionDelegate.repository.observeArtwork(id)
	}

	@MainThread
	fun observeBoxMetadata(id: String): StateFlow<PlaybackBoxMetadata> {
		_metadataStateMap[id]?.let { return it }

		val state = flow {
			combine(observeArtwork(id), observeMetadata(id)) { art: Any?, metadata: MediaMetadata? ->
				val title = metadata?.title?.ifBlank { null }
					?: (metadata as? AudioFileMetadata)?.file?.fileName?.ifBlank { null }
					?: ((metadata as? AudioFileMetadata)?.file as? VirtualFileMetadata)?.uri?.toString()
				val subtitle = (metadata as? AudioMetadata)?.let {
					it.albumArtistName ?: it.artistName
				}
				PlaybackBoxMetadata(art, title ?: "", subtitle ?: "")
			}.collect(this)
		}.stateIn(viewModelScope, SharingStarted.Lazily, PlaybackBoxMetadata())

		return state.also {
			if (_metadataStateMap.put(id, state) != null) {
				error("Duplicate StateFlow ($id)")
			}
		}
	}

	/**
	 * Observe The Playback Positions
	 */
	fun observePositions(): Flow<PlaybackBoxPositions> {
		return callbackFlow {
			val job = launch(dispatchers.io) {
				mediaConnection.playback.observePositionStream(500.milliseconds).safeCollect { stream ->
					send(stream.asPlaybackBoxPosition)
				}
			}
			awaitClose { job.cancel() }
		}
	}
}

@Immutable
data class PlaybackBoxPositions(
	/*val key: Any? = null,*/
	val bufferedPosition: Duration = Contract.POSITION_UNSET,
	val position: Duration = Contract.POSITION_UNSET,
	val duration: Duration = Contract.DURATION_UNSET,
) {
	companion object {
		val MediaConnection.Playback.PositionStream.asPlaybackBoxPosition
			get() = PlaybackBoxPositions(
				bufferedPosition = bufferedPosition,
				position = position,
				duration = duration
			)
	}
}

@Immutable
data class PlaybackBoxMetadata(
	val artwork: Any? = ART_UNSET,
	val title: String = "",
	val subtitle: String = "",
) {
	companion object {
		val ART_UNSET = Any()
	}
}


@Immutable
data class PlaybackBoxModel(
	val id: String = "",
	val playing: Boolean = false,
	val playWhenReady: Boolean = false,
) {

	companion object  {
		val UNSET = PlaybackBoxModel()
 	}
}