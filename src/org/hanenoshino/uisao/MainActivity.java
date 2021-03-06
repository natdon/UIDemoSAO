package org.hanenoshino.uisao;

import io.vov.vitamio.MediaPlayer;
import io.vov.vitamio.MediaPlayer.OnCompletionListener;
import io.vov.vitamio.MediaPlayer.OnErrorListener;
import io.vov.vitamio.Vitamio;

import java.io.File;
import java.util.ArrayList;

import org.hanenoshino.uisao.anim.AnimationListener;
import org.hanenoshino.uisao.decoder.BackgroundDecoder;
import org.hanenoshino.uisao.decoder.CoverDecoder;
import org.hanenoshino.uisao.widget.MediaController;
import com.footmark.utils.cache.FileCache;
import com.footmark.utils.image.ImageManager;
import com.footmark.utils.image.ImageSetter;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.RelativeLayout;

public class MainActivity extends GameController implements OnItemClickListener, OnClickListener, OnTouchListener {

	{
		// Set the priority, trick useful for some CPU
		Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
	}

	private static volatile boolean isVideoInitialized = false;

	private ImageManager imgMgr;

	private GameAdapter items;

// Following code on GameController
//	protected ListView games;
//	protected ImageView cover, background;
//	protected TextView gametitle;
//	protected VideoView preview;
//	protected RelativeLayout videoframe;
//	protected ImageView btn_settings, btn_about;

	private void initImageManager() {
		destroyImageManager();
		if(Environment.MEDIA_MOUNTED.equals(
				Environment.getExternalStorageState())){
			imgMgr = new ImageManager(new FileCache(
					new File(
							Environment.getExternalStorageDirectory(),
							"saoui/.cover")));
		}else{
			imgMgr = new ImageManager(new FileCache(
					new File(
							getCacheDir(),
							"cover")));
		}
	}

	private void destroyImageManager() {
		if(imgMgr != null)
			imgMgr.shutdown();
	}

	private void configureVideoPlayer() {
		preview.setVideoQuality(MediaPlayer.VIDEOQUALITY_HIGH);
		preview.setOnCompletionListener(new OnCompletionListener() {

			public void onCompletion(MediaPlayer player) {
				Command.invoke(Command.LOOP_VIDEO_PREVIEW).of(preview).send();
			}

		});
		preview.setOnErrorListener(new OnErrorListener() {

			@Override
			public boolean onError(MediaPlayer player, int framework_err, int impl_err) {
				releaseVideoPlay();
				return true;
			}

		});
		preview.setMediaController(new MediaController(this));

		// Initialize the Vitamio codecs
		if(!Vitamio.isInitialized(this)) {
			new AsyncTask<Object, Object, Boolean>() {
				@Override
				protected void onPreExecute() {
					isVideoInitialized = false;
				}

				@Override
				protected Boolean doInBackground(Object... params) {
					Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
					boolean inited = Vitamio.initialize(MainActivity.this);
					Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
					return inited;
				}

				@Override
				protected void onPostExecute(Boolean inited) {
					if (inited) {
						isVideoInitialized = true;
						
						// Play video if exists
						Command.invoke(Command.MAINACTIVITY_PLAY_VIDEO).of(MainActivity.this).only().sendDelayed(2000);
					}
				}

			}.execute();
		}else{
			isVideoInitialized = true;
		}
	}
	
	private boolean environmentCheck() {
		File mCurrentDirectory = new File(
				Environment.getExternalStorageDirectory() + "/saoui"
				);
		if (!mCurrentDirectory.exists()) {
			new AlertDialog.Builder(this)
			.setTitle(getString(R.string.error))
			.setMessage(getString(R.string.no_sdcard_dir))
			.setPositiveButton(getString(R.string.known), 
					new DialogInterface.OnClickListener(){
				public void onClick(DialogInterface dialog, int whichButton) {
					finish();
				}
			})
			.create()
			.show();
			return false;
		}
		return true;
	}
	
	private void scanGames () {
		items.clear();
		items.notifyDataSetChanged();
		
		new Thread() {
			
			public void run() {
				File root = new File(
						Environment.getExternalStorageDirectory() + "/saoui"
						);
				File[] mDirectoryFiles = root.listFiles();
				for(File file: mDirectoryFiles) {
					if(!file.isHidden() && file.isDirectory()) {
						Game g = Game.scanGameDir(file);
						if(g != null) {
							// Add Game to Game List
							Command.invoke(Command.ADD_ITEM_TO_LISTADAPTER)
							.of(items).args(g.toBundle()).send();
						}
					}
				}
			}
			
		}.start();
	}

	private Animation animCoverOut = GetAnimation.For.MainInterface.ToHideCover(new AnimationListener() {

		public void onAnimationEnd(Animation animation) {
			animCoverOut = GetAnimation.For.MainInterface.ToHideCover(this);
			displayCover();
		}

	});

	private Animation animBackgroundOut = GetAnimation.For.MainInterface.ToHideBackground(new AnimationListener() {

		public void onAnimationEnd(Animation arg0) {
			animBackgroundOut = GetAnimation.For.MainInterface.ToHideBackground(this);
			if(background.getTag() instanceof Bitmap) {
				background.setImageBitmap((Bitmap) background.getTag());
				background.setBackgroundDrawable(null);
				background.setTag(null);
				background.startAnimation(GetAnimation.For.MainInterface.ToShowBackground(null));
			}
		}

	});

	private Animation animHideVideo = GetAnimation.For.MainInterface.ToHideVideoPlayerFrame(new AnimationListener(){

		public void onAnimationEnd(Animation animation) {
			videoframe.setVisibility(View.GONE);
		}

	});

	private Animation animPlayVideo = GetAnimation.For.MainInterface.ToShowVideoPlayerFrame(new AnimationListener(){

		public void onAnimationEnd(Animation animation) {
			startVideoPlay();
		}
		
		public void onAnimationStart(Animation animation) {
			videoframe.setVisibility(View.VISIBLE);
		}
		
		private void startVideoPlay() {
			Game item = items.getItem(items.getSelectedPosition());
			if(item.video != null && isVideoInitialized) {
					videoframe.setVisibility(View.VISIBLE);
					preview.setVisibility(View.VISIBLE);
					Command.revoke(Command.RELEASE_VIDEO_PREVIEW, preview);
					preview.setVideoURI(null);
					preview.setVideoPath(item.video);
			}
		}

	});


	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if(!environmentCheck()) return;

		// Pass parameters to CoverDecoder to get better performance
		CoverDecoder.init(getApplicationContext(), cover.getWidth(), cover.getHeight());

		initImageManager();

		configureVideoPlayer();

		// Initializing data and binding to ListView
		items = new GameAdapter(this, R.layout.gamelist_item, new ArrayList<Game>());
		games.setAdapter(items);
		games.setOnItemClickListener(this);

		Command.invoke(Command.RUN).of(
				new Runnable() { public void run() {scanGames();}}
		).sendDelayed(500);
		
		btn_settings.setOnClickListener(this);
		btn_about.setOnClickListener(this);
		items.setOnConfigClickListener(this);
		items.setOnPlayClickListener(this);
		
		preview.setOnTouchListener(this);
		
	}

	public void onDestroy() {
		super.onDestroy();
		destroyImageManager();
	}

	public void onResume() {
		super.onResume();
	}

	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	private void displayCover() {
		final Object o = cover.getTag();
		if(o instanceof Bitmap) {
			cover.setTag(null);
			cover.startAnimation(GetAnimation.For.MainInterface.ToShowCover(new AnimationListener (){

				public void onAnimationStart(Animation animation) {
					cover.setImageBitmap((Bitmap) o);
					cover.setBackgroundDrawable(null);
				}
				
			}));
		}
	}

	public void playVideo() {
		Game item = items.getItem(items.getSelectedPosition());
		if(item.video != null && isVideoInitialized) {
			videoframe.clearAnimation();
			animPlayVideo.reset();
			videoframe.startAnimation(animPlayVideo);
		}
	}

	public void releaseVideoPlay() {
		videoframe.clearAnimation();

		// Clear Video Player
		if(preview.isPlaying()){
			preview.stopPlayback();
		}
		
		preview.setVisibility(View.GONE);

		if(videoframe.getVisibility() == View.VISIBLE) {
			animHideVideo.reset();
			videoframe.startAnimation(animHideVideo);
		}
		
		Command.invoke(Command.RELEASE_VIDEO_PREVIEW).exclude(Command.MAINACTIVITY_PLAY_VIDEO)
		.of(preview).sendDelayed(2000);
	}

	private void updateCover(final String url, final boolean coverToBkg) {
		cover.setVisibility(View.INVISIBLE);
		Object o = cover.getTag();
		if(o instanceof ImageSetter) {
			((ImageSetter) o).cancel();
		}

		if(!animCoverOut.hasStarted()) {
			cover.startAnimation(animCoverOut);
		}

		imgMgr.requestImageAsync(url,
				new ImageSetter(cover) {

			protected void act() {
				cover.setTag(image().bmp());
				if(!animCoverOut.hasStarted())
					displayCover();
				String background = CoverDecoder.getThumbernailCache(url);
				// Exception for Web Images
				if(background == null)
					background = CoverDecoder.getThumbernailCache(image().file().getAbsolutePath());
				if(coverToBkg && background != null) {
					updateBackground(background);
				}
			}

		},
		new CoverDecoder(cover.getWidth(), cover.getHeight()));
	}

	private void updateBackground(String url) {
		background.setVisibility(View.INVISIBLE);
		Object o = background.getTag();
		if(o instanceof ImageSetter) {
			((ImageSetter) o).cancel();
		}

		if(!animBackgroundOut.hasStarted())
			background.startAnimation(animBackgroundOut);

		imgMgr.requestImageAsync(url, new ImageSetter(background) {

			protected void act() {
				if(animBackgroundOut.hasEnded()||!animBackgroundOut.hasStarted()) {
					super.act();
					background.startAnimation(GetAnimation.For.MainInterface.ToShowBackground(null));
				}else{
					background.setTag(image().bmp());
				}
			}

		},
		new BackgroundDecoder());

	}

	/**
	 * Scroll view to the center of the game list
	 * @param view
	 * child view of game list
	 */
	private void scrollViewToCenter(View view) {
		int viewY = view.getTop() + view.getHeight() / 2 - games.getHeight() / 2;
		if(viewY < 0 && games.getFirstVisiblePosition() == 0){
			games.smoothScrollToPosition(0);
		}else if(viewY > 0 && games.getLastVisiblePosition() == items.getCount() - 1){
			games.smoothScrollToPosition(items.getCount() - 1);
		}else{
			Command.invoke(Command.SCROLL_LIST_FOR_DISTANCE_IN_ANY_MILLIS)
			.of(games).only().args(viewY, 300).sendDelayed(100);
		}
	}

	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

		scrollViewToCenter(view);

		if(items.getSelectedPosition() != position) {

			releaseVideoPlay();

			// Set Selection
			items.setSelectedPosition(position);

			final Game item = items.getItem(position);

			if(item.background != null) {
				updateBackground(item.background);
			}
			if(item.cover != null) {
				updateCover(item.cover, item.background == null);
				Command.invoke(Command.MAINACTIVITY_PLAY_VIDEO).of(this).only().sendDelayed(3000);
			}else{
				// If no cover but video, play video directly
				if(item.video != null) {
					playVideo();
				}else{
					// With no multimedia information
					cover.setImageResource(R.drawable.dbkg_und);
				}
				if(item.background == null) {
					background.setImageResource(R.drawable.dbkg_und_blur);
				}
			}

			gametitle.setText(item.title);
		}
		
		items.showPanel(view);
	}

	public void onClick(View v) {
		// TODO Handle Click Events Here
		Game item;
		switch(v.getId()) {
		case R.id.btn_settings:
			
			break;
		case R.id.btn_about:
			
			break;
		case R.id.btn_config:
			item = items.getItem(items.getSelectedPosition());
			configForGame(item);
			break;
		case R.id.btn_play:
			item = items.getItem(items.getSelectedPosition());
			startGame(item);
			break;
		}
	}
	
	private RelativeLayout.LayoutParams videoframelayout = null;
	private RelativeLayout.LayoutParams fullscreenlayout = 
			new RelativeLayout.LayoutParams(
					LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
	
	private void toggleFullscreen() {
		RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) videoframe.getLayoutParams();
		if(videoframelayout == null) {
			videoframelayout = params;
		}
		if(!isVideoFullscreen()) {
			videoframe.setLayoutParams(fullscreenlayout);
		}else{
			videoframe.setLayoutParams(videoframelayout);
		}
		Command.invoke(Command.UPDATE_VIDEO_SIZE).of(preview).send();
	}
	
	private boolean isVideoFullscreen() {
		return videoframelayout != null && videoframe.getLayoutParams() != videoframelayout;
	}

	private long last_videotouch = 0;
	
	@Override
	public boolean onTouch(View v, MotionEvent event) {
		switch(v.getId()) {
		case R.id.surface_view:
			int action = event.getAction() & MotionEvent.ACTION_MASK;
			if(action == MotionEvent.ACTION_UP) {
				if(System.currentTimeMillis() - last_videotouch < 500) {
					toggleFullscreen();
				}else{
					if (preview.isPlaying())
						preview.toggleMediaControlsVisiblity();
				}
				last_videotouch = System.currentTimeMillis();
			}
			break;
		}
		return true;
	}
	
	public boolean onKeyUp(int keyCode, KeyEvent msg) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
        	if(isVideoFullscreen()) {
        		toggleFullscreen();
        		return true;
        	}
        	if(preview.isInPlaybackState()) {
        		releaseVideoPlay();
        		return true;
        	}
        }
		return super.onKeyUp(keyCode, msg);
	}

}
