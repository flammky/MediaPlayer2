package com.flammky.musicplayer.base.coroutine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Dispatchers for non blocking operations
 */
@OptIn(ExperimentalCoroutinesApi::class)
object NonBlockingDispatcherPool {

	fun get(parallelism: Int): CoroutineDispatcher {
		return Dispatchers.Default.limitedParallelism(parallelism)
	}
}