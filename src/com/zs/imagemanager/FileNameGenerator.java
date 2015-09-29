package com.zs.imagemanager;


/**
 * 文件名字生产者
 * @author zhangshuo
 */
public class FileNameGenerator {
	
	private static final String URI_AND_SIZE_SEPARATOR = "_";
	private static final String WIDTH_AND_HEIGHT_SEPARATOR = "x";
	
	/**
	 * 返回imageUri的hashCode值
	 * @param imageUri
	 * @return
	 */
	public String generate(String imageUri) {
		return String.valueOf(imageUri.hashCode());
	}
	
	/**
	 * Generates key for memory cache for incoming image (URI + size).<br />
	 * Pattern for cache key - <b>[imageUri]_[width]x[height]</b>.
	 */
	public static String generateMemoryCacheKey(String imageUri, int[] targetSize) {
		return new StringBuilder(imageUri).append(URI_AND_SIZE_SEPARATOR).append(targetSize[0]).append(WIDTH_AND_HEIGHT_SEPARATOR).append(targetSize[1]).toString();
	}
}
