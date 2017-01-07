/*
 * Copyright (C) 2010 Ionut Dediu <deionut@yahoo.com>
 *
 * Licensed under the GNU General Public License Version 2
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.smartcam.webcam.video;

public class JpegHandler
{
	public native byte[] initRgb(int frameBufferSize, int frameWidth, int frameHeight);
	public native byte[] initYuv(int frameBufferSize, int frameWidth, int frameHeight);
	public native byte[] encodeYUV420SP(byte[] yuv420spBuff);
	public native byte[] encodeRGB24(byte[] rgba8888Buff);

	static
	{
		System.loadLibrary("jpeghandler");
	}
}
