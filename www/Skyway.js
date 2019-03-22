var exec = require('cordova/exec');

exports.createPeer = function (options, successCallback, errorCallback) {
    exec(success, error, 'Skyway', 'createPeer', [options]);
};
