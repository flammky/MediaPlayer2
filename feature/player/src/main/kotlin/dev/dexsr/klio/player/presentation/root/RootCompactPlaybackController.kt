package dev.dexsr.klio.player.presentation.root

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface RootCompactPlaybackController {

    fun currentlyPlayingMediaIdAsFlow(): Flow<String?>

    fun playbackProgressAsFlow(
        uiWidthDp: Float
    ): Flow<PlaybackProgress>
}

object NoOpRootCompactPlaybackController : RootCompactPlaybackController {

    override fun currentlyPlayingMediaIdAsFlow(): Flow<String?> = flowOf()

    override fun playbackProgressAsFlow(uiWidthDp: Float): Flow<PlaybackProgress> = flowOf()
}