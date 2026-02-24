package com.microlumin.xlink.spp.common;

public interface SppCallback {
    void onStateChanged(SppState state, String deviceName, String deviceAddress);
    default void onDataReceived(byte[] data) {}
    default void onPacketReceived(byte[] payload) {}
    void onError(String message);
}