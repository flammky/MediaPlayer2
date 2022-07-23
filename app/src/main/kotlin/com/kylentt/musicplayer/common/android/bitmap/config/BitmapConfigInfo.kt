package com.kylentt.musicplayer.common.android.bitmap.config

import android.graphics.Bitmap
import com.kylentt.musicplayer.core.sdk.VersionHelper

object BitmapConfigInfo {
	fun getPixelSize(config: Bitmap.Config): Int {
		return when {
			config == Bitmap.Config.ALPHA_8 -> 1
			config == Bitmap.Config.RGB_565 -> 2
			config == Bitmap.Config.ARGB_4444 -> 2
			config == Bitmap.Config.ARGB_8888 -> 4
			VersionHelper.hasSnowCone() && config == Bitmap.Config.RGBA_F16 -> 8
			else -> throw NotImplementedError()
		}
	}
}
