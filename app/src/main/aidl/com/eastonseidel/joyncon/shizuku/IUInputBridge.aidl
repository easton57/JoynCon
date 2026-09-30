package com.eastonseidel.joyncon.shizuku;

import com.eastonseidel.joyncon.shizuku.IBridgeCallback;

interface IUInputBridge {
    void setCallback(IBridgeCallback callback);

    /** Scans /dev/input for both Joy-Cons without grabbing them; used by the remap screen's "detect raw codes" flow. */
    String scanRawDevices();

    /** Grabs whichever Joy-Con(s) are present, creates the merged /dev/uinput gamepad, and starts the read/merge loop. */
    boolean startMerge(String mappingJson);

    /** Pushes an updated mapping table to the running merge loop without restarting it. */
    void updateMapping(String mappingJson);

    /** Ungrabs all devices, destroys the virtual gamepad, stops the loop. Safe to call when not running. */
    void stopMerge();

    boolean isUinputWritable();
    boolean isEvdevReadable();

    void destroy();
}
