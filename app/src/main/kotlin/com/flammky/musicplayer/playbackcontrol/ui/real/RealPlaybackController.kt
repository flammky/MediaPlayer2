package com.flammky.musicplayer.playbackcontrol.ui.real

import androidx.annotation.GuardedBy
import com.flammky.kotlin.common.sync.sync
import com.flammky.musicplayer.media.mediaconnection.playback.PlaybackConnection
import com.flammky.musicplayer.playbackcontrol.ui.controller.PlaybackController
import com.flammky.musicplayer.playbackcontrol.ui.presenter.PlaybackObserver
import com.flammky.musicplayer.ui.playbackcontrol.RealPlaybackControlPresenter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal class RealPlaybackController(
	val owner: Any,
	private val scope: CoroutineScope,
	private val presenter: RealPlaybackControlPresenter,
	private val playbackConnection: PlaybackConnection
) : PlaybackController {

	@GuardedBy("this")
	private var _disposed = false

	private val _observers = mutableListOf<RealPlaybackObserver>()

	override fun dispose() {
		sync {
			if (_disposed) {
				return checkDisposedState()
			}
			scope.cancel()
			disposeObservers()
		}
		presenter.notifyControllerDisposed(this)
	}

	override fun createObserver(): PlaybackObserver {
		return RealPlaybackObserver(
			controller = this,
			parentScope = scope,
			playbackConnection = playbackConnection
		).also {
			sync { if (_disposed) it.dispose() else _observers.sync { add(it) } }
		}
	}

	override fun observePlayCommand(): Flow<Boolean> {
		TODO("Not yet implemented")
	}

	override fun observePauseCommand(): Flow<Boolean> {
		TODO("Not yet implemented")
	}

	override fun requestSeekAsync(position: Duration): Deferred<PlaybackController.RequestResult> {
		return scope.async {
			val success = playbackConnection.getSession()?.controller?.withContext { seekProgress(position) }
				?: false
			PlaybackController.RequestResult(
				success = success,
				eventDispatch = if (success) {
					launch {
						val jobs = mutableListOf<Job>()
						_observers.forEach { jobs.add(it.updateProgress()) }
						jobs.joinAll()
					}
				} else {
					null
				}
			)
		}
	}

	override fun requestSeekAsync(progress: Float): Deferred<PlaybackController.RequestResult> {
		return scope.async {
			val success = playbackConnection.getSession()?.controller?.withContext {
				seekProgress((duration.inWholeMilliseconds * progress).toLong().milliseconds)
			} ?: false
			PlaybackController.RequestResult(
				success = success,
				eventDispatch = if (success) {
					launch {
						val jobs = mutableListOf<Job>()
						_observers.forEach { jobs.add(it.updateProgress()) }
						jobs.joinAll()
					}
				} else {
					null
				}
			)
		}
	}

	override fun requestSeekAsync(index: Int, startPosition: Duration): Deferred<PlaybackController.RequestResult> {
		return scope.async {
			val success = playbackConnection.getSession()?.controller?.withContext {
				seekIndex(index, startPosition)
			} ?: false
			PlaybackController.RequestResult(
				success = success,
				eventDispatch = if (success) {
					launch {
						val jobs = mutableListOf<Job>()
						_observers.forEach { jobs.add(it.updateProgress()) }
						jobs.joinAll()
					}
				} else {
					null
				}
			)
		}
	}

	fun notifyObserverDisposed(observer: RealPlaybackObserver) {
		_observers.sync {
			remove(observer)
		}
	}

	private fun checkDisposedState() {
		check(Thread.holdsLock(this))
		check(!scope.isActive && _observers.sync { isEmpty() }) {
			"Controller was not disposed properly"
		}
	}

	private fun disposeObservers() {
		debugDisposeObservers()
	}

	private fun debugDisposeObservers() {
		_observers.sync {
			val actual = this
			val copy = ArrayList(this)
			var count = copy.size
			for (observer in copy) {
				observer.dispose()
				check(actual.size == --count && actual.firstOrNull() != observer) {
					"Observer $observer did not notify Controller ${this@RealPlaybackController} on disposal"
				}
			}
			check(actual.isEmpty() && count == 0) {
				"disposeObservers failed"
			}
		}
	}
}
