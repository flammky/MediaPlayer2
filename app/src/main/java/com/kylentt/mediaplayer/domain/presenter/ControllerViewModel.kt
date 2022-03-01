package com.kylentt.mediaplayer.domain.presenter

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.media3.common.Player
import com.kylentt.mediaplayer.core.util.Constants.ACTION
import com.kylentt.mediaplayer.core.util.Constants.ACTION_FADE
import com.kylentt.mediaplayer.core.util.Constants.PLAYBACK_INTENT
import com.kylentt.mediaplayer.core.util.Constants.SONG_DATA
import com.kylentt.mediaplayer.core.util.Constants.SONG_LAST_MODIFIED
import com.kylentt.mediaplayer.data.repository.SongRepositoryImpl
import com.kylentt.mediaplayer.domain.model.findIdentified
import com.kylentt.mediaplayer.domain.model.toMediaItems
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
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
        repository.fetchSongFromDocs(uri).collect {
            it?.let{
                handlePlayIntentFromRepo(it.first, it.second, it.third, uri)
            } ?: handleItemIntent(uri)
        }
    }

    suspend fun handleItemIntent(uri: Uri) = withContext(Dispatchers.Main) {
        Timber.d("IntentHandler ItemIntent $uri")
        connector.connectService()
        repository.fetchMetaFromUri(uri).collect { item ->
            item?.let {
                connector.broadcast(Intent(PLAYBACK_INTENT).apply { putExtra(ACTION, ACTION_FADE) })
                delay(750)
                connector.controller(true) {
                    it.seekTo(0,0)
                    it.repeatMode = Player.REPEAT_MODE_OFF
                    it.setMediaItems(listOf(item))
                    it.prepare()
                    it.playWhenReady = true
                    Timber.d("Controller IntentHandler handling ${item.mediaMetadata.title} handled")
                }
            }
        }
    }

    // I dare you to read this, GlHf
    private suspend fun handlePlayIntentFromRepo(
        name: String,
        byte: Long,
        identifier: String,
        uri: Uri
    ) = withContext(Dispatchers.IO) { repository.fetchSongs().collect { list ->
        val identified = when {
            identifier.endsWith(name) -> SONG_DATA
            else -> SONG_LAST_MODIFIED
        }

        val song = identified.let {
            Timber.d("IntentHandler Repo Identified as $identified")
            list.findIdentified( iden = it,
                str = Triple(
                    first = identifier,
                    second = Pair(identifier, name),
                    third = Triple(identifier, byte.toString(), name)
                )).also { it?.let { Timber.d("IntentHandler Repo Handled with Identifier $identified $identifier") } }

        } ?: run { Timber.d("IntentHandler Unable to Find with $identifier")

            list.find {
                identifier.trim() == it.data.trim()
            } ?: list.find {
                identifier.contains(it.lastModified.toString()) && it.fileName == name
            } ?: list.find {
                identifier.contains(it.lastModified.toString()) && it.byteSize == byte
            } ?: list.find {
                it.fileName == name && it.byteSize == byte
            } ?: run {
                handleItemIntent(uri)
                Timber.d("IntentHandler Repository not Found, Forwarding with Uri")
                Timber.d("IntentHandler Repository to Uri with $name $byte $identifier")
                null
            }
        }

        song?.let {
            connector.broadcast(Intent(PLAYBACK_INTENT).apply { putExtra(ACTION, ACTION_FADE) })
            delay(500)
            withContext(Dispatchers.Main) {
                connector.controller(true) {
                    Timber.d("IntentHandler Repository File Found, Handled with Repo")
                    it.seekTo(0,0)
                    it.setMediaItems(list.toMediaItems(), list.indexOf(song), 0)
                    it.prepare()
                    it.playWhenReady = true
                    Timber.d("IntentHandler Repository Handled $name $byte $identifier")
                }
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