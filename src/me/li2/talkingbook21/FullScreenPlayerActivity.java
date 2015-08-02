package me.li2.talkingbook21;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import android.app.ActionBar;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.NavUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import me.li2.audioplayer.AudioPlayerController;
import me.li2.audioplayer.AudioPlayerController.PlaybackState;

public class FullScreenPlayerActivity extends FragmentActivity
    implements LrcFragment.Callbacks {
    
    public final static String EXTRA_AUDIO_PATH = "me.li2.android.lrcbuilder.audio_path";
    public final static String EXTRA_LRC_PATH = "me.li2.android.lrcbuilder.lrc_path";
    private final static String TAG = "FullScreenPlayerActivity";
    private final static int PROGRESS_UPDATE_INTERVAL = 200;
    
    private LrcFragment mLrcFragment;
    private SeekBar mSeekBar;
    private TextView mCurrentTimeLabel;
    private TextView mDurationLabel;
    private ImageView mPlayPauseView;
    private ImageView mSkipPrevView;
    private ImageView mSkipNextView;
    
    private Handler mHandler = new Handler();
    private AudioPlayerController mPlayerController;

    private Drawable mPlayDrawable;
    private Drawable mPauseDrawable;
    private Uri mAudioUri;
    private Uri mLrcUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_player);

        mAudioUri = Uri.parse(getIntent().getStringExtra(EXTRA_AUDIO_PATH));
        mLrcUri = Uri.parse(getIntent().getStringExtra(EXTRA_LRC_PATH));

        String path = mLrcUri.getPath();
        String[] pathParts = path.split("/");
        String title = pathParts[pathParts.length-1];
        
        // Enables the "home" icon to be some kind of button and displays the "<".
        // should also add meta-data "android.support.PARENT_ACTIVITY" in manifest.
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
            if (NavUtils.getParentActivityName(this) != null) {
                ActionBar actionBar = getActionBar();
                if (actionBar != null) {
                    actionBar.setTitle(title);
                    actionBar.setDisplayHomeAsUpEnabled(true);
                }
            }
        }
        
        mPlayPauseView = (ImageView) findViewById(R.id.catcher_playPause);
        mSkipPrevView = (ImageView) findViewById(R.id.catcher_skipPrev);
        mSkipNextView = (ImageView) findViewById(R.id.catcher_skipNext);
        mSeekBar = (SeekBar) findViewById(R.id.catcher_seekbar);
        mCurrentTimeLabel = (TextView) findViewById(R.id.catcher_currentTimeLabel);
        mDurationLabel = (TextView) findViewById(R.id.catcher_durationLabel);
        
        mSkipPrevView.setVisibility(View.GONE);
        mSkipNextView.setVisibility(View.GONE);
        
        mPlayDrawable = getResources().getDrawable(R.drawable.ic_play_arrow_white_24dp);
        mPauseDrawable = getResources().getDrawable(R.drawable.ic_pause_white_24dp);

        mPlayPauseView.setOnClickListener(mPlayPauseViewOnClickListener);        
        mSeekBar.setOnSeekBarChangeListener(mSeekBarOnSeekBarChangeListener);

        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        Fragment oldLrcFragment = fm.findFragmentById(R.id.catcher_lrcFragmentContainer);
        Fragment newLrcFragment = LrcFragment.newInstance(mLrcUri);
        mLrcFragment = (LrcFragment)newLrcFragment;
        if (oldLrcFragment != null) {
            ft.remove(oldLrcFragment);
        }
        ft.add(R.id.catcher_lrcFragmentContainer, newLrcFragment);
        ft.commit();
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        
        mPlayerController = new AudioPlayerController();
        mPlayerController.registerCallback(mPlayerControllerCallbacks);
        mPlayerController.play(this, mAudioUri);
        mPlayerController.setLooping(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSeekbarUpdate();
        mExecutorService.shutdown();
        mPlayerController.stop();
    }
    
    
    private AudioPlayerController.Callbacks mPlayerControllerCallbacks = new AudioPlayerController.Callbacks() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            updatePlaybackState(state);
        }
        
        @Override
        public void onAudioDataChanged(int duration) {
            updateDuration(duration);
        }
    };

    private OnClickListener mPlayPauseViewOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            PlaybackState playbackState = mPlayerController.getPlaybackState();
            switch (playbackState) {
            case PLAYBACK_STATE_PLAYING:
                mPlayerController.pause();
                break;                
            case PLAYBACK_STATE_STOPPED:
            case PLAYBACK_STATE_PAUSED:
                mPlayerController.play();
                break;                    
            default:
                Log.d(TAG, "onClick with state " + playbackState);
                break;
            }
        }
    };
    
    private OnSeekBarChangeListener mSeekBarOnSeekBarChangeListener = new OnSeekBarChangeListener() {        
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            mPlayerController.seekToPosition(seekBar.getProgress());
            scheduleSeekbarUpdate();
        }
        
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            stopSeekbarUpdate();
        }
        
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            mCurrentTimeLabel.setText(DateUtils.formatElapsedTime(progress/1000));
        }
    };
    
    private void updatePlaybackState(PlaybackState playbackState) {
        Log.d(TAG, "updatePlaybackState: " + playbackState);
        switch (playbackState) {
        case PLAYBACK_STATE_PLAYING:
            mPlayPauseView.setVisibility(View.VISIBLE);
            mPlayPauseView.setImageDrawable(mPauseDrawable);
            scheduleSeekbarUpdate();
            break;
        case PLAYBACK_STATE_PAUSED:
        case PLAYBACK_STATE_STOPPED:
        case PLAYBACK_STATE_NONE:
            mPlayPauseView.setVisibility(View.VISIBLE);
            mPlayPauseView.setImageDrawable(mPlayDrawable);
            stopSeekbarUpdate();
            break;            
        default:
            break;
        }
    }
    
    // Update Seekbar *********************************************************
    private final ScheduledExecutorService mExecutorService = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> mScheduleFuture;
    
    private void scheduleSeekbarUpdate() {
        Log.d(TAG, "scheduleSeekbarUpdate()");
        stopSeekbarUpdate();
        if (!mExecutorService.isShutdown()) {
            mScheduleFuture = mExecutorService.scheduleAtFixedRate(
                new Runnable() {
                    @Override
                    public void run() {
                        mHandler.post(mUpdateProgressTask);
                    }
                }, PROGRESS_UPDATE_INTERVAL, PROGRESS_UPDATE_INTERVAL, TimeUnit.MILLISECONDS);
        }
    }
    
    private void stopSeekbarUpdate() {
        if (mScheduleFuture != null) {
            mScheduleFuture.cancel(false);
        }
    }
    private final Runnable mUpdateProgressTask = new Runnable() {
        @Override
        public void run() {
            updateProgress();
        }
    };
    
    private void updateProgress() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int msec = mPlayerController.getCurrentPosition();
                mSeekBar.setProgress(msec);
                mLrcFragment.seekLrcToTime(msec);
            }
        });
    }
    
    // Update duration label ************************************************** 
    private void updateDuration(int duration) {
        // Update Seekbar & TotalTimeLabel
        mSeekBar.setMax(duration);
        mDurationLabel.setText(DateUtils.formatElapsedTime(duration/1000));
    }
    
    // LrcFragment callback ***************************************************
    @Override
    public void onLrcItemSelected(int msec) {
        Log.d(TAG, "onLrcItemSelected: " + msec/1000 + " seconds");
        mPlayerController.seekToPosition(msec);
    }

    // Navigate Back to parent activity ***************************************
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            if (NavUtils.getParentActivityName(this) != null) {
                NavUtils.navigateUpFromSameTask(this);
            }
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
}