package cordova.plugin.skyway;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;

/**
 * This class echoes a string called from JavaScript.
 */
public class Skyway extends CordovaPlugin {
    public static final String ACTION_HANGUP = "action_skyway_hangup";
    public static final String ACTION_CHANGE_MUTE = "action_skyway_change_mute";
    public static final String ACTION_CHANGE_VIDEO = "action_skyway_change_video";
    public static final String EXTRA_DATA_START_TIME_CALL = "extra_data_skyway_start_time_call";
    public static final String EXTRA_DATA_END_TIME_CALL = "extra_data_skyway_en_time_call";
    public static final String EXTRA_DATA_IS_SELF_HANGUP = "extra_data_skyway_is_self_hangup";

    /**
     * Common tag used for logging statements.
     */
    private static final String LOGTAG = "Skyway";

    private static final int DEFAULT_TIME_INTERVAL_RECONNECT = 10000;

    private static final int RC_START_VIDEO_CALL = 89;
    /**
     * Cordova Actions.
     */
    private static final String ACTION_CREATE_PEER = "createPeer";

    /* options */
    private static final String OPT_API_KEY = "apiKey";
    private static final String OPT_DOMAIN = "domain";
    private static final String OPT_PEER_ID = "peerId";
    private static final String OPT_TARGET_PEER_ID = "targetPeerId";
    private static final String OPT_TIME_INTERVAL_RECONNECT = "intervalReconnect";
    private static final String OPT_DEBUG_MODE = "debugMode";
    private static final String OPT_SHOW_LOCAL_VIDEO = "showLocalVideo";
    private static final String OPT_ENABLE_SPEAKER = "enableSpeaker";


    /* event */
    private static final String EVENT_HANGUP = "skyway_hangup";
    private static final String EVENT_CHANGE_MUTE = "skyway_change_mute";
    private static final String EVENT_CHANGE_VIDEO = "skyway_change_video";

    private String apiKey = null;
    private String domain = null;
    private String peerId = null;
    private String targetPeerId = null;
    private boolean isDebugMode = false;
    private boolean isShowLocalVideo = false;
    private boolean enableSpeaker = false;
    private int intervalReconnect = DEFAULT_TIME_INTERVAL_RECONNECT;

    @Override
    public boolean execute(String action, JSONArray inputs, CallbackContext callbackContext) throws JSONException {
        PluginResult result = null;
        if (action.equals(ACTION_CREATE_PEER)) {
            JSONObject options = inputs.optJSONObject(0);
            result = executeCreatePeer(options, callbackContext);
            return true;
        }
        return false;
    }


    private PluginResult executeCreatePeer(JSONObject options, CallbackContext callbackContext) {
        if (isDebugMode) Log.d(LOGTAG, "executeCreatePeer..." + options.toString());
        setOptions(options);
        callbackContext.success();
        startPeer();
        return null;
    }

    private void startPeer() {
        if (isDebugMode) Log.d(LOGTAG, "startPeer... targetPeerId = " + this.targetPeerId);
        if (isDebugMode) Log.d(LOGTAG, "startPeer...");
        Intent intent = new Intent(cordova.getContext(), PeerActivity.class);
        intent.putExtra(PeerActivity.EXTRA_API_KEY, this.apiKey);
        intent.putExtra(PeerActivity.EXTRA_DOMAIN, this.domain);
        intent.putExtra(PeerActivity.EXTRA_DEBUG_MODE, this.isDebugMode);
        intent.putExtra(PeerActivity.EXTRA_TIME_INTERVAL_RECONNECT, this.intervalReconnect);
        intent.putExtra(PeerActivity.EXTRA_SHOW_LOCAL_VIDEO, this.isShowLocalVideo);
        intent.putExtra(PeerActivity.EXTRA_ENABLE_SPEAKER, this.enableSpeaker);
        if (!TextUtils.isEmpty(this.peerId)) {
            intent.putExtra(PeerActivity.EXTRA_PEER_ID, this.peerId);
        }
        if (!TextUtils.isEmpty(this.targetPeerId)) {
            intent.putExtra(PeerActivity.EXTRA_TARGET_PEER_ID, this.targetPeerId);
        }
        cordova.startActivityForResult(this, intent, RC_START_VIDEO_CALL);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        Log.d(LOGTAG, "onActivityResult... ");
        if (requestCode == RC_START_VIDEO_CALL && resultCode == Activity.RESULT_OK) {
            if (intent != null && intent.hasExtra(ACTION_HANGUP)) {
                //seconds
                long startTime = intent.getLongExtra(EXTRA_DATA_START_TIME_CALL, 0);
                long endTime = intent.getLongExtra(EXTRA_DATA_END_TIME_CALL, 0);
                boolean isHangup = intent.getBooleanExtra(EXTRA_DATA_IS_SELF_HANGUP, true);
                String jsonStr = String.format("{ 'start_time': %d, 'end_time': %d, 'is_hangup': %b }", startTime, endTime, isHangup);
                fireEvent(EVENT_HANGUP, jsonStr);
            } else if (intent != null && intent.hasExtra(ACTION_CHANGE_MUTE)) {
                boolean mute = intent.getBooleanExtra(ACTION_CHANGE_MUTE, false);
                String jsonStr = String.format("{ 'mute': %b }", mute);
                fireEvent(EVENT_CHANGE_MUTE, jsonStr);
            } else if (intent != null && intent.hasExtra(ACTION_CHANGE_VIDEO)) {
                boolean mute = intent.getBooleanExtra(ACTION_CHANGE_VIDEO, false);
                String jsonStr = String.format("{ 'video': %b }", mute);
                fireEvent(EVENT_CHANGE_VIDEO, jsonStr);
            }
        }
        super.onActivityResult(requestCode, resultCode, intent);

    }

    private void setOptions(JSONObject options) {
        if (options == null) return;
        try {
            if (options.has(OPT_API_KEY)) this.apiKey = options.getString(OPT_API_KEY);
            if (options.has(OPT_DOMAIN)) this.domain = options.getString(OPT_DOMAIN);
            if (options.has(OPT_PEER_ID)) this.peerId = options.getString(OPT_PEER_ID);
            if (options.has(OPT_TARGET_PEER_ID)) this.targetPeerId = options.getString(OPT_TARGET_PEER_ID);
            if (options.has(OPT_DEBUG_MODE)) this.isDebugMode = options.optBoolean(OPT_DEBUG_MODE);
            if (options.has(OPT_TIME_INTERVAL_RECONNECT)) this.intervalReconnect = options.getInt(OPT_TIME_INTERVAL_RECONNECT);
            if (options.has(OPT_SHOW_LOCAL_VIDEO)) this.isShowLocalVideo = options.optBoolean(OPT_SHOW_LOCAL_VIDEO);
            if (options.has(OPT_SHOW_LOCAL_VIDEO)) this.enableSpeaker = options.optBoolean(OPT_ENABLE_SPEAKER);
        } catch(JSONException e) {
            e.printStackTrace();
        }
    }

    private void fireEvent(final String eventName, final String jsonStr) {
        cordova.getActivity().runOnUiThread(() -> {
            if (isDebugMode) Log.d(LOGTAG, "fireEvent... eventName: " + eventName + " -- jsonStr: " + jsonStr);
            if (jsonStr != null && jsonStr.length() > 0) {
                webView.loadUrl(String.format("javascript:cordova.fireDocumentEvent('%s', %s);", eventName, jsonStr));
            } else {
                webView.loadUrl(String.format("javascript:cordova.fireDocumentEvent('%s');", eventName));
            }
        });
    }
}
