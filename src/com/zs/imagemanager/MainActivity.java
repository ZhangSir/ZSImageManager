package com.zs.imagemanager;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class MainActivity extends Activity {

	private ImageLoader imageLoader;
	
	private ListView mListView = null;
	
	private ImageAdapter mAdapter = null;
	
	private String[] imageUrls = Constants.IMAGES;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		imageLoader = ImageLoader.getInstance(this);
		
		mListView = (ListView) this.findViewById(R.id.lv_main);
		mAdapter = new ImageAdapter();
		mListView.setAdapter(mAdapter);
		
	}
	
	class ImageAdapter extends BaseAdapter {

		@Override
		public int getCount() {
			// TODO Auto-generated method stub
			return imageUrls.length;
		}

		@Override
		public Object getItem(int position) {
			// TODO Auto-generated method stub
			return imageUrls[position];
		}

		@Override
		public long getItemId(int position) {
			// TODO Auto-generated method stub
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = convertView;
			final ViewHolder holder;
			if (convertView == null) {
				view = getLayoutInflater().inflate(R.layout.layout_main_item_list, parent, false);
				holder = new ViewHolder();
				holder.text = (TextView) view.findViewById(R.id.text);
				holder.image = (ImageView) view.findViewById(R.id.image);
				holder.progressBar = (ProgressBar) view.findViewById(R.id.progress);
				view.setTag(holder);
			} else {
				holder = (ViewHolder) view.getTag();
			}

			holder.text.setText("Item " + (position + 1));

			imageLoader.displayImage(imageUrls[position], new ImageViewAware(holder.image), new ImageLoadingListener() {
				
				@Override
				public void onLoadingStarted(String imageUri, View view) {
					// TODO Auto-generated method stub
					System.out.println("Started");
					holder.progressBar.setProgress(0);
					 holder.progressBar.setVisibility(View.VISIBLE);
				}
				
				@Override
				public void onLoadingFailed(String imageUri, View view,
						FailReason failReason) {
					// TODO Auto-generated method stub
					System.out.println("Failed");
					holder.progressBar.setVisibility(View.GONE);
				}
				
				@Override
				public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
					// TODO Auto-generated method stub
					System.out.println("Complete");
					holder.progressBar.setVisibility(View.GONE);
				}
				
				@Override
				public void onLoadingCancelled(String imageUri, View view) {
					// TODO Auto-generated method stub
					System.out.println("Cancelled");
					holder.progressBar.setVisibility(View.GONE);
				}
			}, new ImageLoadingProgressListener() {
				
				@Override
				public void onProgressUpdate(String imageUri, View view, int current,
						int total) {
					// TODO Auto-generated method stub
					holder.progressBar.setProgress(Math.round(100.0f * current / total));
				}
			});
			return view;
		}
		
	}
	
	private static class ViewHolder {
		TextView text;
		ImageView image;
		ProgressBar progressBar;
	}
}
