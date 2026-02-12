package com.microlumin.xlink.spp.common;

public interface SppCallback {
    void onConnected(String deviceName, String deviceAddress);
    void onDisconnected();
    void onDataReceived(byte[] data);
    void onError(String message);
}