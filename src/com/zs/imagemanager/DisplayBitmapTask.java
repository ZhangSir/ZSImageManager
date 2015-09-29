/*******************************************************************************
 * Copyright 2011-2013 Sergey Tarasevich
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.zs.imagemanager;

import android.graphics.Bitmap;
import android.util.Log;

/**
 * Displays bitmap in {@link com.nostra13.universalimageloader.core.imageaware.ImageAware}. Must be called on UI thread.
 *
 * @author Sergey Tarasevich (nostra13[at]gmail[dot]com)
 * @see ImageLoadingListener
 * @see BitmapDisplayer
 * @since 1.3.1
 */
final class DisplayBitmapTask implements Runnable {

	private static final String TAG = DisplayBitmapTask.class.getSimpleName();
	
	private static final String LOG_DISPLAY_IMAGE_IN_IMAGEAWARE = "Display image in ImageAware (loaded from %1$s) [%2$s]";
	private static final String LOG_TASK_CANCELLED_IMAGEAWARE_REUSED = "ImageAware is reused for another image. Task is cancelled. [%s]";
	private static final String LOG_TASK_CANCELLED_IMAGEAWARE_COLLECTED = "ImageAware was collected by GC. Task is cancelled. [%s]";

	private final Bitmap bitmap;
	private final String imageUri;
	private final ImageViewAware imageAware;
	private final String memoryCacheKey;
	private final ImageLoadingListener listener;
	private final ImageLoaderEngine engine;

	public DisplayBitmapTask(Bitmap bitmap, String uri, String memoryCacheKey, ImageViewAware imageAware,
			ImageLoadingListener listener, ImageLoaderEngine engine) {
		this.bitmap = bitmap;
		this.imageUri = uri;
		this.imageAware = imageAware;
		this.memoryCacheKey = memoryCacheKey;
		this.listener = listener;
		this.engine = engine;
	}

	@Override
	public void run() {
		if (imageAware.isCollected()) {
			Log.d(TAG, LOG_TASK_CANCELLED_IMAGEAWARE_COLLECTED + "-->" + memoryCacheKey);
			listener.onLoadingCancelled(imageUri, imageAware.getWrappedView());
		} else if (isViewWasReused()) {
			Log.d(TAG, LOG_TASK_CANCELLED_IMAGEAWARE_REUSED + "-->" + memoryCacheKey);
			listener.onLoadingCancelled(imageUri, imageAware.getWrappedView());
		} else {
			Log.d(TAG, LOG_DISPLAY_IMAGE_IN_IMAGEAWARE + "-->" + memoryCacheKey);
			imageAware.setImageBitmap(bitmap);
			engine.cancelDisplayTaskFor(imageAware);
			listener.onLoadingComplete(imageUri, imageAware.getWrappedView(), bitmap);
		}
	}

	/** Checks whether memory cache key (image URI) for current ImageAware is actual */
	private boolean isViewWasReused() {
		String currentCacheKey = engine.getLoadingUriForView(imageAware);
		return !memoryCacheKey.equals(currentCacheKey);
	}
}
