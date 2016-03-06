package com.thedevgeeks.sysupdate;

import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.util.Date;


public class MainActivity extends AppCompatActivity {
    public static final String UPDATE_SETTINGS = "InfinityUpdateSettings";
    private DownloadManager downloadManager;
    private long downloadReference;

    final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                long downloadId = intent.getLongExtra(
                        DownloadManager.EXTRA_DOWNLOAD_ID, 0);
                Query query = new Query();
                query.setFilterById(downloadReference);
                Cursor c = downloadManager.query(query);
                if (c.moveToFirst()) {
                    int columnIndex = c
                            .getColumnIndex(DownloadManager.COLUMN_STATUS);
                    if (DownloadManager.STATUS_SUCCESSFUL == c
                            .getInt(columnIndex)) {
                        checkDown("true");
                    }
                    if (DownloadManager.STATUS_FAILED == c
                            .getInt(columnIndex)) {
                        checkDown("Download Failed");
                    }
                }
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        checkUpdateAvail();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Button button = (Button) findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (button.getText() == "DOWNLOAD") {
                    beginDownload();
                } else if (button.getText() == "INSTALL") {
                    flashMe();
                } else {
                    checkUpdateAvail();
                }
            }
        });
        SharedPreferences settings = getSharedPreferences(UPDATE_SETTINGS, 0);
        boolean avail = settings.getBoolean("avail", false);
        boolean down = settings.getBoolean("down", false);
        String last = settings.getString("last", "N/A");
        if (avail) {
            if (down) {
                setTextView1("Update Downloaded");
                setTextView2("Last checked for update on " + last);
                button.setText("INSTALL");
            } else {
                setTextView1("Update Available");
                setTextView2("Last checked for update on " + last);
                button.setText("DOWNLOAD");
            }
        }

        registerReceiver(receiver, new IntentFilter(
                DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
    }

    @Override
    public View findViewById(int id) {
        return super.findViewById(id);
    }

    public boolean isNetworkAvailable(Context context) {
        if (((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo() != null) {
            return true;
        } else {
            makeSnacks("NO INTERNET CONNECTION DETECTED!!!");
            Log.v("isNetworkAvailable", "NO INTERNET CONNECTION DETECTED!!!");
            return false;
        }
    }

    public void flashMe() {
        makeSnacks("flashMe");
        SharedPreferences settings = getSharedPreferences(UPDATE_SETTINGS, 0);
        //settings.edit().clear().commit();
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("mkdir -p /cache/recovery/\n");
            os.writeBytes("rm -f /cache/recovery/command\n");
            os.writeBytes("echo 'boot-recovery' >> /cache/recovery/command\n");
            os.writeBytes("echo '--update_package=/data//media/0/Infinity_Update/update.zip' >> /cache/recovery/command\n");
        os.writeBytes("reboot recovery" + "\n");
        os.writeBytes("sync\n");
        os.writeBytes("exit\n");
        os.flush();
        p.waitFor();
        ((PowerManager) getSystemService(POWER_SERVICE)).reboot("recovery");
    } catch(Exception e) {
        e.printStackTrace();
    }

    }
    public void beginDownload() {
        Log.v("beginDownload", "Begin Download");
        SharedPreferences settings = getSharedPreferences(UPDATE_SETTINGS, 0);
        final ProgressBar pr = (ProgressBar) findViewById(R.id.progressBar);
        String url = settings.getString("url", " ");
        try {
            File upBack = new File("/sdcard/Infinity_Update/update_Backup.zip");
            if(upBack.exists()) {
                Log.v("beginDownload", "Removing Backup.zip");
                upBack.delete();
            }
            File up = new File("/sdcard/Infinity_Update/update.zip");
            if(up.exists()) {
                Log.v("beginDownload", "Renaming old update.zip");
                File to = new File("/sdcard/Infinity_Update/update_Backup.zip");
                up.renameTo(to);
            }
            Log.v("beginDownload", "beginning download: " + url);
            if (isNetworkAvailable(this)) {
                URL uri = new URL(url);
                downloadUpdate down = new downloadUpdate();
                down.execute(uri);
                setTextView1("Downloading Update ...");
                setTextView2("");
                pr.setVisibility(View.VISIBLE);
            } else {
                setTextView1("Download Failed");
                Log.v("beginDownload", "NO INTERNET CONNECTION DETECTED!!!");
            }
        } catch (MalformedURLException e) {
            settings.edit().clear().commit();
            setTextView1("Download Failed");
            e.printStackTrace();
        }
    }
    public void checkUpdateAvail() {
        SharedPreferences settings = getSharedPreferences(UPDATE_SETTINGS, 0);
        final Button button = (Button) findViewById(R.id.button);
       boolean avail = settings.getBoolean("avail", false);
        boolean down = settings.getBoolean("down", false);
        String last = settings.getString("last", " ");
        //Log.v("checkUpdateAvail", "avail: " + avail + " down: " + down + " last: " + last);
        if (avail){
            //Log.v("checkUpdateAvail", "Update Avail found");
            if(down) {
                //Log.v("checkUpdateAvail", "Update Down found");
                setTextView1("Update Downloaded");
                setTextView2("Last checked for update on " + last);
                button.setText("INSTALL");
            } else {
                setTextView1("Update Available");
                setTextView2("Last checked for update on " + last);
                button.setText("DOWNLOAD");
            }
        } else {
            if (isNetworkAvailable(this)) {
                String id = URLEncoder.encode(getBuild());
                getCurrent get = new getCurrent();
                if (get.getStatus() != AsyncTask.Status.RUNNING) {
                    makeSnacks("Checking For Updates");
                    get.execute("http://68.96.238.235:8080/ota/?id=" + id);
                } else {
                    makeSnacks("Checking For Updates!!!");
                }
            }
        }
    }
    public void makeSnacks(String s){
        final RelativeLayout view = (RelativeLayout) findViewById(R.id.main);
        Snackbar.make(view, s, Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();
    }

    public void setTextView1(String s) {
        final TextView tv = (TextView) findViewById(R.id.textView);
        tv.setText(s);
    }
    public void setTextView2(String s) {
        final TextView tv = (TextView) findViewById(R.id.textView2);
        tv.setText(s);
    }

    public void setProgressPercent(Integer i){
        final ProgressBar pr = (ProgressBar) findViewById(R.id.progressBar);
        Log.v("setProgressPercent", "Fired!!!");
        if(i>0){
                pr.setProgress(i);

        }
    }

    public String getBuild() {
        Process p = null;
        String id = "";
        try {
            p = new ProcessBuilder("/system/bin/getprop", "ro.build.display.id").redirectErrorStream(true).start();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = "";
            while ((line=br.readLine()) != null){
                id = line;
            }
            p.destroy();
            return id;
        } catch (IOException e) {
// TODO Auto-generated catch block
            e.printStackTrace();
            return "";
        }
    }

    private Void checkRes(String r){
        SharedPreferences settings = getSharedPreferences(UPDATE_SETTINGS, 0);
        String date = DateFormat.getDateInstance().format(new Date());
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("last", date).apply();
        if (r.equals("false")) {
            setTextView1("Your System is up to date");
            setTextView2("Last checked for update on " + date);
            makeSnacks("No Update Found");
            editor.putBoolean("avail", false).apply();
        } else if(r.contains("http")){
            makeSnacks("Update Found");
            setTextView1("Update Available");
            setTextView2("Last checked for update on " + date);
            final Button button = (Button) findViewById(R.id.button);
            button.setText("DOWNLOAD");
            editor.putBoolean("avail", true).apply();
            editor.putString("url", r).apply();
        } else {
            setTextView1("Your System is up to date");
            setTextView2("Last checked for update on " + date);
            makeSnacks("No Update Found");
            editor.putBoolean("avail", false).apply();
        }
        return null;
    }

    private void checkDown(String r){
        SharedPreferences settings = getSharedPreferences(UPDATE_SETTINGS, 0);
        SharedPreferences.Editor editor = settings.edit();
        String dateTime = DateFormat.getDateTimeInstance().format(new Date());
        final ProgressBar pr = (ProgressBar) findViewById(R.id.progressBar);
        if (r.equals("true")) {
            pr.setVisibility(View.INVISIBLE);
            editor.putBoolean("down", true).apply();
            setTextView1("Update Downloaded");
            setTextView2(dateTime);
            final Button button = (Button) findViewById(R.id.button);
            button.setText("INSTALL");

        } else {
            setTextView1(r);
            settings.edit().clear().commit();
        }
    }
    private class downloadUpdate extends AsyncTask<URL, Integer, String> {

        @Override
        protected String doInBackground(URL... urls) {
            Log.v("downloadUpdate", urls[0].toString()); //makeSnacks(urls[0].toString());
            downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            Uri Download_Uri = Uri.parse(urls[0].toString());
            DownloadManager.Request request = new DownloadManager.Request(Download_Uri);
            request.setTitle("System Update");
            request.setDescription("Infinity Smart Box System Update");
            request.setDestinationInExternalPublicDir("/Infinity_Update/", "update.zip" );
            downloadReference = downloadManager.enqueue(request);
            //Log.v("downloadUpdate", "Downloading");
            return null;
        }

        protected void onProgressUpdate(Integer... progress) {
            Log.v("onProgressUpdate", "progress: " + progress[0]);
            setProgressPercent(progress[0]);
        }

        protected void onPostExecute(String result) {
            //checkDown(result);
        }



    }
    private class getCurrent extends AsyncTask<String, Void, Void> {
        String r = "";

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            checkRes(r);
        }

        @Override
        protected Void doInBackground(String... params) {
            URL url;
            HttpURLConnection urlConnection = null;
            try {
                url = new URL(params[0]);
                urlConnection = (HttpURLConnection) url.openConnection();
                int responseCode = urlConnection.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    r = readStream(urlConnection.getInputStream());
                    //Log.v("CheckForUpdate", "Update found at: " + r);

                } else {
                    Log.v("CheckForUpdate", "Response code:" + responseCode);
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (urlConnection != null)
                    urlConnection.disconnect();
            }
            return null;
        }


        private String readStream(InputStream in) {
            BufferedReader reader = null;
            StringBuffer response = new StringBuffer();
            try {
                reader = new BufferedReader(new InputStreamReader(in));
                String line = "";
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            return response.toString();
        }

    }

}
