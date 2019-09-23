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

import java.util.Locale;
import java.util.Set;

/**
 * This class echoes a string called from JavaScript.
 */
public class Skyway extends CordovaPlugin {
    public static final String ACTION_HANGUP = "action_skyway_hangup";
    public static final String ACTION_CHANGE_MUTE = "action_skyway_change_mute";
    public static final String ACTION_CHANGE_VIDEO = "action_skyway_change_video";
    public static final String ACTION_RESET_PEER = "action_skyway_reset_peer";

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
    private static final String OPT_BROWSER_URL = "browserUrl";
    private static final String OPT_INCALL_URL = "inCallUrl";
    private static final String OPT_INCALL_HEADER = "inCallHeader";
    private static final String OPT_TIME_LIMITING_CONFIG = "timeLimitingConfig";
    private static final String OPT_SELF_CALLING = "selfCalling";


    /* event */
    private static final String EVENT_HANGUP = "skyway_hangup";
    private static final String EVENT_CHANGE_MUTE = "skyway_change_mute";
    private static final String EVENT_CHANGE_VIDEO = "skyway_change_video";
    private static final String EVENT_RESET_PEER = "skyway_reset_peer";

    private String apiKey = null;
    private String domain = null;
    private String peerId = null;
    private String targetPeerId = null;
    private boolean isDebugMode = false;
    private boolean isShowLocalVideo = false;
    private boolean enableSpeaker = false;
    private boolean isSelfCalling = false;
    private int intervalReconnect = DEFAULT_TIME_INTERVAL_RECONNECT;
    private String browserUrl = null;
    private String inCallUrl = null;
    private JSONObject inCallHeader = null;
    private JSONObject timeLimitingConfig = null;
    private static CallbackContext context;

    @Override
    public boolean execute(String action, JSONArray inputs, CallbackContext callbackContext) throws JSONException {
        if (action.equals(ACTION_CREATE_PEER)) {
            JSONObject options = inputs.optJSONObject(0);
            executeCreatePeer(options, callbackContext);
            return true;
        }
        return false;
    }


    private void executeCreatePeer(JSONObject options, CallbackContext callbackContext) {
        if (isDebugMode) Log.d(LOGTAG, "executeCreatePeer..." + options.toString());
        context = callbackContext;
        cordova.getThreadPool().execute(() -> {
            setOptions(options);
//        callbackContext.success();
            startPeer();
        });

//        setOptions(options);
//        callbackContext.success();
//        startPeer();
    }

    private void startPeer() {
        if (isDebugMode) Log.d(LOGTAG, "startPeer... targetPeerId = " + this.targetPeerId);
        if (isDebugMode) Log.d(LOGTAG, "startPeer...");
        Intent intent = new Intent(cordova.getContext(), PeerActivity.class);
//        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(PeerActivity.EXTRA_API_KEY, this.apiKey);
        intent.putExtra(PeerActivity.EXTRA_DOMAIN, this.domain);
        intent.putExtra(PeerActivity.EXTRA_DEBUG_MODE, this.isDebugMode);
        intent.putExtra(PeerActivity.EXTRA_TIME_INTERVAL_RECONNECT, this.intervalReconnect);
        intent.putExtra(PeerActivity.EXTRA_SHOW_LOCAL_VIDEO, this.isShowLocalVideo);
        intent.putExtra(PeerActivity.EXTRA_ENABLE_SPEAKER, this.enableSpeaker);
        intent.putExtra(PeerActivity.EXTRA_BROWSER_URL, this.browserUrl);
        intent.putExtra(PeerActivity.EXTRA_INCALL_URL, this.inCallUrl);
        intent.putExtra(PeerActivity.EXTRA_SELF_CALLING, this.isSelfCalling);
        if (this.inCallHeader != null) {
            intent.putExtra(PeerActivity.EXTRA_INCALL_HEADER, this.inCallHeader.toString());
        }
        if (this.timeLimitingConfig != null) {
            intent.putExtra(PeerActivity.EXTRA_TIME_LIMITING_CONFIG, this.timeLimitingConfig.toString());
        }
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
                JSONObject jso = new JSONObject();
                try {
                    jso.put("event", EVENT_HANGUP);
                    jso.put("start_time", startTime);
                    jso.put("end_time", endTime);
                    jso.put("is_hangup", isHangup);
                } catch (Exception e) {}
                fireEventJson(jso);
//                String jsonStr = String.format(Locale.ENGLISH, "{ 'event': %s, 'start_time': %d, 'end_time': %d, 'is_hangup': %b }", EVENT_HANGUP, startTime, endTime, isHangup);
//                fireEvent(EVENT_HANGUP, jsonStr);
            } else if (intent != null && intent.hasExtra(ACTION_CHANGE_MUTE)) {
                boolean mute = intent.getBooleanExtra(ACTION_CHANGE_MUTE, false);

                JSONObject jso = new JSONObject();
                try {
                    jso.put("event", EVENT_CHANGE_MUTE);
                    jso.put("mute", mute);
                } catch (Exception e) {}
                fireEventJson(jso);


//                String jsonStr = String.format("{ 'mute': %b }", mute);
//                fireEvent(EVENT_CHANGE_MUTE, jsonStr);
            } else if (intent != null && intent.hasExtra(ACTION_CHANGE_VIDEO)) {
                boolean mute = intent.getBooleanExtra(ACTION_CHANGE_VIDEO, false);
                JSONObject jso = new JSONObject();
                try {
                    jso.put("event", EVENT_CHANGE_VIDEO);
                    jso.put("mute", mute);
                } catch (Exception e) {}
                fireEventJson(jso);
//                String jsonStr = String.format("{ 'video': %b }", mute);
//                fireEvent(EVENT_CHANGE_VIDEO, jsonStr);
            } else if (intent != null && intent.hasExtra(ACTION_RESET_PEER)) {
                JSONObject jso = new JSONObject();
                try {
                    jso.put("event", EVENT_RESET_PEER);
                } catch (Exception e) {}
                fireEventJson(jso);
//                fireEvent(EVENT_RESET_PEER, null);
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
            if (options.has(OPT_TARGET_PEER_ID))
                this.targetPeerId = options.getString(OPT_TARGET_PEER_ID);
            if (options.has(OPT_DEBUG_MODE)) this.isDebugMode = options.optBoolean(OPT_DEBUG_MODE);
            if (options.has(OPT_TIME_INTERVAL_RECONNECT))
                this.intervalReconnect = options.getInt(OPT_TIME_INTERVAL_RECONNECT);
            if (options.has(OPT_SHOW_LOCAL_VIDEO))
                this.isShowLocalVideo = options.optBoolean(OPT_SHOW_LOCAL_VIDEO);
            if (options.has(OPT_SHOW_LOCAL_VIDEO))
                this.enableSpeaker = options.optBoolean(OPT_ENABLE_SPEAKER);
            if (options.has(OPT_BROWSER_URL)) this.browserUrl = options.optString(OPT_BROWSER_URL);
            if (options.has(OPT_INCALL_URL)) this.inCallUrl = options.optString(OPT_INCALL_URL);
            if (options.has(OPT_INCALL_HEADER))
                this.inCallHeader = options.getJSONObject(OPT_INCALL_HEADER);
            if (options.has(OPT_TIME_LIMITING_CONFIG))
                this.timeLimitingConfig = options.getJSONObject(OPT_TIME_LIMITING_CONFIG);
            if (options.has(OPT_SELF_CALLING))
                this.isSelfCalling = options.optBoolean(OPT_SELF_CALLING);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void fireEvent(final String eventName, final String jsonStr) {
        cordova.getActivity().runOnUiThread(() -> {
            if (Skyway.context != null) {
                PluginResult pluginresult = new PluginResult(PluginResult.Status.OK, jsonStr);
                pluginresult.setKeepCallback(false);
                Skyway.context.sendPluginResult(pluginresult);
            }
        });
//        cordova.getActivity().runOnUiThread(() -> {
//            if (isDebugMode)
//                Log.d(LOGTAG, "fireEvent... eventName: " + eventName + " -- jsonStr: " + jsonStr);
//            if (jsonStr != null && jsonStr.length() > 0) {
//                webView.loadUrl(String.format("javascript:cordova.fireDocumentEvent('%s', %s);", eventName, jsonStr));
//            } else {
//                webView.loadUrl(String.format("javascript:cordova.fireDocumentEvent('%s');", eventName));
//            }
//        });
    }

    private void fireEventJson(JSONObject jso) {
        Log.d(LOGTAG, "fireEventJson... ");
        Log.d(LOGTAG, "fireEventJson... " + jso);
        cordova.getActivity().runOnUiThread(() -> {
            if (Skyway.context != null) {
                PluginResult pluginresult = new PluginResult(PluginResult.Status.OK, jso);
                pluginresult.setKeepCallback(true);
                Skyway.context.sendPluginResult(pluginresult);
            }
        });
    }
}
