package com.dung.notifycore.contract;

// Realtime callback for satellite apps. API v1.
interface INotifyCallback {
    void onNotificationPosted(String packageName, String title, String text, long postTime);
}
