/**
 * @author : Paul Taylor
 *
 * Version @version:$Id$
 *
 * Jaudiotagger Copyright (C)2004,2005
 *
 * This library is free software; you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public  License as published by the Free Software Foundation; either version 2.1 of the License,
 * or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License ainteger with this library; if not,
 * you can get a copy from http://www.opensource.org/licenses/lgpl-license.php or write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 *
 * Description:
 * Channel type used by
 */
package com.kylentt.musicplayer.common.media.audio.meta_tag.tag.id3.valuepair

import com.kylentt.musicplayer.common.media.audio.meta_tag.tag.datatype.AbstractIntStringValuePair

class ChannelTypes private constructor() : AbstractIntStringValuePair() {
	init {
		idToValue[0x00] = "Other"
		idToValue[0x01] = "Master volume"
		idToValue[0x02] = "Front right"
		idToValue[0x03] = "Front left"
		idToValue[0x04] = "Back right"
		idToValue[0x05] = "Back left"
		idToValue[0x06] = "Front centre"
		idToValue[0x07] = "Back centre"
		idToValue[0x08] = "Subwoofer"
		createMaps()
	}

	companion object {
		private var channelTypes: ChannelTypes? = null

		@JvmStatic
		val instanceOf: ChannelTypes
			get() {
				if (channelTypes == null) {
					channelTypes = ChannelTypes()
				}
				return channelTypes!!
			}
	}
}
