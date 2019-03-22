var exec = require('cordova/exec');

exports.createPeer = function (myId, partnerId, successCallback, errorCallback) {
    exec(success, error, 'Skyway', 'createPeer', [myId, partnerId]);
};
