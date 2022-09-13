/*
 *  @author : Paul Taylor
 *  @author : Eric Farng
 *
 *  Version @version:$Id$
 *
 *  MusicTag Copyright (C)2003,2004
 *
 *  This library is free software; you can redistribute it and/or modify it under the terms of the GNU Lesser
 *  General Public  License as published by the Free Software Foundation; either version 2.1 of the License,
 *  or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 *  the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License along with this library; if not,
 *  you can get a copy from http://www.opensource.org/licenses/lgpl-license.php or write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 *
 */
package com.kylentt.musicplayer.common.media.audio.meta_tag.tag

/**
 * Thrown if a frame identifier isn't valid.
 *
 * @author Eric Farng
 * @version $Revision$
 */
open class InvalidFrameIdentifierException : InvalidFrameException {
	/**
	 * Creates a new InvalidFrameIdentifierException datatype.
	 */
	constructor()

	/**
	 * Creates a new InvalidFrameIdentifierException datatype.
	 *
	 * @param ex the cause.
	 */
	constructor(ex: Throwable?) : super(ex)

	/**
	 * Creates a new InvalidFrameIdentifierException datatype.
	 *
	 * @param msg the detail message.
	 */
	constructor(msg: String?) : super(msg)

	/**
	 * Creates a new InvalidFrameIdentifierException datatype.
	 *
	 * @param msg the detail message.
	 * @param ex  the cause.
	 */
	constructor(msg: String?, ex: Throwable?) : super(msg, ex)

	companion object {
		/**
		 *
		 */
		private const val serialVersionUID = 6459527941265009134L
	}
}
