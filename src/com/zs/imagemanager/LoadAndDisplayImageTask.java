package com.zs.imagemanager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import android.graphics.Bitmap;
import android.os.Handler;
import android.util.Log;

import com.zs.imagemanager.FailReason.FailType;
import com.zs.imagemanager.decoder.ImageDecoder;
import com.zs.imagemanager.downloader.ImageDownloader;
import com.zs.imagemanager.downloader.ImageDownloader.Scheme;

public class LoadAndDisplayImageTask implements Runnable, IoUtils.CopyListener{
	
	private static final String TAG = LoadAndDisplayImageTask.class.getSimpleName();

	private static final String LOG_WAITING_FOR_RESUME = "ImageLoader is paused. Waiting...  [%s]";
	private static final String LOG_RESUME_AFTER_PAUSE = ".. Resume loading [%s]";
	private static final String LOG_DELAY_BEFORE_LOADING = "Delay %d ms before loading...  [%s]";
	private static final String LOG_START_DISPLAY_IMAGE_TASK = "Start display image task [%s]";
	private static final String LOG_WAITING_FOR_IMAGE_LOADED = "Image already is loading. Waiting... [%s]";
	private static final String LOG_GET_IMAGE_FROM_MEMORY_CACHE_AFTER_WAITING = "...Get cached bitmap from memory after waiting. [%s]";
	private static final String LOG_LOAD_IMAGE_FROM_NETWORK = "Load image from network [%s]";
	private static final String LOG_LOAD_IMAGE_FROM_DISK_CACHE = "Load image from disk cache [%s]";
	private static final String LOG_RESIZE_CACHED_IMAGE_FILE = "Resize image in disk cache [%s]";
	private static final String LOG_PREPROCESS_IMAGE = "PreProcess image before caching in memory [%s]";
	private static final String LOG_POSTPROCESS_IMAGE = "PostProcess image before displaying [%s]";
	private static final String LOG_CACHE_IMAGE_IN_MEMORY = "Cache image in memory [%s]";
	private static final String LOG_CACHE_IMAGE_ON_DISK = "Cache image on disk [%s]";
	private static final String LOG_PROCESS_IMAGE_BEFORE_CACHE_ON_DISK = "Process image before cache on disk [%s]";
	private static final String LOG_TASK_CANCELLED_IMAGEAWARE_REUSED = "ImageAware is reused for another image. Task is cancelled. [%s]";
	private static final String LOG_TASK_CANCELLED_IMAGEAWARE_COLLECTED = "ImageAware was collected by GC. Task is cancelled. [%s]";
	private static final String LOG_TASK_INTERRUPTED = "Task was interrupted [%s]";

	private static final String ERROR_PRE_PROCESSOR_NULL = "Pre-processor returned null [%s]";
	private static final String ERROR_POST_PROCESSOR_NULL = "Post-processor returned null [%s]";
	private static final String ERROR_PROCESSOR_FOR_DISK_CACHE_NULL = "Bitmap processor for disk cache returned null [%s]";

	private ImageLoader loader;
	private final ImageLoaderEngine engine;
	private final Handler handler;

	// Helper references
	private final ImageDownloader downloader;
	private final ImageDecoder decoder;
	final String uri;
	private final String memoryCacheKey;
	final ImageViewAware imageAware;
	final ImageLoadingListener listener;
	final ImageLoadingProgressListener progressListener;
	private ReentrantLock loadFromUriLock;
	private LruMemoryCache memoryCache;
	private LruDiskCache diskCache;

	public LoadAndDisplayImageTask(String uri, String memoryCacheKey, ImageViewAware imageAware, 
			ImageLoader loader,
			ImageLoaderEngine engine, 
			ImageDownloader downloader,
			ImageDecoder decoder,
			LruMemoryCache memoryCache,
			LruDiskCache diskCache,
			ImageLoadingListener listener,
			ImageLoadingProgressListener progressListener,
			ReentrantLock loadFromUriLock,
			Handler handler) {
		this.loader = loader;
		this.engine = engine;
		this.handler = handler;

		this.downloader = downloader;
		this.decoder = decoder;
		this.uri = uri;
		this.memoryCacheKey = memoryCacheKey;
		this.imageAware = imageAware;
		this.memoryCache = memoryCache;
		this.diskCache = diskCache;
		this.listener = listener;
		this.progressListener = progressListener;
		this.loadFromUriLock = loadFromUriLock;
	}

	@Override
	public void run() {
		if (waitIfPaused()) return;

		Log.d(TAG, LOG_START_DISPLAY_IMAGE_TASK +"-->"+ memoryCacheKey);
		if (loadFromUriLock.isLocked()) {
			Log.d(TAG, LOG_WAITING_FOR_IMAGE_LOADED +"-->"+ memoryCacheKey);
		}

		loadFromUriLock.lock();
		Bitmap bmp;
		try {
			checkTaskNotActual();

			bmp = memoryCache.get(memoryCacheKey);
			if (bmp == null || bmp.isRecycled()) {
				bmp = tryLoadBitmap();
				if (bmp == null) return; // listener callback already was fired

				checkTaskNotActual();
				checkTaskInterrupted();

				Log.d(TAG, LOG_CACHE_IMAGE_IN_MEMORY + "-->" + memoryCacheKey);
				memoryCache.put(memoryCacheKey, bmp);
			} else {
				Log.d(TAG, LOG_GET_IMAGE_FROM_MEMORY_CACHE_AFTER_WAITING + "-->" + memoryCacheKey);
			}

			checkTaskNotActual();
			checkTaskInterrupted();
		} catch (TaskCancelledException e) {
			fireCancelEvent();
			return;
		} finally {
			loadFromUriLock.unlock();
		}

		DisplayBitmapTask displayBitmapTask = new DisplayBitmapTask(bmp, uri, memoryCacheKey, imageAware, listener, engine);
		runTask(displayBitmapTask, handler, engine);
	}
	
	/** @return <b>true</b> - if task should be interrupted; <b>false</b> - otherwise */
	private boolean waitIfPaused() {
		AtomicBoolean pause = engine.getPause();
		if (pause.get()) {
			synchronized (engine.getPauseLock()) {
				if (pause.get()) {
					Log.d(LOG_WAITING_FOR_RESUME, memoryCacheKey);
					try {
						engine.getPauseLock().wait();
					} catch (InterruptedException e) {
						Log.e(LOG_TASK_INTERRUPTED, memoryCacheKey);
						return true;
					}
					Log.d(LOG_RESUME_AFTER_PAUSE, memoryCacheKey);
				}
			}
		}
		return isTaskNotActual();
	}
	
	private Bitmap tryLoadBitmap() throws TaskCancelledException {
		Bitmap bitmap = null;
		try {
			File imageFile = diskCache.get(uri);
			if (imageFile != null && imageFile.exists()) {
				Log.d(TAG, LOG_LOAD_IMAGE_FROM_DISK_CACHE + "-->" + memoryCacheKey);
				checkTaskNotActual();
				bitmap = decodeImage(Scheme.FILE.wrap(imageFile.getAbsolutePath()));
			}
			if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
				Log.d(TAG, LOG_LOAD_IMAGE_FROM_NETWORK + "-->" + memoryCacheKey);

				String imageUriForDecoding = uri;
				if (tryCacheImageOnDisk()) {
					imageFile = diskCache.get(uri);
					if (imageFile != null) {
						imageUriForDecoding = Scheme.FILE.wrap(imageFile.getAbsolutePath());
					}
				}

				checkTaskNotActual();
				bitmap = decodeImage(imageUriForDecoding);

				if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
					fireFailEvent(FailType.DECODING_ERROR, null);
				}
			}
		} catch (IllegalStateException e) {
			fireFailEvent(FailType.NETWORK_DENIED, null);
		} catch (TaskCancelledException e) {
			throw e;
		} catch (IOException e) {
			Log.e(TAG, "tryLoadBitmap", e);
			fireFailEvent(FailType.IO_ERROR, e);
		} catch (OutOfMemoryError e) {
			Log.e(TAG, "tryLoadBitmap",e);
			fireFailEvent(FailType.OUT_OF_MEMORY, e);
		} catch (Throwable e) {
			Log.e(TAG, "tryLoadBitmap",e);
			fireFailEvent(FailType.UNKNOWN, e);
		}
		return bitmap;
	}
	
	private Bitmap decodeImage(String imageUri) throws IOException {
		Log.d(TAG, "memoryCacheKey-->" + memoryCacheKey);
		Log.d(TAG, "imageUri-->" + imageUri);
		return decoder.decode(imageUri, imageAware, downloader, null);
	}
	
	/** @return <b>true</b> - if image was downloaded successfully; <b>false</b> - otherwise */
	private boolean tryCacheImageOnDisk() throws TaskCancelledException {
		Log.d(TAG, LOG_CACHE_IMAGE_ON_DISK + "-->" + memoryCacheKey);

		boolean loaded;
		try {
			loaded = downloadImage();
//			if (loaded) {
//				int width = configuration.maxImageWidthForDiskCache;
//				int height = configuration.maxImageHeightForDiskCache;
//				if (width > 0 || height > 0) {
//					L.d(LOG_RESIZE_CACHED_IMAGE_FILE, memoryCacheKey);
//					resizeAndSaveImage(width, height); // TODO : process boolean result
//				}
//			}
		} catch (IOException e) {
			Log.e(TAG, "tryCacheImageOnDisk", e);
			loaded = false;
		}
		return loaded;
	}
	
	private boolean downloadImage() throws IOException {
		InputStream is = downloader.getStream(uri, null);
		return diskCache.put(uri, is, this);
	}
	
	@Override
	public boolean onBytesCopied(int current, int total) {
		// TODO Auto-generated method stub
		return fireProgressEvent(current, total);
	}

	/** @return <b>true</b> - if loading should be continued; <b>false</b> - if loading should be interrupted */
	private boolean fireProgressEvent(final int current, final int total) {
		if (isTaskInterrupted() || isTaskNotActual()) return false;
		if (progressListener != null) {
			Runnable r = new Runnable() {
				@Override
				public void run() {
					progressListener.onProgressUpdate(uri, imageAware.getWrappedView(), current, total);
				}
			};
			runTask(r, handler, engine);
		}
		return true;
	}
	
	private void fireFailEvent(final FailType failType, final Throwable failCause) {
		if (isTaskInterrupted() || isTaskNotActual()) return;
		Runnable r = new Runnable() {
			@Override
			public void run() {
				imageAware.setImageDrawable(loader.getImageOnFail());
				listener.onLoadingFailed(uri, imageAware.getWrappedView(), new FailReason(failType, failCause));
			}
		};
		runTask(r, handler, engine);
	}
	
	private void fireCancelEvent() {
		if (isTaskInterrupted()) return;
		Runnable r = new Runnable() {
			@Override
			public void run() {
				listener.onLoadingCancelled(uri, imageAware.getWrappedView());
			}
		};
		runTask(r, handler, engine);
	}
	
	/**
	 * @throws TaskCancelledException if task is not actual (target ImageAware is collected by GC or the image URI of
	 *                                this task doesn't match to image URI which is actual for current ImageAware at
	 *                                this moment)
	 */
	private void checkTaskNotActual() throws TaskCancelledException {
		checkViewCollected();
		checkViewReused();
	}
	
	/** @throws TaskCancelledException if target ImageAware is collected */
	private void checkViewCollected() throws TaskCancelledException {
		if (isViewCollected()) {
			throw new TaskCancelledException();
		}
	}
	
	/** @throws TaskCancelledException if target ImageAware is collected by GC */
	private void checkViewReused() throws TaskCancelledException {
		if (isViewReused()) {
			throw new TaskCancelledException();
		}
	}
	
	/**
	 * @return <b>true</b> - if task is not actual (target ImageAware is collected by GC or the image URI of this task
	 * doesn't match to image URI which is actual for current ImageAware at this moment)); <b>false</b> - otherwise
	 */
	private boolean isTaskNotActual() {
		return isViewCollected() || isViewReused();
	}
	
	/** @return <b>true</b> - if target ImageAware is collected by GC; <b>false</b> - otherwise */
	private boolean isViewCollected() {
		if (imageAware.isCollected()) {
			Log.d(TAG, LOG_TASK_CANCELLED_IMAGEAWARE_COLLECTED + "-->" + memoryCacheKey);
			return true;
		}
		return false;
	}
	
	/** @return <b>true</b> - if current ImageAware is reused for displaying another image; <b>false</b> - otherwise */
	private boolean isViewReused() {
		String currentCacheKey = engine.getLoadingUriForView(imageAware);
		// Check whether memory cache key (image URI) for current ImageAware is actual.
		// If ImageAware is reused for another task then current task should be cancelled.
		boolean imageAwareWasReused = !memoryCacheKey.equals(currentCacheKey);
		if (imageAwareWasReused) {
			Log.d(TAG, LOG_TASK_CANCELLED_IMAGEAWARE_REUSED + "-->" + memoryCacheKey);
			return true;
		}
		return false;
	}

	/** @throws TaskCancelledException if current task was interrupted */
	private void checkTaskInterrupted() throws TaskCancelledException {
		if (isTaskInterrupted()) {
			throw new TaskCancelledException();
		}
	}
	
	/** @return <b>true</b> - if current task was interrupted; <b>false</b> - otherwise */
	private boolean isTaskInterrupted() {
		if (Thread.interrupted()) {
			Log.d(TAG, LOG_TASK_INTERRUPTED + "-->" + memoryCacheKey);
			return true;
		}
		return false;
	}
	
	
	String getLoadingUri() {
		return uri;
	}
	
	static void runTask(Runnable r, Handler handler, ImageLoaderEngine engine) {
		if (handler == null) {
			engine.fireCallback(r);
		} else {
			handler.post(r);
		}
	}
	
	/**
	 * Exceptions for case when task is cancelled (thread is interrupted, image view is reused for another task, view is
	 * collected by GC).
	 *
	 * @author Sergey Tarasevich (nostra13[at]gmail[dot]com)
	 * @since 1.9.1
	 */
	class TaskCancelledException extends Exception {
	}
}
