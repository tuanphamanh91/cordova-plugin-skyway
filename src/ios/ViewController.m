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
    
    NSTimer *_timer;
}

@end

@implementation ViewController


- (void)viewDidLoad {
    [super viewDidLoad];
    // Do any additional setup after loading the view, typically from a nib.
    SKWPeerOption *option = [[SKWPeerOption alloc] init];
    option.key = _apiKey;
    option.domain = _domain;
    _peer = [[SKWPeer alloc] initWithId:_myId options:option];
    
    [_peer on:SKW_PEER_EVENT_OPEN callback:^(NSObject * _Nullable arg) {
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
    }];
}

- (void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];
    [[UIApplication sharedApplication] setIdleTimerDisabled:YES];
}

- (void)viewDidDisappear:(BOOL)animated {
    [self closeAllConnection];
    [[UIApplication sharedApplication] setIdleTimerDisabled:NO];
    [super viewDidDisappear:animated];
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
                        [[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryPlayAndRecord withOptions:AVAudioSessionCategoryOptionDefaultToSpeaker error: nil];
                    }
                });
            }
        }
    }];
    [_mediaConnection on:SKW_MEDIACONNECTION_EVENT_CLOSE callback:^(NSObject* obj) {
        if (!self->_bConnected) {
            return;
        }
        [self closeRemoteStream];
        [self unsetMediaCallbacks];
        self->_mediaConnection = nil;
        [self->_signalingChannel close];
        self->_bConnected = NO;
        [self doHangup:NO];
    }];
    
}

- (void)closeAllConnection {
    [self closeRemoteStream];
    [_mediaConnection close];
    [self unsetMediaCallbacks];
    [_signalingChannel close];
    [self unsetDataConnectionCallbacks];
    [self unsetPeerCallbacks];
}

- (void)closeRemoteStream {
    if(_remoteStream) {
        if(partnerView) {
            [_remoteStream removeVideoRenderer:partnerView track:0];
        }
        [_remoteStream close];
        _remoteStream = nil;
    }
    if (_localStream) {
        if (localVideo) {
            [_localStream removeVideoRenderer:localVideo track:0];
        }
        [_localStream close];
        _localStream = nil;
    }
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
    [self closeRemoteStream];
    [_mediaConnection close];
    [self->_signalingChannel close];
    endCallTime = [[NSDate date] timeIntervalSince1970];
    if (self.successBlock) {
        self.successBlock(startCallTime, endCallTime, isSelfHangup);
    }
    self.successBlock = nil;
    [self dismissViewControllerAnimated:NO completion:nil];
    [_timer invalidate];
    _timer = nil;
}

- (void)dealloc {
    _localStream = nil;
    _mediaConnection = nil;
    _peer = nil;
    [_timer invalidate];
    _timer = nil;
}

@end
