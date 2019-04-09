//
//  ViewController.h
//  VideoCall
//
//  Created by Tuan Pham Anh on 3/13/19.
//  Copyright © 2019 Tuan Pham Anh. All rights reserved.
//

#import <UIKit/UIKit.h>
#import <SkyWay/SKWPeer.h>
@interface ViewController : UIViewController

@property(nonatomic, strong) NSString *partnerId;
@property(nonatomic, strong) NSString *myId;
@property(nonatomic, strong) NSString *apiKey;
@property(nonatomic, strong) NSString *domain;
@property(nonatomic, strong) NSString *browserUrl;
@property(nonatomic) BOOL showLocalVideo;
@property(nonatomic) BOOL enableSpeaker;
@property(nonatomic) NSInteger intervalReconnect;
@property (nonatomic, copy) void (^successBlock)(NSUInteger start, NSUInteger end, BOOL isHangup);

@end

