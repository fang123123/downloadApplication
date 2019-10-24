package com.example.updateapplication.Server;

import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import com.example.updateapplication.Server.DownloadListner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/*
* 使用AsyncTask来执行异步操作，使用OkHttp来执行Http操作
* */
public class DownloadTask extends AsyncTask<String,Integer,Integer> {
    public static final int TYPE_SUCCESS = 0;
    public static final int TYPE_FAILED = 1;
    public static final int TYPE_PAUSED = 2;
    public static final int TYPE_CANCELED = 3;
    private DownloadListner listner;
    private boolean isCanceled = false;
    private boolean isPaused = false;
    private int lastProgress;
    private static final String TAG = "DownloadTask";
    public DownloadTask(DownloadListner listner){
        this.listner = listner;
    }
    /*
    * 用于在后台执行下载任务,params是传入的Url*/
    @Override
    protected Integer doInBackground(String... params) {
        InputStream is = null;
        RandomAccessFile savedFile = null;
        File file = null;
        try{
            long downloadedLength = 0;//记录已下载的文件长度
            String downloadUrl = params[0];//从参数中获取下载Url
            //获取文件名
            String fileName = downloadUrl.substring(downloadUrl.lastIndexOf("/"));
            //获取下载路径
            String directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
            String savePath = directory + fileName;
            file = new File(savePath);
            Log.d(TAG, "安装地址："+savePath);
            //判断文件是否已存在
            if(file.exists()){
                //获取当前文件长度
                downloadedLength = file.length();
            }
            //获取待下载文件的长度
            long contentLength = getContentLength(downloadUrl);
            if(contentLength == 0){
                //如果文件长度等于0，说明文件有问题，下载失败
                Log.d(TAG, "文件长度等于0，下载失败");
                return TYPE_FAILED;
            }else if(contentLength == downloadedLength){
                //如果文件长度已等于下载文件长度，说明文件已经下载完成
                Log.d(TAG, "文件已经下载完成");
                return TYPE_SUCCESS;
            }
            //使用OkHttp来实现Http请求
            OkHttpClient client = new OkHttpClient();
            //header告诉服务器应该从哪个字节开始传输数据（已经下载的就不用重新下载了）
            Request request = new Request.Builder()
                    .addHeader("RANGE","bytes="+downloadedLength+"-")
                    .url(downloadUrl)
                    .build();
            //执行请求，获取返回数据
            Response response = client.newCall(request).execute();
            //开始解析服务器获取的数据
            if(response!=null){
                is = response.body().byteStream();
                //开始写文件
                savedFile = new RandomAccessFile(file,"rw");
                savedFile.seek(downloadedLength);//跳过已下载的字节
                byte[] b = new byte[1024];
                int total = 0;
                int len;
                while((len=is.read(b))!=-1){
                    if(isCanceled){
                        //用户点击取消
                        Log.d(TAG, "用户点击取消");
                        return TYPE_CANCELED;
                    }else if(isPaused){
                        //用户点击暂停
                        Log.d(TAG, "用户点击暂停");
                        return TYPE_PAUSED;
                    }else{
                        //本次下载的文件长度
                        total += len;
                        savedFile.write(b,0,len);
                        //计算已下载的百分比=本次下载文件长度+已存在文件长度/文件总长度
                        int progress = (int)((total+downloadedLength)*100/contentLength);
                        //将当前的进度从子线程传到主线程
                        publishProgress(progress);
                    }
                }
                response.body().close();
                Log.d(TAG, "下载成功");
                return TYPE_SUCCESS;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            try{
                if(is != null){
                    is.close();
                }
                if(savedFile!=null){
                    savedFile.close();
                }
                if(isCanceled&&file!=null){
                    Log.d(TAG, "文件已删除");
                    file.delete();
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        Log.d(TAG, "发生异常，下载失败");
        return TYPE_FAILED;
    }
    /*
    * 获取子线程传来的数据values*/
    @Override
    protected void onProgressUpdate(Integer... values) {
        int progress = values[0];
        if(progress>lastProgress){
            listner.onProgress(progress);
            lastProgress = progress;
        }
    }
    //在主线程中处理子线程中任务结束返回的状态status
    @Override
    protected void onPostExecute(Integer status) {
        switch (status){
            case TYPE_SUCCESS:
                listner.onSuccess();
                break;
            case TYPE_FAILED:
                listner.onFailed();
                break;
            case TYPE_PAUSED:
                listner.onPaused();
                break;
            case TYPE_CANCELED:
                listner.onCanceled();
                break;
            default:
                break;
        }
    }
    public void pauseDownload(){
        isPaused = true;
    }
    public void cancelDownload(){
        isCanceled = true;
    }
    /*
    * 获取要下载内容的长度
    * */
    private long getContentLength(String downloadUrl)throws IOException{
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(downloadUrl)
                .build();
        Response response = client.newCall(request).execute();
        if(response != null&&response.isSuccessful()){
            long contentLength = response.body().contentLength();
            response.body().close();
            return contentLength;
        }
        return 0;
    }

}
