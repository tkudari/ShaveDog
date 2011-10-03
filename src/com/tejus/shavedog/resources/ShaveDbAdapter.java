package com.tejus.shavedog.resources;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class ShaveDbAdapter {

    public static final String KEY_ROWID = "_id";
    public static final String KEY_USERNAME = "username";
    public static final String KEY_ADDRESS = "address";
    public static final String KEY_STATUS = "status";
    private static final String DATABASE_TABLE = "friends";

    private static String[] PROJECTION = new String[] {
        KEY_ROWID,
        KEY_USERNAME,
        KEY_ADDRESS,
        KEY_STATUS
    };

    private Context context;
    private SQLiteDatabase database;
    private ShaveDbHelper dbHelper;

    public ShaveDbAdapter( Context context ) {
        this.context = context;
    }

    public ShaveDbAdapter open() throws SQLException {
        dbHelper = new ShaveDbHelper( context );
        database = dbHelper.getWritableDatabase();
        return this;
    }

    public void close() {
        dbHelper.close();
    }

    public long insertFriend( String userName, String address, String status ) {
        ContentValues initialValues = createContentValues( userName, address, status );
        Log.d("XXXX", "gonna start dumping cursor: ");
        DatabaseUtils.dumpCursor( database.query( DATABASE_TABLE, new String[] {KEY_USERNAME}, null, null, null, null, null  ) );

        if (database.query( DATABASE_TABLE, new String[] {KEY_USERNAME}, KEY_USERNAME + " = '" + userName + "'", null, null, null, null  ).getCount() == 0) {
            Log.d("XXXX: ", "inserting new friend: " + userName);
            return database.insert( DATABASE_TABLE, null, initialValues );
        } else {
            Log.d("XXXX: ", "friend already exists: " + userName);
        }
        return 0;
    }

    public boolean updateFriend( long rowId, String userName, String address, String status ) {
        ContentValues updateValues = createContentValues( userName, address, status );

        return database.update( DATABASE_TABLE, updateValues, KEY_ROWID + "=" + rowId, null ) > 0;
    }

    public boolean deleteFriend( long rowId ) {
        return database.delete( DATABASE_TABLE, KEY_ROWID + "=" + rowId, null ) > 0;
    }

    public Cursor fetchAllFriends() {
        return database.query( DATABASE_TABLE, PROJECTION, null, null, null, null, null );
    }

    public Cursor fetchFriend( long rowId ) throws SQLException {
        Cursor mCursor = database.query( true, DATABASE_TABLE, PROJECTION, KEY_ROWID + "=" + rowId, null, null, null, null, null );
        if ( mCursor != null ) {
            mCursor.moveToFirst();
        }
        return mCursor;
    }

    private ContentValues createContentValues( String userName, String address, String status ) {
        ContentValues values = new ContentValues();
        values.put( KEY_USERNAME, userName );
        values.put( KEY_ADDRESS, address );
        values.put( KEY_STATUS, status );
        return values;
    }
}
