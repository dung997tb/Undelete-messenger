package com.dung.notifycore.contract;

import com.dung.notifycore.contract.INotifyCallback;

// NotifyCore IPC contract — API v1.
// RULE: add-only. Never change or remove existing methods; append new ones.
interface INotifyCoreService {
    /** Contract API version implemented by the connected Core. */
    int getApiVersion();
    /** Total notifications captured since install. */
    long getCapturedCount();
    /** Subscribe to realtime notification events (phase 3). */
    void registerCallback(INotifyCallback callback);
    void unregisterCallback(INotifyCallback callback);
}
