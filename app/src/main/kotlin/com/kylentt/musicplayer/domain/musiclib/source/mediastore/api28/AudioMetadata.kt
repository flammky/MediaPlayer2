package com.kylentt.musicplayer.domain.musiclib.source.mediastore.api28

import com.kylentt.musicplayer.domain.musiclib.source.mediastore.audio.MediaStoreAudioMetadata

/**
 * class representing Audio File Metadata Information on MediaStore API 29 / Android 9.0 / Pie
 * @see MediaStore28.MediaColumns
 * @see MediaStore28.Audio
 */

class MediaStoreAudioMetadata28 private constructor(
	override val album: String,
	override val artist: String,
	override val bookmark: Long,
	override val composer: String,
	override val durationMs: Long,
	override val genre: String,
	override val title: String,
	override val track: String,
	override val year: Int
) : MediaStoreAudioMetadata() {

	class Builder internal constructor() {
		var album: String = ""
		var artist: String = ""
		var bookmark: Long = -1
		var composer: String = ""
		var durationMs: Long = -1
		var genre: String = ""
		var title: String = ""
		var track: String = ""
		var year: Int = -1

		internal fun build(): MediaStoreAudioMetadata28 {
			return MediaStoreAudioMetadata28(
				album = album,
				artist = artist,
				bookmark = bookmark,
				composer = composer,
				durationMs = durationMs,
				genre = genre,
				title = title,
				track = track,
				year = year
			)
		}

	}

	companion object {
		val empty = Builder().build()
	}
}
