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
    NSLog(@"createPeer");
    NSLog(@"startTrip");
    NSString* driverID = [command.arguments objectAtIndex:0];
    ViewController *vc = [[ViewController alloc] initWithNibName:@"ViewController" bundle:nil];
    [ROOTVIEW presentViewController:vc animated:YES completion:^{}];

    CDVPluginResult* pluginResult = nil;
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

@end
