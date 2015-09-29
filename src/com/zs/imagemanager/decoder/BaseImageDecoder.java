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
package com.zs.imagemanager.decoder;

import java.io.IOException;
import java.io.InputStream;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.util.Log;

import com.zs.imagemanager.ImageViewAware;
import com.zs.imagemanager.IoUtils;
import com.zs.imagemanager.downloader.ImageDownloader;

/**
 * Decodes images to {@link Bitmap}, scales them to needed size
 *
 * @author Sergey Tarasevich (nostra13[at]gmail[dot]com)
 * @see ImageDecodingInfo
 * @since 1.8.3
 */
public class BaseImageDecoder implements ImageDecoder {

	private static final String TAG = BaseImageDecoder.class.getSimpleName();
	
	protected static final String LOG_SUBSAMPLE_IMAGE = "Subsample original image (%1$s) to %2$s (scale = %3$d) [%4$s]";
	protected static final String LOG_SCALE_IMAGE = "Scale subsampled image (%1$s) to %2$s (scale = %3$.5f) [%4$s]";
	protected static final String LOG_ROTATE_IMAGE = "Rotate image on %1$d\u00B0 [%2$s]";
	protected static final String LOG_FLIP_IMAGE = "Flip image horizontally [%s]";
	protected static final String ERROR_CANT_DECODE_IMAGE = "Image can't be decoded [%s]";


	/**
	 * Decodes image from URI into {@link Bitmap}. Image is scaled close to incoming {@linkplain ImageSize target size}
	 * during decoding (depend on incoming parameters).
	 *
	 * @param decodingInfo Needed data for decoding image
	 * @return Decoded bitmap
	 * @throws IOException                   if some I/O exception occurs during image reading
	 * @throws UnsupportedOperationException if image URI has unsupported scheme(protocol)
	 */
	@Override
	public Bitmap decode(String uri, ImageViewAware imageAware, ImageDownloader downloader, Object extraForDownloader) throws IOException {
		Bitmap decodedBitmap;

		InputStream imageStream = getImageStream(uri, downloader, extraForDownloader);
		try {
			Log.d(TAG, "decode-uri-->" + uri);
			if(imageAware.isShouldCompress()){
				/*允许压缩图片*/
				int[] imageSize = defineImageSize(imageStream);
				Log.d(TAG, "decode-imageSize-->width:" + imageSize[0] + " height:" + imageSize[1]);
				imageStream = resetStream(imageStream, uri, downloader, extraForDownloader);
				Log.d(TAG, "decode-targetSize-->width:" + imageAware.getTargetSize()[0] + " height:" + imageAware.getTargetSize()[1]);
				Options decodingOptions = prepareDecodingOptions(imageSize, imageAware.getTargetSize());
				Log.d(TAG, "decode-scale-->" + decodingOptions.inSampleSize);
				decodedBitmap = BitmapFactory.decodeStream(imageStream, null, decodingOptions);
			}else{
				decodedBitmap = BitmapFactory.decodeStream(imageStream, null, null);
			}
//			imageInfo = defineImageSizeAndRotation(imageStream, decodingInfo);
//			imageStream = resetStream(imageStream, decodingInfo);
//			Options decodingOptions = prepareDecodingOptions(imageInfo.imageSize, decodingInfo);
		} finally {
			IoUtils.closeSilently(imageStream);
		}

		if (decodedBitmap == null) {
			Log.e(TAG, ERROR_CANT_DECODE_IMAGE + "-->" + uri);
		} else {
//			decodedBitmap = considerExactScaleAndOrientatiton(decodedBitmap, decodingInfo, imageInfo.exif.rotation,
//					imageInfo.exif.flipHorizontal);
		}
		return decodedBitmap;
	}

	protected InputStream getImageStream(String uri, ImageDownloader downloader, Object extraForDownloader) throws IOException {
		return downloader.getStream(uri, extraForDownloader);
	}
	
	/**
	 * 计算并返回图片的实际宽高
	 * @param imageStream
	 * @return int[] imageSize；imageSize[0]宽，imageSize[1]高
	 * @throws IOException
	 */
	protected int[] defineImageSize(InputStream imageStream)
			throws IOException {
		Options options = new Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeStream(imageStream, null, options);
		int[] imageSize = {options.outWidth, options.outHeight};
		return imageSize;
	}
	
//	protected ImageFileInfo defineImageSizeAndRotation(InputStream imageStream, ImageDecodingInfo decodingInfo)
//			throws IOException {
//		Options options = new Options();
//		options.inJustDecodeBounds = true;
//		BitmapFactory.decodeStream(imageStream, null, options);
//
//		ExifInfo exif;
//		String imageUri = decodingInfo.getImageUri();
//		if (decodingInfo.shouldConsiderExifParams() && canDefineExifParams(imageUri, options.outMimeType)) {
//			exif = defineExifOrientation(imageUri);
//		} else {
//			exif = new ExifInfo();
//		}
//		return new ImageFileInfo(new ImageSize(options.outWidth, options.outHeight, exif.rotation), exif);
//	}
//
//	private boolean canDefineExifParams(String imageUri, String mimeType) {
//		return "image/jpeg".equalsIgnoreCase(mimeType) && (Scheme.ofUri(imageUri) == Scheme.FILE);
//	}
//
//	protected ExifInfo defineExifOrientation(String imageUri) {
//		int rotation = 0;
//		boolean flip = false;
//		try {
//			ExifInterface exif = new ExifInterface(Scheme.FILE.crop(imageUri));
//			int exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
//			switch (exifOrientation) {
//				case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
//					flip = true;
//				case ExifInterface.ORIENTATION_NORMAL:
//					rotation = 0;
//					break;
//				case ExifInterface.ORIENTATION_TRANSVERSE:
//					flip = true;
//				case ExifInterface.ORIENTATION_ROTATE_90:
//					rotation = 90;
//					break;
//				case ExifInterface.ORIENTATION_FLIP_VERTICAL:
//					flip = true;
//				case ExifInterface.ORIENTATION_ROTATE_180:
//					rotation = 180;
//					break;
//				case ExifInterface.ORIENTATION_TRANSPOSE:
//					flip = true;
//				case ExifInterface.ORIENTATION_ROTATE_270:
//					rotation = 270;
//					break;
//			}
//		} catch (IOException e) {
//			L.w("Can't read EXIF tags from file [%s]", imageUri);
//		}
//		return new ExifInfo(rotation, flip);
//	}
//
	protected Options prepareDecodingOptions(int[] imageSize, int[] targetSize) {

		int	scale = computeImageSampleSize(imageSize[0], imageSize[1], targetSize[0], targetSize[1], true, true);

		Options decodingOptions = new Options();
		decodingOptions.inSampleSize = scale;
		return decodingOptions;
	}

	protected InputStream resetStream(InputStream imageStream, String uri, ImageDownloader downloader, Object extraForDownloader) throws IOException {
		try {
			imageStream.reset();
		} catch (IOException e) {
			IoUtils.closeSilently(imageStream);
			imageStream = getImageStream(uri, downloader, extraForDownloader);
		}
		return imageStream;
	}
//
//	protected Bitmap considerExactScaleAndOrientatiton(Bitmap subsampledBitmap, ImageDecodingInfo decodingInfo,
//			int rotation, boolean flipHorizontal) {
//		Matrix m = new Matrix();
//		// Scale to exact size if need
//		ImageScaleType scaleType = decodingInfo.getImageScaleType();
//		if (scaleType == ImageScaleType.EXACTLY || scaleType == ImageScaleType.EXACTLY_STRETCHED) {
//			ImageSize srcSize = new ImageSize(subsampledBitmap.getWidth(), subsampledBitmap.getHeight(), rotation);
//			float scale = ImageSizeUtils.computeImageScale(srcSize, decodingInfo.getTargetSize(), decodingInfo
//					.getViewScaleType(), scaleType == ImageScaleType.EXACTLY_STRETCHED);
//			if (Float.compare(scale, 1f) != 0) {
//				m.setScale(scale, scale);
//
//				if (loggingEnabled) {
//					L.d(LOG_SCALE_IMAGE, srcSize, srcSize.scale(scale), scale, decodingInfo.getImageKey());
//				}
//			}
//		}
//		// Flip bitmap if need
//		if (flipHorizontal) {
//			m.postScale(-1, 1);
//
//			if (loggingEnabled) L.d(LOG_FLIP_IMAGE, decodingInfo.getImageKey());
//		}
//		// Rotate bitmap if need
//		if (rotation != 0) {
//			m.postRotate(rotation);
//
//			if (loggingEnabled) L.d(LOG_ROTATE_IMAGE, rotation, decodingInfo.getImageKey());
//		}
//
//		Bitmap finalBitmap = Bitmap.createBitmap(subsampledBitmap, 0, 0, subsampledBitmap.getWidth(), subsampledBitmap
//				.getHeight(), m, true);
//		if (finalBitmap != subsampledBitmap) {
//			subsampledBitmap.recycle();
//		}
//		return finalBitmap;
//	}
//
//	protected static class ExifInfo {
//
//		public final int rotation;
//		public final boolean flipHorizontal;
//
//		protected ExifInfo() {
//			this.rotation = 0;
//			this.flipHorizontal = false;
//		}
//
//		protected ExifInfo(int rotation, boolean flipHorizontal) {
//			this.rotation = rotation;
//			this.flipHorizontal = flipHorizontal;
//		}
//	}
//
//	protected static class ImageFileInfo {
//
//		public final ImageSize imageSize;
//		public final ExifInfo exif;
//
//		protected ImageFileInfo(ImageSize imageSize, ExifInfo exif) {
//			this.imageSize = imageSize;
//			this.exif = exif;
//		}
//	}
	
	/**
	 * Computes sample size for downscaling image size (<b>srcSize</b>) to view size (<b>targetSize</b>). This sample
	 * size is used during
	 * {@linkplain BitmapFactory#decodeStream(java.io.InputStream, android.graphics.Rect, android.graphics.BitmapFactory.Options)
	 * decoding image} to bitmap.<br />
	 * <br />
	 * <b>Examples:</b><br />
	 * <p/>
	 * <pre>
	 * srcSize(100x100), targetSize(10x10), powerOf2Scale = true -> sampleSize = 8
	 * srcSize(100x100), targetSize(10x10), powerOf2Scale = false -> sampleSize = 10
	 *
	 * srcSize(100x100), targetSize(20x40), viewScaleType = FIT_INSIDE -> sampleSize = 5
	 * srcSize(100x100), targetSize(20x40), viewScaleType = CROP       -> sampleSize = 2
	 * </pre>
	 * <p/>
	 * <br />
	 * The sample size is the number of pixels in either dimension that correspond to a single pixel in the decoded
	 * bitmap. For example, inSampleSize == 4 returns an image that is 1/4 the width/height of the original, and 1/16
	 * the number of pixels. Any value <= 1 is treated the same as 1.
	 *
	 * @param srcSize       Original (image) size
	 * @param targetSize    Target (view) size
	 * @param viewScaleType {@linkplain ViewScaleType Scale type} for placing image in view
	 * @param powerOf2Scale <i>true</i> - if sample size be a power of 2 (1, 2, 4, 8, ...)
	 * @return Computed sample size
	 */
	public static int computeImageSampleSize(int srcWidth, int srcHeight, int targetWidth, int targetHeight,
			boolean isQualityPriority,
			boolean powerOf2Scale) {
		int scale = 1;

		if(isQualityPriority){
			if (powerOf2Scale) {
				final int halfWidth = srcWidth / 2;
				final int halfHeight = srcHeight / 2;
				while ((halfWidth / scale) > targetWidth && (halfHeight / scale) > targetHeight) { // &&
					scale *= 2;
				}
			} else {
				scale = Math.min(srcWidth / targetWidth, srcHeight / targetHeight); // min
			}
		}else{
			if (powerOf2Scale) {
				final int halfWidth = srcWidth / 2;
				final int halfHeight = srcHeight / 2;
				while ((halfWidth / scale) > targetWidth || (halfHeight / scale) > targetHeight) { // ||
					scale *= 2;
				}
			} else {
				scale = Math.max(srcWidth / targetWidth, srcHeight / targetHeight); // max
			}
		}
		
		if (scale < 1) {
			scale = 1;
		}

		return scale;
	}
}