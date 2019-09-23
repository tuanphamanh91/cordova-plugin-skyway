package cordova.plugin.skyway;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.smartidea.moneyreco.R;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Iterator;

import javax.net.ssl.HttpsURLConnection;

import io.skyway.Peer.Browser.Canvas;
import io.skyway.Peer.Browser.MediaConstraints;
import io.skyway.Peer.Browser.MediaStream;
import io.skyway.Peer.Browser.Navigator;
import io.skyway.Peer.CallOption;
import io.skyway.Peer.DataConnection;
import io.skyway.Peer.MediaConnection;
import io.skyway.Peer.OnCallback;
import io.skyway.Peer.Peer;
import io.skyway.Peer.PeerError;
import io.skyway.Peer.PeerOption;

/**
 * PeerActivity.java
 * ECL WebRTC p2p call sample
 * <p>
 * In this sample, a callee will be prompted by an alert-dialog to select
 * either "answer" or "reject" an incoming call (unlike p2p-videochat sample,
 * in which a callee will answer the call automatically).
 */
public class PeerActivity extends AppCompatActivity {
    private static final String TAG = "Skyway";
    public static final String EXTRA_API_KEY = "skyway_extra_api_key";
    public static final String EXTRA_DOMAIN = "skyway_extra_domain";
    public static final String EXTRA_PEER_ID = "skyway_extra_peer_id";
    public static final String EXTRA_TARGET_PEER_ID = "skyway_extra_target_peer_id";
    public static final String EXTRA_DEBUG_MODE = "skyway_extra_debug_mode";
    public static final String EXTRA_TIME_INTERVAL_RECONNECT = "skyway_extra_interval_reconnect";
    public static final String EXTRA_SHOW_LOCAL_VIDEO = "skyway_extra_show_local_video";
    public static final String EXTRA_ENABLE_SPEAKER = "skyway_extra_enable_speaker";
    public static final String EXTRA_BROWSER_URL = "skyway_extra_browser_url";
    public static final String EXTRA_INCALL_URL = "skyway_extra_incall_url";
    public static final String EXTRA_INCALL_HEADER = "skyway_extra_incall_header";
    public static final String EXTRA_TIME_LIMITING_CONFIG = "skyway_extra_time_limiting_config";
    public static final String EXTRA_SELF_CALLING = "skyway_extra_self_calling";

    private final String DISCONNECTED_EVENT = "disconnected";

    private Peer _peer;
    private MediaStream _localStream;
    private MediaStream _remoteStream;
    private MediaConnection _mediaConnection;
    private DataConnection _signalingChannel;

    public enum CallState {
        TERMINATED,
        CALLING,
        ESTABLISHED
    }

    private CallState _callState;

    private Handler _handler;
    private MyTimers _timer;
    private TimerLimiting _timmerLimiting;

    private boolean enableAudio = true;
    private boolean enableVideo = true;
    private String apiKey;
    private String domain;
    private String peerId;
    private String targetPeerId;
    private boolean isDebugMode = false;
    private boolean isShowLocalVideo = false;
    private boolean isEnableSpeaker = false;
    private boolean isSelfCalling = false;
    private String browserUrl;
    private String inCallUrl = null;
    private JSONObject inCallHeader = null;
    private JSONObject timeLimitConfig = null;
    private int intervalReconnect;
    private long startCall = 0;
    private long endCall = 0;
    private boolean passiveCallHangup = false;
    private boolean didHangup = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

//        Window wnd = getWindow();
//        wnd.addFlags(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_skyway_calling);

        _handler = new Handler(Looper.getMainLooper());
        final Activity activity = this;
        _callState = CallState.TERMINATED;

        this.apiKey = getIntent().getExtras().getString(EXTRA_API_KEY);
        this.domain = getIntent().getExtras().getString(EXTRA_DOMAIN);
        this.peerId = getIntent().getExtras().getString(EXTRA_PEER_ID);
        this.targetPeerId = getIntent().getExtras().getString(EXTRA_TARGET_PEER_ID);
        this.isDebugMode = getIntent().getExtras().getBoolean(EXTRA_DEBUG_MODE, false);
        this.intervalReconnect = getIntent().getExtras().getInt(EXTRA_TIME_INTERVAL_RECONNECT, 0);
        this.isShowLocalVideo = getIntent().getExtras().getBoolean(EXTRA_SHOW_LOCAL_VIDEO, false);
        this.isEnableSpeaker = getIntent().getExtras().getBoolean(EXTRA_ENABLE_SPEAKER, false);
        this.isSelfCalling = getIntent().getExtras().getBoolean(EXTRA_SELF_CALLING, false);
        this.browserUrl = getIntent().getExtras().getString(EXTRA_BROWSER_URL);
        this.inCallUrl = getIntent().getExtras().getString(EXTRA_INCALL_URL);
        try {
            this.inCallHeader = new JSONObject(getIntent().getExtras().getString(EXTRA_INCALL_HEADER));
        } catch (Exception e) {
        }
        try {
            this.timeLimitConfig = new JSONObject(getIntent().getExtras().getString(EXTRA_TIME_LIMITING_CONFIG));
        } catch (Exception e) {
        }
        this.isDebugMode = true;
        //
        // Initialize Peer
        //
        PeerOption option = new PeerOption();
        option.key = apiKey;
        option.domain = domain;
        if (!TextUtils.isEmpty(peerId)) {
            _peer = new Peer(this, peerId, option);
        } else {
            _peer = new Peer(this, option);
        }

        //
        // Set Peer event callbacks
        //

        // OPEN
        _peer.on(Peer.PeerEventEnum.OPEN, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                // Request permissions
                if (isDebugMode) Log.d(TAG, "Peer.PeerEventEnum.OPEN");
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                        || ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 0);
                } else {
                    // Get a local MediaStream & show it
                    startLocalStream();
                    intervalTimmerCall();
                }

            }
        });

        // CALL (Incoming call)
        _peer.on(Peer.PeerEventEnum.CALL, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                if (isDebugMode) Log.d(TAG, "Peer.PeerEventEnum.CALL");
                if (!(object instanceof MediaConnection)) {
                    return;
                }

                _mediaConnection = (MediaConnection) object;
                _callState = CallState.CALLING;

                _mediaConnection.answer(_localStream);
                setMediaCallbacks();
                _callState = CallState.ESTABLISHED;
                destroyTimer();
            }
        });

        // CONNECT (Custom Signaling Channel for a call)
        _peer.on(Peer.PeerEventEnum.CONNECTION, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                if (isDebugMode) Log.d(TAG, "Peer.PeerEventEnum.CONNECTION");
                if (!(object instanceof DataConnection)) {
                    return;
                }

                _signalingChannel = (DataConnection) object;
                setSignalingCallbacks();

            }
        });

        _peer.on(Peer.PeerEventEnum.CLOSE, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                if (isDebugMode) Log.d(TAG, "Peer.PeerEventEnum.CLOSE");
            }
        });
        _peer.on(Peer.PeerEventEnum.DISCONNECTED, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                if (isDebugMode) Log.d(TAG, "Peer.PeerEventEnum.DISCONNECTED");
            }
        });
        _peer.on(Peer.PeerEventEnum.ERROR, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                PeerError error = (PeerError) object;
                _callState = CallState.TERMINATED;
                if (error.type == PeerError.PeerErrorEnum.UNAVAILABLE_ID) {
                    try {
                        new AlertDialog.Builder(activity)
                                .setTitle("Error")
                                .setMessage(error.getMessage())
                                // A null listener allows the button to dismiss the dialog and take no further action.
                                .setNegativeButton(android.R.string.ok, (dialog, which) -> {
                                    dialog.dismiss();
                                    setResult(RESULT_OK, createResultResetPeer());
                                    finish();
                                })
                                .create()
                                .show();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if (isDebugMode) Log.d(TAG, "[Peer.PeerEventEnum.ERROR]" + error.message);
            }
        });


        ImageButton btnVoice = findViewById(R.id.btnVoice);
        btnVoice.setOnClickListener(v -> {
            int audioTrack = (_localStream != null) ? _localStream.getAudioTracks() : 0;
            if (audioTrack > 0) {
                enableAudio = !enableAudio;
                _localStream.setEnableAudioTrack(0, enableAudio);
                btnVoice.setBackgroundResource(enableAudio ? R.drawable.icon_voice_on : R.drawable.icon_voice_off);
                Toast.makeText(this, enableAudio ? "Enable audio" : "Disable audio", Toast.LENGTH_SHORT).show();
            }
        });

        ImageButton btnVideo = findViewById(R.id.btnVideo);
        btnVideo.setOnClickListener(v -> {
            int videoTrack = (_localStream != null) ? _localStream.getVideoTracks() : 0;
            if (videoTrack > 0) {
                enableVideo = !enableVideo;
                _localStream.setEnableVideoTrack(0, enableVideo);
                btnVideo.setBackgroundResource(enableVideo ? R.drawable.icon_video_on : R.drawable.icon_video_off);
                Toast.makeText(this, enableVideo ? "Enable video" : "Disable video", Toast.LENGTH_SHORT).show();
            }
        });

        View hangupAction = findViewById(R.id.btnHangup);
        hangupAction.setOnClickListener(v -> {
            hangup(this.isSelfCalling);
        });

        findViewById(R.id.btn_switch_camera).setOnClickListener(v -> {
            if (null != _localStream) {
                _localStream.switchCamera();
            }
        });

        View btnOpenBrowser = findViewById(R.id.btn_open_browser);
        if (TextUtils.isEmpty(browserUrl)) btnOpenBrowser.setVisibility(View.GONE);
        btnOpenBrowser.setOnClickListener(v -> {
            try {
                this.hangup(this.isSelfCalling);
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(browserUrl));
                startActivity(browserIntent);
            } catch (Exception e) {
            }
        });
    }

    private void hangup(boolean isSelfHangup) {
        if (didHangup) return;
        if (isDebugMode) Log.d(TAG, "PeerActivity... hangup");
        this.didHangup = true;
        endCall = System.currentTimeMillis() / 1000;
        if (isSelfHangup && _signalingChannel != null) {
            if (isDebugMode) Log.d(TAG, "PeerActivity... hangup 1");
            //send event disconnected
            _signalingChannel.send(DISCONNECTED_EVENT);
            _handler.postDelayed(() -> {
                if (isDebugMode) Log.d(TAG, "PeerActivity... hangup 2");
//                destroyAll();
                setResult(RESULT_OK, createResultHangup(true));
                finish();
            }, 200);
        } else {
            if (isDebugMode) Log.d(TAG, "PeerActivity... hangup 3");
//            destroyAll();
            setResult(RESULT_OK, createResultHangup(isSelfHangup));
            finish();
        }
    }

    private void intervalTimmerCall() {
        if (isDebugMode) Log.d(TAG, "intervalTimmerCall...");
        if (_callState == CallState.TERMINATED) {
            _timer = new MyTimers();
            _timer.sendEmptyMessage(MyTimers.START);
        }
    }

    private void destroyTimer() {
        if (isDebugMode) Log.d(TAG, "destroyTimer");
        if (_timer != null) {
            _timer.removeMessages(MyTimers.START);
            _timer = null;
        }
        if (_timmerLimiting != null) {
            _timmerLimiting.removeMessages(TimerLimiting.START);
            _timmerLimiting = null;
        }
    }

    private Intent createResultHangup(boolean isSelfHangup) {
        //calculate total time called
        Intent result = new Intent();
        result.putExtra(Skyway.ACTION_HANGUP, true);
        result.putExtra(Skyway.EXTRA_DATA_START_TIME_CALL, startCall);
        result.putExtra(Skyway.EXTRA_DATA_END_TIME_CALL, endCall);
        result.putExtra(Skyway.EXTRA_DATA_IS_SELF_HANGUP, isSelfHangup);
        return result;
    }

    private Intent createResultResetPeer() {
        //calculate total time called
        Intent result = new Intent();
        result.putExtra(Skyway.ACTION_RESET_PEER, true);
        return result;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (isDebugMode) Log.d(TAG, "onNewIntent");
        super.onNewIntent(intent);
        if (intent != null && intent.hasExtra(EXTRA_TARGET_PEER_ID)) {
            this.targetPeerId = intent.getStringExtra(EXTRA_TARGET_PEER_ID);
            if (!TextUtils.isEmpty(targetPeerId)) {
                onPeerSelected(targetPeerId);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 0: {
                if (grantResults.length >= 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    startLocalStream();
                    intervalTimmerCall();
                } else {
                    Toast.makeText(this, "Failed to access the camera and microphone.\nclick allow when asked for permission.", Toast.LENGTH_LONG).show();
                }
                break;
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Disable Sleep and Screen Lock
        Window wnd = getWindow();
        wnd.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        wnd.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Set volume control stream type to WebRTC audio.
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
    }

    @Override
    protected void onPause() {
        // Set default volume control stream type.
        setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);
        super.onPause();
        //destroy if is calling
        if (_callState == CallState.CALLING || _callState == CallState.ESTABLISHED) {
            Log.d(TAG, "onpause and will doing hangup");
            hangup(true);
        } else {
            Log.d(TAG, "onpause and will NOT hangup");
            hangup(false);
        }
    }

    @Override
    protected void onStop() {
        // Enable Sleep and Screen Lock
        Window wnd = getWindow();
        wnd.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        wnd.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        super.onStop();
    }


    @Override
    protected void onDestroy() {
        destroyAll();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        Log.d("MoneyReco", "onBackPressed...");
        hangup(true);
//        super.onBackPressed();
    }

    void destroyAll() {
        destroyPeer();
        destroyTimer();
    }

    //
    // Get a local MediaStream & show it
    //
    void startLocalStream() {
        if (isDebugMode) Log.d(TAG, "startLocalStream");
        Navigator.initialize(_peer);
        MediaConstraints constraints = new MediaConstraints();
        _localStream = Navigator.getUserMedia(constraints);

        Canvas canvas = findViewById(R.id.svLocalView);
        if (this.isShowLocalVideo && _localStream != null) {
//            _localStream.addVideoRenderer(canvas, 0);
        }
    }

    //
    // Set callbacks for MediaConnection.MediaEvents
    //
    void setMediaCallbacks() {

        _mediaConnection.on(MediaConnection.MediaEventEnum.STREAM, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                if (isDebugMode) Log.d(TAG, "MediaConnection.MediaEventEnum.STREAM");
                _remoteStream = (MediaStream) object;
                Canvas canvas = findViewById(R.id.svRemoteView);
                canvas.scaling = Canvas.ScalingEnum.ASPECT_FIT;
                _remoteStream.addVideoRenderer(canvas, 0);

                /**
                 * This code required to fix the problem with local stream cause local will be covered by remote stream
                 */
                Canvas canvasLocal = findViewById(R.id.svLocalView);
                if (isShowLocalVideo && _localStream != null) {
                    _localStream.addVideoRenderer(canvasLocal, 0);
                }

                _callState = CallState.ESTABLISHED;
                startCall = System.currentTimeMillis() / 1000;
                passiveCallHangup = false;
                didHangup = false;
                destroyTimer();
                //enable speaker if required
                if (isEnableSpeaker) {
                    AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    audioManager.setMode(AudioManager.STREAM_MUSIC);
                    audioManager.setSpeakerphoneOn(true);
                }
                sendApiCallProcessing();
                startTimeLimitingIfNeed();
            }
        });

        _mediaConnection.on(MediaConnection.MediaEventEnum.CLOSE, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                if (isDebugMode) Log.d(TAG, "MediaConnection.MediaEventEnum.CLOSE");
                _callState = CallState.TERMINATED;
//                destroyAll();
                if (!passiveCallHangup) {
                    passiveCallHangup = true;
                    hangup(false);
                }
            }
        });

        _mediaConnection.on(MediaConnection.MediaEventEnum.ERROR, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                PeerError error = (PeerError) object;
                if (isDebugMode) Log.d(TAG, "[On/MediaError]" + error);
            }
        });

    }

    void startTimeLimitingIfNeed() {
        if (this.timeLimitConfig == null) return;
        try {
            int maxCallDurationInSeconds = this.timeLimitConfig.getInt("maxCallDurationInSeconds");//in seconds
            int timeBeforeShowWaringInSeconds = this.timeLimitConfig.getInt("timeBeforeShowWaringInSeconds");//in seconds
            int delayMils = Math.max((maxCallDurationInSeconds - timeBeforeShowWaringInSeconds) * 1000, 0);
            if (isDebugMode) {
                String textRemaining = this.timeLimitConfig.optString("textRemaining");
                String backgroundColor = this.timeLimitConfig.optString("backgroundColorHex", "#fff");
                String textFormat = this.timeLimitConfig.optString("textFormat", "$s Seconds");

                JSONObject jsoLogs = new JSONObject();
                jsoLogs.put("maxCallDurationInSeconds", maxCallDurationInSeconds);
                jsoLogs.put("timeBeforeShowWaringInSeconds", timeBeforeShowWaringInSeconds);
                jsoLogs.put("textRemaining", textRemaining);
                jsoLogs.put("backgroundColor", backgroundColor);
                jsoLogs.put("textFormat", textFormat);
                jsoLogs.put("delayMils", delayMils);
                Log.d(TAG, "startTimeLimitingIfNeed... " + jsoLogs.toString());
            }
            _timmerLimiting = new TimerLimiting();
            _timmerLimiting.sendEmptyMessageDelayed(TimerLimiting.START, delayMils);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void showWarningTimeLimiting() {
        try {
            int maxCallDurationInSeconds = this.timeLimitConfig.getInt("maxCallDurationInSeconds");//in seconds
            String textRemaining = this.timeLimitConfig.optString("textRemaining");
            String backgroundColor = this.timeLimitConfig.optString("backgroundColorHex", "#fff");
            String textFormat = this.timeLimitConfig.optString("textFormat", "%d Seconds");
            int callingDurationSeconds = (int) (System.currentTimeMillis() / 1000 - startCall);
            int delaySeconds = (maxCallDurationInSeconds - callingDurationSeconds);

            if (delaySeconds < 0) {
                //end call
                hangup(true);
            } else {
                findViewById(R.id.ln_time_limit).setVisibility(View.VISIBLE);
                try {
                    findViewById(R.id.ln_time_limit).setBackgroundColor(Color.parseColor(backgroundColor));
                } catch (Exception e) {
                }

                TextView txtTimeCaption = findViewById(R.id.text_limit_time_caption);
                txtTimeCaption.setText(textRemaining);
                TextView txtTimeValue = findViewById(R.id.text_limit_time_value);
                txtTimeValue.setText(String.format(textFormat, delaySeconds));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void sendApiCallProcessing() {
        if (TextUtils.isEmpty(this.inCallUrl)) return;
        //call api
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                HttpsURLConnection myConnection = null;
                try {
                    URL url = new URL(inCallUrl);
                    myConnection =
                            (HttpsURLConnection) url.openConnection();
                    myConnection.setReadTimeout(15000);
                    myConnection.setConnectTimeout(15000);
                    myConnection.setRequestMethod("GET");
                    myConnection.setRequestProperty("Content-Type",
                            "application/json");
                    if (inCallHeader != null) {
                        Iterator<String> keys = inCallHeader.keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            String value = inCallHeader.getString(key);
                            myConnection.setRequestProperty(key, value);
                        }
                    }

                    int responseCode = myConnection.getResponseCode();
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(myConnection.getInputStream()));
                    String inputLine;
                    StringBuffer response = new StringBuffer();

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();
                    if (isDebugMode)
                        Log.d("Skyway", "Sending 'Get' request to URL : " + inCallUrl + "--" + responseCode);
                    if (isDebugMode) Log.d("Skyway", "Response : -- " + response.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (myConnection != null) {
                        myConnection.disconnect();
                    }
                }
            }
        });
    }

    //
    // Set callbacks for DataConnection.DataEvents
    //
    void setSignalingCallbacks() {
        _signalingChannel.on(DataConnection.DataEventEnum.OPEN, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                if (isDebugMode)
                    Log.d(TAG, "_signalingChannel on DataConnection.DataEventEnum.OPEN");
            }
        });

        _signalingChannel.on(DataConnection.DataEventEnum.CLOSE, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                if (isDebugMode)
                    Log.d(TAG, "_signalingChannel on DataConnection.DataEventEnum.CLOSE");
            }
        });

        _signalingChannel.on(DataConnection.DataEventEnum.ERROR, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                PeerError error = (PeerError) object;
                if (isDebugMode) Log.d(TAG, "[_signalingChannel On/DataError]" + error);
            }
        });

        _signalingChannel.on(DataConnection.DataEventEnum.DATA, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                String message = (String) object;
                if (isDebugMode) Log.d(TAG, "[_signalingChannel On/Data]" + message);

                switch (message) {
                    case "reject":
                        closeMediaConnection();
                        _signalingChannel.close();
                        _callState = CallState.TERMINATED;
                        break;
                    case "cancel":
                        closeMediaConnection();
                        _signalingChannel.close();
                        _callState = CallState.TERMINATED;
                        break;
                    case DISCONNECTED_EVENT:
                        _callState = CallState.TERMINATED;
//                        destroyAll();
                        if (!passiveCallHangup) {
                            passiveCallHangup = true;
                            hangup(false);
                        }
                }
            }
        });

    }

    //
    // Clean up objects
    //
    private void destroyPeer() {
        closeRemoteStream();
        closeLocalStream();
        closeMediaConnection();
        closeDataConnection();

        if (null != _peer) {
            unsetPeerCallback(_peer);
            if (!_peer.isDisconnected()) {
                _peer.disconnect();
            }

            if (!_peer.isDestroyed()) {
                _peer.destroy();
            }

            _peer = null;
        }

        try {
            Navigator.terminate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //
    // Unset callbacks for PeerEvents
    //
    void unsetPeerCallback(Peer peer) {
        if (null == _peer) {
            return;
        }

        peer.on(Peer.PeerEventEnum.OPEN, null);
        peer.on(Peer.PeerEventEnum.CONNECTION, null);
        peer.on(Peer.PeerEventEnum.CALL, null);
        peer.on(Peer.PeerEventEnum.CLOSE, null);
        peer.on(Peer.PeerEventEnum.DISCONNECTED, null);
        peer.on(Peer.PeerEventEnum.ERROR, null);
    }

    //
    // Unset callbacks for MediaConnection.MediaEvents
    //
    void unsetMediaCallbacks() {
        if (null == _mediaConnection) {
            return;
        }

        _mediaConnection.on(MediaConnection.MediaEventEnum.STREAM, null);
        _mediaConnection.on(MediaConnection.MediaEventEnum.CLOSE, null);
        _mediaConnection.on(MediaConnection.MediaEventEnum.ERROR, null);
    }

    //
    // Unset callbacks for MediaConnection.MediaEvents
    //
    void unsetDataCallback() {
        if (null == _signalingChannel) {
            return;
        }

        _signalingChannel.on(DataConnection.DataEventEnum.OPEN, null);
        _signalingChannel.on(DataConnection.DataEventEnum.ERROR, null);
        _signalingChannel.on(DataConnection.DataEventEnum.DATA, null);
    }

    //
    // Close a MediaConnection
    //
    void closeMediaConnection() {
        if (isDebugMode) Log.d(TAG, "closeMediaConnection...");
        if (null != _mediaConnection) {
            if (isDebugMode) Log.d(TAG, "closeMediaConnection...1");
            try {
                if (_mediaConnection.isOpen()) {
                    _mediaConnection.close();
                }
                unsetMediaCallbacks();
                _mediaConnection = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    void closeDataConnection() {
        if (isDebugMode) Log.d(TAG, "closeDataConnection...");
        if (null != _signalingChannel) {
            if (isDebugMode) Log.d(TAG, "closeDataConnection...1");
            try {
                if (_signalingChannel.isOpen()) {
                    _signalingChannel.close();
                }
                unsetDataCallback();
                _signalingChannel = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //
    // Close a remote MediaStream
    //
    void closeRemoteStream() {
        if (isDebugMode) Log.d(TAG, "closeRemoteStream...");
        if (null == _remoteStream) {
            return;
        }
        if (isDebugMode) Log.d(TAG, "closeRemoteStream...1");
        try {
            Canvas canvas = findViewById(R.id.svRemoteView);
            _remoteStream.removeVideoRenderer(canvas, 0);
            _remoteStream.close();
            _remoteStream = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //
    // Close a local MediaStream
    //
    void closeLocalStream() {
        if (isDebugMode) Log.d(TAG, "closeLocalStream...");
        if (null != _localStream) {
            try {
                Canvas canvas = findViewById(R.id.svLocalView);
                _localStream.removeVideoRenderer(canvas, 0);
                _localStream.close();
                _localStream = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    //
    // Create a MediaConnection
    //
    void onPeerSelected(String strPeerId) {
        if (isDebugMode) Log.d(TAG, "onPeerSelected..." + strPeerId);
        if (null == _peer) {
            return;
        }

        if (null != _mediaConnection) {
            _mediaConnection.close();
        }

        CallOption option = new CallOption();
        _mediaConnection = _peer.call(strPeerId, _localStream, option);
        if (null != _mediaConnection) {
            setMediaCallbacks();
            _callState = CallState.CALLING;
        }

        // custom P2P signaling channel to reject call attempt
        _signalingChannel = _peer.connect(strPeerId);
        if (null != _signalingChannel) {
            setSignalingCallbacks();
        }
    }

    public class MyTimers extends Handler {

        public static final int START = 0;

        @Override
        public void handleMessage(Message msg) {
            if (isDebugMode) Log.d(TAG, "MyTimers... handleMessage: " + msg.what);
            switch (msg.what) {
                case START:
                    // Do something etc.
                    if (isDebugMode)
                        Log.d(TAG, "MyTimers... handleMessage: START _callState = " + _callState);
                    if (_callState == CallState.TERMINATED) {
                        if (!TextUtils.isEmpty(targetPeerId)) {
                            onPeerSelected(targetPeerId);
                        }
                    }
                    sendEmptyMessageDelayed(START, intervalReconnect);
                    break;
                default:
                    removeMessages(START);
                    break;
            }
        }
    }

    public class TimerLimiting extends Handler {

        public static final int START = 10;

        @Override
        public void handleMessage(Message msg) {
            if (isDebugMode) Log.d(TAG, "TimerLimiting... handleMessage: " + msg.what);
            switch (msg.what) {
                case START:
                    // Do something etc.
                    if (isDebugMode) Log.d(TAG, "TimerLimiting... handleMessage: START");
                    showWarningTimeLimiting();
                    sendEmptyMessageDelayed(START, 1000);//each 1 seconds
                    break;
                default:
                    removeMessages(START);
                    break;
            }
        }
    }
}
