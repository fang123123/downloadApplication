package com.example.updateapplication.Server;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.example.updateapplication.MyApplication;
import com.example.updateapplication.R;

import java.io.File;

public class DownloadService extends Service {
    private DownloadTask downloadTask;
    private String downloadUrl;
    private static final String TAG = "DownloadService";

    //在匿名类中实现5个方法
    /*
     * 成功、失败、暂停、取消都会将下载任务清空
     * */
    private DownloadListner listner = new DownloadListner() {
        @Override
        public void onProgress(int progress) {
            getNotificationManager().notify(1,getNotification("Download...",progress));
        }

        @Override
        public void onSuccess() {
            downloadTask = null;
            //下载成功时将前台服务通知关闭，并创建一个下载成功的通知
            stopForeground(true);
            getNotificationManager().notify(1,getNotification("Download Success",-1));
//            Toast.makeText(DownloadService.this, "Download Success", Toast.LENGTH_SHORT).show();
            sendBroadcast(new Intent("com.example.updateapplication.installreceiver"));
        }

        @Override
        public void onFailed() {
            downloadTask = null;
            //下载失败时将前台服务通知关闭，并创建一个下载失败的通知
            stopForeground(true);
            getNotificationManager().notify(1,getNotification("Download Failed",-1));
            Toast.makeText(DownloadService.this, "Download Failed", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onPaused() {
            downloadTask = null;
            Toast.makeText(DownloadService.this, "Paused", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCanceled() {
            downloadTask = null;
            stopForeground(true);
            Toast.makeText(DownloadService.this, "Canceled", Toast.LENGTH_SHORT).show();
        }
    };

    private DownloadBinder mBinder = new DownloadBinder();
    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return mBinder;
    }
    //服务于活动通信接口
    public class DownloadBinder extends Binder {
        public void startDownload(String url){
            Log.d(TAG, "服务接到前台的下载命令");
            if(downloadTask == null){
                downloadUrl = url;
                downloadTask = new DownloadTask(listner);
                //开始执行下载任务，downUrl为传入子线程的数据
                downloadTask.execute(downloadUrl);
                //将该服务变成一个前台服务
                startForeground(1,getNotification("Downloading...",0));
                Toast.makeText(DownloadService.this, "Downloading...", Toast.LENGTH_SHORT).show();
            }
        }
        public void pauseDownload(){
            Log.d(TAG, "服务接到前台的暂停命令");
            if(downloadTask != null){
                downloadTask.pauseDownload();
            }
        }
        /*
         * 在先点击暂停的状态下，下载任务处于清空状态，此时直接进图第二个判断条件，文件就会被删除*/
        public void cancelDownload(){
            Log.d(TAG, "服务接到前台的取消命令");
            if(downloadTask!=null){
                downloadTask.cancelDownload();
            }
            if(downloadUrl != null){
                String fileName = downloadUrl.substring(downloadUrl.lastIndexOf("/"));
                String directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
                File file = new File(directory+fileName);
                if(file.exists()){
                    file.delete();
                }
                getNotificationManager().cancel(1);
                stopForeground(true);
                Toast.makeText(DownloadService.this, "Canceled", Toast.LENGTH_SHORT).show();
            }
        }
    }
    private NotificationManager getNotificationManager(){
        return (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    }
    private Notification getNotification(String title,int progress){
        Notification notification;
        Intent intent = new Intent(DownloadService.this, MyApplication.getContext().getClass());
        PendingIntent pi = PendingIntent.getActivity(DownloadService.this,0,intent,0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = getNotificationManager();
            //当sdk版本大于26
            String NotificationChannelId = "channel_1";
            String description = "143";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(NotificationChannelId, description, importance);
//                     channel.enableLights(true);
//                     channel.enableVibration(true);
            notificationManager.createNotificationChannel(channel);
            Notification.Builder builder = new Notification.Builder(this,NotificationChannelId)
                    .setContentTitle(title)
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setLargeIcon(BitmapFactory.decodeResource(getResources(),R.mipmap.ic_launcher))
                    .setContentIntent(pi);
            if(progress>=0){
                //当进度大于等于0时才需要显示下载进度
                builder.setContentText(progress+"%");
                /*
                 * 该方法有三个参数
                 * 第一个参数是最大进度
                 * 第二个是当前进度
                 * 第三个是是否使用模糊进度条*/
                builder.setProgress(100,progress,false);
            }
            notification = builder.build();
        } else {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                    .setContentTitle(title)
                    .setContentText("This is content text")
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setLargeIcon(BitmapFactory.decodeResource(getResources(),R.mipmap.ic_launcher))
                    .setContentIntent(pi);
            if(progress>=0){
                //当进度大于等于0时才需要显示下载进度
                builder.setContentText(progress+"%");
                builder.setProgress(100,progress,false);
            }
            notification = builder.build();
        }
        return notification;
    }
}
