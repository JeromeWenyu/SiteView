package com.gobot.siteview;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * Created by Jerome_Wen on 2016/12/23.
 */

public class MyDatabaseHelper extends SQLiteOpenHelper {

    public static final String TABLE_NAME ="SiteInfo";
    public static final String COL_CI = "ci integer";
    public static final String COL_NAME = "name text";
    public static final String COL_TAC = "tac integer";
    public static final String COL_PCI = "pci integer";
    public static final String COL_EARFCN = "earfcn integer";
    public static final String COL_LAT = "lat real";
    public static final String COL_LON = "lon real";
    public static final String COL_AZIMUTH = "azimuth integer";
    public static final String COL_TOTAL_DOWNTILT = "total_downtilt real";
    public static final String COL_DOWNTILT = "downtilt real";
    public static final String COL_BAIDU_LAT = "baidu_lat real";
    public static final String COL_BAIDU_LON = "baidu_lon real";
    public static final String COL_HEIGHT = "height real";
    public static final String COL_TYPE = "type integer";
    public static final String COL_PANORAMA = "panorama text";

    public static final String CREATE_SITE = "CREATE TABLE IF NOT EXISTS SiteInfo ("
            + "id integer primary key autoincrement)";

    public static final String GPS_INDEX = "CREATE INDEX GPS_index ON SiteInfo(lat,lon)";

    private Context mContext;

    public MyDatabaseHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version){
        super(context,name,factory,version);
        mContext = context;

    }

    public boolean isColInTable(String col,String table, SQLiteDatabase db){

        boolean result = false ;
        Cursor cursor = null ;
        try{
            //查询一行
            cursor = db.rawQuery( "SELECT * FROM " + table + " LIMIT 0"
                    , null );
            result = cursor != null && cursor.getColumnIndex(col) != -1 ;
        }catch (Exception e){
            Log.e("MainActivity","isColInTable..." + e.getMessage()) ;
        }finally {
            if (null != cursor && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return result;
    }

    @Override
    public void onCreate(SQLiteDatabase db){
        db.execSQL(CREATE_SITE);
        if(isColInTable("ci",TABLE_NAME,db)){

        }else{
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_CI);
        }
        if(isColInTable("name",TABLE_NAME,db)){

        }else{
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_NAME);
        }
        if(isColInTable("tac",TABLE_NAME,db)){

        }else{
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_TAC);
        }
        if(isColInTable("pci",TABLE_NAME,db)){

        }else{
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_PCI);
        }
        if(isColInTable("earfcn",TABLE_NAME,db)){

        }else{
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_EARFCN);
        }
        if(isColInTable("lat",TABLE_NAME,db)){

        }else{
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_LAT);
        }
        if(isColInTable("lon",TABLE_NAME,db)){

        }else{
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_LON);
        }
        if(isColInTable("azimuth",TABLE_NAME,db)){

        }else{
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_AZIMUTH);
        }
        if(isColInTable("total_downtilt",TABLE_NAME,db)){

        }else{
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_TOTAL_DOWNTILT);
        }
        if(isColInTable("downtilt",TABLE_NAME,db)){

        }else{
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_DOWNTILT);
        }
        if(isColInTable("height",TABLE_NAME,db)){

        }else{
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_HEIGHT);
        }
        if(isColInTable("type",TABLE_NAME,db)){

        }else{
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_TYPE);
        }
        if(isColInTable("baidu_lat",TABLE_NAME,db)){

        }else{
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_BAIDU_LAT);
        }
        if(isColInTable("baidu_lon",TABLE_NAME,db)){

        }else{
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_BAIDU_LON);
        }
        if(isColInTable("panorama",TABLE_NAME,db)){

        }else{
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_PANORAMA);
        }
//        db.execSQL(GPS_INDEX);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion){

        switch (newVersion){

            case 2:

                if(isColInTable("baidu_lat",TABLE_NAME,db)||isColInTable("baidu_lon",TABLE_NAME,db)||isColInTable("panorama",TABLE_NAME,db)){
                    db.execSQL("DELETE FROM " + TABLE_NAME );
                }
                if(isColInTable("baidu_lat",TABLE_NAME,db)){

                }else{
                    db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_BAIDU_LAT);
                }
                if(isColInTable("baidu_lon",TABLE_NAME,db)){

                }else{
                    db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_BAIDU_LON);
                }
                if(isColInTable("panorama",TABLE_NAME,db)){

                }else{
                    db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_PANORAMA);
                }

        }
    }
}
