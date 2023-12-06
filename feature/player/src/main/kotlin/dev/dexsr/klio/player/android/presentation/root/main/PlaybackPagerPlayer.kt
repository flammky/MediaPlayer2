package dev.dexsr.klio.player.android.presentation.root.main

import dev.dexsr.klio.player.android.presentation.root.PlaybackTimeline
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow

abstract class PlaybackPagerPlayer {

    abstract fun timelineAndStepAsFlow(
        range: Int
    ): Flow<Pair<PlaybackTimeline, Int>>

    abstract fun seekToNextMediaItemAsync(resultRange: Int): Deferred<Result<PlaybackTimeline>>

    abstract fun seekToPreviousMediaItemAsync(resultRange: Int): Deferred<Result<PlaybackTimeline>>
}