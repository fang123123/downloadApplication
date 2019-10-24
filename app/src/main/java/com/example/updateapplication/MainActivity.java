package com.example.updateapplication;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.updateapplication.Model.LocalInfo;
import com.example.updateapplication.Model.ServerInfo;
import com.example.updateapplication.Server.DownloadService;
import com.example.updateapplication.Util.HttpUtil;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private static String address="http://10.0.2.2:8080/get_data.json";
    private static final String TAG = "MainActivity";
    UpdateReceiver updateReceiver;
    InstallReceiver installReceiver;
    /*
    * 活动与服务之间的连接接口
    * */
    private DownloadService.DownloadBinder downloadBinder;
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            downloadBinder = (DownloadService.DownloadBinder)iBinder;
        }
        @Override
        public void onServiceDisconnected(ComponentName componentName) {

        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
        Button checkUpdate = (Button)findViewById(R.id.check_update);
        checkUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateApp(address);
            }
        });
    }
    //初始化操作
    private void init(){
        getLocalAppInfo();
        Intent intent = new Intent(MyApplication.getContext(), DownloadService.class);
        //启动服务
        startService(intent);
        //绑定服务
        bindService(intent, connection,BIND_AUTO_CREATE);
        /*
         * 判断是否有写SD的权限，如果没有则申请
         * */
        if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(MainActivity.this,new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            },1);
        }

    }
    @Override
    protected void onResume() {
        super.onResume();
        //注册更新广播
        updateReceiver = new UpdateReceiver();
        IntentFilter updateFilter = new IntentFilter();
        updateFilter.addAction(UpdateReceiver.UPDATE_ACTION);
        registerReceiver(updateReceiver,updateFilter);
        
        installReceiver = new InstallReceiver();
        IntentFilter installFilter = new IntentFilter();
        installFilter.addAction(InstallReceiver.INSTALL_ACTION);
        registerReceiver(installReceiver,installFilter);
    }

    /*
    * 获取本地app信息
    * */
    private void getLocalAppInfo(){
        SharedPreferences pref = getSharedPreferences("data",MODE_PRIVATE);
        LocalInfo.appName = pref.getString("appName","");
        LocalInfo.localVersion = pref.getInt("localVersion",1);
        LocalInfo.updateUrl = pref.getString("updateUrl","");
        LocalInfo.upgradeInfo = pref.getString("upgradeInfo","");
        if(LocalInfo.localVersion==0){
            Log.e(TAG, "读取本地app信息失败");
        }
    }
    /*
    * 更新app
    * 比较服务器版本与本地版本，如果服务器版本高，则发送更新广播
    * */
    private void updateApp(String address){
        //获取服务器app信息
        HttpUtil.sendOkHttpRequest(address, new okhttp3.Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Log.e(TAG, "Http请求失败");
                e.printStackTrace();
            }
            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                String responseData = response.body().string();
                Log.d(TAG, "服务器获取数据"+responseData);
                try {
                    JSONObject serverInfoObject = new JSONObject(responseData);
                    ServerInfo.appName =serverInfoObject.getString("appName");
                    ServerInfo.ServerVersion = serverInfoObject.getInt("serverVersion");
                    ServerInfo.updateUrl=serverInfoObject.getString("updateUrl");
                    ServerInfo.upgradeInfo=serverInfoObject.getString("upgradeInfo");
                    if(LocalInfo.localVersion<ServerInfo.ServerVersion){
                        //本地版本低于服务器版本，需要更新
                        Log.d(TAG, "存在新版本可更新");
                        //通过发送广播来进行实现下载
                        sendBroadcast(new Intent(UpdateReceiver.UPDATE_ACTION));
                    }else{
                        Log.d(TAG, "已经是最新版本，不需要更新");
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }
    /*
    * 监听更新广播，弹出提示框
    * 用户点击确定，则启动后台下载服务
    * */
    class UpdateReceiver extends BroadcastReceiver{
        static final String UPDATE_ACTION = "com.example.updateapplication.updatereceiver";
        @Override
        public void onReceive(Context context, Intent intent) {
//            Toast.makeText(context,"收到更新广播",Toast.LENGTH_SHORT).show();
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);
            alertDialog.setTitle("更新提示！");
            alertDialog.setMessage("存在新版本可更新，是否立即更新");
            alertDialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    downloadBinder.startDownload(ServerInfo.updateUrl);
                }
            });
            alertDialog.setNegativeButton("NO", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {

                }
            });
            alertDialog.show();
        }
    }
    class InstallReceiver extends BroadcastReceiver{
        static final String INSTALL_ACTION = "com.example.updateapplication.installreceiver";
        @Override
        public void onReceive(Context context, Intent intent) {
            File file = null;
            try{
                Toast.makeText(context, "收到安装广播", Toast.LENGTH_SHORT).show();
                Intent stopIntent = new Intent(MyApplication.getContext(), DownloadService.class);
                stopService(stopIntent);
                unbindService(connection);
                //获取文件名
                String fileName = ServerInfo.updateUrl.substring(ServerInfo.updateUrl.lastIndexOf("/"));
                //获取下载路径
                String directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
                String savePath = directory+fileName;
//                //完整apk路径
//                file = new File(directory+fileName);
//                //会根据用户的数据类型打开android系统相应的Activity。
//                Intent i = new Intent(Intent.ACTION_VIEW);
//                //设置intent的数据类型是应用程序application
//                i.setDataAndType(Uri.parse("file://" + file.toString()), "application/vnd.android.package-archive");
//                //为这个新apk开启一个新的activity栈
//                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                //开始安装
//                startActivity(i);
//                //关闭旧版本的应用程序的进程
//                android.os.Process.killProcess(android.os.Process.myPid());
                installAPK(savePath);
            }catch (Exception e){
                e.printStackTrace();
            }finally {
                //关闭旧版本的应用程序的进程
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        }
    }
    /**
     * 安装Apk
     */
    private void installAPK(String SavePath) {
        File apkFile = new File(SavePath);
        if (!apkFile.exists()) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            //兼容7.0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Uri contentUri = FileProvider.getUriForFile(MainActivity.this, "com.example.updateapplication.fileprovider", apkFile);
                intent.setDataAndType(contentUri, "application/vnd.android.package-archive");
                //兼容8.0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    boolean hasInstallPermission = getPackageManager().canRequestPackageInstalls();
                    if (!hasInstallPermission) {
                        //注意这个是8.0新API
                        Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                        return;
                    }
                }
            } else {
                intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
            }
            if (getPackageManager().queryIntentActivities(intent, 0).size() > 0) {
                startActivity(intent);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }


    /*
    * 申请权限后的处理
    * */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case 1:
                if(grantResults.length>0&&grantResults[0]!=PackageManager.PERMISSION_GRANTED){
                    Toast.makeText(this, "拒绝权限将无法使用程序", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
                break;
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(connection);
        unregisterReceiver(updateReceiver);
        unregisterReceiver(installReceiver);
    }
}
