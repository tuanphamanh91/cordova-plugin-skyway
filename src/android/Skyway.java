package cordova.plugin.skyway;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This class echoes a string called from JavaScript.
 */
public class Skyway extends CordovaPlugin {
    /**
     * Common tag used for logging statements.
     */
    private static final String LOGTAG = "Skyway";
    /**
     * Cordova Actions.
     */
    private static final String ACTION_CREATE_PEER = "createPeer";
    @Override
    public boolean execute(String action, JSONArray inputs, CallbackContext callbackContext) throws JSONException {
        PluginResult result = null;
        if (action.equals(ACTION_CREATE_PEER)) {
            JSONObject options = inputs.optJSONObject(0);
            result = executeSetOptions(options, callbackContext);
            return true;
        }
        return false;
    }

    private PluginResult executeSetOptions(JSONObject options, CallbackContext callbackContext) {
        callbackContext.success();
        return null;
    }
}
