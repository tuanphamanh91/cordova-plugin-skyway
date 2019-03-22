//
//  ViewController.m
//  VideoCall
//
//  Created by Tuan Pham Anh on 3/13/19.
//  Copyright © 2019 Tuan Pham Anh. All rights reserved.
//

#import "ViewController.h"
static NSString *const kAPIkey = @"e1c8e6ac-435c-4a5c-aad6-1a192b07d8b4";
static NSString *const kDomain = @"money-reco.com";

@interface ViewController () {
    SKWPeer *_peer;
    SKWMediaStream *_localStream;
    SKWMediaStream *_remoteStream;
    SKWMediaConnection *_mediaConnection;
    BOOL _bConnected;
    NSUInteger startCallTime;
    NSUInteger endCallTime;

    __weak IBOutlet UIButton *switchCameraButton;
    __weak IBOutlet UIView *containButtonView;
    __weak IBOutlet UIButton *muteButton;
    __weak IBOutlet UIButton *cameraOffButton;
    __weak IBOutlet UIButton *hangoutButton;
    __weak IBOutlet SKWVideo *partnerView;
    
    NSTimer *_timer;
}

@end

@implementation ViewController


- (void)viewDidLoad {
    [super viewDidLoad];
    // Do any additional setup after loading the view, typically from a nib.
    // hash code
    if (!_partnerId) {
        _partnerId = @"12345";
        _myId = @"4444";
    }
    SKWPeerOption *option = [[SKWPeerOption alloc] init];
    option.key = kAPIkey;
    option.domain = kDomain;
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
//        [self->_localStream addVideoRenderer:self->partnerView track:0];
    }];
    
    [_peer on:SKW_PEER_EVENT_CALL callback:^(NSObject * _Nullable obj) {
        if ([obj isKindOfClass:[SKWMediaConnection class]]) {
            self->_mediaConnection = (SKWMediaConnection *)obj;
            [self setMediaCallbacks];
            [self->_mediaConnection answer:self->_localStream];
        }
    }];

    [_peer on:SKW_PEER_EVENT_CLOSE callback:^(NSObject* obj) {}];
    [_peer on:SKW_PEER_EVENT_DISCONNECTED callback:^(NSObject* obj) {}];
    [_peer on:SKW_PEER_EVENT_ERROR callback:^(NSObject* obj) {}];
    
    [self performSelector:@selector(makeVideoCall) withObject:nil afterDelay:3.0];
    
    _timer = [NSTimer scheduledTimerWithTimeInterval:10 target:self selector:@selector(makeVideoCall) userInfo:NULL repeats:YES];
}

- (void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];
    [[UIApplication sharedApplication] setIdleTimerDisabled:YES];
}

- (void)viewDidDisappear:(BOOL)animated {
    [[UIApplication sharedApplication] setIdleTimerDisabled:NO];
    [super viewDidDisappear:animated];
}


- (void)makeVideoCall {
    if (!_bConnected) {
        NSLog(@"calling...");
        _mediaConnection = [_peer callWithId:_partnerId stream:_localStream];
        [self setMediaCallbacks];
    } else if (_timer) {
        NSLog(@"end timer...");
        [_timer invalidate];
        _timer = nil;
    }
    
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
        
        self->_bConnected = NO;
        
    }];
    
}

- (void)closeRemoteStream {
    if(_remoteStream) {
        if(partnerView) {
            [_remoteStream removeVideoRenderer:partnerView track:0];
        }
        [_remoteStream close];
        _remoteStream = nil;
    }
}

- (void)unsetMediaCallbacks {
    if(_mediaConnection) {
        [_mediaConnection on:SKW_MEDIACONNECTION_EVENT_STREAM callback:nil];
        [_mediaConnection on:SKW_MEDIACONNECTION_EVENT_CLOSE callback:nil];
        [_mediaConnection on:SKW_MEDIACONNECTION_EVENT_ERROR callback:nil];
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
    [self closeRemoteStream];
    [_mediaConnection close];
    endCallTime = [[NSDate date] timeIntervalSince1970];
    NSLog(startCallTime);
    NSLog(endCallTime);
    if (self.succesBlock) {
        self.succesBlock(10, 20);
    }
    self.succesBlock = nil;
    [self dismissViewControllerAnimated:NO completion:nil];

}

- (void)dealloc {
    _localStream = nil;
    _mediaConnection = nil;
    _peer = nil;
}

@end
