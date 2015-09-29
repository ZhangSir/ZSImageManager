package com.zs.imagemanager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.zs.imagemanager.decoder.ImageDecoder;
import com.zs.imagemanager.downloader.ImageDownloader;

public class ImageLoader {
	public static final String TAG = ImageLoader.class.getSimpleName();

	static final String LOG_INIT_CONFIG = "Initialize ImageLoader with configuration";
	static final String LOG_DESTROY = "Destroy ImageLoader";
	static final String LOG_LOAD_IMAGE_FROM_MEMORY_CACHE = "Load image from memory cache [%s]";

	private static final String WARNING_RE_INIT_CONFIG = "Try to initialize ImageLoader which had already been initialized before. " + "To re-init ImageLoader with new configuration call ImageLoader.destroy() at first.";
	private static final String ERROR_WRONG_ARGUMENTS = "Wrong arguments were passed to displayImage() method (ImageView reference must not be null)";
	private static final String ERROR_NOT_INIT = "ImageLoader must be init with configuration before using";
	private static final String ERROR_INIT_CONFIG_WITH_NULL = "ImageLoader configuration can not be initialized with null";
	
	public static int imageResourseOnLoading = R.drawable.ic_stub;
	public static int imageResourseOnFail = R.drawable.ic_error;
	public static int imageResourseOnEmptyUri = R.drawable.ic_empty;
	
	private ImageLoaderEngine engine;
	
	private ImageDownloader downloader;
	private LruMemoryCache memoryCache;
	private LruDiskCache diskCache;
	private ImageDecoder decoder;
	
	private Context context;
	
	/** 记录屏幕宽度*/
	public static int DISPLAY_WIDTH;
	/** 记录屏幕高度*/
	public static int DISPLAY_HEIGHT;

	private Handler handler = null;

	private volatile static ImageLoader instance;

	/** Returns singleton class instance */
	public static ImageLoader getInstance(Context context) {
		if (instance == null) {
			synchronized (ImageLoader.class) {
				if (instance == null) {
					instance = new ImageLoader(context);
				}
			}
		}
		return instance;
	}

	protected ImageLoader(Context context) {
		this.context = context;
		DISPLAY_WIDTH = this.context.getResources().getDisplayMetrics().widthPixels;
		DISPLAY_HEIGHT = this.context.getResources().getDisplayMetrics().heightPixels;
		downloader = DefaultConfigurationFactory.createImageDownloader(context);
		diskCache = DefaultConfigurationFactory.createDiskCache(context, 10240000);
		memoryCache = DefaultConfigurationFactory.createMemoryCache(context, 0);
		decoder = DefaultConfigurationFactory.createImageDecoder();
		engine = new ImageLoaderEngine(diskCache);
	}

	public Drawable getImageOnLoading(){
		return context.getResources().getDrawable(imageResourseOnLoading);
	}
	
	public Drawable getImageOnFail(){
		return context.getResources().getDrawable(imageResourseOnFail);
	}
	
	public Drawable getImageOnEmptyUri(){
		return context.getResources().getDrawable(imageResourseOnEmptyUri);
	}

	/**
	 * Adds display image task to execution pool. Image will be set to ImageAware when it's turn.<br />
	 * <b>NOTE:</b> {@link #init(ImageLoaderConfiguration)} method must be called before this method call
	 *
	 * @param uri              Image URI (i.e. "http://site.com/image.png", "file:///mnt/sdcard/image.png")
	 * @param imageAware       {@linkplain com.nostra13.universalimageloader.core.imageaware.ImageAware Image aware view}
	 *                         which should display image
	 * @param options          {@linkplain com.nostra13.universalimageloader.core.DisplayImageOptions Options} for image
	 *                         decoding and displaying. If <b>null</b> - default display image options
	 *                         {@linkplain ImageLoaderConfiguration.Builder#defaultDisplayImageOptions(DisplayImageOptions)
	 *                         from configuration} will be used.
	 * @param listener         {@linkplain ImageLoadingListener Listener} for image loading process. Listener fires
	 *                         events on UI thread if this method is called on UI thread.
	 * @param progressListener {@linkplain com.nostra13.universalimageloader.core.listener.ImageLoadingProgressListener
	 *                         Listener} for image loading progress. Listener fires events on UI thread if this method
	 *                         is called on UI thread. Caching on disk should be enabled in
	 *                         {@linkplain com.nostra13.universalimageloader.core.DisplayImageOptions options} to make
	 *                         this listener work.
	 * @throws IllegalStateException    if {@link #init(ImageLoaderConfiguration)} method wasn't called before
	 * @throws IllegalArgumentException if passed <b>imageAware</b> is null
	 */
	public void displayImage(String uri, ImageViewAware imageAware,
			ImageLoadingListener listener, ImageLoadingProgressListener progressListener) {
		if (imageAware == null) {
			throw new IllegalArgumentException(ERROR_WRONG_ARGUMENTS);
		}

		if (TextUtils.isEmpty(uri)) {
			engine.cancelDisplayTaskFor(imageAware);
			listener.onLoadingStarted(uri, imageAware.getWrappedView());
			imageAware.setImageDrawable(getImageOnEmptyUri());
			listener.onLoadingComplete(uri, imageAware.getWrappedView(), null);
			return;
		}

		String memoryCacheKey = uri;//默认不压缩图片时memoryCacheKey的值为uri
		if(imageAware.isShouldCompress()){//是否压缩图片，如果压缩图片则memoryCacheKey的值为uri_width X heigth;
			int[] targetSize = defineTagetSize(imageAware);
			memoryCacheKey = FileNameGenerator.generateMemoryCacheKey(uri, targetSize);
		}
		
		engine.prepareDisplayTaskFor(imageAware, memoryCacheKey);

		listener.onLoadingStarted(uri, imageAware.getWrappedView());

		Bitmap bmp = memoryCache.get(memoryCacheKey);
		if (bmp != null && !bmp.isRecycled()) {
			Log.d(TAG, LOG_LOAD_IMAGE_FROM_MEMORY_CACHE + "-->" + memoryCacheKey);
			imageAware.setImageBitmap(bmp);
			listener.onLoadingComplete(uri, imageAware.getWrappedView(), bmp);
		} else {
			imageAware.setImageDrawable(getImageOnLoading());

			LoadAndDisplayImageTask displayTask = new LoadAndDisplayImageTask(
					uri, memoryCacheKey, imageAware, 
					instance,
					engine, 
					downloader,
					decoder,
					memoryCache,
					diskCache,
					listener,
					progressListener,
					engine.getLockForUri(uri),
					defineHandler());
			engine.submit(displayTask);
		}
	}
	
	/**
	 * 为指定view定义压缩图片的目标大小
	 * @param view
	 * @return int数组targetSize;targetSize[0]是宽度，targetSize[1]是高度
	 */
	private int[] defineTagetSize(ImageViewAware view) {
		int[] targetSize = view.getTargetSize();
		if(targetSize[0] > 0 && targetSize[1] > 0){//ImageViewAware中已有合法的targetSize
			return targetSize;
		}else{//ImageViewAware中的targetSize不合法，则getview的大小和屏幕大小重新计算得到targetSize；
			DisplayMetrics displayMetrics = this.context.getResources().getDisplayMetrics();
			
			targetSize[0] = view.getWidth();
			if (targetSize[0] <= 0) targetSize[0] = displayMetrics.widthPixels;

			targetSize[1] = view.getHeight();
			if (targetSize[1] <= 0) targetSize[1] = displayMetrics.heightPixels;
			//重新设置targetSize给ImageViewAware
			view.setTargetSize(targetSize);
		}
		
		return targetSize;
	}

	private Handler defineHandler() {
		if(handler == null && Looper.myLooper() == Looper.getMainLooper()) {
			handler = new Handler();
		}
		return handler;
	}
	
	
	/**
	 * Returns memory cache
	 *
	 * @throws IllegalStateException if {@link #init(ImageLoaderConfiguration)} method wasn't called before
	 */
	public LruMemoryCache getMemoryCache() {
		return memoryCache;
	}

	/**
	 * Clears memory cache
	 *
	 * @throws IllegalStateException if {@link #init(ImageLoaderConfiguration)} method wasn't called before
	 */
	public void clearMemoryCache() {
		memoryCache.clear();
	}

	/**
	 * Returns disk cache
	 *
	 * @throws IllegalStateException if {@link #init(ImageLoaderConfiguration)} method wasn't called before
	 */
	public LruDiskCache getDiskCache() {
		return diskCache;
	}

	/**
	 * Clears disk cache.
	 *
	 * @throws IllegalStateException if {@link #init(ImageLoaderConfiguration)} method wasn't called before
	 */
	public void clearDiskCache() {
		diskCache.clear();
	}

	/**
	 * Returns URI of image which is loading at this moment into passed
	 * {@link com.nostra13.universalimageloader.core.imageaware.ImageAware ImageAware}
	 */
	public String getLoadingUriForView(ImageViewAware imageAware) {
		return engine.getLoadingUriForView(imageAware);
	}

	/**
	 * Returns URI of image which is loading at this moment into passed
	 * {@link android.widget.ImageView ImageView}
	 */
	public String getLoadingUriForView(ImageView imageView) {
		return engine.getLoadingUriForView(new ImageViewAware(imageView));
	}

	/**
	 * Cancel the task of loading and displaying image for passed
	 * {@link com.nostra13.universalimageloader.core.imageaware.ImageAware ImageAware}.
	 *
	 * @param imageAware {@link com.nostra13.universalimageloader.core.imageaware.ImageAware ImageAware} for
	 *                   which display task will be cancelled
	 */
	public void cancelDisplayTask(ImageViewAware imageAware) {
		engine.cancelDisplayTaskFor(imageAware);
	}

	/**
	 * Cancel the task of loading and displaying image for passed
	 * {@link android.widget.ImageView ImageView}.
	 *
	 * @param imageView {@link android.widget.ImageView ImageView} for which display task will be cancelled
	 */
	public void cancelDisplayTask(ImageView imageView) {
		engine.cancelDisplayTaskFor(new ImageViewAware(imageView));
	}

	/**
	 * Denies or allows ImageLoader to download images from the network.<br />
	 * <br />
	 * If downloads are denied and if image isn't cached then
	 * {@link ImageLoadingListener#onLoadingFailed(String, View, FailReason)} callback will be fired with
	 * {@link FailReason.FailType#NETWORK_DENIED}
	 *
	 * @param denyNetworkDownloads pass <b>true</b> - to deny engine to download images from the network; <b>false</b> -
	 *                             to allow engine to download images from network.
	 */
	public void denyNetworkDownloads(boolean denyNetworkDownloads) {
		engine.denyNetworkDownloads(denyNetworkDownloads);
	}

	/**
	 * Sets option whether ImageLoader will use {@link FlushedInputStream} for network downloads to handle <a
	 * href="http://code.google.com/p/android/issues/detail?id=6066">this known problem</a> or not.
	 *
	 * @param handleSlowNetwork pass <b>true</b> - to use {@link FlushedInputStream} for network downloads; <b>false</b>
	 *                          - otherwise.
	 */
	public void handleSlowNetwork(boolean handleSlowNetwork) {
		engine.handleSlowNetwork(handleSlowNetwork);
	}

	/**
	 * Pause ImageLoader. All new "load&display" tasks won't be executed until ImageLoader is {@link #resume() resumed}.
	 * <br />
	 * Already running tasks are not paused.
	 */
	public void pause() {
		engine.pause();
	}

	/** Resumes waiting "load&display" tasks */
	public void resume() {
		engine.resume();
	}

	/**
	 * Cancels all running and scheduled display image tasks.<br />
	 * <b>NOTE:</b> This method doesn't shutdown
	 * {@linkplain com.nostra13.universalimageloader.core.ImageLoaderConfiguration.Builder#taskExecutor(java.util.concurrent.Executor)
	 * custom task executors} if you set them.<br />
	 * ImageLoader still can be used after calling this method.
	 */
	public void stop() {
		engine.stop();
	}

	/**
	 * {@linkplain #stop() Stops ImageLoader} and clears current configuration. <br />
	 * You can {@linkplain #init(ImageLoaderConfiguration) init} ImageLoader with new configuration after calling this
	 * method.
	 */
	public void destroy() {
		stop();
		downloader = null;
		decoder = null;
		memoryCache = null;
		diskCache = null;
		engine = null;
		instance = null;
	}
}
