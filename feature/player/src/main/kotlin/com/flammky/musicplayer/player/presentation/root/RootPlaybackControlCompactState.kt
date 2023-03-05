package com.flammky.musicplayer.player.presentation.root

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.palette.graphics.Palette
import coil.request.ImageRequest
import com.flammky.android.medialib.common.mediaitem.MediaMetadata
import com.flammky.musicplayer.base.compose.SnapshotRead
import com.flammky.musicplayer.base.compose.SnapshotWrite
import com.flammky.musicplayer.base.media.playback.OldPlaybackQueue
import com.flammky.musicplayer.base.media.playback.PlaybackProperties
import com.flammky.musicplayer.base.theme.Theme
import com.flammky.musicplayer.base.theme.compose.isDarkAsState
import com.flammky.musicplayer.base.theme.compose.surfaceVariantColorAsState
import com.flammky.musicplayer.base.theme.compose.surfaceVariantContentColorAsState
import com.flammky.musicplayer.player.presentation.controller.PlaybackController
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.PagerState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import kotlin.time.Duration


class RootPlaybackControlCompactState internal constructor(
    internal val playbackController: PlaybackController,
    val onBackgroundClicked: () -> Unit,
    val onArtworkClicked: () -> Unit,
    val onObserveMetadata: (String) -> Flow<MediaMetadata?>,
    val onObserveArtwork: (String) -> Flow<Any?>,
    val coroutineScope: CoroutineScope,
    val coroutineDispatchScope: CoroutineScope,
) {

    @Suppress("JoinDeclarationAndAssignment")
    internal val coordinator: ControlCompactCoordinator

    var height by mutableStateOf<Dp>(55.dp)
        @SnapshotRead get
        @SnapshotWrite set

    var width by mutableStateOf<Dp>(Dp.Unspecified)
        @SnapshotRead get
        @SnapshotWrite set

    /**
     * the bottom offset for the coordinator to apply
     */
    var bottomSpacing by mutableStateOf<Dp>(0.dp)
        @SnapshotRead get
        @SnapshotWrite set

    /**
     * mutable freeze state for the coordinator to apply, when set to true the layout will no longer
     * collect remote updates nor dispatch user request (dropped)
     */
    var freeze by mutableStateOf<Boolean>(false)
        @SnapshotRead get
        @SnapshotWrite set

    val topPositionRelativeToParent
        @SnapshotRead get() = coordinator.topPositionRelativeToParent

    val topPositionRelativeToAnchor
        @SnapshotRead get() = coordinator.topPositionRelativeToAnchor

    init {
        // I guess we should provide lambdas as opposed to `this`
        coordinator = ControlCompactCoordinator(this, coroutineScope, coroutineDispatchScope)
    }

    internal fun dispose() {
        playbackController.dispose()
        coroutineScope.cancel()
        coroutineDispatchScope.cancel()
    }
}

internal class ControlCompactCoordinator(
    private val state: RootPlaybackControlCompactState,
    private val coroutineScope: CoroutineScope,
    private val coroutineDispatchScope: CoroutineScope,
) {

    val coordinatorSupervisorJob = SupervisorJob()

    val layoutComposition = ControlCompactComposition(
        getLayoutHeight = @SnapshotRead state::height::get,
        getLayoutWidth = @SnapshotRead state::width::get,
        getLayoutBottomOffset = @SnapshotRead state::bottomSpacing::get,
        observeMetadata = state.onObserveMetadata,
        observeArtwork = state.onObserveArtwork,
        observePlaybackQueue = ::observeControllerPlaybackQueue,
        observePlaybackProperties = ::observeControllerPlaybackProperties,
        setPlayWhenReady = ::setPlayWhenReady,
        observeProgressWithIntervalHandle = ::observeProgressWithIntervalHandle,
        coroutineScope = coroutineScope
    )

    val topPositionRelativeToParent
        get() = layoutComposition.topPosition

    val topPositionRelativeToAnchor
        get() = layoutComposition.topPositionFromAnchor

    private var queueReaderCount by mutableStateOf(0)
    private var propertiesReaderCount by mutableStateOf(0)

    var remoteQueueSnapshot by mutableStateOf<OldPlaybackQueue>(OldPlaybackQueue.UNSET)
        private set

    var remotePlaybackPropertiesSnapshot by mutableStateOf(PlaybackProperties.UNSET)
        private set

    val freeze by derivedStateOf { state.freeze }

    private fun observeControllerPlaybackProperties(): Flow<PlaybackProperties> {
        return flow {
            propertiesReaderCount++
            try {
                snapshotFlow { remotePlaybackPropertiesSnapshot }
                    .collect(this)
            }  finally {
                propertiesReaderCount--
            }
        }
    }

    private fun observeControllerPlaybackQueue(): Flow<OldPlaybackQueue> {
        return flow {
            queueReaderCount++
            try {
                snapshotFlow { remoteQueueSnapshot }
                    .collect(this)
            } finally {
                queueReaderCount--
            }
        }
    }

    private fun setPlayWhenReady(
        play: Boolean,
        joinCollectorDispatch: Boolean
    ): Deferred<Result<Boolean>> {
        return coroutineDispatchScope.async {
            runCatching {
                val result = state.playbackController.requestSetPlayWhenReadyAsync(play).await()
                if (joinCollectorDispatch) {
                    result.eventDispatch?.join()
                }
                result.success
            }
        }
    }

    private fun observeProgressWithIntervalHandle (
        getNextInterval: (progress: Duration, duration: Duration, speed: Float) -> Duration
    ): Flow<Duration> {
        return flow {
            val observer = state.playbackController.createPlaybackObserver()
            val collector = observer.createProgressionCollector()
            try {
                collector
                    .apply {
                        setIntervalHandler { _, p, d, s ->
                            getNextInterval(p, d, s)
                        }
                        startCollectPosition().join()
                    }
                collector.positionStateFlow.collect(this@flow)
            } finally {
                observer.dispose()
            }
        }
    }

    companion object {
        @Suppress("NOTHING_TO_INLINE")
        @Composable
        inline fun ControlCompactCoordinator.PrepareCompositionInline() {
            // I don't think these should be composable, but that's for later
            ComposeRemoteQueueReaderObserver()
            DisposableEffect(
                key1 = this,
                effect = {
                    // TODO: assert that this state can only be used within a single composition
                    //  tree at a time (acquire slot)
                    onDispose {
                        // TODO: release slot
                    }
                }
            )
        }

        @Composable
        private inline fun ControlCompactCoordinator.ComposeRemoteQueueReaderObserver() {
            LaunchedEffect(
                key1 = this,
                block = {
                    launch(coordinatorSupervisorJob) {
                        var latestCollectorJob: Job? = null
                        snapshotFlow { queueReaderCount }
                            .map {
                                check(it >= 0)
                                it > 0
                            }
                            .distinctUntilChanged()
                            .collect { hasActiveReader ->
                                if (!hasActiveReader) {
                                    remoteQueueSnapshot = OldPlaybackQueue.UNSET
                                    latestCollectorJob?.cancel()
                                    return@collect
                                }
                                check(latestCollectorJob?.isActive != true)
                                latestCollectorJob = launch {
                                    var collectWithFreezeHandle: Job? = null
                                    snapshotFlow { state.freeze }
                                        .collect { freeze ->
                                            if (freeze) {
                                                collectWithFreezeHandle?.cancel()
                                                return@collect
                                            }
                                            check(collectWithFreezeHandle?.isActive != true)
                                            collectWithFreezeHandle = launch {
                                                val observer = state.playbackController.createPlaybackObserver()
                                                try {
                                                    val collector = observer.createQueueCollector()
                                                        .apply { startCollect().join() }
                                                    collector.queueStateFlow
                                                        .collect {
                                                            remoteQueueSnapshot = it
                                                        }
                                                } finally {
                                                    observer.dispose()
                                                }
                                            }
                                        }
                                }
                            }
                    }.join()
                }
            )
        }

        @Composable
        private inline fun ControlCompactCoordinator.ComposeRemotePropertiesReaderObserver() {
            LaunchedEffect(
                key1 = this,
                block = {
                    launch(coordinatorSupervisorJob) {
                        var latestCollectorJob: Job? = null
                        snapshotFlow { propertiesReaderCount }
                            .map {
                                check(it >= 0)
                                it > 0
                            }
                            .distinctUntilChanged()
                            .collect { hasActiveReader ->
                                if (!hasActiveReader) {
                                    remotePlaybackPropertiesSnapshot = PlaybackProperties.UNSET
                                    latestCollectorJob?.cancel()
                                    return@collect
                                }
                                check(latestCollectorJob?.isActive != true)
                                latestCollectorJob = launch {
                                    var collectWithFreezeHandle: Job? = null
                                    snapshotFlow { state.freeze }
                                        .collect { freeze ->
                                            if (freeze) {
                                                collectWithFreezeHandle?.cancel()
                                                return@collect
                                            }
                                            check(collectWithFreezeHandle?.isActive != true)
                                            collectWithFreezeHandle = launch {
                                                val observer = state.playbackController.createPlaybackObserver()
                                                try {
                                                    val collector = observer.createPropertiesCollector()
                                                        .apply { startCollect().join() }
                                                    collector.propertiesStateFlow
                                                        .collect {
                                                            remotePlaybackPropertiesSnapshot = it
                                                        }
                                                } finally {
                                                    observer.dispose()
                                                }
                                            }
                                        }
                                }
                            }
                    }.join()
                }
            )
        }
    }
}

class CompactControlTransitionState(
    private val getLayoutHeight: @SnapshotRead () -> Dp,
    private val getLayoutWidth: @SnapshotRead () -> Dp,
    private val getLayoutBottomSpacing: @SnapshotRead () -> Dp,
    private val observeQueue: () -> Flow<OldPlaybackQueue>
) {

    val applier: Applier = Applier(this)

    private var showSelf by mutableStateOf(false)

    companion object {

        @Composable
        internal fun CompactControlTransitionState.getLayoutOffset(
            constraints: Constraints,
            density: Density
        ): DpOffset {
            return remember {
                val override = getLayoutHeight()
                Animatable(
                    initialValue = with(density) {
                        if (override == Dp.Unspecified) {
                            (constraints.maxHeight).toDp()
                        } else {
                            override.coerceIn(0.dp, (constraints.maxHeight).toDp())
                        }
                    },
                    typeConverter = Dp.VectorConverter,
                )
            }.apply {
                LaunchedEffect(
                    key1 = this,
                    block = {
                        var latestAnimateJob: Job? = null
                        snapshotFlow { showSelf }
                            .collect { show ->
                                latestAnimateJob?.cancel()
                                latestAnimateJob = launch {
                                    if (show) {
                                        snapshotFlow { getLayoutBottomSpacing().unaryMinus() }
                                            .collect {
                                                Timber.d("RootPlaybackControlCompact_Transition: animateTo: $it")
                                                animateTo(it, tween(200))
                                            }
                                    } else {
                                        snapshotFlow { getLayoutHeight() }
                                            .collect {
                                                Timber.d("RootPlaybackControlCompact_Transition: animateTo: $it")
                                                animateTo(it, tween(200))
                                            }
                                    }
                                }
                            }
                    }
                )
            }.run {
                DpOffset(0.dp, value)
            }
        }

        @Composable
        internal fun CompactControlTransitionState.getLayoutWidth(
            constraints: Constraints
        ): Dp {
            val override = getLayoutWidth()
            return with(LocalDensity.current) {
                if (override == Dp.Unspecified) {
                    (constraints.maxWidth).toDp() - 15.dp
                } else {
                    override.coerceIn(0.dp, (constraints.maxWidth).toDp())
                }
            }
        }

        @Composable
        internal fun CompactControlTransitionState.getLayoutHeight(
            constraints: Constraints
        ): Dp {
            val override = getLayoutHeight()
            return with(LocalDensity.current) {
                if (override == Dp.Unspecified) {
                    55.dp
                } else {
                    override.coerceIn(0.dp, (constraints.maxHeight).toDp())
                }
            }
        }
    }

    class Applier(
        private val state: CompactControlTransitionState
    ) {
        companion object {
            @Composable
            fun Applier.PrepareComposition() {
                val coroutineScope = rememberCoroutineScope()
                DisposableEffect(
                    key1 = this,
                    effect = {
                        val queueListener = coroutineScope.launch {
                            state.observeQueue()
                                .collect { queue ->
                                    state.showSelf = queue.list.getOrNull(queue.currentIndex) != null
                                }
                        }
                        onDispose {
                            queueListener.cancel()
                        }
                    }
                )
            }
        }
    }
}
@OptIn(ExperimentalPagerApi::class)
class CompactControlPagerState(
    val layoutState: PagerState,
    val observeMetadata: (String) -> Flow<MediaMetadata?>,
    val observeArtwork: (String) -> Flow<Any?>,
    val observeQueue: () -> Flow<OldPlaybackQueue>,
    val isSurfaceDark: @SnapshotRead () -> Boolean
) {

    val applier = Applier(this)

    class Applier(private val state: CompactControlPagerState) {
        companion object {

            @Composable
            fun Applier.ComposeLayout(
                content: @Composable CompositionScope.() -> Unit
            ) {
                observeForScope()
                    .apply {
                        content()
                        // assume that invoking the content means the pager layout is recomposed
                        DoLayoutComposedWork()
                    }
            }

            @Composable
            private fun Applier.observeForScope(): CompositionScope {
                val composableCoroutineScope = rememberCoroutineScope()
                val state = remember {
                    mutableStateOf(
                        CompositionScope(
                            state.layoutState,
                            OldPlaybackQueue.UNSET,
                            CoroutineScope(
                                composableCoroutineScope.coroutineContext +
                                        SupervisorJob(composableCoroutineScope.coroutineContext.job)
                            ),
                        )
                    )
                }.apply {
                    DisposableEffect(
                        key1 = this,
                        effect = {
                            val supervisor = SupervisorJob(composableCoroutineScope.coroutineContext.job)
                            composableCoroutineScope.launch(supervisor) {
                                try {
                                    state.observeQueue()
                                        .collect { queue ->
                                            value.lifetimeCoroutineScope.cancel()
                                            val lifetimeCoroutineScope = CoroutineScope(
                                                currentCoroutineContext() + SupervisorJob(supervisor)
                                            )
                                            value = CompositionScope(state.layoutState, queue, lifetimeCoroutineScope)
                                        }
                                } finally {
                                    value.lifetimeCoroutineScope.cancel()
                                }
                            }
                            onDispose { supervisor.cancel() }
                        }
                    )
                }

                return state.value
            }

            @Composable
            private fun CompositionScope.DoLayoutComposedWork() {
                LaunchedEffect(
                    key1 = this,
                    block = {
                        lifetimeCoroutineScope.launch {
                            val targetIndex = queueData.currentIndex.coerceAtLeast(0)
                            layoutState.stopScroll(MutatePriority.PreventUserInput)
                            layoutState.animateScrollToPage(targetIndex)
                            snapshotFlow { layoutState.currentPage }
                                .first {
                                    it == targetIndex
                                }
                            onPageCorrected()
                        }
                        lifetimeCoroutineScope.launch {
                            awaitUserInteractionListener()
                            userScrollEnabled = true
                        }
                    }
                )
            }
        }
    }

    class CompositionScope(
        val layoutState: PagerState,
        val queueData: OldPlaybackQueue,
        val lifetimeCoroutineScope: CoroutineScope,
    ) {
        val userInteractionListenerInstallationJob = Job()


        var userScrollEnabled by mutableStateOf(false)

        private val pageCorrectionJob = Job()

        fun onPageCorrected() {
            check(pageCorrectionJob.isActive)
            pageCorrectionJob.complete()
        }

        fun onUserInteractionListenerInstalled() {
            check(pageCorrectionJob.isCompleted)
            check(userInteractionListenerInstallationJob.isActive)
            userInteractionListenerInstallationJob.complete()
        }

        suspend fun awaitPageCorrected() {
            pageCorrectionJob.join()
        }
        suspend fun awaitUserInteractionListener() {
            userInteractionListenerInstallationJob.join()
        }
    }
}

class CompactButtonControlsState(
    observePlaybackProperties: () -> Flow<PlaybackProperties>,
    setPlayWhenReady: (play: Boolean, joinCollectorDispatch: Boolean) -> Deferred<Result<Boolean>>
) {

    val applier = Applier()

    class Applier {
        companion object {
            @Suppress("NOTHING_TO_INLINE")
            @Composable
            inline fun Applier.PrepareCompositionInline() {
                DisposableEffect(key1 = this, effect = { onDispose { } })
            }
        }
    }


    class LayoutData()
}

class CompactControlTimeBarState(
    private val observeProgressWithIntervalHandle: (
        getInterval: (progress: Duration, duration: Duration, speed: Float) -> Duration
    ) -> Flow<Duration>
) {

    val applier = Applier()

    class Applier {

    }

    class LayoutData()
}

class CompactControlBackgroundState(
    private val observeArtwork: (String) -> Flow<Any?>,
    private val observeQueue: () -> Flow<OldPlaybackQueue>,
    private val coroutineScope: CoroutineScope,
    private val onComposingBackgroundColor: (Color) -> Unit
) {

    val applier = Applier(this)

    private var palette by mutableStateOf<Palette?>(null)

    fun Modifier.backgroundModifier(): Modifier {
        return composed {
            val backgroundColor = @Composable {
                val darkTheme = Theme.isDarkAsState().value
                val compositeOver = Theme.surfaceVariantColorAsState().value
                val factor = 0.7f
                val paletteGen = palette
                    .run {
                        remember(this, darkTheme) {
                            if (this is Palette) {
                                if (darkTheme) {
                                    getDarkVibrantColor(getMutedColor(getDominantColor(-1)))
                                } else {
                                    getVibrantColor(getLightMutedColor(getDominantColor(-1)))
                                }
                            } else {
                                -1
                            }
                        }
                    }
                remember(compositeOver, paletteGen) {
                    if (paletteGen == -1) {
                        compositeOver
                    } else {
                        Color(paletteGen).copy(factor).compositeOver(compositeOver)
                    }
                }
            }
            val animatedBackgroundTransitionColor by
                animateColorAsState(targetValue = backgroundColor())
            onComposingBackgroundColor(animatedBackgroundTransitionColor)
            background(animatedBackgroundTransitionColor)
        }
    }

    companion object {

    }

    class Applier(private val state: CompactControlBackgroundState) {

        private var compositionCount by mutableStateOf(0)

        fun launchQueueObserver() {
            check(compositionCount == 1)
            var latestJob: Job? = null
            state.coroutineScope.launch {
                snapshotFlow { compositionCount }
                    .map { it > 0 }
                    .distinctUntilChanged()
                    .collect { active ->
                        latestJob?.cancel()
                        if (active) {
                            latestJob = launch {
                                var latestBitmapObserver: Job? = null
                                state.observeQueue().collect { queue ->
                                    latestBitmapObserver?.cancel()
                                    val id = queue.list.getOrNull(queue.currentIndex)
                                        ?: run {
                                            state.palette = null
                                            return@collect
                                        }
                                    latestBitmapObserver = launch {
                                        state.observeArtwork(id)
                                            .collect { art ->
                                                if (art !is Bitmap) {
                                                    state.palette = null
                                                    return@collect
                                                }
                                                val palette = withContext(Dispatchers.IO) {
                                                    Palette.from(art).maximumColorCount(16).generate()
                                                }
                                                ensureActive()
                                                state.palette = palette
                                            }
                                    }
                                }
                            }
                        } else {
                            state.palette = null
                        }
                    }
            }
        }

        companion object {

            @Composable
            fun Applier.PrepareCompositionInline() {
                DisposableEffect(
                    key1 = this,
                    effect = {
                        check(compositionCount == 0)
                        compositionCount++
                        launchQueueObserver()
                        onDispose {
                            check(compositionCount == 1)
                            compositionCount--
                        }
                    }
                )
            }
        }
    }

    class LayoutData()
}

class CompactControlArtworkState(
    private val observeArtwork: (String) -> Flow<Any?>,
    private val observeQueue: () -> Flow<OldPlaybackQueue>
) {

    val applier = Applier(this)

    var latestQueue by mutableStateOf<OldPlaybackQueue>(OldPlaybackQueue.UNSET)

    companion object {

        fun CompactControlArtworkState.getInteractionModifier(): Modifier {
            return Modifier.composed { Modifier.clickable {  } }
        }

        fun CompactControlArtworkState.getLayoutModifier(imageModel: Any?): Modifier {
            return Modifier.composed {
                this
                    .fillMaxHeight()
                    .aspectRatio(1f, true)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Theme.surfaceVariantContentColorAsState().value)
                    .then(getInteractionModifier())
            }
        }

        @Composable
        fun CompactControlArtworkState.getImageModel(): Any {
            val data = latestQueue.let { queue ->
                val id = queue.list.getOrNull(queue.currentIndex)
                    ?: return@let null
                val flow = remember(id) {
                    observeArtwork(id)
                }
                flow.collectAsState(initial = Unit).value
            }
            val context = LocalContext.current
            return remember(data) {
                ImageRequest.Builder(context)
                    .data(data)
                    .crossfade(true)
                    .build()
            }
        }

        @Composable
        fun CompactControlArtworkState.getContentScale(imageModel: Any?): ContentScale {
            // TODO: The content scale will depends on the image data,
            //  Spotify for example doesn't allow cropping so we must `fit`
            return ContentScale.Crop
        }
    }

    class Applier(private val state: CompactControlArtworkState) {

        companion object {

            @Composable
            fun Applier.PrepareComposition() {
                val coroutineScope = rememberCoroutineScope()
                DisposableEffect(
                    key1 = this,
                    effect = {
                        val job = coroutineScope.launch {
                            state.observeQueue()
                                .collect { queue ->
                                    state.latestQueue = queue
                                }
                        }
                        onDispose { job.cancel() }
                    }
                )
            }
        }
    }

    class LayoutData()

    class CompositionScope {

    }
}