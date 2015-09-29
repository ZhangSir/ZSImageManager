package com.zs.imagemanager;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class IoUtils {
	/** 每次IO的缓存数据量，默认32K*/
	public static final int DEFAULT_BUFFER_SIZE = 32 * 1024; // 32 Kb
	/** 默认单张图片总大小（在从输入流中读取图片大小失败时，使用此默认值）*/
	public static final int DEFAULT_IMAGE_TOTAL_SIZE = 500 * 1024; // 500 Kb
	/** 是否继续加载图片的界限值，即当图片已经加载超过75%，则继续加载（不响应停止加载操作）*/
	public static final int CONTINUE_LOADING_PERCENTAGE = 75;
	
	/**
	 * Copies stream, fires progress events by listener, can be interrupted by listener.
	 *
	 * @param is         Input stream
	 * @param os         Output stream
	 * @param listener   null-ok; Listener of copying progress and controller of copying interrupting
	 * @param bufferSize Buffer size for copying, also represents a step for firing progress listener callback, i.e.
	 *                   progress event will be fired after every copied <b>bufferSize</b> bytes
	 * @return <b>true</b> - if stream copied successfully; <b>false</b> - if copying was interrupted by listener
	 * @throws IOException
	 */
	public static boolean copyStream(InputStream is, OutputStream os, CopyListener listener, int bufferSize)
			throws IOException {
		int current = 0;
		int total = is.available();
		if (total <= 0) {
			total = DEFAULT_IMAGE_TOTAL_SIZE;
		}

		final byte[] bytes = new byte[bufferSize];
		int count;
		if (shouldStopLoading(listener, current, total)) return false;
		while ((count = is.read(bytes, 0, bufferSize)) != -1) {
			os.write(bytes, 0, count);
			current += count;
			if (shouldStopLoading(listener, current, total)) return false;
		}
		os.flush();
		return true;
	}

	/**
	 * 将copy进度传给调用者，调用通过返回值告知是否继续加载copy
	 * @param listener
	 * @param current
	 * @param total
	 * @return
	 */
	private static boolean shouldStopLoading(CopyListener listener, int current, int total) {
		if (listener != null) {
			boolean shouldContinue = listener.onBytesCopied(current, total);
			if (!shouldContinue) {
				if (100 * current / total < CONTINUE_LOADING_PERCENTAGE) {
					return true; // if loaded more than 75% then continue loading anyway
				}
			}
		}
		return false;
	}
	
	/**
	 * Reads all data from stream and close it silently
	 * 从数据流读取所有的数据，并关闭数据流
	 * @param is Input stream
	 */
	public static void readAndCloseStream(InputStream is) {
		final byte[] bytes = new byte[DEFAULT_BUFFER_SIZE];
		try {
			while (is.read(bytes, 0, DEFAULT_BUFFER_SIZE) != -1) {
			}
		} catch (IOException e) {
			// Do nothing
		} finally {
			closeSilently(is);
		}
	}
	
	/**
	 * 关闭Closeable对象（InputStream继承Closeable）
	 * @param closeable
	 */
	public static void closeSilently(Closeable closeable) {
		try {
			closeable.close();
		} catch (Exception e) {
			// Do nothing
		}
	}
	
	/** 数据流copy和加载的监听器及控制器 */
	public static interface CopyListener {
		/**
		 * @param current Loaded bytes
		 * @param total   Total bytes for loading
		 * @return <b>true</b> - if copying should be continued; <b>false</b> - if copying should be interrupted
		 */
		boolean onBytesCopied(int current, int total);
	}
	
}
