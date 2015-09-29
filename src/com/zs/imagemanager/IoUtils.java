package com.zs.imagemanager;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class IoUtils {
	/** ÿ��IO�Ļ�����������Ĭ��32K*/
	public static final int DEFAULT_BUFFER_SIZE = 32 * 1024; // 32 Kb
	/** Ĭ�ϵ���ͼƬ�ܴ�С���ڴ��������ж�ȡͼƬ��Сʧ��ʱ��ʹ�ô�Ĭ��ֵ��*/
	public static final int DEFAULT_IMAGE_TOTAL_SIZE = 500 * 1024; // 500 Kb
	/** �Ƿ��������ͼƬ�Ľ���ֵ������ͼƬ�Ѿ����س���75%����������أ�����Ӧֹͣ���ز�����*/
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
	 * ��copy���ȴ��������ߣ�����ͨ������ֵ��֪�Ƿ��������copy
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
	 * ����������ȡ���е����ݣ����ر�������
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
	 * �ر�Closeable����InputStream�̳�Closeable��
	 * @param closeable
	 */
	public static void closeSilently(Closeable closeable) {
		try {
			closeable.close();
		} catch (Exception e) {
			// Do nothing
		}
	}
	
	/** ������copy�ͼ��صļ������������� */
	public static interface CopyListener {
		/**
		 * @param current Loaded bytes
		 * @param total   Total bytes for loading
		 * @return <b>true</b> - if copying should be continued; <b>false</b> - if copying should be interrupted
		 */
		boolean onBytesCopied(int current, int total);
	}
	
}
