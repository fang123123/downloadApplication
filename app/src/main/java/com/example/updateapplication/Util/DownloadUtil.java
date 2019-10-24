package com.example.updateapplication.Util;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.view.WindowManager;
import android.widget.Toast;

import com.example.updateapplication.Server.DownloadListner;
import com.example.updateapplication.Server.DownloadTask;

public class DownloadUtil {
    private String mDownloadUrl;
    private DownloadTask downloadTask;
    private ProgressDialog pd;
    private Context mContext;

    public DownloadUtil(Context context, String downloadUrl){
        mDownloadUrl = downloadUrl;
        mContext = context;
    }
    private DownloadListner listner = new DownloadListner() {
        @Override
        public void onProgress(int progress) {
            //getNotificationManager().notify(1,getNotification("Download...",progress));
            pd.setProgress(progress);
        }

        @Override
        public void onSuccess() {
            downloadTask = null;
            mContext.sendBroadcast(new Intent("com.example.updateapplication.installreceiver"));
        }

        @Override
        public void onFailed() {
            downloadTask = null;
            //下载失败时将前台服务通知关闭，并创建一个下载失败的通知
            Toast.makeText(mContext, "Download Failed", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onPaused() {
            downloadTask = null;
            Toast.makeText(mContext, "Paused", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCanceled() {
            downloadTask = null;
            Toast.makeText(mContext, "Canceled", Toast.LENGTH_SHORT).show();
        }
    };
    public static void ExecuteDownload(){
    }
    private void initProgressDialog(){
        pd = new ProgressDialog(mContext);
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setMax(100);
        pd.setTitle("正在下载");
        pd.setCancelable(false);
        pd.setProgress(0);
        pd.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        pd.show();
    }
}
