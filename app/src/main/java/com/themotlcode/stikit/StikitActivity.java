package com.themotlcode.stikit;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.MediaRouteActionProvider;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.app.Dialog;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;

import java.io.IOException;

public class StikitActivity extends ActionBarActivity implements View.OnTouchListener {

    private static final String TAG = "Blake";

    private static final int REQUEST_CODE = 1;

    private static final float SWIPE_FRICTION = 2.0f;

    private MediaRouter mMediaRouter;
    private MediaRouteSelector mMediaRouteSelector;
    private MediaRouter.Callback mMediaRouterCallback;
    private CastDevice mSelectedDevice;
    private GoogleApiClient mApiClient;
    private Cast.Listener mCastListener;
    private ConnectionCallbacks mConnectionCallbacks;
    private ConnectionFailedListener mConnectionFailedListener;
    private HelloWorldChannel mHelloWorldChannel;
    private boolean mApplicationStarted;
    private boolean mWaitingForReconnect;
    private String mSessionId;
    private EditText castText;
    private ViewGroup castTextAndShadow;
    private GestureDetectorCompat gestureDetector;
    private StikitMessageFactory smf;
    private boolean connected;

    public static String color = "#EEEE22";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stikit);
        castText = (EditText)findViewById(R.id.castText);
        castTextAndShadow = (ViewGroup)findViewById(R.id.castTextAndShadow);
        castTextAndShadow.setEnabled(false);
        mMediaRouter = MediaRouter.getInstance(getApplicationContext());
        mMediaRouteSelector = new MediaRouteSelector.Builder()
                .addControlCategory(
                        CastMediaControlIntent.categoryForCast(getResources()
                                .getString(R.string.app_id))).build();
        mMediaRouterCallback = new MyMediaRouterCallback();
        gestureDetector = new GestureDetectorCompat(this, new MyGestureListener());

        /*
        //TODO remove after testing animations on emulator
        Button temp = (Button) findViewById(R.id.temp);
        temp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CastToScreen(null, castText.getText().toString(), color, 3);
            }
        });
        */
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        int errorCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if(errorCode != ConnectionResult.SUCCESS)
        {
            GooglePlayServicesUtil.getErrorDialog(errorCode,this,REQUEST_CODE);
            return;
        }
        mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);

        castText.setOnTouchListener(this);
        smf = new StikitMessageFactory();
        if (connected) {
            styleCastConnect();
        } else {
            styleCastDisconnect();
        }
    }
    @Override
    protected void onPause() {
        if (isFinishing()) {
            // End media router discovery
            mMediaRouter.removeCallback(mMediaRouterCallback);
        }
        super.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
    }

    @Override
    protected void onStop() {
        mMediaRouter.removeCallback(mMediaRouterCallback);
        super.onStop();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        if(isFinishing()) {
            teardown();
        }
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.stikit, menu);
        MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
        MediaRouteActionProvider mediaRouteActionProvider = (MediaRouteActionProvider) MenuItemCompat
                .getActionProvider(mediaRouteMenuItem);
        // Set the MediaRouteActionProvider selector for device discovery.
        mediaRouteActionProvider.setRouteSelector(mMediaRouteSelector);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch(id){
            case R.id.colorPicker:
                // custom dialog
                final ColorPickerDialog colorPickerDialog = new ColorPickerDialog(this, castText);

                colorPickerDialog.show();
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    public void CastToScreen(View v, String message, String color, int command, float velocity) {
        if (smf != null) {
            sendMessage(smf.Message(message, color, command));
        }

        // greater velocity -> less duration
        int duration;
        int vel = (int) velocity / getResources().getDisplayMetrics().densityDpi; // 0 <= vel <= 50
        if (vel == 0) {
            // don't animate
            duration = 0;
        } else if (vel < 5) {
            // super gentle
            duration = 600;
        } else if (vel < 10) {
            // lazy gentle
            duration = 400;
        } else if (vel < 25) {
            // casual
            duration = 200;
        } else {
            // aggresive (with index finger)
            duration = 100;
        }
        Log.d(TAG, "duration: "+duration+"\tvel: "+vel+"\tvelocity: "+velocity);

        switch (command) {
            case 0:
                // UP
                // transition up then alpha fade in from origin
                castTextAndShadow.animate().setDuration(duration).translationY(-castTextAndShadow.getBottom()).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        castText.setText("");
                        castTextAndShadow.setAlpha(0);
                        castTextAndShadow.setTranslationY(0);
                        castTextAndShadow.animate().alpha(1);
                    }
                });
                break;
            case 1:
                // RIGHT
                // translate to the right then translate new in from the left
                castTextAndShadow.animate().setDuration(duration).translationX(castTextAndShadow.getRight()).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        castText.setText("");
                        castTextAndShadow.setTranslationX(-castTextAndShadow.getRight());
                        castTextAndShadow.animate().translationX(0);
                    }
                });
                break;
            case 2:
                // LEFT
                // translate to the left then translate new in from the right
                castTextAndShadow.animate().setDuration(duration).translationX(-castTextAndShadow.getRight()).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        castText.setText("");
                        castTextAndShadow.setTranslationX(castTextAndShadow.getRight());
                        castTextAndShadow.animate().translationX(0);
                    }
                });
                break;
            case 3:
                // DELETE
                // "crumple" the card
                castTextAndShadow.animate().setDuration(duration).scaleX(0).scaleY(0).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        castText.setText("");
                        castTextAndShadow.setScaleX(1);
                        castTextAndShadow.setScaleY(1);
                        castTextAndShadow.setAlpha(0);
                        castTextAndShadow.animate().alpha(1);
                    }
                });
                break;
        }
    }

    /**
     * Callback for MediaRouter events
     */
    private class MyMediaRouterCallback extends MediaRouter.Callback {

        @Override
        public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo info) {
            Log.d(TAG, "onRouteSelected");
            // Handle the user route selection.
            mSelectedDevice = CastDevice.getFromBundle(info.getExtras());

            launchReceiver();
        }

        @Override
        public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo info) {
            Log.d(TAG, "onRouteUnselected: info=" + info);
            teardown();
            mSelectedDevice = null;
        }
    }

    /**
     * Start the receiver app
     */
    private void launchReceiver() {
        try {
            mCastListener = new Cast.Listener() {

                @Override
                public void onApplicationDisconnected(int errorCode) {
                    Log.d(TAG, "application has stopped");
                    teardown();
                }

            };
            // Connect to Google Play services
            mConnectionCallbacks = new ConnectionCallbacks();
            mConnectionFailedListener = new ConnectionFailedListener();
            Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions
                    .builder(mSelectedDevice, mCastListener);
            mApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Cast.API, apiOptionsBuilder.build())
                    .addConnectionCallbacks(mConnectionCallbacks)
                    .addOnConnectionFailedListener(mConnectionFailedListener)
                    .build();

            mApiClient.connect();
        } catch (Exception e) {
            Log.e(TAG, "Failed launchReceiver", e);
        }
    }

    /**
     * Google Play services callbacks
     */
    private class ConnectionCallbacks implements
            GoogleApiClient.ConnectionCallbacks {
        @Override
        public void onConnected(Bundle connectionHint) {
            Log.d(TAG, "onConnected");

            if (mApiClient == null) {
                // We got disconnected while this runnable was pending
                // execution.
                return;
            }

            try {
                if (mWaitingForReconnect) {
                    mWaitingForReconnect = false;

                    // Check if the receiver app is still running
                    if ((connectionHint != null)
                            && connectionHint
                            .getBoolean(Cast.EXTRA_APP_NO_LONGER_RUNNING)) {
                        Log.d(TAG, "App  is no longer running");
                        teardown();
                    } else {
                        // Re-create the custom message channel
                        try {
                            Cast.CastApi.setMessageReceivedCallbacks(
                                    mApiClient,
                                    mHelloWorldChannel.getNamespace(),
                                    mHelloWorldChannel);
                        } catch (IOException e) {
                            Log.e(TAG, "Exception while creating channel", e);
                        }
                    }
                } else {
                    // Launch the receiver app
                    Cast.CastApi
                            .launchApplication(mApiClient,
                                    getString(R.string.app_id), false)
                            .setResultCallback(
                                    new ResultCallback<Cast.ApplicationConnectionResult>() {
                                        @Override
                                        public void onResult(
                                                Cast.ApplicationConnectionResult result) {
                                            Status status = result.getStatus();
                                            if (status.isSuccess()) {
                                                ApplicationMetadata applicationMetadata = result
                                                        .getApplicationMetadata();
                                                mSessionId = result
                                                        .getSessionId();
                                                String applicationStatus = result
                                                        .getApplicationStatus();
                                                boolean wasLaunched = result
                                                        .getWasLaunched();
                                                Log.d(TAG,
                                                        "application name: "
                                                                + applicationMetadata
                                                                .getName()
                                                                + ", status: "
                                                                + applicationStatus
                                                                + ", sessionId: "
                                                                + mSessionId
                                                                + ", wasLaunched: "
                                                                + wasLaunched);
                                                mApplicationStarted = true;
                                                styleCastConnect();

                                                // Create the custom message
                                                // channel
                                                mHelloWorldChannel = new HelloWorldChannel();
                                                try {
                                                    Cast.CastApi
                                                            .setMessageReceivedCallbacks(
                                                                    mApiClient,
                                                                    mHelloWorldChannel
                                                                            .getNamespace(),
                                                                    mHelloWorldChannel);
                                                } catch (IOException e) {
                                                    Log.e(TAG,
                                                            "Exception while creating channel",
                                                            e);
                                                }


                                                // set the initial instructions
                                                // on the receiver
                                                //sendMessage("Test");
                                            } else {
                                                Log.e(TAG,
                                                        "application could not launch");
                                                teardown();
                                            }
                                        }
                                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch application", e);
            }
        }

        @Override
        public void onConnectionSuspended(int cause) {
            Log.d(TAG, "onConnectionSuspended");
            mWaitingForReconnect = true;
        }
    }

    // make the sticky note opaque when we connect to cast
    private void styleCastConnect() {
        //castTextAndShadow.setAlpha(1f);
        castText.setEnabled(true);
        connected = true;
    }

    // make the sticky note transparent if we lose connection with cast
    private void styleCastDisconnect() {
        //castTextAndShadow.setAlpha(0.3f);
        castText.setEnabled(false);
        castText.setText(getResources().getString(R.string.initial_note));
        connected = false;
    }

    /**
     * Google Play services callbacks
     */
    private class ConnectionFailedListener implements
            GoogleApiClient.OnConnectionFailedListener {
        @Override
        public void onConnectionFailed(ConnectionResult result) {
            Log.e(TAG, "onConnectionFailed ");

            teardown();
        }
    }

    /**
     * Tear down the connection to the receiver
     */
    private void teardown() {
        Log.d(TAG, "teardown");
        if (mApiClient != null) {
            if (mApplicationStarted) {
                if (mApiClient.isConnected()  || mApiClient.isConnecting()) {
                    try {
                        Cast.CastApi.stopApplication(mApiClient, mSessionId);
                        if (mHelloWorldChannel != null) {
                            Cast.CastApi.removeMessageReceivedCallbacks(
                                    mApiClient,
                                    mHelloWorldChannel.getNamespace());
                            mHelloWorldChannel = null;
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Exception while removing channel", e);
                    }
                    mApiClient.disconnect();
                }
                mApplicationStarted = false;
            }
            mApiClient = null;
        }
        mSelectedDevice = null;
        mWaitingForReconnect = false;
        mSessionId = null;
        styleCastDisconnect();
    }

    /**
     * Send a text message to the receiver
     *
     * @param message
     */
    private void sendMessage(final String message) {
        if (mApiClient != null && mHelloWorldChannel != null) {
            try {
                Cast.CastApi.sendMessage(mApiClient,
                        mHelloWorldChannel.getNamespace(), message)
                        .setResultCallback(new ResultCallback<Status>() {
                            @Override
                            public void onResult(Status result) {
                                if (!result.isSuccess()) {
                                    Log.e(TAG, "Sending message failed");
                                }
                                else {
                                    //Toast.makeText(StikitActivity.this, "Stuck it!", Toast.LENGTH_LONG).show();
                                }
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, "Exception while sending message", e);
            }
        }
    }

    /**
     * Custom message channel
     */
    class HelloWorldChannel implements Cast.MessageReceivedCallback {

        /**
         * @return custom namespace
         */
        public String getNamespace() {
            return getString(R.string.namespace);
        }

        /*
         * Receive message from the receiver app
         */
        @Override
        public void onMessageReceived(CastDevice castDevice, String namespace,
                                      String message) {
            Log.d(TAG, "onMessageReceived: " + message);
        }

    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        Log.d(TAG, "touch me baby");
        return this.gestureDetector.onTouchEvent(motionEvent);
    }

    class MyGestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final String DEBUG_TAG = "Gestures";

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            // pop up confirmation window to clear all notes
            //super.onLongPress(e);
            new AlertDialog.Builder(StikitActivity.this)
                    .setTitle("Delete All Notes?")
                    .setMessage("Do you wish to delete all the notes from the big screen?")
                    .setPositiveButton("DELETE", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            CastToScreen(null, "", "", 4, 0);
                        }
                    })
                    .setNegativeButton("CANCEL", null)
                    .show();
            // focus current note on device on cast
            return super.onDoubleTap(e);
        }

        @Override
        public boolean onFling(MotionEvent event1, MotionEvent event2, float velocityX, float velocityY) {
            Log.d(DEBUG_TAG, "onFling: " + event1.toString()+event2.toString());
            Log.d(DEBUG_TAG, "velocityX: " + velocityX);
            Log.d(DEBUG_TAG, "velocityY: " + velocityY);
            boolean enabled = castText.isEnabled();
            castText.setEnabled(false);
            if(-velocityY > Math.abs(velocityX)) {
                CastToScreen(null, castText.getText().toString(), color,0,  Math.abs(velocityY));
            }
            else if (velocityY > Math.abs(velocityX)) {
                CastToScreen(null, "", "", 3, Math.abs(velocityY)); //DELETE
            }
            else if (velocityX > Math.abs(velocityY)) {
                CastToScreen(null, "","",1, Math.abs(velocityX)); //LEFT
            }
            else if (-velocityX > Math.abs(velocityY)) {
                CastToScreen(null, "","",2, Math.abs(velocityX)); //RIGHT
            }
            castText.setEnabled(enabled);
            return true;
        }
    }


}
