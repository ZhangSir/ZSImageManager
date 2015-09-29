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
 * SDCard缓存
 * @author zhangshuo
 */
public class LruDiskCache {

	private final String TAG = LruDiskCache.class.getSimpleName();
	
	/** 每次IO的缓存数据量，默认32K*/
	public final int DEFAULT_BUFFER_SIZE = 32 * 1024; // 32 Kb
	/** 图片压缩格式，默认png*/
	public final Bitmap.CompressFormat DEFAULT_COMPRESS_FORMAT = Bitmap.CompressFormat.PNG;
	/** 图片压缩质量，默认100不压缩 */
	public final int DEFAULT_COMPRESS_QUALITY = 100;
	/** 图片缓存文件的后缀*/
	private final String TEMP_IMAGE_POSTFIX = ".tmp";
	/** 非法文件大小*/
	private final int INVALID_SIZE = -1;
	
	private final FileNameGenerator fileNameGenerator;
	
	/**
	 * 缓存文件存储目录
	 */
	private final File cacheDir;
	
	/**
	 * 记录当前缓存的所有文件的大小
	 */
	private final AtomicInteger cacheSize;
	/**
	 * 缓存文件的最大值
	 */
	private final long maxSize;
	/**
	 * 保存SDCard上保存的所有图片文件及其最后的访问时间
	 */
	private final Map<File, Long> lastUsageDates;
	
	/**
	 * 初始化SDCard缓存
	 * @param cacheDir 缓存路径
	 * @param maxSize 最大缓存容量
	 */
	public LruDiskCache(File cacheDir, long maxSize){
		this(cacheDir, new FileNameGenerator(), maxSize);
	}
	
	/**
	 * 初始化SDCard缓存
	 * @param cacheDir 缓存路径
	 * @param fileNameGenerator 文件名字生产者
	 * @param maxSize 最大缓存容量
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
	 * 另开线程计算cacheDir里面文件的大小，并将文件和最后修改的毫秒数加入到Map中 
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
						//将文件的最后修改时间加入到map中
						lastUsageDates.put(cachedFiles[i], cachedFiles[i].lastModified());
					}
					cacheSize.set(size);
				}
				
			}
		}).start();
	}
	
	/**
	 * 保存bitmap到SDCard，并监听下载进度
	 * @param key
	 * @param imageStream
	 * @param listener
	 * @return true 保存成功，false保存失败
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
	 * 保存bitmap到SDCard
	 * @param key
	 * @param bitmap
	 * @return true保存成功，false保存失败
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
	 * 将bitmap输入流保存到SDCard并添加到map记录，必须两个操作都成功，才返回true，否则返回false
	 * @param key
	 * @param imageStream
	 * @param listener 不为null时，则回调加载和保存bitmap输入流的进度，并可通过回调返回值，停止加载bitmap输入流
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
	 * 将bitmap保存到SDCard并添加到map记录，必须两个操作都成功，才返回true，否则返回false
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
	 * 将文件添加到Map中，并计算缓存文件的大小是否超过了我们设置的最大缓存数 
     * 超过了就删除最先加入的那个文件 
	 * @param key
	 * @return true put成功，false put失败
	 */
	private boolean putToMap(String key){
		File file = this.getFile(key);  
        if(null == file || !file.exists()){
        	Log.e(TAG, "putToMap--文件不存在--key->" + key);
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
	 * 根据key生成文件 ，并更新文件的访问时间 
	 * @param key
	 * @return
	 */
    public File get(String key) {  
        File file = this.getFile(key);  
        if(null == file || !file.exists()){
        	Log.e(TAG, "get--文件不存在--key->" + key);
        	return null;
        }
  
        Long currentTime = System.currentTimeMillis();  
        file.setLastModified(currentTime);  
        lastUsageDates.put(file, currentTime);  
  
        return file;  
    }  
    
    /**
     * 根据图片的key（下载路径），返回图片在SDCard上的路径File
     * <p/>
     * 该File可能对应在SDCard上存在文件实体，也可能不存在
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
     * 硬盘缓存的清理 
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
	 * 获取最早加入的缓存文件，并将其删除 
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
	 * 获取文件的大小
	 * @param file
	 * @return
	 */
	private int getSize(File file) {  
        return (int) file.length();  
    } 
	
}
