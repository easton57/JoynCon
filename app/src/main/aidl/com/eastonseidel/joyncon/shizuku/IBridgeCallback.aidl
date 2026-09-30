package com.eastonseidel.joyncon.shizuku;

oneway interface IBridgeCallback {
    void onDeviceGrabbed(String role, String deviceName, String rawCodesJson);
    void onDeviceReleased(String role);
    void onMergeStateChanged(boolean uinputActive, String detail);
    void onError(String message);
}
