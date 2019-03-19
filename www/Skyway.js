var exec = require('cordova/exec');

exports.createPeer = function (arg0, success, error) {
    exec(success, error, 'Skyway', 'createPeer', [arg0]);
};
