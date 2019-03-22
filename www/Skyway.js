var exec = require('cordova/exec');

exports.createPeer = function (options, successCallback, errorCallback) {
    
    exec(successCallback, errorCallback, 'Skyway', 'createPeer', [options]);
};
