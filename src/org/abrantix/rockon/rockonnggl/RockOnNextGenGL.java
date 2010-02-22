package org.abrantix.rockon.rockonnggl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;

import org.abrantix.rockon.rockonnggl.IRockOnNextGenService;
import org.abrantix.rockon.rockonnggl.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.DialogInterface.OnCancelListener;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.database.MergeCursor;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CursorAdapter;
import android.widget.FilterQueryProvider;
import android.widget.ImageView;
import android.widget.SimpleAdapter;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;

public class RockOnNextGenGL extends Activity {
	private final String TAG = "RockOnNextGenGL";
	
	/** Global Vars */
//	Context											mContext;
	int												mRendererMode;
	int												mTheme;
	GLSurfaceView 									mGlSurfaceView;
//	RockOnCubeRenderer 								mRockOnCubeRenderer;
	RockOnRenderer	 								mRockOnRenderer;
    private RockOnNextGenDefaultExceptionHandler 	mDefaultExceptionHandler; 
    boolean											mIsSdCardPresentAndHasMusic = true;

    /** Dialogs */
    private	AlertDialog.Builder					mPlaylistDialog;
    private	AlertDialog.Builder					mViewModeDialog;
    private	AlertDialog.Builder					mThemeDialog;
    private	AlertDialog.Builder					mHalfToneThemeDialog;
	private AlertDialog.Builder					mInstallConcertAppDialog;
    
	/** Initialized vars */
	AlbumArtDownloadOkClickListener		mAlbumArtDownloadOkClickListener = null;
	ThemeChangeClickListener			mThemeChangeClickListener = null;
    private IRockOnNextGenService 		mService = null;
	
	/** State Variables */
	static int			mState = Constants.STATE_INTRO;
	static String		mTrackName = null;
	static String		mArtistName = null;
	static long			mTrackDuration = -1;
	static long			mTrackProgress = -1;
	static float		mNavigatorPositionX = -1;
	static float		mNavigatorTargetPositionX = -1;
	static float		mNavigatorPositionY = -1;
	static float		mNavigatorTargetPositionY = -1;
	static int			mPlaylistId = Constants.PLAYLIST_UNKNOWN;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "ON CREATE!");
        /* set up our default exception handler */
        mDefaultExceptionHandler = new RockOnNextGenDefaultExceptionHandler(this);
        
        setupWindow();
        
        /**
         * SD card check
         */
        if(false && !isSdCardPresent())
        // FIXME: restart app code not working
//        	&&
//        	(
//        		getIntent().getAction() == null 
//        		||
//	        	(
//	    			getIntent().getAction() != null 
//	    			&&
//	    			!getIntent().getAction().equals(Constants.MAIN_ACTIVITY_IGNORE_SD_CARD))))
        {
        	mIsSdCardPresentAndHasMusic = false;
        	showSdCardNotPresentAlert();
        	return;
        }
        
//        if(!hasMusic())
//        {
//        	showNoMusicAlert();
//        	return;
//        }
        
        resumeAlbumArtDownload();
        resumeAlbumArtProcessing();
        
        /* some stuff needs to be read from the preferences before we do anything else */
        initializeState();
        connectToService();
        
        switch(mState){
        case Constants.STATE_INTRO:
        	showIntro();
        	break;
        case Constants.STATE_NAVIGATOR:
        	showNavigator();
        	break;
        case Constants.STATE_FULLSCREEN:
        	showFullScreen();
        	break;
        }
        

    }
    
    /** OnStart */
    public void onStart(){
    	super.onStart();
    	if(mIsSdCardPresentAndHasMusic)
    	{
//	    	Log.i(TAG, "ON START!");
	    	attachListeners();
	    	attachBroadcastReceivers();
	    	// trigger update album to the current playing
	    	mSetNavigatorCurrent.sendEmptyMessageDelayed(0, Constants.SCROLLING_RESET_TIMEOUT-2);
    	}
    }
    
    /** OnPause */
    public void onPause(){
    	super.onPause();
//    	Log.i(TAG, "ON PAUSE!");
    	if(mIsSdCardPresentAndHasMusic){
	    	switch(mState){
	    	case Constants.STATE_NAVIGATOR:
	        	/* save Navigator state */
	    		saveNavigatorState();
	        	// XXX - small GlSurfaceView bughackfix
	            mGlSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
	            android.os.SystemClock.sleep(200);
	            // 
	    		mGlSurfaceView.onPause();
	        	return;
	    	}
    	}
    }
    
    /** OnResume */
    public void onResume(){
    	super.onResume();
//    	Log.i(TAG, "ON RESUME!");
    	if(mIsSdCardPresentAndHasMusic)
    	{
	    	resumeState();
	    	switch(mState){
	    	case Constants.STATE_NAVIGATOR:
	//    		Log.i(TAG, "SURFACE RESUMING!");
	    		mGlSurfaceView.onResume();
	            mGlSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
	    		mRockOnRenderer.forceTextureUpdateOnNextDraw();
	//    		mRockOnCubeRenderer.forceTextureUpdateOnNextDraw();
	    		return;
	    	}
    	}
    }
    
    /** OnStop */
    public void onStop(){
    	super.onStop();
    	if(mIsSdCardPresentAndHasMusic)
    	{
	    	/* unregister receivers */
	    	unregisterReceiver(mStatusListener);
	//    	Log.i(TAG, "ON STOP 2!");
	    	/* remove handler calls */
	    	removePendingHandlerMessages();
    	}
    }
    
    /** OnDestroy */
    public void onDestroy(){
    	super.onDestroy();
    	if(mIsSdCardPresentAndHasMusic)
    	{
	//    	Log.i(TAG, "ON DESTROY!");
	    	/* save navigator state */
	    	saveNavigatorState();
	    	
	    	/* check if downloading album art */
	    	if(mAlbumArtDownloadOkClickListener != null &&
	    			mAlbumArtDownloadOkClickListener.isDownloading())
	    	{
	    		mAlbumArtDownloadOkClickListener.stopArtDownload();
	    		/* save state */
	    		Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
	    		editor.putBoolean(getString(R.string.preference_key_downloading_art_state), true);
	    		editor.commit();
	    	}

	    	/* check if processing album art */
	    	if(mThemeChangeClickListener != null &&
	    			mThemeChangeClickListener.isProcessing() &&
	    			!mThemeChangeClickListener.isInTheBackground())
	    	{
	    		mThemeChangeClickListener.stopArtProcessing();
	    		/* save state */
	    		Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
	    		editor.putBoolean(Constants.prefkey_mThemeProcessing, true);
	    		editor.putInt(Constants.prefkey_mThemeBeingProcessed, mThemeChangeClickListener.getTheme());
	    		editor.commit();
	    	}
	    
	    	/* Unbind service */
	    	if(mService != null){
	    		unbindService(mServiceConnection);
	    	}
	    	
	    	/* Remove our default exception handler */
	    	mDefaultExceptionHandler.destroy();
	    	
	    	/* is the concert app install dialog showing */
	//    	if(mInstallConcertAppDialog != null)
	//    		mInstallConcertAppDialog.
    	}
    }
   
    @Override
    public boolean  onCreateOptionsMenu(Menu menu){
    	super.onCreateOptionsMenu(menu);

    	String[] menuOptionsTitleArray = 
    		getResources().
    			getStringArray(R.array.menu_options_title_array);
    	int[] menuOptionsIdxArray =
    		getResources().
    			getIntArray(R.array.menu_options_index_array);

    	/* create the menu items */
    	for(int i=0; i<menuOptionsTitleArray.length; i++){
    		menu.add(
    				0, // subgroup 
    				menuOptionsIdxArray[i], // id 
    				menuOptionsIdxArray[i], // order
    				menuOptionsTitleArray[i]); // title
    		/* set the icon */
    		if(menuOptionsTitleArray[i].equals(getString(R.string.menu_option_title_preferences)))
    			menu.getItem(i).setIcon(android.R.drawable.ic_menu_preferences);
    		else if(menuOptionsTitleArray[i].equals(getString(R.string.menu_option_title_playlist_id)))
    			menu.getItem(i).setIcon(R.drawable.ic_mp_current_playlist_btn);
    		else if(menuOptionsTitleArray[i].equals(getString(R.string.menu_option_title_get_art)))
    			menu.getItem(i).setIcon(R.drawable.ic_menu_music_library);
    		else if(menuOptionsTitleArray[i].equals(getString(R.string.menu_option_title_concerts)))
    			menu.getItem(i).setIcon(android.R.drawable.ic_menu_today);
    		else if(menuOptionsTitleArray[i].equals(getString(R.string.menu_option_title_view_mode)))
    			menu.getItem(i).setIcon(android.R.drawable.ic_menu_view);
    		else if(menuOptionsTitleArray[i].equals(getString(R.string.menu_option_title_theme)))
    			menu.getItem(i).setIcon(android.R.drawable.ic_menu_gallery);
    	}
    	
    	return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu){
    	// TODO: in case we need to change some the options
    	return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item){
    	/**
    	 *  Preferences 
    	 */
    	if(item.getTitle().
    		equals(getString(R.string.menu_option_title_preferences)))
    	{
    		startActivityForResult(
    				new Intent(
    						this, 
    						RockOnNextGenPreferences.class), 
    				Constants.PREFERENCE_ACTIVITY_REQUEST_CODE);
    	} 
    	/**
    	 *  Get art 
    	 */
    	else if(item.getTitle().
    			equals(getString(R.string.menu_option_title_get_art)))
    	{
    		showAlbumArtDownloadDialog();
    	}
    	/**
    	 * Concerts
    	 */
    	else if(item.getTitle().
    			equals(getString(R.string.menu_option_title_concerts)))
    	{
    		try{
    			ComponentName cName = 
    				new ComponentName(
						Constants.CONCERT_APP_PACKAGE, 
						Constants.CONCERT_APP_MAIN_ACTIVITY);
				
    			/* is concerts installed? */
    			getPackageManager().
    				getActivityInfo(
    						cName,
    						0);
    			
    			Intent i = new Intent();
    			i.setComponent(cName);
    			/* start it */
    			startActivity(i);
    			
    		} catch(NameNotFoundException e) {
    			showConcertsRequiresAppInstallDialog();
    		}
    	}
    	/**
    	 *  View Mode 
    	 */
    	else if(item.getTitle().
    		equals(getString(R.string.menu_option_title_view_mode)))
    	{
    		mViewModeDialog = new AlertDialog.Builder(this);
    		mViewModeDialog.setTitle(getString(R.string.menu_option_title_view_mode));
    		mViewModeDialog.setAdapter(
    				new ArrayAdapter<String>(
    						this,
    						android.R.layout.select_dialog_item,
    						android.R.id.text1,
    						getResources().getStringArray(R.array.view_modes)),
    				mRendererChoiceDialogClick);
    		mViewModeDialog.show();
    	}
    	/**
    	 *  Theme 
    	 */
    	else if(item.getTitle().
    		equals(getString(R.string.menu_option_title_theme)))
    	{
    		mThemeDialog = new AlertDialog.Builder(this);
    		mThemeDialog.setTitle(getString(R.string.menu_option_title_theme));
    		mThemeDialog.setAdapter(
    				new ArrayAdapter<String>(
    						this,
    						android.R.layout.select_dialog_item,
    						android.R.id.text1,
    						getResources().getStringArray(R.array.themes)),
    				mThemeChoiceDialogClick);
    		mThemeDialog.show();
    	}
    	/**
    	 *  Playlists 
    	 */
    	else if(item.getTitle().
    		equals(getString(R.string.menu_option_title_playlist_id)))
    	{
    		CursorUtils cursorUtils = new CursorUtils(getApplicationContext());
    		mPlaylistDialog = new AlertDialog.Builder(this);
    		mPlaylistDialog.setTitle(
    				getString(R.string.playlist_dialog_title));
    		mPlaylistDialog.setNegativeButton(
    				getString(R.string.playlist_dialog_cancel), 
    				null);
    		// create adapter
    		ArrayList<Playlist> playlistArray = new ArrayList<Playlist>();
    		
    		// all + 
    		playlistArray.add(
    				new Playlist(
    						Constants.PLAYLIST_ALL, 
    						getString(R.string.playlist_all_title)));
    		
//    		// recent +
//    		playlistArray.add(
//    				new Playlist(
//    						Constants.PLAYLIST_MOST_RECENT, 
//    						getString(R.string.playlist_most_recent_title)));
    		
//    		// most played +
//    		playlistArray.add(
//    				new Playlist(
//    						Constants.PLAYLIST_MOST_PLAYED, 
//    						getString(R.string.playlist_most_played_title)));
 
    		// 'normal' playlists +
    		Cursor playlistCursor = cursorUtils.getPlaylists();
    		for(int i=0; i<playlistCursor.getCount(); i++){
    			playlistCursor.moveToPosition(i);
    			playlistArray.add(
    					new Playlist(
    							(int) playlistCursor.getLong(
    									playlistCursor.getColumnIndexOrThrow(
    											MediaStore.Audio.Playlists._ID)),
    							playlistCursor.getString(
    									playlistCursor.getColumnIndexOrThrow(
    											MediaStore.Audio.Playlists.NAME))
    					));
    		}
    		playlistCursor.close();
 
    		// genre cursor + 
    		Cursor genreCursor = cursorUtils.getGenres();
    		for (int i=0; i<genreCursor.getCount(); i++){
    			genreCursor.moveToPosition(i);
    			playlistArray.add(
    					new Playlist(
    							(int) (Constants.PLAYLIST_GENRE_OFFSET 
    								- genreCursor.getLong(
    										genreCursor.getColumnIndexOrThrow(
    												MediaStore.Audio.Genres._ID))),
    							genreCursor.getString(
    									genreCursor.getColumnIndexOrThrow(
    											MediaStore.Audio.Genres.NAME))
    					));
    		}
    		genreCursor.close();

    		
    		// create adapter
    		SimpleAdapter playlistSimpleAdapter = new SimpleAdapter(
    				getApplicationContext(), 
    				playlistArray, 
    				android.R.layout.select_dialog_item, 
    				new String[]{
    					Constants.PLAYLIST_NAME_KEY
    					},
    				new int[]{
    					android.R.id.text1
    					});
    		mPlaylistDialog.setAdapter(
    				playlistSimpleAdapter,
    				new PlaylistSelectedClickListener(mPlaylistSelectedHandler, playlistArray));
    		mPlaylistDialog.show();
    	}
    	/* other options... */
    	return true;
    }
    
    /**
     * ask the user if he wants to install the new app or not
     */
    private void showConcertsRequiresAppInstallDialog()
    {
    	mInstallConcertAppDialog = new AlertDialog.Builder(this);
    	mInstallConcertAppDialog.setTitle(getString(R.string.concert_app_install_dialog_title));
    	mInstallConcertAppDialog.setMessage(getString(R.string.concert_app_install_dialog_message));
    	mInstallConcertAppDialog.setPositiveButton(
    			getString(R.string.concert_app_install_dialog_ok), 
    			mConcertAppInstallClickListener);
    	mInstallConcertAppDialog.setNegativeButton(
    			getString(R.string.concert_app_install_dialog_cancel), 
    			null);
    	mInstallConcertAppDialog.show();
    }
    
    DialogInterface.OnClickListener mConcertAppInstallClickListener = 
    	new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				Intent i = new Intent(Intent.ACTION_VIEW, 
				Uri.parse("market://search?q=pname:"+Constants.CONCERT_APP_PACKAGE));
				startActivity(i);					
			}
		};
    
	private DialogInterface.OnClickListener mRendererChoiceDialogClick = 
		new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				String[] rendererArray = getResources().getStringArray(R.array.view_modes);
				if(rendererArray[which].equals(getString(R.string.view_mode_cube)))
				{
					// save in preferences
					Editor edit = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit();
					edit.putInt(Constants.prefkey_mRendererMode, Constants.RENDERER_CUBE);
					edit.commit();
					// reload views
					mLoadNewViewModeOrTheme.sendEmptyMessage(Constants.RENDERER_CUBE);
				}
				else if(rendererArray[which].equals(getString(R.string.view_mode_wall)))
				{
					// save in preferences
					Editor edit = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit();
					edit.putInt(Constants.prefkey_mRendererMode, Constants.RENDERER_WALL);
					edit.commit();
					// reload views
					mLoadNewViewModeOrTheme.sendEmptyMessage(Constants.RENDERER_WALL);
				}
			}
		};
	
		private DialogInterface.OnClickListener mThemeChoiceDialogClick = 
			new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					String[] themeArray = getResources().getStringArray(R.array.themes);
					if(themeArray[which].equals(getString(R.string.theme_normal)))
					{
						// save in preferences
						Editor edit = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit();
						edit.putInt(Constants.prefkey_mTheme, Constants.THEME_NORMAL);
						edit.commit();
						// reload views
						mLoadNewViewModeOrTheme.sendEmptyMessage(Constants.THEME_NORMAL);
					}
					else if(themeArray[which].equals(getString(R.string.theme_halftone)))
					{
						/**
						 * Create our image processing manager
						 */
						if(mThemeChangeClickListener == null)
							mThemeChangeClickListener = 
								new ThemeChangeClickListener(
										RockOnNextGenGL.this, 
										Constants.THEME_HALFTONE,
										mLoadNewViewModeOrTheme);
						else
							mThemeChangeClickListener.
								changeTheme(Constants.THEME_HALFTONE, false);
						
						/**
						 * Check preferences to see if art was already processed
						 */
						if(!PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).
								getBoolean(Constants.prefkey_mThemeHalfToneDone, false))
						{
							mHalfToneThemeDialog = new AlertDialog.Builder(RockOnNextGenGL.this);
							mHalfToneThemeDialog.setTitle(R.string.half_tone_dialog_title);
							mHalfToneThemeDialog.setMessage(R.string.half_tone_dialog_message);
							mHalfToneThemeDialog.setPositiveButton(
									R.string.half_tone_dialog_yes, 
									mThemeChangeClickListener);
							mHalfToneThemeDialog.setNegativeButton(R.string.half_tone_dialog_no, null);
							mHalfToneThemeDialog.show();
						}
						else
						{
							mThemeChangeClickListener.mArtProcessingTrigger.sendEmptyMessage(0);
//							// save in preferences
//							Editor edit = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit();
//							edit.putInt(Constants.prefkey_mTheme, Constants.THEME_HALFTONE);
//							edit.commit();
//							// reload views
//							mLoadNewViewModeOrTheme.sendEmptyMessage(Constants.THEME_HALFTONE);
						}
					}
				}
			};
		
			
	private Handler mLoadNewViewModeOrTheme = new Handler()
	{
		@Override
		public void handleMessage(Message msg)
		{
			showNavigator();
			attachListeners();
			resumeNavigatorState();
		}
	};
	
    private Handler mPlaylistSelectedHandler = new Handler(){
    	public void handleMessage(Message msg){
    		int playlistId = msg.what;
    		Log.i(TAG, "New Playlist Id: "+playlistId);
    		CursorUtils cursorUtils = new CursorUtils(getApplicationContext());
    		// reget the album cursor
    		Cursor albumCursor = cursorUtils.getAlbumListFromPlaylist(playlistId);
    		if(albumCursor != null && albumCursor.getCount() > 0){
    			// Debug
//	    		for(int i=0; i<albumCursor.getCount(); i++){
//	    			albumCursor.moveToPosition(i);
//	    			Log.i(
//	    					TAG, 
//	    					albumCursor.getString(
//	    							albumCursor.getColumnIndexOrThrow(
//	    									MediaStore.Audio.Albums.ALBUM)));
//	    		}
	    		try{
		    		// inform service of new playlist
	    			// 	usually also stops the playback
	    			mService.setPlaylistId(playlistId);
	    			// put the playlist in the sharedPrefs
		    		setAndSavePlaylist(playlistId);
		    		// wait until this becomes global
		    		Thread.sleep(300);
		    		// update the cube
		    		mRockOnRenderer.changePlaylist(playlistId);
//		    		mRockOnCubeRenderer.changePlaylist(playlistId);
		    		// update the playing state
		    		updateCurrentPlayerStateButtonsFromServiceHandler.sendEmptyMessage(0);
		    		// FIXEM: XXX _ KIND OF A HACK_ 
		    		setCurrentSongLabels("", "", -1, 0);
	    		} catch(Exception e){
	    			e.printStackTrace();
	    		}
	    		// clean up play queue -- setPlaylistId already does this...
	    		// refresh cube state
	    		// TODO: stop playing if it is playing??
	    	} else {
        		// show Playlist Empty notification
        		Toast.
        			makeText(
        				getApplicationContext(), 
        				R.string.playlist_empty_toast, 
        				Toast.LENGTH_LONG).
        			show();
        	}
    	} 
    };
    
    /**
     * 
     * @param playlistId
     */
    private void setAndSavePlaylist(int playlistId){
    	mPlaylistId = playlistId;
    	Editor editor = 
    		PreferenceManager.
    			getDefaultSharedPreferences(getApplicationContext()).
    				edit();
    	editor.putInt(
    			Constants.prefkey_mPlaylistId,
    			mPlaylistId);
    	editor.commit();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)  {
    	switch(requestCode){
    	case Constants.PREFERENCE_ACTIVITY_REQUEST_CODE:
    		Log.i(TAG, "Back from preference activity");
    		// reload preferences :P
    		/* in case the user changed to full screen */
    		// TODO: reload app -- creaty dummy act that starts the main activity and finish this one
    		break;
    	case Constants.ALBUM_ART_CHOOSER_ACTIVITY_REQUEST_CODE:
//    		mRockOnCubeRenderer.reverseClickAnimation();
    		mRockOnRenderer.reverseClickAnimation();
//    		// reloadTextures??
//    		mRockOnCubeRenderer.forceTextureUpdateOnNextDraw();
    		mRockOnRenderer.forceTextureUpdateOnNextDraw();
    		break;
    	}
    }
    
    /**
     * remove pending Handler Messages
     */
    private void removePendingHandlerMessages(){
    	mPassIntroHandler.removeCallbacksAndMessages(null);
    	mAlbumClickHandler.removeCallbacksAndMessages(null);
    	mSetNavigatorCurrent.removeCallbacksAndMessages(null);
    	mPlayPauseClickHandler.removeCallbacksAndMessages(null);
    	mNextClickHandler.removeCallbacksAndMessages(null);
    	mPreviousClickHandler.removeCallbacksAndMessages(null);
    	mSearchClickHandler.removeCallbacksAndMessages(null);
    	mPlayQueueClickHandler.removeCallbacksAndMessages(null);
    	mSongItemSelectedHandler.removeCallbacksAndMessages(null);
    	mSongSearchClickHandler.removeCallbacksAndMessages(null);
    	mSongListDialogOverallOptionsHandler.removeCallbacksAndMessages(null);
    	mRequestRenderHandler.removeCallbacksAndMessages(null);
    	updateCurrentPlayerStateButtonsFromServiceHandler.removeCallbacksAndMessages(null);
    	mPlaylistSelectedHandler.removeCallbacksAndMessages(null);
    	mUpdateSongProgressHandler.removeCallbacksAndMessages(null);
    	if(mAlbumArtDownloadOkClickListener != null)
    		mAlbumArtDownloadOkClickListener
				.mArtDownloadTrigger
					.removeCallbacksAndMessages(null);
    }
    
    /**
     * isSdCardPresent
     */
    private boolean isSdCardPresent(){
    	return 
    		android.os.Environment.
    			getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED);  
    }
    
    /**
     * showSdCardNotPresentAlert
     */
    private void showSdCardNotPresentAlert(){
    	AlertDialog.Builder noSdCardAlert = new AlertDialog.Builder(this);
    	noSdCardAlert.setTitle(R.string.no_sd_card_dialog_title);
    	noSdCardAlert.setMessage(R.string.no_sd_card_dialog_message);
    	// FIXME: code is unable to restart the app - 
    	// would need another dummy actvity for restarting the app
//    	noSdCardAlert.setPositiveButton(
//    			R.string.no_sd_card_dialog_continue_anyway, 
//    			new DialogInterface.OnClickListener() {
//					@Override
//					public void onClick(DialogInterface dialog, int which) {
//						Intent i = new Intent(RockOnNextGenGL.this, RockOnNextGenGL.class);
//						i.setAction(Constants.MAIN_ACTIVITY_IGNORE_SD_CARD);
//						i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//						startActivity(i);
////						finish();
//					}
//				});
    	noSdCardAlert.setNegativeButton(
    			R.string.no_sd_card_dialog_exit,
    			new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						finish();
					}
				});
    	// TODO: better to set an ondismiss listener
    	noSdCardAlert.setCancelable(false);
    	noSdCardAlert.show();
    }
    
    /**
     * showSdCardNotPresentAlert
     */
    private void showNoMusicAlert(){
    	AlertDialog.Builder noMusicAlert = new AlertDialog.Builder(this);
    	noMusicAlert.setTitle(R.string.no_music_dialog_title);
    	noMusicAlert.setMessage(R.string.no_music_dialog_message);
    	noMusicAlert.setNegativeButton(
    			R.string.no_music_dialog_exit,
    			new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						finish();
					}
				});
    	// TODO: better to set an ondismiss listener
    	noMusicAlert.setCancelable(false);
    	noMusicAlert.show();
    }
//	
    /**
     * SetupWindow
     */
    private void setupWindow(){
    	requestWindowFeature(Window.FEATURE_NO_TITLE);
    	
    	if(PreferenceManager.
    			getDefaultSharedPreferences(getApplicationContext()).
    				getBoolean(Constants.prefkey_mFullscreen, false))
	    	getWindow().setFlags(
	    			WindowManager.LayoutParams.FLAG_FULLSCREEN,   
	    			WindowManager.LayoutParams.FLAG_FULLSCREEN); 
    }
    
    /**
     * resumeAlbumArtDownload
     */
    private void resumeAlbumArtDownload(){
    	/* resume art download if the application was shut down while downloading art */
    	if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
    			.getBoolean(getString(R.string.preference_key_downloading_art_state), false))
    	{
	    	if(mAlbumArtDownloadOkClickListener == null){
				mAlbumArtDownloadOkClickListener = 
					new AlbumArtDownloadOkClickListener(this);
			}
			mAlbumArtDownloadOkClickListener.mArtDownloadTrigger.sendEmptyMessageDelayed(0, 2000);
			Editor editor = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext()).edit();
			editor.putBoolean(getString(R.string.preference_key_downloading_art_state), false);
			editor.commit();
    	}
    }
    
    /**
     * resumeAlbumArtProcessing
     */
    private void resumeAlbumArtProcessing(){
    	/* resume art processing if the application was shut down while downloading art */
    	if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
    			.getBoolean(Constants.prefkey_mThemeProcessing, false))
    	{
			if(mThemeChangeClickListener == null)
				mThemeChangeClickListener = 
					new ThemeChangeClickListener(
							RockOnNextGenGL.this, 
							PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
    	    					.getInt(Constants.prefkey_mThemeBeingProcessed, Constants.THEME_NORMAL),
							mLoadNewViewModeOrTheme);
			else
				mThemeChangeClickListener.
					changeTheme(
						PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
	    					.getInt(Constants.prefkey_mThemeBeingProcessed, Constants.THEME_NORMAL),
						false);

    		mThemeChangeClickListener.mArtProcessingTrigger.sendEmptyMessage(0);
			Editor editor = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext()).edit();
			editor.putBoolean(Constants.prefkey_mThemeProcessing, false);
			editor.commit();    		
    	}
    }
    
    /**
     * save navigator state
     */
    private void saveNavigatorState(){
//    	if(mRockOnCubeRenderer != null){
//    		mNavigatorPositionX = mRockOnCubeRenderer.mPositionX;
//	    	mNavigatorTargetPositionX = mRockOnCubeRenderer.mTargetPositionX;
//	    	mNavigatorPositionY = mRockOnCubeRenderer.mPositionY;
//	    	mNavigatorTargetPositionY = mRockOnCubeRenderer.mTargetPositionY;
    	if(mRockOnRenderer != null){
    		mNavigatorPositionX = mRockOnRenderer.getPositionX();
	    	mNavigatorTargetPositionX = mRockOnRenderer.getTargetPositionX();
	    	mNavigatorPositionY = mRockOnRenderer.getPositionY();
	    	mNavigatorTargetPositionY = mRockOnRenderer.getTargetPositionY();
		   	Editor editor = PreferenceManager.
	    		getDefaultSharedPreferences(getApplicationContext()).edit();
		   	/* navigator position */
	    	editor.putFloat(Constants.prefkey_mNavigatorPositionX, mNavigatorPositionX);
	    	editor.putFloat(Constants.prefkey_mNavigatorTargetPositionX, mNavigatorTargetPositionX);
	    	editor.putFloat(Constants.prefkey_mNavigatorPositionY, mNavigatorPositionY);
	    	editor.putFloat(Constants.prefkey_mNavigatorTargetPositionY, mNavigatorTargetPositionY);
	    	/* renderer mode */
	    	editor.putInt(Constants.prefkey_mRendererMode, mRendererMode);
	    	
	    	editor.commit();
    	}
    }
    
    /**
     * connectToService
     */
    private void connectToService(){
    	Intent intent = new Intent(this, RockOnNextGenService.class);
    	startService(intent);
    	bindService(intent, mServiceConnection, BIND_AUTO_CREATE);
    }
    
    /** 
     * showIntro
     */
    private void showIntro(){
    	setContentView(R.layout.intro);
    	mPassIntroHandler.sendEmptyMessageDelayed(0, 2000);
    }
    
    /**
     * showNavigator
     */
    private void showNavigator(){
    	setContentView(R.layout.navigator_main);
    	mGlSurfaceView = (GLSurfaceView) findViewById(R.id.cube_surface_view);
    	/*************************************************
         * 
         * OPENGL ES HACK FOR GALAXY AND OTHERS
         * 
         *************************************************/
        mGlSurfaceView.setEGLConfigChooser(
        	     new GLSurfaceView.EGLConfigChooser() {
					@Override
					public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
     	               int[] attributes=new int[]{
       	                    //EGL10.EGL_RED_SIZE,
       	                    //5,
       	                    //EGL10.EGL_BLUE_SIZE,
       	                    //5,
       	                    //EGL10.EGL_GREEN_SIZE,
       	                    //6,
       	                    EGL10.EGL_DEPTH_SIZE,
       	                    16,
       	                    EGL10.EGL_NONE
       	               };
       	               EGLConfig[] configs=new EGLConfig[1];
       	               int[] result=new int[1];
       	               egl.eglChooseConfig(display,attributes,configs,1,result);
       	               return configs[0];
					}
        	     }
        	);
//    	mGlSurfaceView.setEGLConfigChooser(
//    			5, 6, 5, // RGB 
//    			0, 
//    			16, 
//    			0);
        /************************************************
         * HACK END
         ************************************************/

        mRendererMode = 
        	PreferenceManager.
        		getDefaultSharedPreferences(getApplicationContext()).
        			getInt(Constants.prefkey_mRendererMode, Constants.RENDERER_CUBE);
        mTheme = 
        	PreferenceManager.
        		getDefaultSharedPreferences(getApplicationContext()).
        			getInt(Constants.prefkey_mTheme, Constants.THEME_NORMAL);
        
        switch(mRendererMode)
        {
        case Constants.RENDERER_CUBE:
	   		RockOnCubeRenderer rockOnCubeRenderer = new RockOnCubeRenderer(
	        		getApplicationContext(),
	        		mRequestRenderHandler,
	        		mTheme);
	   		mGlSurfaceView.setRenderer(rockOnCubeRenderer);
	   		mRockOnRenderer = (RockOnRenderer) rockOnCubeRenderer;	
	        break;
        case Constants.RENDERER_WALL:
        	RockOnWallRenderer rockOnWallRenderer = new RockOnWallRenderer(
	        		getApplicationContext(),
	        		mRequestRenderHandler,
	        		mTheme);
	   		mGlSurfaceView.setRenderer(rockOnWallRenderer);
	   		mRockOnRenderer = (RockOnRenderer) rockOnWallRenderer;	
	        break;
        }
        mGlSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        
        /** check if we were able to find any music */
        if(mRockOnRenderer.getAlbumCount() <= 0)
        {
        	mIsSdCardPresentAndHasMusic = false;
   			showNoMusicAlert();
   			return;
        } else {
        	if(mRockOnRenderer.getAlbumCursor() != null)
        		startManagingCursor(mRockOnRenderer.getAlbumCursor());
        }
    }
    
    /**
     * handler for updating the surface view
     */
    Handler mRequestRenderHandler = new Handler(){
    	public void handleMessage(Message msg){
//    		Log.i(TAG, "requesting GL surface to render");
    		if(mGlSurfaceView.getRenderMode() == GLSurfaceView.RENDERMODE_CONTINUOUSLY)
    			mGlSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    		mGlSurfaceView.requestRender();
    	}
    };
    
    /**
     * showFullScreen
     */
    private void showFullScreen(){
    	
    }
    
    /**
     * Show Search auto complete box
     */
    private void showSearch(){
    	/** inflate our new group of views */
    	ViewGroup searchViewGroup = (ViewGroup) 
    		((LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE)).
    			inflate(
    				R.layout.search_layout, 
    				null);
    	/** add it to the current layout */
    	((ViewGroup) findViewById(R.id.the_mother_of_all_views_navigator)).
    			addView(
    					searchViewGroup, 
    					new LayoutParams(
    							LayoutParams.FILL_PARENT, 
    							LayoutParams.FILL_PARENT));
    }
    
    private void prepareSearch(){
    	/** get our autocomplete stuff */
    	setupAutoCompleteSearch(mPlaylistId);
    	((AutoCompleteTextView)findViewById(R.id.search_textview)).
    			setOnItemClickListener(mSongSearchClickListener);
    	findViewById(R.id.search_textview).requestFocus();
    	((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).
    		showSoftInput(
    				findViewById(R.id.search_textview), 
    				InputMethodManager.SHOW_FORCED);
    }
    
    private void hideSearch(){
    	/* hide also the input keyboard */
    	((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).
    			hideSoftInputFromWindow(
    					findViewById(R.id.search_textview).getWindowToken(), 
    					0);
    	
    	/* get rid of the search ui */
    	((ViewGroup)findViewById(R.id.search_container)).removeAllViewsInLayout();
    	ViewGroup baseLayout = (ViewGroup) findViewById(R.id.the_mother_of_all_views_navigator);
    	baseLayout.removeView(findViewById(R.id.search_container));
     	
    	
    }
    
    /**
     * resumeState
     */
    private void resumeState(){
    	switch(mState){
        case Constants.STATE_INTRO:
        	// TODO: nothing?
        	return;
        case Constants.STATE_NAVIGATOR:
        	resumeNavigatorState();
        	return;
        case Constants.STATE_FULLSCREEN:
        	// TODO: ??
        	return;
        }	
    }
    
    /**
     * initialize state
     * 	- for now we only need this for the playlist 
     * 		(it is needed to initialize the cube and other stuff)
     */
    private void initializeState(){
    	/* it is only needed for the playlist */
    	if(mPlaylistId == Constants.PLAYLIST_UNKNOWN)
    		mPlaylistId = 
    			PreferenceManager.
    				getDefaultSharedPreferences(getApplicationContext()).
    					getInt(Constants.prefkey_mPlaylistId, Constants.PLAYLIST_ALL);
    	/* sanity check */
        if(mPlaylistId == Constants.PLAYLIST_UNKNOWN)
        	mPlaylistId = Constants.PLAYLIST_ALL;
    }
    
    private void resumeNavigatorState(){
    	/* update current song label */
    	updateTrackMetaFromService();
    	setCurrentSongLabels(
    			mTrackName, 
    			mArtistName, 
    			mTrackDuration, 
    			mTrackProgress);
    	updateCurrentPlayerStateButtonsFromServiceHandler
    		.sendEmptyMessage(0);
    
    	/* set the navigator in the right position */
    	if(mNavigatorPositionY == -1)
    		mNavigatorPositionY = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).
    			getFloat(Constants.prefkey_mNavigatorPositionY, 0);
    	if(mNavigatorTargetPositionY == -1)
    		mNavigatorTargetPositionY = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).
    			getFloat(Constants.prefkey_mNavigatorTargetPositionY, 0);
    	if(mNavigatorPositionX == -1)
    		mNavigatorPositionX = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).
    			getFloat(Constants.prefkey_mNavigatorPositionX, 0);
    	if(mNavigatorTargetPositionX == -1)
    		mNavigatorTargetPositionX = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).
    			getFloat(Constants.prefkey_mNavigatorTargetPositionX, 0);
    	setNavigatorPosition(
    			mNavigatorPositionX, mNavigatorTargetPositionX,
    			mNavigatorPositionY, mNavigatorTargetPositionY);    	
    }
    
    /**
     * attachBroadcastReceivers
     */
    private void attachBroadcastReceivers(){
    	/* service play status update */
        IntentFilter f = new IntentFilter();
        f.addAction(Constants.PLAYSTATE_CHANGED);
        f.addAction(Constants.META_CHANGED);
        f.addAction(Constants.PLAYBACK_COMPLETE);
        registerReceiver(mStatusListener, new IntentFilter(f));
    }
    
    /** 
     * showAlbumArtDownloadDialog
     */
    private void showAlbumArtDownloadDialog(){
    	
    	if(mAlbumArtDownloadOkClickListener == null)
    		mAlbumArtDownloadOkClickListener = 
    			new AlbumArtDownloadOkClickListener(this);

		AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
		dialogBuilder.setTitle(getResources().getString(R.string.get_art_dialog_title));
		dialogBuilder.setMessage(getResources().getString(R.string.get_art_dialog_message));
		dialogBuilder.setPositiveButton(
				getResources().getString(R.string.get_art_dialog_yes), 
				mAlbumArtDownloadOkClickListener);
		dialogBuilder.setNegativeButton(
				getResources().getString(R.string.get_art_dialog_no), 
				null);
		dialogBuilder.setCancelable(false);
		dialogBuilder.show();
    }
    
    /**
     * Attach UI Listeners
     */
    public void attachListeners(){
    	switch(mState){
    	case Constants.STATE_NAVIGATOR:
    		/* Renderer scrolling */
    		if(mGlSurfaceView != null){
	        	NavGLTouchListener 	navGLTouchListener = new NavGLTouchListener();
//	        	navGLTouchListener.setRenderer(mRockOnCubeRenderer);
	        	navGLTouchListener.setRenderer(mRockOnRenderer);
	        	navGLTouchListener.setClickHandler(mAlbumClickHandler);
	        	navGLTouchListener.setTimeoutHandler(mSetNavigatorCurrent);
	        	mGlSurfaceView.setOnTouchListener(navGLTouchListener);
//	        	AlbumClickListener	albumClickListener = 
//	        		new AlbumClickListener(
//	        			this, 
//	        			mRockOnCubeRenderer);
//	        	mGlSurfaceView.setOnClickListener(albumClickListener);
    		}
    		/* Progress bar seek */
    		ProgressBarTouchListener progressTouchListener = new ProgressBarTouchListener();
    		progressTouchListener.setSeekHandler(mSeekHandler);
    		findViewById(R.id.progress_bar).setOnTouchListener(progressTouchListener);
    		/* Play/next/previous button click listener */
    		findViewById(R.id.player_controls_play).setOnClickListener(mPlayPauseClickListener);
    		findViewById(R.id.player_controls_next).setOnClickListener(mNextClickListener);
    		findViewById(R.id.player_controls_previous).setOnClickListener(mPreviousClickListener);
    		/* Repeat and Shuffle Buttons */
    		findViewById(R.id.player_controls_repeat).setOnClickListener(mRepeatClickListener);
    		findViewById(R.id.player_controls_shuffle).setOnClickListener(mShuffleClickListener);
    		/* Search */
    		findViewById(R.id.search_button).setOnClickListener(mSearchClickListener);
    		/* Play Queue */
    		findViewById(R.id.play_queue_button).setOnClickListener(mPlayQueueClickListener);
        	return;
    	}
    }
    
    /**
     * Pass Intro
     */
    Handler	mPassIntroHandler = new Handler(){
    	public void handleMessage(Message msg){
    		mState = Constants.STATE_NAVIGATOR;
    		showNavigator();
    		attachListeners();
    		resumeState();
    		// Check if this is the first boot or new version
    		if(!PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
    				.contains(getResources().getString(R.string.preference_key_version)))
    		{
    			showAlbumArtDownloadDialog();
    			Editor editor = PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
    				.edit();
    			editor.putString(getResources().getString(R.string.preference_key_version), "1");
    			editor.commit();
    		}
    	}
    };
    
    /** 
     * Navigator Timeout reset
     */
    Handler	mSetNavigatorCurrent = new Handler(){
    	@Override
    	public void handleMessage(Message msg){
    		try {
//				mRockOnCubeRenderer.setCurrentByAlbumId(mService.getAlbumId());
				mRockOnRenderer.setCurrentByAlbumId(mService.getAlbumId());
			} catch (RemoteException e) {
				e.printStackTrace();
			}
    	}
    };
    
    /**
     * onKeyDown
     */
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
		switch(keyCode){
		case KeyEvent.KEYCODE_BACK:
			if(findViewById(R.id.search_container) != null)
				hideSearch();
			else{
				super.onKeyDown(keyCode, event);
			}
			return true;
		}
		
    	return false;
    	
    };
    
    /**
     * Player controls Listener and helpers
     */
    OnClickListener mPlayPauseClickListener = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			if(!mPlayPauseClickHandler.hasMessages(0)){
				try{
					if(mService.isPlaying()){
						setPlayButton();
						trackSongProgress(Constants.HARD_REFRESH);
					}
					else{
						setPauseButton();
						stopTrackingSongProgress();
					}
				} catch(Exception e){
					e.printStackTrace();
				}
					
				mPlayPauseClickHandler.sendEmptyMessage(0);
//				mPlayPauseClickHandler.sendEmptyMessageDelayed(0, Constants.CLICK_ACTION_DELAY);
			}
		}
	};
	
	Handler	mPlayPauseClickHandler = new Handler(){
		@Override
		public void handleMessage(Message msg){
			if(mService != null){
				try{
					/* check if service playing something */
					if(mService.isPlaying()){
						mService.pause();
//						Log.i(TAG, "was playing... now pausing...");
						// TODO: stop progress update
						// TODO: change button image
					} else {
						/* There are still songs to be played in the queue */
						if(mService.getQueuePosition() >= mService.getQueue().length ||
								mService.getAudioId() >= 0){
							mService.play(); // or is it resume...
//							Log.i(TAG, "queue has songs... lets play or resume them...");
						} 
						/* The play queue has been fully played */
						else {
//							if(mRockOnCubeRenderer.isSpinning())
							if(mRockOnRenderer.isSpinning())
									return;
							// Queue a song of the currently displayed album
							else{
//								int albumId = mRockOnCubeRenderer.getShownAlbumId();
								int albumId = mRockOnRenderer.getShownAlbumId(
										findViewById(R.id.cube_surface_view).getWidth()/2,
										findViewById(R.id.cube_surface_view).getHeight()/2);
								Log.i(TAG, "current album: "+albumId);

								if(albumId == -1)
									return;
								else{
									// song list cursor
									CursorUtils cursorUtils = new CursorUtils(getApplicationContext());
									Cursor		songCursor = cursorUtils.getSongListCursorFromAlbumId(
											albumId, 
											mPlaylistId); // TODO: read the actual playlist ID
									songCursor.moveToFirst();
									// queue the first? song
									long[] songIdList = new long[1];
									songIdList[0] = 
										ContentProviderUnifier.
											getAudioIdFromUnknownCursor(songCursor); 
//										songCursor.getLong(
//											songCursor.getColumnIndexOrThrow(
//													MediaStore.Audio.Media._ID));
									Log.i(TAG, "enqueing song: "+songCursor.getString(songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)));
									mService.enqueue(songIdList, Constants.NOW); // calls play already
									songCursor.close();
								}
									
							}
						}
					}
				} catch (Exception e){
					e.printStackTrace();
				}
			}else{
				Log.i(TAG, "service interface has not been created");
			}
		}
	};
	
	OnClickListener mNextClickListener = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			if(!mNextClickHandler.hasMessages(0))
				mNextClickHandler.sendEmptyMessage(0);
//				mNextClickHandler.sendEmptyMessageDelayed(0, Constants.CLICK_ACTION_DELAY);
		}
	};
	
	Handler	mNextClickHandler = new Handler(){
		@Override
		public void handleMessage(Message msg){
			try {
				mService.next();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
	};
	
	OnClickListener mPreviousClickListener = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			if(!mPreviousClickHandler.hasMessages(0))
				mPreviousClickHandler.sendEmptyMessage(0);
//				mPreviousClickHandler.sendEmptyMessageDelayed(0, Constants.CLICK_ACTION_DELAY);
		}
	};
	
	Handler	mPreviousClickHandler = new Handler(){
		@Override
		public void handleMessage(Message msg){
			try {
				mService.prev();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
	};
	
	OnClickListener mRepeatClickListener = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			if(!mRepeatClickHandler.hasMessages(0))
				mRepeatClickHandler.sendEmptyMessage(0);
//				mRepeatClickHandler.sendEmptyMessageDelayed(0, Constants.CLICK_ACTION_DELAY);
		}
	};
	
	Handler mRepeatClickHandler = new Handler(){
		
		@Override
		public void handleMessage(Message msg){
			// TODO:
		}
		
	};
	
	OnClickListener mShuffleClickListener = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			if(!mShuffleClickHandler.hasMessages(0))
				mShuffleClickHandler.sendEmptyMessage(0);
//				mShuffleClickHandler.sendEmptyMessageDelayed(0, Constants.CLICK_ACTION_DELAY);
		}
	};
	
	Handler mShuffleClickHandler = new Handler(){
		
		@Override
		public void handleMessage(Message msg){
			try{
				if(mService.getShuffleMode() == Constants.SHUFFLE_AUTO ||
						mService.getShuffleMode() == Constants.SHUFFLE_NORMAL){
					mService.setShuffleMode(Constants.SHUFFLE_NONE);
					setShuffleNoneButton();
				} else {
					mService.setShuffleMode(Constants.SHUFFLE_NORMAL);
					setShuffleButton();
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		
	};
	
	OnClickListener mSearchClickListener = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			if(!mSearchClickHandler.hasMessages(0)){
				if(findViewById(R.id.search_container) ==  null)
					showSearch();
				else
					hideSearch();
				mSearchClickHandler.sendEmptyMessage(0);
//				mSearchClickHandler.sendEmptyMessageDelayed(0, Constants.CLICK_ACTION_DELAY);
			}
		}
	};
	
	Handler	mSearchClickHandler = new Handler(){
		@Override
		public void handleMessage(Message msg){
			if(findViewById(R.id.search_container) !=  null)
				prepareSearch();
		}
	};
	
	OnClickListener mPlayQueueClickListener = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			if(!mPlayQueueClickHandler.hasMessages(0)){
				mPlayQueueClickHandler.sendEmptyMessage(0);
//				mPlayQueueClickHandler.sendEmptyMessageDelayed(
//						0, 
//						Constants.CLICK_ACTION_DELAY);
			}
		}
	};
	
	Handler mPlayQueueClickHandler = new Handler(){
		@Override
		public void handleMessage(Message msg){
			try{
				/* song list cursor */
				CursorUtils cursorUtils = new CursorUtils(getApplicationContext());
//				Cursor		songCursor = cursorUtils.getSongListCursorFromSongList(
//						mService.getQueue(),
//						mService.getQueuePosition()); // TODO: read the actual playlist ID
				Cursor		songCursor = 
					cursorUtils.getSongListCursorFromSongList(
						mService.getOutstandingQueue(),
						0); // TODO: read the actual playlist ID

				if(songCursor != null){
					startManagingCursor(songCursor);
					songCursor.moveToFirst();				
					AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(RockOnNextGenGL.this);
					/* dialog title - album and artist name */
					dialogBuilder.setTitle(
							getString(R.string.play_queue_title));
					/* show the album song list  in a dialog */
	//				dialogBuilder.setCursor(
	//						songCursor,
	//						new SongSelectedClickListener(mSongItemSelectedHandler, songCursor),
	//						MediaStore.Audio.Media.TITLE);
					SongCursorAdapter songCursorAdapter = 
						new SongCursorAdapter(
								getApplicationContext(),
								Constants.queueSongListLayoutId, //R.layout.songlist_dialog_item or android.R.layout.select_dialog_item
								songCursor, 
								Constants.queueSongListFrom, 
								Constants.queueSongListTo,
								mPlayQueueItemSelectedHandler);
					dialogBuilder.setAdapter(
	 						songCursorAdapter,
	 						null);
//							new SongSelectedClickListener(
//									mSongItemSelectedHandler, 
//									songCursor)); // can be null
					dialogBuilder.setNegativeButton(
							R.string.clear_playlist_dialog_option, 
							mClearPlayQueueClickListener);
					dialogBuilder.setOnCancelListener(mSongDialogCancelListener);
					/* set the selection listener */
					songCursorAdapter.setDialogInterface(dialogBuilder.show());
				} else {
					// Play queue is empty
					Toast.makeText(
							getApplicationContext(), 
							R.string.playqueue_empty_toast, 
							Toast.LENGTH_SHORT).show();
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	};

	DialogInterface.OnClickListener mClearPlayQueueClickListener = new DialogInterface.OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			if(mService != null){
				try{
					mService.removeTracks(
						0,
						mService.getQueue().length-1);
				}catch(Exception e){
					e.printStackTrace();
				}
			}
		}
	};
	
	/**
	 * AlbumClick Handler
	 */
	Handler mAlbumClickHandler = new Handler(){
		int	x;
		int	y;
		@Override
		public void handleMessage(Message msg){
			x = msg.arg1;
			y = msg.arg2;
			if(msg.what == Constants.SINGLE_CLICK){
				/* song list cursor */
//				int albumId = mRockOnCubeRenderer.getShownAlbumId();
				int albumId = mRockOnRenderer.getShownAlbumId(x, y);
				if(albumId < 0){
					this.sendEmptyMessageDelayed(0, Constants.CLICK_ACTION_DELAY);
					return;
				}
				CursorUtils cursorUtils = new CursorUtils(getApplicationContext());
				Cursor		songCursor = 
					cursorUtils.getSongListCursorFromAlbumId(
						albumId, 
						mPlaylistId); // TODO: read the actual playlist ID
				
				if(songCursor != null){
					startManagingCursor(songCursor);
					songCursor.moveToFirst();				
					AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(RockOnNextGenGL.this);
					/* dialog title - album and artist name */
					dialogBuilder.setTitle(
							mRockOnRenderer.getShownAlbumArtistName(x, y)+"\n"+
							mRockOnRenderer.getShownAlbumName(x, y));
//							mRockOnCubeRenderer.getShownAlbumArtistName()+"\n"+
//							mRockOnCubeRenderer.getShownAlbumName());
					/* show the album song list  in a dialog */
	//				dialogBuilder.setCursor(
	//						songCursor,
	//						new SongSelectedClickListener(mSongItemSelectedHandler, songCursor),
	//						MediaStore.Audio.Media.TITLE);
					SongCursorAdapter songCursorAdapter = 
						new SongCursorAdapter(
								getApplicationContext(), 
								Constants.albumSongListLayoutId,
	//							R.layout.songlist_dialog_item, 
								songCursor, 
								Constants.albumSongListFrom, 
								Constants.albumSongListTo,
								mSongItemSelectedHandler);
					dialogBuilder.setAdapter(
	 						songCursorAdapter,
	 						null);
	//						new SongSelectedClickListener(
	//								mSongItemSelectedHandler, 
	//								songCursor)); // can be null
					dialogBuilder.setPositiveButton(getString(R.string.album_song_list_play_all), mSongDialogPlayAllListener);
					dialogBuilder.setNeutralButton(getString(R.string.album_song_list_queue_all), mSongDialogQueueAllListener);
					dialogBuilder.setOnCancelListener(mSongDialogCancelListener);
					/* set the selection listener */
		//			dialogBuilder.setOnItemSelectedListener(mSongItemSelectedListener);
					songCursorAdapter.setDialogInterface(dialogBuilder.show());
				}
			} else if(msg.what == Constants.LONG_CLICK){
				// start album chooser activity
				Intent intent = new Intent(RockOnNextGenGL.this, ManualAlbumArtActivity.class);
				Log.i(TAG, "sedning intent extra: "+mRockOnRenderer.getShownAlbumId(x, y));
				intent.putExtra("albumId", (long)mRockOnRenderer.getShownAlbumId(x, y));
				startActivityForResult(intent, Constants.ALBUM_ART_CHOOSER_ACTIVITY_REQUEST_CODE);
			}
			
		}
	};
	
	Handler mSongItemSelectedHandler = new Handler(){
		@Override
		public void handleMessage(Message msg){
			try{
				long[] list = {msg.arg1};
				mService.enqueue(list, msg.arg2);
				if(msg.arg2 == Constants.LAST){
					Toast.makeText(
							getApplicationContext(), 
							R.string.song_added_to_playlist, 
							Toast.LENGTH_SHORT).
						show();
				} else {
					setPauseButton();
					setCurrentSongLabels(
							mService.getTrackName(),
							mService.getArtistName(),
							mService.duration(),
							mService.position());
				}
				/* reverse the click animation */
				reverseRendererClickAnimation();
			} catch(Exception e){
				e.printStackTrace();
			}
		}
	};
	
	Handler mPlayQueueItemSelectedHandler = new Handler(){
		@Override
		public void handleMessage(Message msg){
			try{
				long[] list = {msg.arg1};
				if(msg.arg2 == Constants.LAST){
					mService.removeTrack(msg.arg1);
					Toast.makeText(
							RockOnNextGenGL.this, 
							R.string.song_removed_from_playlist, 
							Toast.LENGTH_SHORT).
						show();
				} else {
					mService.removeTrack(msg.arg1);
					mService.enqueue(list, msg.arg2);
					// this should all be done on upon the reception of the service intent 
					// notifying the new song
//					setPauseButton();
//					setCurrentSongLabels(
//							mService.getTrackName(),
//							mService.getArtistName(),
//							mService.duration(),
//							mService.position());
				}
			} catch(Exception e){
				e.printStackTrace();
			}
		}
	};
	
	/**
	 * OnCancelDialogListener
	 */
	OnCancelListener mSongDialogCancelListener = new OnCancelListener() {
		
		@Override
		public void onCancel(DialogInterface dialog) {
			reverseRendererClickAnimation();
			mSetNavigatorCurrent.sendEmptyMessageDelayed(0, Constants.SCROLLING_RESET_TIMEOUT);
		}
	};
	
	/**
	 * OnPositive/NeutralButtonListener
	 */
	android.content.DialogInterface.OnClickListener mSongDialogPlayAllListener = 
		new android.content.DialogInterface.OnClickListener(){

			@Override
			public void onClick(DialogInterface dialog, int which) {
				Message msg = new Message();
				msg.what = 0;
				msg.obj = dialog;
				msg.arg1 = Constants.NOW;
//				mRockOnCubeRenderer.reverseClickAnimation();
				mRockOnRenderer.reverseClickAnimation();
				mSongListDialogOverallOptionsHandler.sendMessageDelayed(msg, Constants.CLICK_ACTION_DELAY);				
			}
		
	};
	
	android.content.DialogInterface.OnClickListener mSongDialogQueueAllListener = 
		new android.content.DialogInterface.OnClickListener(){

			@Override
			public void onClick(DialogInterface dialog, int which) {
				Message msg = new Message();
				msg.what = 0;
				msg.obj = dialog;
				msg.arg1 = Constants.LAST;
//				mRockOnCubeRenderer.reverseClickAnimation();
				mRockOnRenderer.reverseClickAnimation();
				mSongListDialogOverallOptionsHandler.sendMessageDelayed(msg, Constants.CLICK_ACTION_DELAY);
			}
		
	};
	
	/**
	 * handler
	 */
	Handler mSongListDialogOverallOptionsHandler = new Handler(){
		@Override
		public void handleMessage(Message msg){
			AlertDialog aD = (AlertDialog) msg.obj;
			SongCursorAdapter songCursorAdapter = (SongCursorAdapter) aD.getListView().getAdapter();
			Cursor	cursor = songCursorAdapter.getCursor();
			long[] songVector = new long[cursor.getCount()];
			for(int i = 0; i<cursor.getCount(); i++){
				cursor.moveToPosition(i);
				// The _ID field has a different meaning in playlist content providers
				//		-- if it is a playlist we need to fetch the AUDIO_ID field
				songVector[i] = 
					ContentProviderUnifier.
						getAudioIdFromUnknownCursor(cursor);
//				// DEBUG TIME
//				Log.i(TAG, i + " - " + 
//						String.valueOf(
//								cursor.getLong(
//										cursor.getColumnIndexOrThrow(
//												MediaStore.Audio.Media._ID))));
//				Log.i(TAG, i + " - " + 
//						cursor.getString(
//										cursor.getColumnIndexOrThrow(
//												MediaStore.Audio.Media.ARTIST)));
//				Log.i(TAG, i + " - " + 
//						cursor.getString(
//										cursor.getColumnIndexOrThrow(
//												MediaStore.Audio.Media.ALBUM)));
			}
			try{
				if(mService != null)
					mService.enqueue(songVector, msg.arg1);
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	};
	
//	/**
//	 * Some tables (e.g. MediaStore.Audio.Playlist.Members) of the 
//	 * internal content provider have the audio id field
//	 * in different columns
//	 * @param cursor
//	 * @return
//	 */
//	long	getAudioIdFromUnknownCursor(Cursor cursor){
//		/* Playlist.Members */
//		if(cursor.getColumnIndex(MediaStore.Audio.Playlists.Members.AUDIO_ID) != -1){
//			return 
//				cursor.getLong(
//						cursor.getColumnIndexOrThrow(
//								MediaStore.Audio.Playlists.Members.AUDIO_ID));
//		} 
//		/* Audio.Media / Genres.Members/... */
//		else {
//			return 
//				cursor.getLong(
//						cursor.getColumnIndexOrThrow(
//								MediaStore.Audio.Media._ID));
//		}
//	}
	
	/**
	 * reverse the click animation
	 */
	private void reverseRendererClickAnimation(){
//		mRockOnCubeRenderer.reverseClickAnimation();
		mRockOnRenderer.reverseClickAnimation();
	}

	/**
	 * Song Progress
	 */
	public void trackSongProgress(int refreshCode){
		mUpdateSongProgressHandler.removeCallbacksAndMessages(null);
		mUpdateSongProgressHandler.sendEmptyMessage(refreshCode);
	}
	
	public void stopTrackingSongProgress(){
		mUpdateSongProgressHandler.removeCallbacksAndMessages(null);
	}
	
	Handler	mUpdateSongProgressHandler = new Handler(){
		@Override
		public void handleMessage(Message msg){
			try{
//				if(!mRockOnCubeRenderer.isSpinning())
				if(!mRockOnRenderer.isSpinning() || msg.what == Constants.HARD_REFRESH)
				{
					if(msg.what == Constants.HARD_REFRESH)
						((ProgressBarView)findViewById(R.id.progress_bar)).
							setDuration((int) mService.duration(), false);
					((ProgressBarView)findViewById(R.id.progress_bar)).
						setProgress((int) mService.position(), false);
					((ProgressBarView)findViewById(R.id.progress_bar)).
						refresh();
				}
			} catch(Exception e){
				e.printStackTrace();
			}
			if(msg.what == Constants.KEEP_REFRESHING ||
				msg.what == Constants.HARD_REFRESH)
				this.sendEmptyMessageDelayed(Constants.KEEP_REFRESHING, 1000);
		}
	};
	
	/**
	 * 
	 */
	Handler mSeekHandler = new Handler(){
		public void handleMessage(Message msg){
			if(mService != null){
				try{
					if(msg.what <= mService.duration())
						mService.seek(msg.what);
				} catch(Exception e){
					e.printStackTrace();
				}
			}
		}
	};
	
	/**
	 * Controls and Playing Status
	 */
	public void setPlayButton(){
		if(findViewById(R.id.player_controls_play) != null)
			((ImageView)findViewById(R.id.player_controls_play)).
				setImageResource(R.drawable.play); 
	}
	
	public void setPauseButton(){
		if(findViewById(R.id.player_controls_play) != null)
			((ImageView)findViewById(R.id.player_controls_play)).
				setImageResource(R.drawable.pause);
	}
	
	public void setShuffleButton(){
		if(findViewById(R.id.player_controls_shuffle) != null)
			((ImageView)findViewById(R.id.player_controls_shuffle)).
				setImageResource(R.drawable.shuffle_selector);
	}
	
	public void setShuffleNoneButton(){
		if(findViewById(R.id.player_controls_shuffle) != null)
			((ImageView)findViewById(R.id.player_controls_shuffle)).
				setImageResource(R.drawable.shuffle_none_selector);
	}
	
	public void setRepeatCurrentButton(){
		if(findViewById(R.id.player_controls_repeat) != null)
			((ImageView)findViewById(R.id.player_controls_repeat)).
				setImageResource(R.drawable.repeat_current_selector);
	}
	
	public void setRepeatNoneButton(){
		if(findViewById(R.id.player_controls_repeat) != null)
			((ImageView)findViewById(R.id.player_controls_repeat)).
				setImageResource(R.drawable.repeat_none_selector);
	}
	
	Handler updateCurrentPlayerStateButtonsFromServiceHandler = new Handler(){
		public void handleMessage(Message msg){
			try{
				Log.i(TAG, "Checking Service null");
				if(mService != null){
					Log.i(TAG, "Service is not null");
					setCurrentPlayerStateButtons(
							mService.isPlaying(), 
							mService.getShuffleMode(), 
							mService.getRepeatMode());
				} else {
					this.sendEmptyMessageDelayed(0, 1000);
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	};
	
	private void setCurrentPlayerStateButtons(
			boolean isPlaying,
			int		shuffle,
			int		repeat){
    	if(isPlaying){
    		setPauseButton();
    		trackSongProgress(Constants.HARD_REFRESH);
    	}
    	else{
    		setPlayButton();
    		stopTrackingSongProgress();
    	}
    	if(shuffle == Constants.SHUFFLE_NONE)
    		setShuffleNoneButton();
    	else
    		setShuffleButton();
    	if(repeat == Constants.REPEAT_NONE)
    		setRepeatNoneButton();
    	else if(repeat == Constants.REPEAT_CURRENT)
    		setRepeatCurrentButton();
	}
	
	/**
	 * ??
	 * @param songName
	 * @param artistName
	 * @param songDuration
	 * @param trackProgress
	 */
	public void	setCurrentSongLabels(
			String	songName,
			String	artistName,
			long	songDuration,
			long	trackProgress){
		mTrackName = songName;
		mArtistName = artistName;
		mTrackDuration = songDuration;
		mTrackProgress = trackProgress; // ??
//    	Log.i(TAG, "--track: "+mTrackName);
//    	Log.i(TAG, "--artist: "+mArtistName);
//    	Log.i(TAG, "--duration: "+mTrackDuration);
//    	Log.i(TAG, "--progress: "+mTrackProgress);
		((TextView)findViewById(R.id.song_name)).setText(songName);
		((TextView)findViewById(R.id.artist_name)).setText(artistName);
	}
	
	private boolean updateTrackMetaFromService(){
		try{
			if(mService != null){
				mTrackName = mService.getTrackName();
		    	mArtistName = mService.getArtistName();
		    	mTrackDuration = mService.duration();
		    	mTrackProgress = mService.position();
		    	return true;
			} else {
				return false;
			}
		} catch(Exception e){
			e.printStackTrace();
			return false;
		}
	}
	
	/** 
	 * Navigator resume state
	 */
	private void setNavigatorPosition(
			float positionX, 
			float targetPositionX,
			float positionY,
			float targetPositionY)
	{
//		mRockOnCubeRenderer.mPositionX = positionX;
//    	mRockOnCubeRenderer.mTargetPositionX = targetPositionX;
//    	mRockOnCubeRenderer.mPositionY = positionY;
//    	mRockOnCubeRenderer.mTargetPositionY = targetPositionY;
//    	mRockOnCubeRenderer.triggerPositionUpdate();
		mRockOnRenderer.mPositionX = positionX;
    	mRockOnRenderer.mTargetPositionX = targetPositionX;
    	mRockOnRenderer.mPositionY = positionY;
    	mRockOnRenderer.mTargetPositionY = targetPositionY;
    	mRockOnRenderer.triggerPositionUpdate();
	}
	
	/**
	 * Setup the adapter for the autocomplete search box
	 */
	private void setupAutoCompleteSearch(int playlistId){
    	/* cursor */
		Cursor allSongsCursor = new CursorUtils(getApplicationContext()).
    		getAllSongsFromPlaylist(playlistId);
		if(allSongsCursor != null)
			startManagingCursor(allSongsCursor);
    		
		/* adapter */
    	SimpleCursorAdapter songAdapter = new SimpleCursorAdapter(
    			getApplicationContext(),
    			R.layout.simple_dropdown_item_2line,
    			allSongsCursor,
    			new String[] {
    				MediaStore.Audio.Media.TITLE,
					MediaStore.Audio.Media.ARTIST},
    			new int[] {
    				R.id.autotext1, 
    				R.id.autotext2});
    	
    	/* filter */
		AutoCompleteFilterQueryProvider songSearchFilterProvider = 
				new AutoCompleteFilterQueryProvider(
						getApplicationContext(), 
						playlistId);
		
		/* apply filter to view */
		songAdapter.setFilterQueryProvider(songSearchFilterProvider);
    	/* apply adapter to view */
		((AutoCompleteTextView) findViewById(R.id.search_textview)).
    			setAdapter(songAdapter);
    	/* set conversion column of the adapter */
		songAdapter.setStringConversionColumn(
    			allSongsCursor.getColumnIndexOrThrow(
    					MediaStore.Audio.Media.TITLE));
	}
	
	/**
	 * AutoComplete Item Click
	 */
	OnItemClickListener mSongSearchClickListener = new OnItemClickListener() {

		@Override
		public void onItemClick(
				AdapterView<?> arg0, 
				View arg1, 
				int arg2,
				long arg3) 
		{
			Cursor	songCursor = ((SimpleCursorAdapter)arg0.getAdapter()).getCursor();
			long trackId = 
				ContentProviderUnifier.
					getAudioIdFromUnknownCursor(songCursor); 
//				songCursor.getLong(
//					songCursor.getColumnIndexOrThrow(
//							MediaStore.Audio.Media._ID));
			hideSearch();
			Message msg = new Message();
			msg.arg1 = (int) trackId;
			msg.arg2 = Constants.NOW;
			mSongSearchClickHandler.sendMessageDelayed(msg, Constants.CLICK_ACTION_DELAY);
			songCursor.close();
		}
		
	};
	
	Handler	mSongSearchClickHandler = new Handler(){
		public void handleMessage(Message msg){
			long[]	list = {
					msg.arg1
			};
			try{
				mService.enqueue(list, msg.arg2);
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	};
	
	/**
	 * Broadcast Receivers
	 */
    private BroadcastReceiver mStatusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            try{
	            if (action.equals(Constants.META_CHANGED)) {
	                // redraw the artist/title info and
	            	updateTrackMetaFromService();
	                setCurrentSongLabels(
	                		mTrackName, 
	                		mArtistName, 
	                		mTrackDuration, 
	                		mTrackProgress);
	            	// set new max for progress bar
	            	// TODO:
	            	if(mService != null && mService.isPlaying())
	            	{
	            		setPauseButton();
	            		trackSongProgress(Constants.HARD_REFRESH);
	            	}
	            	else
	            	{
	            		setPlayButton();
	            		stopTrackingSongProgress();
	            	}
	//                setPauseButtonImage();
	//                queueNextRefresh(1);
//	            	mRockOnCubeRenderer.setCurrentByAlbumId(mService.getAlbumId());
	            	mRockOnRenderer.setCurrentByAlbumId(mService.getAlbumId());
	            } else if (action.equals(Constants.PLAYBACK_COMPLETE)) {
	            	if(mService != null && mService.isPlaying()){
	            		setPauseButton();
	            		trackSongProgress(Constants.HARD_REFRESH);
	            	}
	            	else{
	            		setPlayButton();
	            		stopTrackingSongProgress();
	            	}
	//                if (mOneShot) {
	//                    finish();
	//                }
	            } else if (action.equals(Constants.PLAYSTATE_CHANGED)) {
	            	if(mService != null && mService.isPlaying()){
	            		setPauseButton();
	            		trackSongProgress(Constants.HARD_REFRESH);
	            	}
	            	else{
	            		setPlayButton();
	            		stopTrackingSongProgress();
	            	}
	//                setPauseButtonImage();
	            }
            } catch(Exception e){
            	e.printStackTrace();
            }
        }
    };

	
	/**
	 * Service connection
	 */
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName classname, IBinder obj) {
            mService = IRockOnNextGenService.Stub.asInterface(obj);
//            startPlayback();
            try {
//                // Assume something is playing when the service says it is,
//                // but also if the audio ID is valid but the service is paused.
//                if (mService.getAudioId() >= 0 || mService.isPlaying() ||
//                        mService.getPath() != null) {
//                    // something is playing now, we're done
//                    if (mOneShot || mService.getAudioId() < 0) {
//                        mRepeatButton.setVisibility(View.INVISIBLE);
//                        mShuffleButton.setVisibility(View.INVISIBLE);
//                        mQueueButton.setVisibility(View.INVISIBLE);
//                    } else {
//                        mRepeatButton.setVisibility(View.VISIBLE);
//                        mShuffleButton.setVisibility(View.VISIBLE);
//                        mQueueButton.setVisibility(View.VISIBLE);
//                        setRepeatButtonImage();
//                        setShuffleButtonImage();
//                    }
//                    setPauseButtonImage();
//                    return;
//                }
                if(mService.getAudioId() >= 0 || mService.getPath() != null){
                	/* get playing meta */
                	updateTrackMetaFromService();
                	
                	/* update UI */
                	updateCurrentPlayerStateButtonsFromServiceHandler
                		.sendEmptyMessage(0);
                	
                	Log.i(TAG, "track: "+mTrackName);
                	Log.i(TAG, "artist: "+mArtistName);
                	Log.i(TAG, "duration: "+mTrackDuration);
                	Log.i(TAG, "progress: "+mTrackProgress);
                	
                	return;
                } else {
                	Log.i(TAG, "Not Playing...");
                }
            } catch (RemoteException ex) {
            }
//            // Service is dead or not playing anything. If we got here as part
//            // of a "play this file" Intent, exit. Otherwise go to the Music
//            // app start screen.
//            if (getIntent().getData() == null) {
//                Intent intent = new Intent(Intent.ACTION_MAIN);
//                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                intent.setClass(RockOnNextGenGL.this, RockOnNextGenGL.class);
//                startActivity(intent);
//            }
//            finish();
        }
        public void onServiceDisconnected(ComponentName classname) {
        }
};

}