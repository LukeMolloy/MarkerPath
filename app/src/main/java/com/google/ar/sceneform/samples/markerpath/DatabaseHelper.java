package com.google.ar.sceneform.samples.markerpath;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

//Taken from https://www.youtube.com/watch?v=cp2rL3sAFmI
public class DatabaseHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "anchor.db";

    // Table User data
    public static final String TABLE_ANCHOR_DATA = "AnchorData";
    public static final String COL_X_COORD = "xCoord";
    public static final String COL_Y_COORD = "yCoord";
    public static final String COL_Z_COORD = "zCoord";

    public DatabaseHelper(Context context) {super(context, DATABASE_NAME, null, 1);}

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("create table "+ TABLE_ANCHOR_DATA +" (ANCHORID INTEGER PRIMARY KEY AUTOINCREMENT, XCOORD TEXT, YCOORD TEXT, ZCOORD TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i1) {
        db.execSQL("DROP TABLE IF EXISTS "+TABLE_ANCHOR_DATA);
        onCreate(db);
    }
    //For Kyle
    public boolean insertAnchorData(String xCoord, String yCoord, String zCoord) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(COL_X_COORD, xCoord);
        contentValues.put(COL_Y_COORD, yCoord);
        contentValues.put(COL_Z_COORD, zCoord);
        long result = db.insert(TABLE_ANCHOR_DATA,null ,contentValues);
        if (result == -1)
            return false;
        else
            return true;
    }

    //Insert data needs to be copied and changed to suit each insert's needs
    public void clearData() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_ANCHOR_DATA,null,null);
    }

    public Cursor getAllData(String tableName) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor res = db.rawQuery("select * from "+tableName,null);
        return res;
    }

}
