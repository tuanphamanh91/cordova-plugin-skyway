/********* Skyway.m Cordova Plugin Implementation *******/

#import <Cordova/CDV.h>
#import "ViewController.h"

#define ROOTVIEW [[[UIApplication sharedApplication] keyWindow] rootViewController]

@interface Skyway : CDVPlugin {
  // Member variables go here.
}

- (void)coolMethod:(CDVInvokedUrlCommand*)command;
@end

@implementation Skyway

- (void)coolMethod:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = nil;
    NSString* echo = [command.arguments objectAtIndex:0];

    if (echo != nil && [echo length] > 0) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:echo];
    } else {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR];
    }

    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)createPeer:(CDVInvokedUrlCommand*)command 
{
    NSLog(@"startTrip");
    self.callbackId = command.callbackId;

    NSString *myId = [command.arguments objectAtIndex:0];
    NSString  *partnerId = [command.arguments objectAtIndex:1];
    
    ViewController *vc = [[ViewController alloc] initWithNibName:@"ViewController" bundle:nil];
    vc.myId = myId;
    vc.partnerId = partnerId;
    [vc setSuccesBlock:^(NSUInteger start, NSUInteger end) {
        NSLog(@"block call: ", start, end);
        NSDictionary *timeDict = @[@"startTime": start, @"endTime": end];
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:timeDict];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        }];

    [ROOTVIEW presentViewController:vc animated:YES completion:^{}];

}

@end
