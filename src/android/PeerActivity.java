package cordova.plugin.skyway;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
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
import android.widget.Toast;

import com.smartidea.moneyreco.R;

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

    private Peer _peer;
    private MediaStream _localStream;
    private MediaStream _remoteStream;
    private MediaConnection _mediaConnection;
    private DataConnection _signalingChannel;

    private String _strOwnId;

    public enum CallState {
        TERMINATED,
        CALLING,
        ESTABLISHED
    }

    private CallState _callState;

    private Handler _handler;
    private MyTimers _timer;

    private boolean enableAudio = true;
    private boolean enableVideo = true;
    private String apiKey;
    private String domain;
    private String peerId;
    private String targetPeerId;
    private long startCall = 0;
    private long endCall = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window wnd = getWindow();
        wnd.addFlags(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_skyway_calling);

        _handler = new Handler(Looper.getMainLooper());
        final Activity activity = this;
        _callState = CallState.TERMINATED;

        this.apiKey = getIntent().getExtras().getString(EXTRA_API_KEY);
        this.domain = getIntent().getExtras().getString(EXTRA_DOMAIN);
        this.peerId = getIntent().getExtras().getString(EXTRA_PEER_ID);
        this.targetPeerId = getIntent().getExtras().getString(EXTRA_TARGET_PEER_ID);
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
                if (ContextCompat.checkSelfPermission(activity,
                        Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(activity,
                        Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 0);
                } else {

                    // Get a local MediaStream & show it
                    startLocalStream();
                    if (!TextUtils.isEmpty(targetPeerId)) {
                        onPeerSelected(targetPeerId);
                    } else {
                        intervalTimmerCall();
                    }
                }

            }
        });

        // CALL (Incoming call)
        _peer.on(Peer.PeerEventEnum.CALL, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                if (!(object instanceof MediaConnection)) {
                    return;
                }

                _mediaConnection = (MediaConnection) object;
                _callState = CallState.CALLING;

                _mediaConnection.answer(_localStream);
                setMediaCallbacks();
                _callState = CallState.ESTABLISHED;
            }
        });

        // CONNECT (Custom Signaling Channel for a call)
        _peer.on(Peer.PeerEventEnum.CONNECTION, new OnCallback() {
            @Override
            public void onCallback(Object object) {
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
                Log.d(TAG, "[On/Close]");
            }
        });
        _peer.on(Peer.PeerEventEnum.DISCONNECTED, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                Log.d(TAG, "[On/Disconnected]");
            }
        });
        _peer.on(Peer.PeerEventEnum.ERROR, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                PeerError error = (PeerError) object;
                Log.d(TAG, "[On/Error]" + error);
            }
        });


        ImageButton btnVoice = findViewById(R.id.btnVoice);
        btnVoice.setOnClickListener(v -> {
            int audioTrack = _localStream.getAudioTracks();
            if (audioTrack > 0) {
                enableAudio = !enableAudio;
                _localStream.setEnableAudioTrack(0, enableAudio);
                btnVoice.setBackgroundResource(enableAudio ? R.drawable.icon_voice_on : R.drawable.icon_voice_off);
                Toast.makeText(this, enableAudio ? "Enable audio" : "Disable audio", Toast.LENGTH_SHORT).show();
            }
        });

        ImageButton btnVideo = findViewById(R.id.btnVideo);
        btnVideo.setOnClickListener(v -> {
            int videoTrack = _localStream.getVideoTracks();
            if (videoTrack > 0) {
                enableVideo = !enableVideo;
                _localStream.setEnableVideoTrack(0, enableVideo);
                btnVideo.setBackgroundResource(enableVideo ? R.drawable.icon_video_on : R.drawable.icon_video_off);
                Toast.makeText(this, enableVideo ? "Enable video" : "Disable video", Toast.LENGTH_SHORT).show();
            }
        });

        View hangupAction = findViewById(R.id.btnHangup);
        hangupAction.setOnClickListener(v -> {
            endCall = System.currentTimeMillis() / 1000;
            setResult(RESULT_OK, createResultHangup());
            finish();
        });
    }

    private void intervalTimmerCall() {
        Log.d(TAG, "intervalTimmerCall...");
        if (_callState == CallState.TERMINATED) {
            _timer = new MyTimers();
            _timer.sendEmptyMessage(MyTimers.START);
        }
    }

    private void destroyTimer() {
        if (_timer != null) {
            _timer.removeMessages(MyTimers.START);
            _timer = null;
        }
    }

    private Intent createResultHangup() {
        //calculate total time called
        Intent result = new Intent();
        result.putExtra(Skyway.ACTION_HANGUP, true);
        result.putExtra(Skyway.EXTRA_DATA_START_TIME_CALL, startCall);
        result.putExtra(Skyway.EXTRA_DATA_END_TIME_CALL, endCall);
        return result;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d(TAG, "onNewIntent...");
//		final Bundle extras = intent.getExtras();
//		final StringBuilder sb = new StringBuilder();
//		final Set<String> keySet = extras.keySet();
//		for (final String key: keySet) {
//			sb.append('\"');
//			sb.append(key);
//			sb.append("\"=\"");
//			sb.append(extras.get(key));
//			sb.append("\", ");
//		}
//		Log.d(TAG, "onNewIntent..." + sb);
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
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startLocalStream();
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
        destroyPeer();
        destroyTimer();
        super.onDestroy();
    }

    //
    // Get a local MediaStream & show it
    //
    void startLocalStream() {
        Navigator.initialize(_peer);
        MediaConstraints constraints = new MediaConstraints();
        _localStream = Navigator.getUserMedia(constraints);

        Canvas canvas = (Canvas) findViewById(R.id.svLocalView);
        _localStream.addVideoRenderer(canvas, 0);
    }

    //
    // Set callbacks for MediaConnection.MediaEvents
    //
    void setMediaCallbacks() {

        _mediaConnection.on(MediaConnection.MediaEventEnum.STREAM, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                _remoteStream = (MediaStream) object;
                Canvas canvas = (Canvas) findViewById(R.id.svRemoteView);
                _remoteStream.addVideoRenderer(canvas, 0);
                _callState = CallState.ESTABLISHED;
                startCall = System.currentTimeMillis() / 1000;
                destroyTimer();
            }
        });

        _mediaConnection.on(MediaConnection.MediaEventEnum.CLOSE, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                closeRemoteStream();
                _signalingChannel.close();
                _callState = CallState.TERMINATED;
                endCall = System.currentTimeMillis() / 1000;
            }
        });

        _mediaConnection.on(MediaConnection.MediaEventEnum.ERROR, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                PeerError error = (PeerError) object;
                Log.d(TAG, "[On/MediaError]" + error);
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

            }
        });

        _signalingChannel.on(DataConnection.DataEventEnum.CLOSE, new OnCallback() {
            @Override
            public void onCallback(Object object) {

            }
        });

        _signalingChannel.on(DataConnection.DataEventEnum.ERROR, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                PeerError error = (PeerError) object;
                Log.d(TAG, "[On/DataError]" + error);
            }
        });

        _signalingChannel.on(DataConnection.DataEventEnum.DATA, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                String message = (String) object;
                Log.d(TAG, "[On/Data]" + message);

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
                }
            }
        });

    }

    //
    // Clean up objects
    //
    private void destroyPeer() {
        closeRemoteStream();

        if (null != _localStream) {
            Canvas canvas = (Canvas) findViewById(R.id.svLocalView);
            _localStream.removeVideoRenderer(canvas, 0);
            _localStream.close();
        }

        closeMediaConnection();

        Navigator.terminate();

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
    // Close a MediaConnection
    //
    void closeMediaConnection() {
        if (null != _mediaConnection) {
            if (_mediaConnection.isOpen()) {
                _mediaConnection.close();
            }
            unsetMediaCallbacks();
        }
    }

    //
    // Close a remote MediaStream
    //
    void closeRemoteStream() {
        if (null == _remoteStream) {
            return;
        }

        Canvas canvas = (Canvas) findViewById(R.id.svRemoteView);
        _remoteStream.removeVideoRenderer(canvas, 0);
        _remoteStream.close();
    }

    //
    // Create a MediaConnection
    //
    void onPeerSelected(String strPeerId) {
        Log.d(TAG, "onPeerSelected..." + strPeerId);
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
            switch (msg.what) {
                case START:
                    // Do something etc.
                    Log.d("TimerExample", "START");
                    if (_callState == CallState.TERMINATED && !TextUtils.isEmpty(targetPeerId)) {
                        onPeerSelected(targetPeerId);
                        removeMessages(START);
                    } else {

                        sendEmptyMessageDelayed(START, 10000);
                    }
                    break;
                default:
                    removeMessages(START);
                    break;
            }
        }
    }
}
