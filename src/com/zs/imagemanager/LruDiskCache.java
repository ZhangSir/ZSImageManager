package com.zs.imagemanager;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import android.graphics.Bitmap;
import android.text.TextUtils;
import android.util.Log;

/**
 * SDCard����
 * @author zhangshuo
 */
public class LruDiskCache {

	private final String TAG = LruDiskCache.class.getSimpleName();
	
	/** ÿ��IO�Ļ�����������Ĭ��32K*/
	public final int DEFAULT_BUFFER_SIZE = 32 * 1024; // 32 Kb
	/** ͼƬѹ����ʽ��Ĭ��png*/
	public final Bitmap.CompressFormat DEFAULT_COMPRESS_FORMAT = Bitmap.CompressFormat.PNG;
	/** ͼƬѹ��������Ĭ��100��ѹ�� */
	public final int DEFAULT_COMPRESS_QUALITY = 100;
	/** ͼƬ�����ļ��ĺ�׺*/
	private final String TEMP_IMAGE_POSTFIX = ".tmp";
	/** �Ƿ��ļ���С*/
	private final int INVALID_SIZE = -1;
	
	private final FileNameGenerator fileNameGenerator;
	
	/**
	 * �����ļ��洢Ŀ¼
	 */
	private final File cacheDir;
	
	/**
	 * ��¼��ǰ����������ļ��Ĵ�С
	 */
	private final AtomicInteger cacheSize;
	/**
	 * �����ļ������ֵ
	 */
	private final long maxSize;
	/**
	 * ����SDCard�ϱ��������ͼƬ�ļ��������ķ���ʱ��
	 */
	private final Map<File, Long> lastUsageDates;
	
	/**
	 * ��ʼ��SDCard����
	 * @param cacheDir ����·��
	 * @param maxSize ��󻺴�����
	 */
	public LruDiskCache(File cacheDir, long maxSize){
		this(cacheDir, new FileNameGenerator(), maxSize);
	}
	
	/**
	 * ��ʼ��SDCard����
	 * @param cacheDir ����·��
	 * @param fileNameGenerator �ļ�����������
	 * @param maxSize ��󻺴�����
	 */
	public LruDiskCache(File cacheDir, FileNameGenerator fileNameGenerator, long maxSize){
		this.cacheDir = cacheDir;
		this.fileNameGenerator = fileNameGenerator;
		this.maxSize = maxSize;
		this.cacheSize = new AtomicInteger();
		this.lastUsageDates = Collections.synchronizedMap(new HashMap<File, Long>()); 
		this.calculateCacheSizeAndFillUsageMap();
	}
	
	/**
	 * ���̼߳���cacheDir�����ļ��Ĵ�С�������ļ�������޸ĵĺ��������뵽Map�� 
	 */
	private void calculateCacheSizeAndFillUsageMap(){
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				int size = 0;
				File[] cachedFiles  = cacheDir.listFiles();
				if(null != cachedFiles){
					for (int i = 0; i < cachedFiles.length; i++) {
						size = size + getSize(cachedFiles[i]);
						//���ļ�������޸�ʱ����뵽map��
						lastUsageDates.put(cachedFiles[i], cachedFiles[i].lastModified());
					}
					cacheSize.set(size);
				}
				
			}
		}).start();
	}
	
	/**
	 * ����bitmap��SDCard�����������ؽ���
	 * @param key
	 * @param imageStream
	 * @param listener
	 * @return true ����ɹ���false����ʧ��
	 * @throws IOException
	 */
	private boolean save(String key, InputStream imageStream, IoUtils.CopyListener listener) throws IOException {
		File imageFile = getFile(key);
		File tmpFile = new File(imageFile.getAbsolutePath() + TEMP_IMAGE_POSTFIX);
		boolean loaded = false;
		try {
			OutputStream os = new BufferedOutputStream(new FileOutputStream(tmpFile), DEFAULT_BUFFER_SIZE);
			try {
				loaded = IoUtils.copyStream(imageStream, os, listener, DEFAULT_BUFFER_SIZE);
			} finally {
				IoUtils.closeSilently(os);
			}
		} finally {
			IoUtils.closeSilently(imageStream);
			if (loaded && !tmpFile.renameTo(imageFile)) {
				loaded = false;
			}
			if (!loaded) {
				tmpFile.delete();
			}
		}
		return loaded;
	}
	
	/**
	 * ����bitmap��SDCard
	 * @param key
	 * @param bitmap
	 * @return true����ɹ���false����ʧ��
	 * @throws IOException
	 */
	private boolean save(String key, Bitmap bitmap) throws IOException {
		File imageFile = getFile(key);
		File tmpFile = new File(imageFile.getAbsolutePath() + TEMP_IMAGE_POSTFIX);
		OutputStream os = new BufferedOutputStream(new FileOutputStream(tmpFile), DEFAULT_BUFFER_SIZE);
		boolean savedSuccessfully = false;
		try {
			savedSuccessfully = bitmap.compress(DEFAULT_COMPRESS_FORMAT, DEFAULT_COMPRESS_QUALITY, os);
		} finally {
			IoUtils.closeSilently(os);
			if (savedSuccessfully && !tmpFile.renameTo(imageFile)) {
				savedSuccessfully = false;
			}
			if (!savedSuccessfully) {
				tmpFile.delete();
			}
		}
		bitmap.recycle();
		return savedSuccessfully;
	}
	
	/**
	 * ��bitmap���������浽SDCard����ӵ�map��¼�����������������ɹ����ŷ���true�����򷵻�false
	 * @param key
	 * @param imageStream
	 * @param listener ��Ϊnullʱ����ص����غͱ���bitmap�������Ľ��ȣ�����ͨ���ص�����ֵ��ֹͣ����bitmap������
	 * @return
	 * @throws IOException
	 */
	public boolean put(String key, InputStream imageStream, IoUtils.CopyListener listener) throws IOException{
		boolean isSaved = this.save(key, imageStream, listener);
		if(isSaved){
			return this.putToMap(key);
		}else{
			return isSaved;
		}
	}
	
	/**
	 * ��bitmap���浽SDCard����ӵ�map��¼�����������������ɹ����ŷ���true�����򷵻�false
	 * @param key
	 * @param bitmap
	 * @return
	 * @throws IOException
	 */
	public boolean put(String key, Bitmap bitmap) throws IOException{
		boolean isSaved = this.save(key, bitmap);
		if(isSaved){
			return this.putToMap(key);
		}else{
			return isSaved;
		}
	}
	
	/**
	 * ���ļ���ӵ�Map�У������㻺���ļ��Ĵ�С�Ƿ񳬹����������õ���󻺴��� 
     * �����˾�ɾ�����ȼ�����Ǹ��ļ� 
	 * @param key
	 * @return true put�ɹ���false putʧ��
	 */
	private boolean putToMap(String key){
		File file = this.getFile(key);  
        if(null == file || !file.exists()){
        	Log.e(TAG, "putToMap--�ļ�������--key->" + key);
        	return false;
        }
		int valueSize = getSize(file);
		int curCacheSize = this.cacheSize.get();
		
		while(curCacheSize + valueSize > maxSize){
			int freedSize = removeNext();  
            if (freedSize == INVALID_SIZE) break; // cache is empty (have nothing to delete)  
            curCacheSize = this.cacheSize.addAndGet(-freedSize); 
		}
		cacheSize.addAndGet(valueSize);  
		  
        Long currentTime = System.currentTimeMillis();  
        file.setLastModified(currentTime);  
        lastUsageDates.put(file, currentTime);  
        return true;
	}
	
	/**
	 * ����key�����ļ� ���������ļ��ķ���ʱ�� 
	 * @param key
	 * @return
	 */
    public File get(String key) {  
        File file = this.getFile(key);  
        if(null == file || !file.exists()){
        	Log.e(TAG, "get--�ļ�������--key->" + key);
        	return null;
        }
  
        Long currentTime = System.currentTimeMillis();  
        file.setLastModified(currentTime);  
        lastUsageDates.put(file, currentTime);  
  
        return file;  
    }  
    
    /**
     * ����ͼƬ��key������·����������ͼƬ��SDCard�ϵ�·��File
     * <p/>
     * ��File���ܶ�Ӧ��SDCard�ϴ����ļ�ʵ�壬Ҳ���ܲ�����
     * @param key
     * @return
     */
    private File getFile(String key) {
    	if(TextUtils.isEmpty(key)){
    		Log.e(TAG, "getFile--key->" + key);
    		return null;
    	}
		String fileName = fileNameGenerator.generate(key);
		return new File(cacheDir, fileName);
	}
  
    /** 
     * Ӳ�̻�������� 
     */  
    public void clear() {  
        lastUsageDates.clear();  
        cacheSize.set(0);  
    	File[] files = cacheDir.listFiles();
    	if (files != null) {
    		for (File f : files) {
    			f.delete();
    		}
    	}
    }  
	
	/**
	 * ��ȡ�������Ļ����ļ���������ɾ�� 
	 * @return
	 */
	private int removeNext(){
		if (lastUsageDates.isEmpty()) {  
            return INVALID_SIZE;  
        }  
		
		Long oldestUsage = null;  
        File mostLongUsedFile = null; 
        
        Set<Entry<File, Long>> entries = lastUsageDates.entrySet();  
        
        synchronized (lastUsageDates) {  
            for (Entry<File, Long> entry : entries) {  
                if (mostLongUsedFile == null) {  
                    mostLongUsedFile = entry.getKey();  
                    oldestUsage = entry.getValue();  
                } else {  
                    Long lastValueUsage = entry.getValue();  
                    if (lastValueUsage < oldestUsage) {  
                        oldestUsage = lastValueUsage;  
                        mostLongUsedFile = entry.getKey();  
                    }  
                }  
            }  
        }  
        
        int fileSize = 0;  
        if (mostLongUsedFile != null) {  
            if (mostLongUsedFile.exists()) {  
                fileSize = getSize(mostLongUsedFile);  
                if (mostLongUsedFile.delete()) {  
                    lastUsageDates.remove(mostLongUsedFile);  
                }  
            } else {  
                lastUsageDates.remove(mostLongUsedFile);  
            }  
        }  
        return fileSize;  
	}
	
	/**
	 * ��ȡ�ļ��Ĵ�С
	 * @param file
	 * @return
	 */
	private int getSize(File file) {  
        return (int) file.length();  
    } 
	
}
