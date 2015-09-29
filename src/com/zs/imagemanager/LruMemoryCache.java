package com.zs.imagemanager;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.util.Log;

/**
 * 内存缓存
 * <p/>
 * 注：该类实现参考GitHub开源项目universal image library 及Android系统缓存类LruCache源码
 * @author zhangshuo
 */
public class LruMemoryCache {

	private final String TAG = LruMemoryCache.class.getSimpleName();
	
	/**
	 * 允许占用内存的总量
	 */
	private final int maxSize;
	
	/**
	 * 当前缓存所占内存的总量
	 */
	private int size;
	
	private final LinkedHashMap<String, Bitmap> map;
	
	public LruMemoryCache(Context context, int maxSize){
		int memClass = ((ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass();
		if(maxSize <= 0){
			//使用系统分配给本应用的1/8内存大小作为强引用的内存
			this.maxSize = (int) (Runtime.getRuntime().maxMemory() / 8);
		}else{
			this.maxSize = maxSize;
		}
		
		Log.e(TAG, "ActivityManager--memClass->" + memClass);
		Log.e(TAG, "RunTime--maxSize->" + maxSize);
		
		/*初始化LinkedHashMap，
		 * 第一个参数initialCapacity，作用是指定map初始容量；
		 * 第二个参数loadFactor,作用是指定map的增长因子，即当前map的大小不够用是，增加initialCapacity * loadFactor大小的空间；
		 * 第三个参数accessOrder，作用是指定存取顺序，false的话按插入顺序排序，true按访问顺序排序；
		 * 注：通过源码可以看到，设置loadFactor已经没有意义了，因为默认只使用值为3/4的增长因子
		 */
		map = new LinkedHashMap<String, Bitmap>(0, 0.75f, true);
	}
	
	/**
	 * 如果key的bitmap存在在缓存中，则返回该bitmap，并将该bitmap移动到缓存序列的头部；
	 * 如果key的bitmap不存在在缓存中，则返回null；
	 * @param key
	 * @return
	 */
	public final Bitmap get(String key){
		
		if(TextUtils.isEmpty(key)){
			throw new NullPointerException("key == null");
		}
		
		synchronized (this) {
			return map.get(key);
		}
		
	}
	
	/**
	 * 添加bitmap和key到缓存，bitmap会被添加到序列表头部（如果key已存在，会替换为当前bitmap并移动到序列头部）
	 * @param key
	 * @param value
	 * @return
	 */
	public final boolean put(String key, Bitmap value){
		if(TextUtils.isEmpty(key) || null == value){
			throw new NullPointerException("key == null || value == null");
		}
		
		synchronized (this) {
			this.size = this.size + this.sizeOf(key, value);
			//如果对应key已存在，则会返回原来key所对应的bitmap对象，不存在则返回null
			Bitmap previous = map.put(key, value);
			if(null != previous){
				//如果原来缓存中已存在该key，则当前缓存的大小应该是当前大小减去原来key所对应的bitmap的大小
				//因为新put进去的value（Bitmap）会覆盖掉原来key所对应的bitmap
				this.size = this.size - this.sizeOf(key, previous);
			}
		}
		
		this.trimToSize(maxSize);
		
		return true;
	}
	
	/**
	 * 移除最久未访问的bitmap，知道剩下的所有bitmap所占内存不大于maxSize
	 * @param maxSize 最大内存容量，-1则会清空所有缓存
	 */
	private void trimToSize(int maxSize){
		while (true) {
			String key;
			Bitmap value;
			synchronized (this) {
				if(this.size < 0 || (map.isEmpty() && this.size != 0)){
					throw new IllegalStateException(getClass().getName() + ".sizeOf() is reporting inconsistent results!");
				}
				if(this.size <= maxSize || map.isEmpty()){
					break;
				}
				
				Map.Entry<String, Bitmap> toEvict = map.entrySet().iterator().next();
				if(null == toEvict){
					break;
				}
				key = toEvict.getKey();
				value = toEvict.getValue();
				map.remove(key);
				this.size = this.size - this.sizeOf(key, value);
			}
			
		}
	}
	
	/**
	 * 如果缓存中存在key，则移除key及其对应的bitmap
	 * @param key
	 * @return
	 */
	public final Bitmap remove(String key){
		if (TextUtils.isEmpty(key)) {
			throw new NullPointerException("key == null");
		}
		
		synchronized (this) {
			Bitmap previous = map.remove(key);
			if(null != previous){
				this.size = this.size - this.sizeOf(key, previous);
			}
			return previous;
		}
	}
	
	public Collection<String> keys(){
		synchronized (this) {
			return new HashSet<String>(map.keySet());
		}
	}
	
	/**
	 * 清空缓存
	 */
	public void clear(){
		this.trimToSize(-1);
	}
	
	/**
	 * 返回bitamp的大小
	 * @param key
	 * @param value
	 * @return
	 */
	private int sizeOf(String key, Bitmap value){
		return value.getRowBytes() * value.getHeight();
	}
	
	public synchronized final String toString(){
		return String.format("LruMemoryCache[maxSize=%d]", maxSize);
	}
}
