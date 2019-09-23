//
//  ViewController.m
//  VideoCall
//
//  Created by Tuan Pham Anh on 3/13/19.
//  Copyright © 2019 Tuan Pham Anh. All rights reserved.
//

#import "ViewController.h"
#import <AudioToolbox/AudioToolbox.h>
#import <AVFoundation/AVFoundation.h>

@interface ViewController () {
    SKWPeer *_peer;
    SKWMediaStream *_localStream;
    SKWMediaStream *_remoteStream;
    SKWMediaConnection *_mediaConnection;
    SKWDataConnection *_signalingChannel;
    BOOL _bConnected;
    NSUInteger startCallTime;
    NSUInteger endCallTime;

    __weak IBOutlet UIButton *switchCameraButton;
    __weak IBOutlet UIButton *openBrowserButton;
    __weak IBOutlet UIView *containButtonView;
    __weak IBOutlet UIButton *muteButton;
    __weak IBOutlet UIButton *cameraOffButton;
    __weak IBOutlet UIButton *hangoutButton;
    __weak IBOutlet SKWVideo *partnerView;
    __weak IBOutlet SKWVideo *localVideo;
    __weak IBOutlet UIView *viewTimeLimitting;
    __weak IBOutlet UILabel *lblTimeLimittingCaption;
    __weak IBOutlet UILabel *lblTimeLimittingValue;
    
    NSTimer *_timer;
    NSTimer *_timerLimitting;
}

@end

@implementation ViewController


- (void)viewDidLoad {
    [super viewDidLoad];

    self->partnerView.scaling = SKW_VIDEO_SCALING_ASPECT_FIT;
    
    //setup view
    viewTimeLimitting.layer.cornerRadius = 5;
    viewTimeLimitting.layer.masksToBounds = true;
    if (!_browserUrl || _browserUrl.length == 0) {
        openBrowserButton.hidden = YES;
    }
    // Do any additional setup after loading the view, typically from a nib.
    SKWPeerOption *option = [[SKWPeerOption alloc] init];
    option.key = _apiKey;
    option.domain = _domain;
    _peer = [[SKWPeer alloc] initWithId:_myId options:option];

    [_peer on:SKW_PEER_EVENT_OPEN callback:^(NSObject * _Nullable arg) {
        [self checkPermissionVideoCall];
    }];

    [_peer on:SKW_PEER_EVENT_CALL callback:^(NSObject * _Nullable obj) {
        if ([obj isKindOfClass:[SKWMediaConnection class]]) {
            self->_mediaConnection = (SKWMediaConnection *)obj;
            [self setMediaCallbacks];
            [self->_mediaConnection answer:self->_localStream];
        }
    }];

    // CONNECT (Custom Signaling Channel for a call)
    [_peer on:SKW_PEER_EVENT_CONNECTION callback:^(NSObject* obj) {
        if (YES == [obj isKindOfClass:[SKWDataConnection class]]) {
            _signalingChannel = (SKWDataConnection *)obj;
            [self setSignalingCallbacks];
        }
    }];

    [_peer on:SKW_PEER_EVENT_CLOSE callback:^(NSObject* obj) {}];
    [_peer on:SKW_PEER_EVENT_DISCONNECTED callback:^(NSObject* obj) {}];
    [_peer on:SKW_PEER_EVENT_ERROR callback:^(NSObject* obj) {
        NSLog(@"[SKW_PEER_EVENT_ERROR] -- %@", obj);
         SKWPeerError* err = (SKWPeerError *)obj;
        if (err.type == SKW_PEER_ERR_UNAVAILABLE_ID) {
            NSString *message = self.errorMessageWhenPeerIdUnavailable ? self.errorMessageWhenPeerIdUnavailable : [NSString stringWithFormat:@"%@[type=%@]", err.message, err.typeString];
            [self showAlertError:message];
        }
    }];
    
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(routeChange:)
                                                 name:AVAudioSessionRouteChangeNotification
                                               object:nil];
}

- (void)startPeerCall {
    // Set MediaConstraints
    SKWMediaConstraints* constraints = [[SKWMediaConstraints alloc] init];
    constraints.maxWidth = 960;
    constraints.maxHeight = 540;
    constraints.cameraPosition = SKW_CAMERA_POSITION_FRONT;
    
    // Get a local MediaStream & show it
    [SKWNavigator initialize:self->_peer];
    self->_localStream = [SKWNavigator getUserMedia:constraints];
    [self->_localStream addVideoRenderer:self->localVideo track:0];
    //        [self->_localStream addVideoRenderer:self->partnerView track:0];
    [self performSelector:@selector(makeVideoCall) withObject:nil];
    
    _timer = [NSTimer scheduledTimerWithTimeInterval:_intervalReconnect target:self selector:@selector(makeVideoCall) userInfo:NULL repeats:YES];
}

- (void)checkPermissionVideoCall {
    [self checkPermission:AVMediaTypeVideo successBlock:^{
        [self checkPermission:AVMediaTypeAudio successBlock:^{
//            [self startPeerCall];
            dispatch_async(dispatch_get_main_queue(), ^{
                [self startPeerCall];
            });
        }];
    }];
}

- (void)checkPermission:(NSString*)mediaType successBlock:(void (^)(void))success {
    AVAuthorizationStatus authStatus = [AVCaptureDevice authorizationStatusForMediaType:mediaType];
    if(authStatus == AVAuthorizationStatusAuthorized) {
        success();
    } else if(authStatus == AVAuthorizationStatusDenied){
        // denied -> alert error
        UIAlertController *_alertController = [UIAlertController alertControllerWithTitle:NSLocalizedString(@"title_dialog_request_camera_permission", comment: @"") message:NSLocalizedString(@"msg_dialog_request_camera_permission", comment: @"") preferredStyle:UIAlertControllerStyleAlert];
        
        UIAlertAction *settingAction = [UIAlertAction actionWithTitle:NSLocalizedString(@"btn_go_setting", comment: @"") style:UIAlertActionStyleDefault handler:^(UIAlertAction * _Nonnull action) {
            UIApplication *application = [UIApplication sharedApplication];
            NSURL *URL = [NSURL URLWithString:UIApplicationOpenSettingsURLString];
            if ([application respondsToSelector:@selector(openURL:options:completionHandler:)]) {
                [application openURL:URL options:@{}
                   completionHandler:^(BOOL success) {
                   }];
            } else {
                [application openURL:URL];
            }
//            [[UIApplication sharedApplication] openURL:[NSURL URLWithString:UIApplicationOpenSettingsURLString]];
            //go setting
        }];
        UIAlertAction *cancelAction = [UIAlertAction actionWithTitle:NSLocalizedString(@"btn_cancel", comment: @"") style:UIAlertActionStyleDefault handler:^(UIAlertAction * _Nonnull action) {
            //go setting
        }];
        [_alertController addAction:settingAction];
        [_alertController addAction:cancelAction];
        [self presentViewController:_alertController animated:YES completion:nil];
    } else if(authStatus == AVAuthorizationStatusRestricted){
        // restricted, normally won't happen
    } else if(authStatus == AVAuthorizationStatusNotDetermined){
        // not determined?!
        [AVCaptureDevice requestAccessForMediaType:mediaType completionHandler:^(BOOL granted) {
            if(granted){
                NSLog(@"Granted access to %@", mediaType);
                success();
            } else {
                NSLog(@"Not granted access to %@", mediaType);
            }
        }];
    } else {
        // impossible, unknown authorization status
    }
}

- (void)showAlertError:(NSString*)error {
    UIAlertController *_alertController = [UIAlertController alertControllerWithTitle:@"Error" message:error preferredStyle:UIAlertControllerStyleAlert];
    
    UIAlertAction *ok = [UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleDefault handler:^(UIAlertAction * _Nonnull action) {
        [self doResetPeer];
    }];
    [_alertController addAction:ok];
    [self presentViewController:_alertController animated:YES completion:nil];
}

- (void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];
    [[UIApplication sharedApplication] setIdleTimerDisabled:YES];
}

- (void)viewWillDisappear:(BOOL)animated {
    [self clearTimer];
    [self closeAllConnection];
    [super viewWillDisappear:animated];
}
- (void)viewDidDisappear:(BOOL)animated {
    [[UIApplication sharedApplication] setIdleTimerDisabled:NO];
    [super viewDidDisappear:animated];
}

- (void)startTimeLimitingIfNeed {
    if (self.timeLimitConfig == nil) return;
    @try {
        NSUInteger maxCallDurationInSeconds = [[self.timeLimitConfig objectForKey:@"maxCallDurationInSeconds"] integerValue];//in seconds
        NSUInteger timeBeforeShowWaringInSeconds = [[self.timeLimitConfig objectForKey:@"timeBeforeShowWaringInSeconds"] integerValue];//in seconds
        NSUInteger delaySeconds = (maxCallDurationInSeconds - timeBeforeShowWaringInSeconds);
        delaySeconds = delaySeconds > 0 ? delaySeconds : 0;
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, delaySeconds * NSEC_PER_SEC), dispatch_get_main_queue(), ^{
            [self performStartTimeLimitting];
        });
    } @catch (NSException *e) {
        NSLog(@"%@", e);
    }
}

- (void)performStartTimeLimitting {
    @try {
        NSLog(@"perfomrStartTimeLimitting...");
        NSString *textRemaining = [self.timeLimitConfig objectForKey:@"textRemaining"];
        NSString *backgroundColor = [self.timeLimitConfig objectForKey:@"backgroundColorHex"];
        viewTimeLimitting.hidden = FALSE;
        @try {
            unsigned rgbValue = 0;
            NSScanner *scanner = [NSScanner scannerWithString:backgroundColor];
            [scanner setScanLocation:1]; // bypass '#' character
            [scanner scanHexInt:&rgbValue];
            UIColor *bgColor = [UIColor colorWithRed:((rgbValue & 0xFF0000) >> 16)/255.0 green:((rgbValue & 0xFF00) >> 8)/255.0 blue:(rgbValue & 0xFF)/255.0 alpha:1.0];
            viewTimeLimitting.backgroundColor = bgColor;
        } @catch(NSException *e) {
            
        }
        lblTimeLimittingCaption.text = textRemaining;
        
        [self updateTimeLimittingValue];
        //each 1 second
        _timerLimitting = [NSTimer scheduledTimerWithTimeInterval:1 target:self selector:@selector(updateTimeLimittingValue) userInfo:NULL repeats:YES];
    } @catch(NSException *ex) {
        NSLog(@"%@", ex);
    }
}

- (void)updateTimeLimittingValue {
    @try {
        NSLog(@"updateTimeLimittingValue...");
        NSUInteger maxCallDurationInSeconds = [[self.timeLimitConfig objectForKey:@"maxCallDurationInSeconds"] integerValue];//in seconds
        NSUInteger now = [[NSDate date] timeIntervalSince1970];
        NSUInteger timeRemainingSeconds = maxCallDurationInSeconds + startCallTime - now;
        if (timeRemainingSeconds <= 0) {
            //force hangup
            [self doHangup:self.isSelfCalling];
        } else {
            NSString *textFormat = [self.timeLimitConfig objectForKey:@"textFormat"];
            if (!textFormat) textFormat = @"%d Seconds";
            
            lblTimeLimittingValue.text = [NSString stringWithFormat:textFormat, timeRemainingSeconds];
        }
    } @catch(NSException *ex) {
        NSLog(@"%@", ex);
    }
}

- (void)makeVideoCall {
    if (!_bConnected) {
        NSLog(@"calling...");
        _mediaConnection = [_peer callWithId:_partnerId stream:_localStream];
        [self setMediaCallbacks];
        // custom P2P signaling channel to reject call attempt
        _signalingChannel = [_peer connectWithId:_partnerId];
        [self setSignalingCallbacks];
    } else if (_timer) {
        NSLog(@"end timer...");
        [_timer invalidate];
        _timer = nil;
    }
}

- (void)showWarningTimeLimiting {
    
}

//
// Set callbacks for SKW_DATACONNECTION_EVENTs
//
- (void)setSignalingCallbacks {
    if (nil == _signalingChannel) {
        return;
    }
    
    [_signalingChannel on:SKW_DATACONNECTION_EVENT_OPEN callback:^(NSObject* obj) { }];
    [_signalingChannel on:SKW_DATACONNECTION_EVENT_CLOSE callback:^(NSObject* obj) { }];
    [_signalingChannel on:SKW_DATACONNECTION_EVENT_ERROR callback:^(NSObject* obj) {
        SKWPeerError* err = (SKWPeerError *)obj;
        NSLog(@"[SKW_DATACONNECTION_EVENT_ERROR] -- %@", err);
    }];
    [_signalingChannel on:SKW_DATACONNECTION_EVENT_DATA callback:^(NSObject* obj) {
        NSString *message = (NSString *)obj;
        NSLog(@"[On/Data] %@", message);
        if ([message isEqualToString:@"disconnected"]) {
            [self doHangup:NO];
        }
    }];
}

- (void)setMediaCallbacks {
    if (_mediaConnection == nil) {
        return;
    }
    [_mediaConnection on:SKW_MEDIACONNECTION_EVENT_STREAM callback:^(NSObject * _Nullable obj) {
        if ([obj isKindOfClass:[SKWMediaStream class]]) {
            if (!self->_bConnected) {
                self->_bConnected = YES;
                startCallTime = [[NSDate date] timeIntervalSince1970];
                self->_remoteStream = (SKWMediaStream *)obj;
                dispatch_async(dispatch_get_main_queue(), ^{
                    [self->_remoteStream addVideoRenderer:self->partnerView track:0];
                    if (_enableSpeaker) {
                        BOOL isCurrentHeadphone = [self isCurrentHeadphone];
                        BOOL isCurrentBluetooth = [self isCurrentBluetooth];
                        NSLog(@"isCurrentHeadphone = %d, isCurrentBluetooth=%d", isCurrentHeadphone, isCurrentBluetooth);
                        if (isCurrentHeadphone) {
                            [[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryPlayAndRecord withOptions:AVAudioSessionCategoryOptionMixWithOthers error: nil];
                        } else if (isCurrentBluetooth) {
                           [[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryPlayAndRecord withOptions:AVAudioSessionCategoryOptionAllowBluetooth|AVAudioSessionCategoryOptionAllowBluetoothA2DP error: nil];
                        } else {
                            [[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryPlayAndRecord withOptions:AVAudioSessionCategoryOptionDefaultToSpeaker error: nil];
                        }
                    }
                });
                //call api in here
                [self invokeInCallApi];
                //start check show limitting time
                [self startTimeLimitingIfNeed];
            }
        }
    }];
    [_mediaConnection on:SKW_MEDIACONNECTION_EVENT_CLOSE callback:^(NSObject* obj) {
        if (!self->_bConnected) {
            return;
        }
        self->_bConnected = NO;
        [self doHangup:NO];
    }];
    
}

- (void)closeAllConnection {
    [self closeVideoStream];
    [self closeMediaConnection];
    [self closeDataConnection];
    [self destroyPeer];
}

- (void)closeMediaConnection {
    if (nil != _mediaConnection) {
        [_mediaConnection close];
        [self unsetMediaCallbacks];
        _mediaConnection = nil;
    }
}
- (void)closeDataConnection {
    if (nil != _signalingChannel) {
        [_signalingChannel close];
        [self unsetDataConnectionCallbacks];
        _signalingChannel = nil;
    }
}
- (void)destroyPeer {
    @try {
        [self unsetPeerCallbacks];
//        [_peer destroy];
        _peer = nil;
    } @catch(NSException * e) {
        NSLog(@"Exception: %@", e);
    }
}
- (void)closeVideoStream {
    if(_remoteStream) {
        if(partnerView) {
            [_remoteStream removeVideoRenderer:partnerView track:0];
        }
        [_remoteStream close];
        _remoteStream = nil;
    }
//    if (_localStream) {
//        if (localVideo) {
//            [_localStream removeVideoRenderer:localVideo track:0];
//        }
//        [_localStream close];
//        _localStream = nil;
//    }
}

//
// Unset callbacks for PEER_EVENTs
//
- (void)unsetPeerCallbacks {
    if (nil == _peer) {
        return;
    }
    
    [_peer on:SKW_PEER_EVENT_OPEN callback:nil];
    [_peer on:SKW_PEER_EVENT_CONNECTION callback:nil];
    [_peer on:SKW_PEER_EVENT_CALL callback:nil];
    [_peer on:SKW_PEER_EVENT_CLOSE callback:nil];
    [_peer on:SKW_PEER_EVENT_DISCONNECTED callback:nil];
    [_peer on:SKW_PEER_EVENT_ERROR callback:nil];
}

- (void)unsetMediaCallbacks {
    if(_mediaConnection) {
        [_mediaConnection on:SKW_MEDIACONNECTION_EVENT_STREAM callback:nil];
        [_mediaConnection on:SKW_MEDIACONNECTION_EVENT_CLOSE callback:nil];
        [_mediaConnection on:SKW_MEDIACONNECTION_EVENT_ERROR callback:nil];
    }
}

- (void)unsetDataConnectionCallbacks {
    if (_signalingChannel) {
        [_signalingChannel on:SKW_DATACONNECTION_EVENT_OPEN callback:nil];
        [_signalingChannel on:SKW_DATACONNECTION_EVENT_CLOSE callback:nil];
        [_signalingChannel on:SKW_DATACONNECTION_EVENT_ERROR callback:nil];
        [_signalingChannel on:SKW_DATACONNECTION_EVENT_DATA callback:nil];
    }
}

- (void)invokeInCallApi {
    if (!self.inCallUrl) return;
    dispatch_async(dispatch_get_global_queue( DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^(void){
        //Background Thread
        NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:[NSURL URLWithString: self.inCallUrl]];
        if (self.inCallHeader && self.inCallHeader.count > 0) {
            NSArray *keys = self.inCallHeader.allKeys;
            for (NSString* key in keys) {
                NSString *value = [self.inCallHeader objectForKey:key];
                [request addValue:key forHTTPHeaderField:value];
            }
        }
        NSURLSessionConfiguration *sessionConfiguration = [NSURLSessionConfiguration defaultSessionConfiguration];
        if (self.inCallHeader) {
            sessionConfiguration.HTTPAdditionalHeaders = [self.inCallHeader copy];
        }
        
        // Disables cacheing
        sessionConfiguration.requestCachePolicy = NSURLRequestReloadIgnoringLocalCacheData;
        NSURLSession *session = [NSURLSession sessionWithConfiguration:sessionConfiguration delegate:nil delegateQueue:nil];

        NSURLSessionDataTask * dataTask = [session dataTaskWithRequest:request completionHandler:^(NSData *data, NSURLResponse *response, NSError *error) {
            // Process the response
            NSString *responseString = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
            NSLog(@"%@",responseString);
        }];
        // Fire the data task.
        [dataTask resume];
    });
}

#pragma mark - AudioRouteChange
- (void)routeChange:(NSNotification*)notification {
    
    NSDictionary *interuptionDict = notification.userInfo;
    
    NSInteger routeChangeReason = [[interuptionDict valueForKey:AVAudioSessionRouteChangeReasonKey] integerValue];
    BOOL willCheck = FALSE;
    switch (routeChangeReason) {
        case AVAudioSessionRouteChangeReasonUnknown:
            NSLog(@"routeChangeReason : AVAudioSessionRouteChangeReasonUnknown");
            break;
            
        case AVAudioSessionRouteChangeReasonNewDeviceAvailable:
            // a headset was added or removed
            NSLog(@"routeChangeReason : AVAudioSessionRouteChangeReasonNewDeviceAvailable");
            willCheck = TRUE;
            break;
            
        case AVAudioSessionRouteChangeReasonOldDeviceUnavailable:
            // a headset was added or removed
            NSLog(@"routeChangeReason : AVAudioSessionRouteChangeReasonOldDeviceUnavailable");
            willCheck = TRUE;
            break;
            
        case AVAudioSessionRouteChangeReasonCategoryChange:
            // called at start - also when other audio wants to play
            NSLog(@"routeChangeReason : AVAudioSessionRouteChangeReasonCategoryChange");//AVAudioSessionRouteChangeReasonCategoryChange
            break;
            
        case AVAudioSessionRouteChangeReasonOverride:
            NSLog(@"routeChangeReason : AVAudioSessionRouteChangeReasonOverride");
            break;
            
        case AVAudioSessionRouteChangeReasonWakeFromSleep:
            NSLog(@"routeChangeReason : AVAudioSessionRouteChangeReasonWakeFromSleep");
            break;
            
        case AVAudioSessionRouteChangeReasonNoSuitableRouteForCategory:
            NSLog(@"routeChangeReason : AVAudioSessionRouteChangeReasonNoSuitableRouteForCategory");
            break;
            
        default:
            break;
    }
    
    if (willCheck) {
        AVAudioSessionRouteDescription *prevOutputs = interuptionDict[AVAudioSessionRouteChangePreviousRouteKey];
        BOOL isCurrentHeadphone = [self isCurrentHeadphone];
        BOOL isCurrentBluetooth = [self isCurrentBluetooth];
        BOOL isPrevHeadphone = [self headphonePlugedIn:prevOutputs.outputs];
        NSLog(@"isPrevHeadphone = %d, isCurrentHeadphone=%d, isCurrentBluetooth = %d", isPrevHeadphone, isCurrentHeadphone, isCurrentBluetooth);
        if (isCurrentHeadphone) {
            [[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryPlayAndRecord withOptions:AVAudioSessionCategoryOptionMixWithOthers error: nil];
        } else if(isCurrentBluetooth)  {
            [[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryPlayAndRecord withOptions:AVAudioSessionCategoryOptionAllowBluetooth|AVAudioSessionCategoryOptionAllowBluetoothA2DP error: nil];
        } else {
            [[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryPlayAndRecord withOptions:AVAudioSessionCategoryOptionDefaultToSpeaker error: nil];
        }
    }
}

- (BOOL) isCurrentHeadphone {
    NSArray<AVAudioSessionPortDescription *> *outputs = [AVAudioSession sharedInstance].currentRoute.outputs;
    return [self headphonePlugedIn:outputs];
}
- (BOOL) isCurrentBluetooth {
    NSArray<AVAudioSessionPortDescription *> *outputs = [AVAudioSession sharedInstance].currentRoute.outputs;
    return [self isBluetoothPlugedIn:outputs];
}

- (BOOL) headphonePlugedIn:(NSArray<AVAudioSessionPortDescription *> *)outputs  {
    for (int i=0; i<outputs.count; i++) {
        AVAudioSessionPortDescription *output = [outputs objectAtIndex:i];
        if ([output.portType isEqualToString:AVAudioSessionPortHeadphones]) {
            //Head phone plug in
            return TRUE;
        }
    }
    return FALSE;
}

- (BOOL)isBluetoothPlugedIn:(NSArray<AVAudioSessionPortDescription *> *)outputs  {
    for (int i=0; i<outputs.count; i++) {
        AVAudioSessionPortDescription *output = [outputs objectAtIndex:i];
        NSLog(@"isBluetoothPlugedIn... %@", output);
        if ([output.portType isEqualToString:AVAudioSessionPortBluetoothA2DP]
            || [output.portType isEqualToString:AVAudioSessionPortBluetoothHFP]) {
            //Bluetooth phone plug in
            return TRUE;
        }
    }
    return FALSE;
}

#pragma mark - Action
- (IBAction)switchCameraButtonPressed:(id)sender {
    if (_localStream) {
        SKWCameraPositionEnum pos = [_localStream getCameraPosition];
        if (pos == SKW_CAMERA_POSITION_FRONT) {
            pos = SKW_CAMERA_POSITION_BACK;
        } else if (pos == SKW_CAMERA_POSITION_BACK) {
            pos = SKW_CAMERA_POSITION_FRONT;
        }
        [_localStream setCameraPosition:pos];
    }
}
- (IBAction)openBrowserButtonPressed:(id)sender {
    //force hangup
    [self doHangup:self.isSelfCalling];
    [[UIApplication sharedApplication] openURL:[NSURL URLWithString:_browserUrl] options:@{} completionHandler:nil];
}
- (IBAction)muteButtonPressed:(id)sender {
    BOOL isEnable = [_localStream getEnableAudioTrack:0];
    [_localStream setEnableAudioTrack:0 enable:!isEnable];
    if (isEnable) {
        [muteButton setBackgroundImage:[UIImage imageNamed:@"mute_active.png"] forState:UIControlStateNormal];
    } else {
        [muteButton setBackgroundImage:[UIImage imageNamed:@"mute.png"] forState:UIControlStateNormal];
    }
}

- (IBAction)cameraOffButonPressed:(id)sender {
    BOOL isEnable = [_localStream getEnableVideoTrack:0];
    [_localStream setEnableVideoTrack:0 enable:!isEnable];
    if (isEnable) {
        [cameraOffButton setBackgroundImage:[UIImage imageNamed:@"offvideo_active.png"] forState:UIControlStateNormal];
    } else {
        [cameraOffButton setBackgroundImage:[UIImage imageNamed:@"video.png"] forState:UIControlStateNormal];
    }

}
- (IBAction)hangoutButtonPressed:(id)sender {
    if (_signalingChannel && _signalingChannel.isOpen) {
        [_signalingChannel send:@"disconnected"];
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 0.2 * NSEC_PER_SEC), dispatch_get_main_queue(), ^{
            [self doHangup:YES];
        });
    } else {
        [self doHangup:YES];
    }
}

- (void)doHangup:(BOOL)isSelfHangup {
    NSLog(@"doHangup....%s", isSelfHangup ? "true" : "false");
    endCallTime = [[NSDate date] timeIntervalSince1970];
    if (self.successBlock) {
        self.successBlock(startCallTime, endCallTime, isSelfHangup);
    }
    self.successBlock = nil;
    [self clearTimer];
    [self closeAllConnection];
    [self dismissViewControllerAnimated:NO completion:nil];
}

- (void)doResetPeer {
    if (self.resetBlock) {
        self.resetBlock();
    }
    self.resetBlock = nil;
    [self clearTimer];
    [self closeAllConnection];
    [self dismissViewControllerAnimated:NO completion:nil];
}
- (void)clearTimer {
    if (_timer) {
        [_timer invalidate];
        _timer = nil;
    }
    if (_timerLimitting) {
        [_timerLimitting invalidate];
        _timerLimitting = nil;
    }
}
- (void)dealloc {
    if (_localStream) _localStream = nil;
    if (_mediaConnection) _mediaConnection = nil;
    if (_peer) _peer = nil;
    [self clearTimer];
}

@end
