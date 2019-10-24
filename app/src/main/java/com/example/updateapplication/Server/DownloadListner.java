package com.example.updateapplication.Server;

public interface DownloadListner {
    void onProgress(int progress);
    void onSuccess();
    void onFailed();
    void onPaused();
    void onCanceled();
}
