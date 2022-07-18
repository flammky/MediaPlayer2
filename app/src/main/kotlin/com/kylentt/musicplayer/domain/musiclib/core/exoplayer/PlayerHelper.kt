package com.kylentt.musicplayer.domain.musiclib.core.exoplayer

import androidx.media3.common.Player
import com.kylentt.musicplayer.common.extenstions.clamp

object PlayerHelper {
  private const val minVol = PlayerConstants.MIN_VOLUME
  private const val maxVol = PlayerConstants.MAX_VOLUME

  /**
   * @param [vol] the requested Volume as [Float]
   * @return proper Volume according to [androidx.media3.session.MediaController.setVolume] range
   */

  fun fixVolumeToRange(vol: Float): Float = vol.clamp(minVol, maxVol)
}
