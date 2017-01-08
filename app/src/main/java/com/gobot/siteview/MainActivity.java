package com.gobot.siteview;

import android.app.ProgressDialog;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.AppCompatTextView;
import android.support.v7.widget.Toolbar;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrength;
import android.telephony.CellSignalStrengthLte;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;
import android.Manifest;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.Marker;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.map.TextOptions;
import com.baidu.mapapi.map.UiSettings;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.model.inner.GeoPoint;
import com.baidu.mapapi.utils.CoordinateConverter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class MainActivity extends AppCompatActivity {

    public LocationClient mLocationClient;
    public static final int FILE_RESULT_CODE = 1;
    public static final int URL_RESULT_CODE = 2;
    public static final int CLOSE_PROGRESSDIALOG_CODE = 1;

    private AppCompatTextView positionText;
    private AppCompatTextView signalText;
    private MapView mapView = null;
    private BaiduMap baiduMap;
    private UiSettings mUiSettings;
    private DrawerLayout mDrawerLayout;
    private EditText mFindSiteName;
    private ListView mSiteRow;
    private boolean isFirstLocate = true;
    private boolean isOverlay = false;
    private boolean isDeleteOldData = false;
    private String myFileDir = "";
    private MyDatabaseHelper dbHelper;
    private String panoramaURL = "";
    private String lteCi = "0";
    private LatLng currentPosition;
    ProgressDialog myProgressDialog = null;

    ArrayList<Integer> site_ids = new ArrayList<Integer>();

    TelephonyManager telephonyManager;
    MyPhoneStateListener myPhoneStateListener;

    /**
     * 设置线程之间的消息传递
     * 在往数据库里添加数据的时候会用到这里
     * 创建时间：2016-12-24
     * 作者：Jerome Wen
     */
    private Handler handler = new Handler(){
        public void handleMessage(Message messsage){
            switch (messsage.what){
                case CLOSE_PROGRESSDIALOG_CODE:
                    if (myProgressDialog != null){
                        myProgressDialog.dismiss();
                        myProgressDialog =null;
                        int lineCount = messsage.arg1;
                        int lineNum = messsage.arg2;
                        AlertDialog.Builder dialog_notice = new AlertDialog.Builder(MainActivity.this);
                        dialog_notice.setTitle("导入成功！");
                        dialog_notice.setMessage("总共导入" + String.valueOf(lineCount) + "/" + String.valueOf(lineNum-1) + "个小区数据。");
                        dialog_notice.setIcon(R.mipmap.ic_launcher);
                        dialog_notice.setCancelable(false);
                        dialog_notice.setNegativeButton("关闭",new DialogInterface.OnClickListener(){
                            @Override
                            public void onClick(DialogInterface dialog,int which){
                                dialog.dismiss();
                            }
                        });
                        dialog_notice.show();
                    }else{}
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isFirstLocate = true;
        mLocationClient = new LocationClient(getApplicationContext());
        mLocationClient.registerLocationListener(new MyLocationListener());
        SDKInitializer.initialize(getApplicationContext());
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setLogo(R.drawable.title);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mSiteRow = (ListView) findViewById(R.id.main_site_row_include);

        mapView = (MapView) findViewById(R.id.map_view);
        baiduMap = mapView.getMap();
        baiduMap.setMyLocationEnabled(true);

        //设置地图禁止手势旋转并启用指南针图层
        mUiSettings = baiduMap.getUiSettings();
        mUiSettings.setRotateGesturesEnabled(false);
        mUiSettings.setCompassEnabled(true);

        positionText = (AppCompatTextView) findViewById(R.id.position_text_view);
        signalText = (AppCompatTextView) findViewById(R.id.signal_text_view);

        /**
         * 创建对手机信号的监听事件
         * 创建时间：2017-01-05
         * 作者：Jerome Wen
         */
        myPhoneStateListener = new MyPhoneStateListener();
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(myPhoneStateListener,PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

        /**
         * 这里添加go_to_home浮动按钮的点击相应事件：
         * 点击该按钮以后，地图设置当前终端所在地点GPS为中心点
         * 创建时间：2016-12-25
         * 作者：Jerome Wen
         */
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab_go_to_home);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isFirstLocate = true;
            }
        });

        List<String> permissionList = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED){
            permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_PHONE_STATE)!= PackageManager.PERMISSION_GRANTED){
            permissionList.add(Manifest.permission.READ_PHONE_STATE);
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED){
            permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (!permissionList.isEmpty()){
            String [] permissions = permissionList.toArray(new String[permissionList.size()]);
            ActivityCompat.requestPermissions(MainActivity.this,permissions,1);
        }else{
            requestLocation();
        }

        baiduMap.setOnMyLocationClickListener(new BaiduMap.OnMyLocationClickListener() {
            /**
             * 地图定位图标点击事件监听函数
             */
            public boolean onMyLocationClick(){

                return true;
            }
        });

        /**
         * 注册地图覆盖物监听事件：
         * 当基站扇区被点击以后，根据基站ID查询数据库，然后在弹出对话框显示当前扇区的基站工参信息；
         * 2016-12-29 更新：增加"进入基站全景"按钮，如果基站有全景数据，则进入基站全景
         * 创建时间：2016-12-25
         * 作者：Jerome Wen
         */
        baiduMap.setOnMarkerClickListener(new BaiduMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {

                int ci = 0;
                String name = null;
                int tac = 0;
                int pci = 0;
                int earfcn = 0;
                double lat = 0.000000;
                double lon = 0.000000;
                int azimuth = 0;
                double total_downtilt = 0.0;
                double downtilt = 0.0;
                double height = 0.0;
                int type = 0;
                int index = marker.getZIndex();

                dbHelper = new MyDatabaseHelper(MainActivity.this, "Site.db", null, 2);
                SQLiteDatabase dbWriter = dbHelper.getWritableDatabase();
                //dbHelper.onUpgrade(dbWriter,1,2);
                if(dbWriter != null){
                    Cursor cursor = dbWriter.query("SiteInfo", null, "id = ?", new String[]{String.valueOf(index)}, null,null,null);
                    if(cursor.moveToFirst()){
                        do{
                            ci = cursor.getInt(cursor.getColumnIndex("ci"));
                            name = cursor.getString(cursor.getColumnIndex("name"));
                            tac = cursor.getInt(cursor.getColumnIndex("tac"));
                            pci = cursor.getInt(cursor.getColumnIndex("pci"));
                            earfcn = cursor.getInt(cursor.getColumnIndex("earfcn"));
                            lat = cursor.getDouble(cursor.getColumnIndex("lat"));
                            lon = cursor.getDouble(cursor.getColumnIndex("lon"));
                            azimuth = cursor.getInt(cursor.getColumnIndex("azimuth"));
                            total_downtilt = cursor.getDouble(cursor.getColumnIndex("total_downtilt"));
                            //    downtilt = cursor.getDouble(cursor.getColumnIndex("downtilt"));
                            height = cursor.getDouble(cursor.getColumnIndex("height"));
                            // type = cursor.getInt(cursor.getColumnIndex("type"));
                            panoramaURL = cursor.getString(cursor.getColumnIndex("panorama"));
                        }while(cursor.moveToNext());
                    }
                    cursor.close();

                    AlertDialog.Builder dialog_nosite = new AlertDialog.Builder(MainActivity.this);
                    dialog_nosite.setTitle("小区信息：");
                    dialog_nosite.setMessage("CI：" + String.valueOf(ci) + "\n"
                            + "基站名称："+ String.valueOf(name) + "\n"
                            + "经度：" + String.valueOf(lon) + "\n"
                            + "纬度：" + String.valueOf(lat)+ "\n"
                            + "TAC：" + String.valueOf(tac) + "          PCI：" + String.valueOf(pci) + "\n"
                            + "EARFCN：" + String.valueOf(earfcn) + "   站高：" + String.valueOf(height)+ "\n"
                            + "方向角：" + String.valueOf(azimuth) + "          下倾角：" + String.valueOf(total_downtilt));
                    dialog_nosite.setIcon(R.mipmap.ic_launcher);
                    dialog_nosite.setCancelable(true);
                    if(!panoramaURL.equals("0") && panoramaURL != null){

                        dialog_nosite.setNegativeButton("进入基站全景",new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                //new Thread(new Runnable() {
                                //    @Override
                                //    public void run() {
                                        Intent intent = new Intent(MainActivity.this, SitePanorama.class);
                                        Bundle bundle = new Bundle();
                                        bundle.putString("URL", panoramaURL);
                                        intent.putExtras(bundle);
                                        startActivityForResult(intent, URL_RESULT_CODE);
                                //    }
                                //}).start();
                            }
                        });
                    }
                    dialog_nosite.show();
                }
                dbWriter.close();
                return true;
            }
        });

        /**
         * 注册地图状态改变监听事件
         * 创建时间：2016-12-25
         * 作者：Jerome Wen
         */
        baiduMap.setOnMapStatusChangeListener(new BaiduMap.OnMapStatusChangeListener() {
            /**
             * 手势操作地图，设置地图状态等操作导致地图状态开始改变。
             * @param status 地图状态改变开始时的地图状态
             */
            public void onMapStatusChangeStart(MapStatus status){
            }
            /**
             * 地图状态变化中
             * @param status 当前地图状态
             */
            public void onMapStatusChange(MapStatus status){
            }
            /**
             * 地图状态改变结束
             * @param status 地图状态改变结束后的地图状态
             */
            public void onMapStatusChangeFinish(MapStatus status){
                //绘制扇区
                dbHelper = new MyDatabaseHelper(MainActivity.this, "Site.db", null, 2);
                SQLiteDatabase dbWriter = dbHelper.getWritableDatabase();
                //dbHelper.onUpgrade(dbWriter,1,2);
                if(dbWriter != null && isOverlay){
                    DrawCell(getStartGPS(), getEndGPS(), dbWriter);
                }
                dbWriter.close();
            }
        });
    }

    public boolean onCreateOptionsMenu(Menu menu){
        getMenuInflater().inflate(R.menu.toolbar,menu);
        return true;
    }

    /**
     * toolbar菜单点击后执行的操作
     * 2016-12-28 更新：在toobar_find里，点击查询出来的Item后，直接从数据库中查询出来baidu地图坐标，不再做转换。
     * @param item
     * @return
     * 创建时间：2016-12-23
     * 作者：Jerome Wen
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        switch (item.getItemId()){
            /**
             * 点击查询按钮后，弹出侧滑窗口，输入需查询的基站名称，在数据库中查询出基站信息
             */
            case R.id.toolbar_find:
                mDrawerLayout.openDrawer(GravityCompat.START);

                Button find_site_button = (Button) findViewById(R.id.find_site);
                mFindSiteName = (EditText) findViewById(R.id.find_site_name);

                find_site_button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                        ArrayList<String> site_names = new ArrayList<String>();
                        int site_num = 0;
                        String mSiteName = mFindSiteName.getText().toString();

                        site_ids.clear();

                        if ( mSiteName !="请输入站点名称" && mSiteName != null){

                            dbHelper = new MyDatabaseHelper(MainActivity.this, "Site.db", null, 2);
                            SQLiteDatabase dbWriter = dbHelper.getWritableDatabase();
                            //dbHelper.onUpgrade(dbWriter,1,2);
                            if(dbWriter != null) {
                                Cursor cursor = dbWriter.query("SiteInfo", new String[]{"id","name"}, "name like ?", new String[]{"%" + mSiteName + "%"}, null, null, null);
                                if (cursor.moveToFirst()) {
                                    do {
                                        site_ids.add(cursor.getInt(cursor.getColumnIndex("id")));
                                        site_names.add(cursor.getString(cursor.getColumnIndex("name")));
                                        site_num++;
                                    } while (cursor.moveToNext());
                                }else{
                                    AlertDialog.Builder alertDialog_findsitefail = new AlertDialog.Builder(MainActivity.this);
                                    alertDialog_findsitefail.setTitle("查询失败！");
                                    alertDialog_findsitefail.setMessage("查询的站点不存在！");
                                    alertDialog_findsitefail.setCancelable(false);
                                    alertDialog_findsitefail.setNegativeButton("关闭", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    });
                                    alertDialog_findsitefail.show();
                                }
                                cursor.close();
                            }else{
                                Log.d("MainActivity","<--------数据库为空，请先导入数据！-------->");
                            }
                            dbWriter.close();
                            if(site_num != 0){

                                ListAdapter adapter = new ArrayAdapter<String>(MainActivity.this,R.layout.site_row,site_names);
                                mSiteRow.setAdapter(adapter);

                                mSiteRow.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                                    @Override
                                    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

                                        isOverlay = false;
                                        String name = null;
                                        double lat = 0.000000;
                                        double lon = 0.000000;
                                        double azimuth = 0.0;
                                        int type = 2;
                                        BitmapDescriptor bitmap_indoorcell = BitmapDescriptorFactory.fromResource(R.drawable.select_indoor_cell);
                                        BitmapDescriptor bitmap_outdoorcell = BitmapDescriptorFactory.fromResource(R.drawable.select_outdoor_cell);

                                        dbHelper = new MyDatabaseHelper(MainActivity.this, "Site.db", null, 2);
                                        SQLiteDatabase dbWriter = dbHelper.getWritableDatabase();
                                        //dbHelper.onUpgrade(dbWriter,1,2);
                                        if (dbWriter != null) {
                                            Cursor cursor = dbWriter.query("SiteInfo", null, "id = ?", new String[]{String.valueOf(site_ids.get(i))}, null, null, null);
                                            if (cursor.moveToFirst()) {
                                                do {
                                                    name = cursor.getString(cursor.getColumnIndex("name"));
                                                    lat = cursor.getDouble(cursor.getColumnIndex("baidu_lat"));
                                                    lon = cursor.getDouble(cursor.getColumnIndex("baidu_lon"));
                                                    azimuth = cursor.getInt(cursor.getColumnIndex("azimuth"));
                                                    type = cursor.getInt(cursor.getColumnIndex("type"));
                                                } while (cursor.moveToNext());
                                            } else {
                                            }
                                            cursor.close();
                                            LatLng sourcepoint = new LatLng(lat, lon);
                                            //LatLng point = GPSTansfor(sourcepoint, "GPS");
                                            //baiduMap.clear();
                                            if (type == 0) {
                                                OverlayOptions option = new MarkerOptions()
                                                        .position(sourcepoint)
                                                        .icon(bitmap_outdoorcell)
                                                        .zIndex(site_ids.get(i))
                                                        .title(name)
                                                        .perspective(true)
                                                        .rotate(360 - Float.parseFloat(String.valueOf(azimuth)));
                                                baiduMap.addOverlay(option); //在地图上添加Marker，并显示
                                            } else if (type == 1) {
                                                OverlayOptions option = new MarkerOptions()
                                                        .position(sourcepoint)
                                                        .icon(bitmap_indoorcell)
                                                        .zIndex(site_ids.get(i))
                                                        .perspective(true)
                                                        .title(name);
                                                baiduMap.addOverlay(option);
                                            } else {
                                            }
                                            MapStatusUpdate update = MapStatusUpdateFactory.newLatLng(sourcepoint);
                                            baiduMap.animateMapStatus(update);
                                            baiduMap.setMapStatus(update);
                                            update = MapStatusUpdateFactory.zoomTo(16f);
                                            baiduMap.animateMapStatus(update);
                                            baiduMap.setMapStatus(update);
                                        }
                                        dbWriter.close();
                                        mDrawerLayout.closeDrawer(GravityCompat.START);
                                    }
                                });
                            }else{}
                        }else{
                            AlertDialog.Builder alertDialog_sitename = new AlertDialog.Builder(MainActivity.this);
                            alertDialog_sitename.setTitle("查询失败！");
                            alertDialog_sitename.setMessage("请输入要查找的基站名称！");
                            alertDialog_sitename.setCancelable(false);
                            alertDialog_sitename.setNegativeButton("关闭", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                            alertDialog_sitename.show();
                        }
                    }
                });
                break;
            /**
             * 点击扇区图层打开和关闭按钮后，可打开或关闭扇区图层
             */
            case R.id.toolbar_show_site:
                //绘制扇区
                if(isOverlay){
                    baiduMap.clear();
                    isOverlay = false;
                }else {
                    dbHelper = new MyDatabaseHelper(MainActivity.this, "Site.db", null, 2);
                    SQLiteDatabase dbWriter = dbHelper.getWritableDatabase();
                    //dbHelper.onUpgrade(dbWriter,1,2);
                    if (dbWriter != null) {
                        DrawCell(getStartGPS(), getEndGPS(), dbWriter);
                        //dbWriter.close();
                        isOverlay = true;
                    } else {
                        AlertDialog.Builder dialog_nosite = new AlertDialog.Builder(MainActivity.this);
                        dialog_nosite.setTitle("没有基站信息：");
                        dialog_nosite.setMessage("请先导入基站信息！");
                        dialog_nosite.setIcon(R.mipmap.ic_launcher);
                        dialog_nosite.setCancelable(false);
                        dialog_nosite.setNegativeButton("关闭", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                        dialog_nosite.show();
                    }
                    dbWriter.close();
                }
                break;
            /**
             * 点击数据添加按钮后，往数据库中添加数据
             */
            case R.id.toolbar_add_data:
                Intent intent = new Intent(MainActivity.this, MyFileManager.class);
                startActivityForResult(intent,FILE_RESULT_CODE);
                break;
            /**
             * 点击关于按钮后，显示应用的相关信息
             */
            case R.id.toolbar_about:
                AlertDialog.Builder dialog_about = new AlertDialog.Builder(MainActivity.this);
                dialog_about.setTitle("关于本应用：");
                dialog_about.setMessage("版本：V1.0.1\n版权所有：Jerome Wen\n联系方式：Gobot@qq.com");
                dialog_about.setIcon(R.mipmap.ic_launcher);
                dialog_about.setCancelable(false);
                dialog_about.setNegativeButton("关闭",new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog,int which){
                        dialog.dismiss();
                    }
                });
                dialog_about.show();
                break;
            default:
        }
        return true;
    }

    /**
     * 用于设置当前位置坐标为地图中心点
     * @param location
     * 创建时间：2016-12-23
     * 作者：Jerome Wen
     */
    private void navigateTo(BDLocation location){
        LatLng sourceLatLng = new LatLng(location.getLatitude(),location.getLongitude());
        LatLng latLng = GPSTansfor(sourceLatLng,"COMMON");
        currentPosition = latLng;
        if(isFirstLocate){
            MapStatusUpdate update = MapStatusUpdateFactory.newLatLng(latLng);
            baiduMap.animateMapStatus(update);
            baiduMap.setMapStatus(update);
            update = MapStatusUpdateFactory.zoomTo(16f);
            baiduMap.animateMapStatus(update);
            baiduMap.setMapStatus(update);
            isFirstLocate = false;
            //    Log.d("MainActivity", "navigateTo: good! ");
        }
        MyLocationData locationData = new MyLocationData.Builder()
                .latitude(latLng.latitude)
                .longitude(latLng.longitude)
                .direction(location.getDirection())
                .accuracy(location.getRadius())
                .build();
        baiduMap.setMyLocationData(locationData);
        BitmapDescriptor mCurrentMarker = BitmapDescriptorFactory.fromResource(R.drawable.compass_logo);
        MyLocationConfiguration config = new MyLocationConfiguration(MyLocationConfiguration.LocationMode.NORMAL, true, mCurrentMarker);
        baiduMap.setMyLocationConfigeration(config);

        //解决人在走动的时候，基站和终端连线不更新的问题
        if(isOverlay){

            dbHelper = new MyDatabaseHelper(MainActivity.this, "Site.db", null, 2);
            SQLiteDatabase dbWriter = dbHelper.getWritableDatabase();

            if(dbWriter != null) {
                DrawCell(getStartGPS(), getEndGPS(), dbWriter);
            }

            dbWriter.close();
        }


    }

    private void requestLocation(){
        initLocation();
        mLocationClient.start();
    }

    private void initLocation(){
        LocationClientOption option = new LocationClientOption();
        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
        option.setCoorType("bd0911"); //gcj02:国测局经纬度坐标系；bd09：百度墨卡托坐标系；bd0911：百度经纬度坐标系
        option.setScanSpan(1000);     //位置信息获取刷新周期，单位：ms
        option.setIsNeedAddress(true);
        option.setNeedDeviceDirect(true);
        mLocationClient.setLocOption(option);
    }

    /**
     * onDestroy状态下手机执行操作
     * 创建时间：2016-12-20
     * 作者：Jerome Wen
     */
    @Override
    protected void onDestroy(){
        super.onDestroy();
        mLocationClient.stop();
        mapView.onDestroy();
        baiduMap.setMyLocationEnabled(false);
    }

    /**
     * onResume状态下手机执行操作
     * 2017-01-05 更新：增加对手机信号监听的操作
     * 创建时间：2016-12-20
     * 作者：Jerome Wen
     */
    @Override
    protected void onResume(){
        super.onResume();
        mapView.onResume();
        telephonyManager.listen(myPhoneStateListener,PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
    }

    /**
     * onPause状态下手机执行操作
     * 2017-01-05 更新：增加对手机信号监听的操作
     * 创建时间：2016-12-20
     * 作者：Jerome Wen
     */
    @Override
    protected void onPause(){
        super.onPause();
        mapView.onPause();
        telephonyManager.listen(myPhoneStateListener,PhoneStateListener.LISTEN_NONE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        switch (requestCode){
            case 1:
                if (grantResults.length>0){
                    for(int result:grantResults){
                        if (result != PackageManager.PERMISSION_GRANTED){
                            Toast.makeText(this,"必须同意所有权限才能使用本程序",Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }
                    }
                    requestLocation();
                }else{
                    Toast.makeText(this,"发生未知错误",Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
        }
    }

    public class MyLocationListener implements BDLocationListener{

        @Override
        public void onReceiveLocation(BDLocation location){
            if ((location.getLocType()==BDLocation.TypeGpsLocation)||(location.getLocType() == BDLocation.TypeNetWorkLocation)){
                navigateTo(location);
            }
            StringBuilder currentPosition = new StringBuilder();
            currentPosition.append("纬度：").append(location.getLatitude()).append("   ");
            currentPosition.append("经度：").append(location.getLongitude()).append("\n");
            currentPosition.append("地址：").append(location.getCountry());
            currentPosition.append(location.getProvince());
            currentPosition.append(location.getCity());
            currentPosition.append(location.getDistrict());
            currentPosition.append(location.getStreet()).append("\n");
            currentPosition.append("定位方式：");
            if(location.getLocType()==BDLocation.TypeGpsLocation){
                currentPosition.append("GPS（高精度）");
            }else if(location.getLocType()==BDLocation.TypeNetWorkLocation){
                currentPosition.append("无线网络（精度较低）");
            }
            positionText.setText(currentPosition);
        }
    }

    /**
     * 用于转换GPS
     * @param sourceLatLng 原始GPS，可以是系统获取的，也可以从外部文件读取
     * @param GPSType GPS的类型设置，根据百度地图工具描述，有两种：
     *                "COMMON"将google地图、soso地图、aliyun地图、mapabc地图和amap地图所用坐标转换成百度坐标；
     *                "GPS"将GPS设备采集的原始GPS坐标转换成百度坐标。
     * @return 返回转换后的坐标，并对转换后的坐标格式标准化，保留6位小数
     * 创建时间：2016-12-24
     * 作者：Jerome Wen
     */
    public static LatLng GPSTansfor (LatLng sourceLatLng, String GPSType) {

        CoordinateConverter converter = new CoordinateConverter();
        switch (GPSType){
            case "COMMON":
                converter.from(CoordinateConverter.CoordType.COMMON);
                break;
            case "GPS":
                converter.from(CoordinateConverter.CoordType.GPS);
                break;
            default:
                break;
        }
        converter.coord(sourceLatLng);
        double lat = FormatDouble(converter.convert().latitude,6);
        double lon = FormatDouble(converter.convert().longitude,6);
        LatLng desLatLng = new LatLng(lat,lon);
        return desLatLng;
    }

    /*public static LatLng ReverseGPSTansfor (LatLng sourceLatLng, String GPSType, double zoomLevel) {

        LatLng desLatLng;
        switch (GPSType){
            case "GPS":
                LatLng tempGPS = GPSTansfor(sourceLatLng,"GPS");
                //double lat = FormatDouble(sourceLatLng.latitude*2-tempGPS.latitude,6);
                //double lat = tempGPS.latitude; //位置偏北 1／5
                //double lat = sourceLatLng.latitude; //位置偏南
                double lat = FormatDouble(sourceLatLng.latitude + 0.001*(17-zoomLevel),6);
                double lon = FormatDouble(sourceLatLng.longitude*2-tempGPS.longitude,6);
                desLatLng = new LatLng(lat,lon);

                break;
            default:
                desLatLng = sourceLatLng;
                break;
        }
        return desLatLng;
    }*/

    /**
     * 用于格式化Double值
     * @param num 要格式化的数
     * @param length 保留小数点后的位数
     * @return
     * 创建时间：2016-12-22
     * 作者：Jerome Wen
     */
    public static double FormatDouble(double num, int length){
        String numStr = Double.toString(num);
        if((numStr.length()-numStr.indexOf("."))<length){
            return num;
        }
        StringBuffer strAfterDot = new StringBuffer();
        int i = 0;
        while(i<length){
            strAfterDot.append("0");
            i++;
        }
        String formatStr = "0."+strAfterDot.toString();
        DecimalFormat df = new DecimalFormat(formatStr);
        String numFormated = df.format(num);
        return Double.parseDouble(numFormated);
    }

    /**
     * 将读取到的文件里的数据保存到数据库
     * 2016-12-28 更新：增加了两列用来保存转换后的百度地图坐标
     * @param filePath 文件名称以及路径
     * 创建时间：2016-12-24
     * 作者：Jerome Wen
     */
    private void FileToDatabase(final String filePath) {

        ArrayList<String> lists = new ArrayList<String>();

        FileInputStream in = null;
        BufferedReader bufferedReader = null;
        ContentValues contentValues = new ContentValues();

       // int times = 0;
        int i = 0;
       // double div = 0.0;
        int lineNum = 0;
        int lineCount = 0;
        /**
         * 导入表格的总列数
         * 表格的数据列必须按照如下顺序：CI,NAME,TAC,PCI,EARFCN,LAT,LON,AZIMUTH,TOTAL_DOWNTILT,DOWNTILT,HEIGHT,TYPE
         */
        final int TABLE_COL_NUM = 13;

        lineNum = getTotalLines(filePath);

        Log.d("MainActivity", "<--------表里总共有：" + String.valueOf(lineNum) + "行数据！-------->");

        /*if (lineNum != 0) {
            div = lineNum / 1000;
            times = (int) Math.ceil(div);
        }

        Log.d("MainActivity", "<--------times = ：" + String.valueOf(times) + "-------->");*/

        dbHelper = new MyDatabaseHelper(MainActivity.this, "Site.db", null, 2);
        SQLiteDatabase dbWriter = dbHelper.getWritableDatabase();
        if(isDeleteOldData){
            dbWriter.delete("SiteInfo",null,null);
        }else{}
        //dbHelper.onUpgrade(dbWriter,1,2);
        if(dbHelper==null){
            dbHelper.onCreate(dbWriter);
        }else {
            try {
                bufferedReader = new BufferedReader(new FileReader(filePath));
                String str = null;
                String line = null;
                dbWriter.beginTransaction();
                while ((line = bufferedReader.readLine()) != null) {
                    //   Log.d("MainActivity", "<--------line = ：" + line + "-------->");
                    StringTokenizer st = new StringTokenizer(line, ",");
                    while (st.hasMoreTokens()) {        //一次一个 lists.size()=1
                        str = st.nextToken();
                        lists.add(str);
                    }
                    if (lists.size() > TABLE_COL_NUM) {
                        LatLng sourceGPS = new LatLng(Double.parseDouble(lists.get(i + TABLE_COL_NUM + 5)),Double.parseDouble(lists.get(i + TABLE_COL_NUM + 6)));
                        LatLng baiduGPS = GPSTansfor(sourceGPS,"GPS");
                        contentValues.put("ci", Integer.parseInt(lists.get(i + TABLE_COL_NUM)));
                        contentValues.put("name", lists.get(i + TABLE_COL_NUM +1));
                        contentValues.put("tac", Integer.parseInt(lists.get(i + TABLE_COL_NUM + 2)));
                        contentValues.put("pci", Integer.parseInt(lists.get(i + TABLE_COL_NUM + 3)));
                        contentValues.put("earfcn", Integer.parseInt(lists.get(i + TABLE_COL_NUM + 4)));
                        contentValues.put("lat", sourceGPS.latitude);
                        contentValues.put("lon", sourceGPS.longitude);
                        contentValues.put("baidu_lat", baiduGPS.latitude);
                        contentValues.put("baidu_lon", baiduGPS.longitude);
                        contentValues.put("azimuth", Integer.parseInt(lists.get(i + TABLE_COL_NUM + 7)));
                        contentValues.put("total_downtilt", Double.parseDouble(lists.get(i + TABLE_COL_NUM + 8)));
                        contentValues.put("downtilt", Double.parseDouble(lists.get(i + TABLE_COL_NUM + 9)));
                        contentValues.put("height", Double.parseDouble(lists.get(i + TABLE_COL_NUM + 10)));
                        contentValues.put("type", Integer.parseInt(lists.get(i + TABLE_COL_NUM + 11)));
                        contentValues.put("panorama", String.valueOf(lists.get(i + TABLE_COL_NUM + 12)));
                        dbWriter.insert("SiteInfo", null, contentValues);
                        lineCount = lineCount + 1;
                        i = i + TABLE_COL_NUM;
                    }
                }
                dbWriter.setTransactionSuccessful();
                dbWriter.endTransaction();

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                dbWriter.close();
            }
        }

        Message message = new Message();
        message.what = CLOSE_PROGRESSDIALOG_CODE;
        message.arg1 = lineCount;
        message.arg2 = lineNum;
        handler.sendMessage(message);

    }

    /**
     * 采用 LineNumberReader方式读取总行数
     * @param fileName
     * @return 读取到的文件的总行数
     * @throws IOException
     * 创建时间：2016-12-23
     * 作者：Jerome Wen
     */
    private int getTotalLines(String fileName) {

        FileReader in = null;
        LineNumberReader reader = null;
        int totalLines = 0;
        try{
            in = new FileReader(fileName);
            reader = new LineNumberReader(in);
            String strLine = reader.readLine();

            while (strLine != null) {
                totalLines++;
                strLine = reader.readLine();
            }
        }catch(IOException e){
            e.printStackTrace();
        }finally {
            if (reader != null){
                try{
                    reader.close();
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        }
        return totalLines;
    }

    /**
     * 调用新的Activity返回结果后的操作：
     *        返回1 FILE_RESULT_CODE：执行导入文件到数据库操作；
     *        返回2 URL_RESULT_CODE：打开全景图后的操作；
     * 2016-12-29 更新：增加全景图的操作
     * @param requestCode
     * @param resultCode
     * @param data
     * 创建时间：2016-12-22
     * 作者：Jerome Wen
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if(FILE_RESULT_CODE == requestCode){
            Bundle bundle = null;
            if(data!=null&&(bundle=data.getExtras())!=null){
                //    Log.d("MainActivity","选择文件为："+bundle.getString("file"));
                myFileDir = bundle.getString("file");
                //    Toast.makeText(MainActivity.this, "选择的文件是：" + myFileDir,Toast.LENGTH_SHORT).show();
                AlertDialog.Builder dialog_add_data = new AlertDialog.Builder(MainActivity.this);
                dialog_add_data.setTitle("准备导入数据文件");
                dialog_add_data.setMessage("导入新数据将会清除原有数据！\n" + "确定要将以下文件数据导入地图吗？\n" + myFileDir
                        + "\n \n新建数据：删除旧数据，导入新数据\n"
                        + "增加数据：保留旧数据，导入新数据\n"
                        + "取消：取消导入数据操作");
                dialog_add_data.setIcon(R.mipmap.ic_launcher);
                dialog_add_data.setCancelable(false);
                dialog_add_data.setPositiveButton("新建数据",new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog,int which){

                        dialog.dismiss();

                        isDeleteOldData = true;

                        if (myProgressDialog != null){
                            myProgressDialog.dismiss();
                        }else{
                            myProgressDialog = new ProgressDialog(MainActivity.this);
                            myProgressDialog.setTitle("请耐心等待！");
                            myProgressDialog.setMessage("站点数据加载中……");
                            myProgressDialog.setCancelable(false);
                            myProgressDialog.show();
                        }

                        //开启新线程
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                FileToDatabase(myFileDir);
                            }
                        }).start();
                    }
                });
                dialog_add_data.setNegativeButton("增加数据",new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog,int which){

                        dialog.dismiss();

                        isDeleteOldData = false;

                        if (myProgressDialog != null){
                            myProgressDialog.dismiss();
                        }else{
                            myProgressDialog = new ProgressDialog(MainActivity.this);
                            myProgressDialog.setTitle("请耐心等待！");
                            myProgressDialog.setMessage("站点数据加载中……");
                            myProgressDialog.setCancelable(false);
                            myProgressDialog.show();
                        }

                        //开启新线程
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                FileToDatabase(myFileDir);
                            }
                        }).start();
                    }
                });
                dialog_add_data.setNeutralButton("取消",new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog,int which){
                        dialog.dismiss();
                    }
                });
                dialog_add_data.show();
            }
        }
        if(FILE_RESULT_CODE == requestCode){
            Bundle bundle = null;
            if(data!=null&&(bundle=data.getExtras())!=null){
                boolean isRealURL = bundle.getBoolean("result");
                if(isRealURL){

                }else{
                    Log.d("MainActivity","<--------站点的全景地图地址不正确!-------->");
                }
            }
        }
    }

    /**
     * 在指定经纬度范围内，从数据库中取出基站信息，并在地图上将扇区画出来
     * 2016-12-28 更新：增加百度地图坐标的读取，直接用百度地图坐标显示，不做转换
     * 2017-01-05 更新：地图状态改变，除了绘制扇区外，还要绘制当前位置和基站之间的连线
     * @param startGPS
     * @param endGPS
     * @param mSQLiteDatabase
     * 创建时间：2016-12-24
     * 作者：Jerome Wen
     */
    public void DrawCell(LatLng startGPS, LatLng endGPS, SQLiteDatabase mSQLiteDatabase){

        ArrayList<Integer> ids = new ArrayList<Integer>();
        ArrayList<Integer> cis = new ArrayList<Integer>();
        ArrayList<String> names = new ArrayList<String>();
        ArrayList<Integer> tacs = new ArrayList<Integer>();
        ArrayList<Integer> pcis = new ArrayList<Integer>();
        ArrayList<Integer> earfcns = new ArrayList<Integer>();
        ArrayList<Double> lats = new ArrayList<Double>();
        ArrayList<Double> lons = new ArrayList<Double>();
        ArrayList<Double> baidu_lats = new ArrayList<Double>();
        ArrayList<Double> baidu_lons = new ArrayList<Double>();
        ArrayList<Integer> azimuths = new ArrayList<Integer>();
        ArrayList<Double> total_downtilts = new ArrayList<Double>();
        ArrayList<Double> downtilts = new ArrayList<Double>();
        ArrayList<Double> heights = new ArrayList<Double>();
        ArrayList<Integer> types = new ArrayList<Integer>();


        double min_GPS_lat;
        double min_GPS_lon;
        double max_GPS_lat;
        double max_GPS_lon;
        int sitecount = 0;
        int i = 0;

        double zoomLevel = baiduMap.getMapStatus().zoom;
        Log.d("MainActivity","<--------当前图层缩放级别：" + String.valueOf(zoomLevel) + "！-------->");
//        LatLng tempstartGPS = ReverseGPSTansfor(startGPS,"GPS",zoomLevel);
//        LatLng tempendGPS = ReverseGPSTansfor(endGPS,"GPS",zoomLevel);

        LatLng tempstartGPS = startGPS;
        LatLng tempendGPS = endGPS;
        LatLng serviceCellGPS = null;

        if(tempstartGPS.latitude > tempendGPS.latitude){
            min_GPS_lat = tempendGPS.latitude;
            max_GPS_lat = tempstartGPS.latitude;
        }else{
            min_GPS_lat = tempstartGPS.latitude;
            max_GPS_lat = tempendGPS.latitude;
        }
        if(tempstartGPS.longitude > tempendGPS.longitude){
            min_GPS_lon = tempendGPS.longitude;
            max_GPS_lon = tempstartGPS.longitude;
        }else{
            min_GPS_lon = tempstartGPS.longitude;
            max_GPS_lon = tempendGPS.longitude;
        }

        mSQLiteDatabase.beginTransaction();

        Cursor cursor = mSQLiteDatabase.query("SiteInfo", null,
                "(baidu_lat>? and baidu_lat<?) and (baidu_lon>? and baidu_lon<?)",
                new String[]{String.valueOf(min_GPS_lat),String.valueOf(max_GPS_lat),String.valueOf(min_GPS_lon),String.valueOf(max_GPS_lon)},
                null,null,"height desc");
        if(cursor.moveToFirst()){
            do{
                ids.add(cursor.getInt(cursor.getColumnIndex("id")));
                //    cis.add(cursor.getInt(cursor.getColumnIndex("ci")));
                names.add(cursor.getString(cursor.getColumnIndex("name")));
                //    tacs.add(cursor.getInt(cursor.getColumnIndex("tac")));
                //    pcis.add(cursor.getInt(cursor.getColumnIndex("pci")));
                //    earfcns.add(cursor.getInt(cursor.getColumnIndex("earfcn")));
                baidu_lats.add(cursor.getDouble(cursor.getColumnIndex("baidu_lat")));
                baidu_lons.add(cursor.getDouble(cursor.getColumnIndex("baidu_lon")));
                azimuths.add(cursor.getInt(cursor.getColumnIndex("azimuth")));
                //    total_downtilts.add(cursor.getDouble(cursor.getColumnIndex("total_downtilt")));
                //    downtilts.add(cursor.getDouble(cursor.getColumnIndex("downtilt")));
                heights.add(cursor.getDouble(cursor.getColumnIndex("height")));
                types.add(cursor.getInt(cursor.getColumnIndex("type")));
                sitecount++;
            }while(cursor.moveToNext());
        }

        if (currentPosition.latitude > min_GPS_lat && currentPosition.latitude < max_GPS_lat
                && currentPosition.longitude > min_GPS_lon && currentPosition.longitude < max_GPS_lon && !lteCi.equals("0") ){

            Double tempCi = (Double.valueOf(lteCi))/256;
            int lteShortCi = (int)Math.floor(tempCi);
            cursor = mSQLiteDatabase.query("SiteInfo", new String[]{"baidu_lat","baidu_lon"},
                    "ci like ? OR ci like ?", new String[]{String.valueOf(lteShortCi) + "%",String.valueOf(lteCi) + "%"},
                    null,null,null);
            if(cursor.moveToFirst()){
                //do{
                    serviceCellGPS = new LatLng(cursor.getDouble(cursor.getColumnIndex("baidu_lat")),cursor.getDouble(cursor.getColumnIndex("baidu_lon")));
                //}while(cursor.moveToNext());
            }
        }
        cursor.close();

        Log.d("MainActivity", "<--------在数据库中找到了" + String.valueOf(sitecount) + "个站点数据！-------->");
        mSQLiteDatabase.setTransactionSuccessful();
        mSQLiteDatabase.endTransaction();
        mSQLiteDatabase.close();

        baiduMap.clear();
        BitmapDescriptor bitmap_indoorcell = BitmapDescriptorFactory.fromResource(R.drawable.indoor_cell);
        BitmapDescriptor bitmap_outdoorcell = BitmapDescriptorFactory.fromResource(R.drawable.outdoor_cell);
        //定义Maker坐标点
        LatLng point;
        LatLng sourcepoint;

        int azimuth = 0;
        ArrayList<String> tempString = new ArrayList<String>();

        //显示排名靠前的200个小区（按基站高度从高到低排名）
        if(sitecount>200){
            sitecount=200;
        }else{
        }
        for(i=0;i<sitecount;i++){
            sourcepoint = new LatLng(baidu_lats.get(i),baidu_lons.get(i));
            //    point = GPSTansfor(sourcepoint,"GPS");
            azimuth = azimuths.get(i);
            if(types.get(i)==0){
                OverlayOptions option = new MarkerOptions()
                        .position(sourcepoint)
                        .icon(bitmap_outdoorcell)
                        .zIndex(ids.get(i))
                        .title(names.get(i))
                        .perspective(true)
                        .rotate(360-Float.parseFloat(String.valueOf(azimuth)));
                baiduMap.addOverlay(option); //在地图上添加Marker，并显示
            }else if(types.get(i)==1){
                OverlayOptions option = new MarkerOptions()
                        .position(sourcepoint)
                        .icon(bitmap_indoorcell)
                        .zIndex(ids.get(i))
                        .perspective(true)
                        .title(names.get(i));
                baiduMap.addOverlay(option);
            }else{}
        }

        //绘制当前服务小区和当前位置之间的连线
        if((currentPosition.latitude > min_GPS_lat && currentPosition.latitude < max_GPS_lat
                && currentPosition.longitude > min_GPS_lon && currentPosition.longitude < max_GPS_lon)
                && (serviceCellGPS != null)
                && (serviceCellGPS.latitude > min_GPS_lat && serviceCellGPS.latitude < max_GPS_lat
                && serviceCellGPS.longitude > min_GPS_lon && serviceCellGPS.longitude < max_GPS_lon)){

            List<LatLng> points = new ArrayList<LatLng>();
            points.add(serviceCellGPS);
            points.add(currentPosition);
            OverlayOptions mPolyline = new PolylineOptions()
                    .width(8)
                    .color(0xFFFF00FF)
                    .points(points);
            baiduMap.addOverlay(mPolyline);

            double distance= Distance(serviceCellGPS,currentPosition);
            int length=(int)distance;
            LatLng llText = new LatLng((serviceCellGPS.latitude + currentPosition.latitude)/2
                    ,(serviceCellGPS.longitude + currentPosition.longitude)/2);
            OverlayOptions textOption = new TextOptions()
                    .bgColor(0xFFFF00FF)
                    .fontSize(40)
                    .fontColor(0xFFFFFFFF)
                    .text(String.valueOf(length)+"米")
                    .rotate(0)
                    .position(llText);
            baiduMap.addOverlay(textOption);

        }

    }

    /**
     * 获取当前地图显示的左上角经纬度
     * @return
     * 创建时间：2016-12-24
     * 作者：Jerome Wen
     */
    public LatLng getStartGPS(){

        int top = mapView.getTop();            //地图view上部
        int left = mapView.getLeft();          //地图view左部
        android.graphics.Point mypoint = new android.graphics.Point(left,top);
        LatLng sourcepoint = baiduMap.getProjection().fromScreenLocation(mypoint);
        LatLng point = new LatLng(FormatDouble(sourcepoint.latitude,6),FormatDouble(sourcepoint.longitude,6));
        //    Log.d("MainActivity","<--------地图左上角的经纬度：" + String.valueOf(point.latitude) + ","+ String.valueOf(point.longitude) + "-------->");
        return point;
    }

    /**
     * 获取当前地图显示的右下角经纬度
     * @return
     * 创建时间：2016-12-24
     * 作者：Jerome Wen
     */
    public LatLng getEndGPS(){

        int bottom = mapView.getBottom();        //地图view下部
        int right = mapView.getRight();          //地图view右部
        android.graphics.Point mypoint = new android.graphics.Point(right,bottom);
        LatLng sourcepoint = baiduMap.getProjection().fromScreenLocation(mypoint);
        LatLng point = new LatLng(FormatDouble(sourcepoint.latitude,6),FormatDouble(sourcepoint.longitude,6));
        //    Log.d("MainActivity","<--------地图右下角的经纬度：" + String.valueOf(point.latitude) + ","+ String.valueOf(point.longitude) + "-------->");
        return point;
    }

    /**
     * 手机状态监听类，当手机接收信号状态发生变化时，显示手机信号强度
     * 创建时间：2017-01-05
     * 作者：Jerome Wen
     */
    private class MyPhoneStateListener extends PhoneStateListener {
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            super.onSignalStrengthsChanged(signalStrength);

            StringBuilder currentSignal = new StringBuilder();

            //获取小区信息
            List<CellInfo> cellInfoList = telephonyManager.getAllCellInfo();
            boolean isServiceLteCell = true;
            String cellSignalStrength = null;
            String lteRsrp = null;
            String lteSinr = null;
            String lteRsrq = null;
            //int index = 0;
            if(cellInfoList != null){
                for (CellInfo cellInfo:cellInfoList)
                {
                    //获取所有Lte网络信息
                    if (cellInfo instanceof CellInfoLte)
                    {
                        //    currentSignal.append("["+index+"]==CellInfoLte"+"\n");
                        //    if(cellInfo.isRegistered()){
                        //        currentSignal.append("isRegistered=YES"+"\n");
                        //    }
                        //currentSignal.append("TimeStamp:"+cellInfo.getTimeStamp()+"\n");
                        if (isServiceLteCell){

                            cellSignalStrength = ((CellInfoLte)cellInfo).getCellSignalStrength().toString();
                            lteRsrp = cellSignalStrength.substring(cellSignalStrength.indexOf("rsrp=")+5,cellSignalStrength.indexOf("rsrp=")+9);
                            lteRsrq = cellSignalStrength.substring(cellSignalStrength.indexOf("rsrq=")+5,cellSignalStrength.indexOf("rsrq=")+8);
                            lteSinr = cellSignalStrength.substring(cellSignalStrength.indexOf("ss=")+3,cellSignalStrength.indexOf("ss=")+5);
                            lteCi = String.valueOf(((CellInfoLte)cellInfo).getCellIdentity().getCi());
                            currentSignal.append("小区信息：CI:");
                            currentSignal.append(lteCi+"   ");
                            currentSignal.append("PCI:");
                            currentSignal.append(String.valueOf(((CellInfoLte)cellInfo).getCellIdentity().getPci())+"   ");
                            currentSignal.append("TAC:");
                            currentSignal.append(String.valueOf(((CellInfoLte)cellInfo).getCellIdentity().getTac())+"\n");
                            //currentSignal.append("EARFCN:");
                            //currentSignal.append(String.valueOf(((CellInfoLte)cellInfo).getCellIdentity().getEarfcn())+"\n");
                            currentSignal.append("信号强度：RSRP:");
                            currentSignal.append(lteRsrp+"dBm   ");
                            currentSignal.append("SINR:");
                            currentSignal.append(lteSinr+"dB   ");
                            currentSignal.append("RSRQ:");
                            currentSignal.append(lteRsrq+"dB   ");
                            isServiceLteCell = false;
                        }

                    }
                    //获取所有的cdma网络信息
                    if(cellInfo instanceof CellInfoCdma){
                        //    currentSignal.append("["+index+"]==CellInfoCdma"+"\n");
                        //    if(cellInfo.isRegistered()){
                        //       currentSignal.append("isRegistered=YES"+"\n");
                        //   }
                        //currentSignal.append("TimeStamp:"+cellInfo.getTimeStamp()+"\n");
                        //   currentSignal.append(((CellInfoCdma)cellInfo).getCellIdentity().toString()+"\n");
                        //   currentSignal.append(((CellInfoCdma)cellInfo).getCellSignalStrength().toString()+"\n");
                    }
                    //获取所有的Gsm网络
                    if(cellInfo instanceof CellInfoGsm){
                        //    currentSignal.append("["+index+"]==CellInfoGsm"+"\n");
                        //    if(cellInfo.isRegistered()){
                        //        currentSignal.append("isRegistered=YES"+"\n");
                        //    }
                        //currentSignal.append("TimeStamp:"+cellInfo.getTimeStamp()+"\n");
                        //    currentSignal.append(((CellInfoGsm)cellInfo).getCellIdentity().toString()+"\n");
                        //    currentSignal.append(((CellInfoGsm)cellInfo).getCellSignalStrength().toString()+"\n");
                    }
                    //获取所有的Wcdma网络
                    if(cellInfo instanceof CellInfoWcdma){
                        //    currentSignal.append("["+index+"]==CellInfoWcdma"+"\n");
                        //    if(cellInfo.isRegistered()){
                        //        currentSignal.append("isRegistered=YES"+"\n");
                        //    }
                        //currentSignal.append("TimeStamp:"+cellInfo.getTimeStamp()+"\n");
                        //    currentSignal.append(((CellInfoWcdma)cellInfo).getCellIdentity().toString()+"\n");
                        //    currentSignal.append(((CellInfoWcdma)cellInfo).getCellSignalStrength().toString()+"\n");
                    }
                    //index++;
                }
            }else{
                currentSignal.append("无法获取手机信号测量信息！");
            }

            signalText.setText(currentSignal);

        }
    }


    /**
     * 计算百度地图上两点之间的距离
     * @param latLng1
     * @param latLng2
     * @return
     * 创建时间：2017-01-05
     * 作者：Jerome Wen
     */
    public Double Distance(LatLng latLng1, LatLng latLng2) {


        Double R=6370996.81;  //地球的半径

     /*
     * 获取两点间x,y轴之间的距离
     */
        Double x = (latLng2.longitude - latLng1.longitude)*Math.PI*R*Math.cos(((latLng1.latitude+latLng2.latitude)/2)*Math.PI/180)/180;
        Double y = (latLng2.latitude - latLng1.latitude)*Math.PI*R/180;


        Double distance = Math.hypot(x, y);   //得到两点之间的直线距离

        return   distance;

    }

}
