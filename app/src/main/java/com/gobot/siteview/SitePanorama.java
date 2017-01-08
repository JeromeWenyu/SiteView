package com.gobot.siteview;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Created by Jerome_Wen on 2016/12/29.
 */

public class SitePanorama extends AppCompatActivity {

    public static final int URL_RESULT_CODE = 2;

    private WebView webView;
    private boolean isRealURL = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.site_panorama);

        webView = (WebView) findViewById(R.id.webview_site_panorama);

        Intent data = getIntent();
        Bundle bundle = data.getExtras();

        if(data!=null && bundle!=null){
            String myURL = bundle.getString("URL");
            try{
                URL urlObj = new URL(myURL);
                URI uriObj = new URI(urlObj.getProtocol(), urlObj.getHost(), urlObj.getPath(), urlObj.getQuery(), null);

                isRealURL = true;
                webView.getSettings().setJavaScriptEnabled(true);
                //webView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
                WebSettings webSettings = webView.getSettings();
                webSettings.setAllowFileAccess(true);
                webSettings.setBuiltInZoomControls(true);
                webView.getSettings().setDomStorageEnabled(true);
                webView.loadUrl(myURL);
                webView.setWebChromeClient(new WebChromeClient(){
                    @Override
                    public void onProgressChanged(WebView view,int newProgress) {
                        if (newProgress == 100) {
                            SitePanorama.this.setTitle("加载完成");
                        } else {
                            SitePanorama.this.setTitle("加载中.......");
                        }
                    }
                });
                //webView.setWebViewClient(new WebViewClient());

            }catch(MalformedURLException e){
                isRealURL = false;
                e.printStackTrace();
            }catch(URISyntaxException e){
                isRealURL = false;
                e.printStackTrace();
            }finally {

            }
        }

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab_go_back_main);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent=new Intent(SitePanorama.this, MainActivity.class);
                intent.putExtra("result", isRealURL);
                setResult(URL_RESULT_CODE, intent);
                finish();
            }
        });
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode ,KeyEvent keyEvent){
        if(keyCode==keyEvent.KEYCODE_BACK){//监听返回键，如果可以后退就后退
            if(webView.canGoBack()){
                webView.goBack();
                return true;
            }
        }
        return super.onKeyDown(keyCode, keyEvent);
    }

}
