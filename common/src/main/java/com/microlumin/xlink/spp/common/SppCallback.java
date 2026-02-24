package com.microlumin.xlink.spp.common;

public interface SppCallback {
    void onStateChanged(SppState state, String deviceName, String deviceAddress);
    void onDataReceived(byte[] data);
    void onError(String message);
}