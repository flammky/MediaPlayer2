package com.flammky.android.medialib.temp.image

import android.graphics.Bitmap
import com.flammky.android.medialib.temp.cache.lru.LruCache

interface ImageRepository {

	@Deprecated("")
	val sharedBitmapLru: LruCache<String, Bitmap>
	companion object {
		val NO_BITMAP = Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8)
	}
}
