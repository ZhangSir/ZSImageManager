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
 * �ڴ滺��
 * <p/>
 * ע������ʵ�ֲο�GitHub��Դ��Ŀuniversal image library ��Androidϵͳ������LruCacheԴ��
 * @author zhangshuo
 */
public class LruMemoryCache {

	private final String TAG = LruMemoryCache.class.getSimpleName();
	
	/**
	 * ����ռ���ڴ������
	 */
	private final int maxSize;
	
	/**
	 * ��ǰ������ռ�ڴ������
	 */
	private int size;
	
	private final LinkedHashMap<String, Bitmap> map;
	
	public LruMemoryCache(Context context, int maxSize){
		int memClass = ((ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass();
		if(maxSize <= 0){
			//ʹ��ϵͳ�������Ӧ�õ�1/8�ڴ��С��Ϊǿ���õ��ڴ�
			this.maxSize = (int) (Runtime.getRuntime().maxMemory() / 8);
		}else{
			this.maxSize = maxSize;
		}
		
		Log.e(TAG, "ActivityManager--memClass->" + memClass);
		Log.e(TAG, "RunTime--maxSize->" + maxSize);
		
		/*��ʼ��LinkedHashMap��
		 * ��һ������initialCapacity��������ָ��map��ʼ������
		 * �ڶ�������loadFactor,������ָ��map���������ӣ�����ǰmap�Ĵ�С�������ǣ�����initialCapacity * loadFactor��С�Ŀռ䣻
		 * ����������accessOrder��������ָ����ȡ˳��false�Ļ�������˳������true������˳������
		 * ע��ͨ��Դ����Կ���������loadFactor�Ѿ�û�������ˣ���ΪĬ��ֻʹ��ֵΪ3/4����������
		 */
		map = new LinkedHashMap<String, Bitmap>(0, 0.75f, true);
	}
	
	/**
	 * ���key��bitmap�����ڻ����У��򷵻ظ�bitmap��������bitmap�ƶ����������е�ͷ����
	 * ���key��bitmap�������ڻ����У��򷵻�null��
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
	 * ���bitmap��key�����棬bitmap�ᱻ��ӵ����б�ͷ�������key�Ѵ��ڣ����滻Ϊ��ǰbitmap���ƶ�������ͷ����
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
			//�����Ӧkey�Ѵ��ڣ���᷵��ԭ��key����Ӧ��bitmap���󣬲������򷵻�null
			Bitmap previous = map.put(key, value);
			if(null != previous){
				//���ԭ���������Ѵ��ڸ�key����ǰ����Ĵ�СӦ���ǵ�ǰ��С��ȥԭ��key����Ӧ��bitmap�Ĵ�С
				//��Ϊ��put��ȥ��value��Bitmap���Ḳ�ǵ�ԭ��key����Ӧ��bitmap
				this.size = this.size - this.sizeOf(key, previous);
			}
		}
		
		this.trimToSize(maxSize);
		
		return true;
	}
	
	/**
	 * �Ƴ����δ���ʵ�bitmap��֪��ʣ�µ�����bitmap��ռ�ڴ治����maxSize
	 * @param maxSize ����ڴ�������-1���������л���
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
	 * ��������д���key�����Ƴ�key�����Ӧ��bitmap
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
	 * ��ջ���
	 */
	public void clear(){
		this.trimToSize(-1);
	}
	
	/**
	 * ����bitamp�Ĵ�С
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
