package com.kylentt.mediaplayer.domain.presenter

import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.kylentt.mediaplayer.core.util.Version
import com.kylentt.mediaplayer.core.util.VersionHelper
import com.kylentt.mediaplayer.data.repository.SongRepositoryImpl
import com.kylentt.mediaplayer.domain.model.toMediaItems
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ControllerViewModel @Inject constructor(
    private val connector: ServiceConnectorImpl,
    private val repository: SongRepositoryImpl
) : ViewModel() {

    /** Connector State */
    val mediaItems = connector.mediaItems
    val mediaItem = connector.mediaItem
    val isPlaying = connector.isPlaying
    val playerIndex = connector.playerIndex
    val position = connector.position
    val duration = connector.duration

    /** Controller Command */
    fun play(f: Boolean) = connector.controller(f) { it.play() }
    fun stop(f: Boolean) = connector.controller(f) { it.stop() }
    fun pause(f: Boolean) = connector.controller(f) { it.pause() }
    fun prepare(f: Boolean) = connector.controller(f) { it.prepare() }
    fun skipToNext(f: Boolean) = connector.controller(f) { it.seekToNext() }
    fun skipToPrev(f: Boolean) = connector.controller(f) { it.seekToPrevious() }
    fun getItemAt(f: Boolean, i: Int) = connector.controller(f) { it.getMediaItemAt(i) }

    suspend fun handleDocsIntent(uri: Uri) = withContext(Dispatchers.Main) {
        Timber.d("IntentHandler DocsIntent $uri")
        connector.connectService()
        repository.fetchSongFromDocs(uri)?.let {
            handlePlayIntentFromRepo(it.first, it.second, it.third, uri)
        }
    }

    suspend fun handleItemIntent(uri: Uri) = withContext(Dispatchers.Main) {
        Timber.d("IntentHandler ItemIntent $uri")
        connector.connectService()
        val item = repository.fetchMetaFromUri(uri)
        item?.let {
            connector.sController(true) {
                it.seekTo(0,0)
                it.repeatMode = Player.REPEAT_MODE_OFF
                it.setMediaItems(listOf(item))
                it.prepare()
                it.playWhenReady = true
                Timber.d("Controller IntentHandler handling ${item.mediaMetadata.title} handled")
            }
        }
    }

    private suspend fun handlePlayIntentFromRepo(
        name: String,
        byte: Long,
        lastModified: Long,
        uri: Uri
    ) = withContext(Dispatchers.IO) { repository.fetchSongs().collectLatest { list ->
        val song = list.find {
            lastModified.toString().contains(it.lastModified.toString()) && it.fileName == name
        } ?: list.find {
            it.fileName == name && it.byteSize == byte
        } ?: list.find {
            it.lastModified == lastModified
        } ?: list.find {
            it.fileName == name
        } ?: run {
            handleItemIntent(uri)
            Timber.d("IntentHandler Repository not Found, Forwarding with Uri")
            Timber.d("IntentHandler Repository to Uri with $name $byte $lastModified")
            null
        }
        song?.let {
            connector.sController(true) {
                Timber.d("IntentHandler Repository File Found, Handled with Repo")
                it.seekTo(0,0)
                it.setMediaItems(list.toMediaItems(), list.indexOf(song), 0)
                it.prepare()
                it.playWhenReady = true
                Timber.d("IntentHandler Repository Handled")
            }
        }
    } }

    private val mainUpdater = CoroutineScope( Dispatchers.Main + Job() )
    private val ioUpdater = CoroutineScope( Dispatchers.IO + Job() )

    init {
        connector.connectService()
        ioUpdater.launch {
            connector.positionEmitter().collect {
                Timber.d("positionEmitter isValid $it")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (mainUpdater.isActive) mainUpdater.cancel()
        if (ioUpdater.isActive) ioUpdater.cancel()
    }
}