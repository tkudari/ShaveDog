package com.tejus.shavedog.activity;

import java.util.HashMap;

import com.tejus.shavedog.Definitions;
import com.tejus.shavedog.R;
import com.tejus.shavedog.ShaveService;
import com.tejus.shavedog.resources.ShaveDbAdapter;

import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

public class FriendsActivity extends ListActivity {
    private BroadcastReceiver mLocalIntentReceiver = new LocalIntentReceiver();
    ShaveDbAdapter dbAdapter;
    Cursor mCursor;
    private ServiceConnection mConnection;
    private ShaveService mShaveService;
    

    @Override
    public void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        initReceiver();
        dbAdapter = new ShaveDbAdapter( this );
        dbAdapter.open();
        Bundle bundle = getIntent().getExtras();
        String userName = bundle.get( "user_name" ).toString();
        String address = bundle.get( "address" ).toString();
        Log.d("XXXX", "oncreate received : " + userName + " from : " + address + "; inserting into db..");
        dbAdapter.insertFriend(userName, address, "active" );
        showFriends();
        connectToService();
    }

    private void connectToService() {
        mConnection = new ServiceConnection() {
            @Override
            public void onServiceDisconnected( ComponentName className ) {
                mShaveService = null;
            }

            @Override
            public void onServiceConnected( ComponentName name, IBinder service ) {
                mShaveService = ( ( ShaveService.ShaveBinder ) service ).getService();
            }
        };        
        bindService( new Intent( this, ShaveService.class ), mConnection, Context.BIND_AUTO_CREATE );

    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        // Get the item that was clicked
       Log.d("XXXX", "position = " + position + ", id = "+ id);
       String address = dbAdapter.fetchFriend( id ).getString( dbAdapter.COLUMN_ADDRESS );
       Log.d("XXXX", "address selected = " + address);
       //message this guy for a listing of files:
       mShaveService.sendMessage( address, Definitions.REQUEST_LISTING );
    }
    
    private void showFriends() {
        
            mCursor = dbAdapter.fetchAllFriends();
            startManagingCursor(mCursor);

            String[] from = new String[] { ShaveDbAdapter.KEY_USERNAME };
            int[] to = new int[] { R.id.label };

            SimpleCursorAdapter friends = new SimpleCursorAdapter(this,
                    R.layout.friend_row, mCursor, from, to);
            setListAdapter(friends);
        
    }

    private void initReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction( Definitions.INTENT_FRIEND_ACCEPTED );
        registerReceiver( mLocalIntentReceiver, filter );
    }

    @Override
    protected void onSaveInstanceState( Bundle outState ) {
        super.onSaveInstanceState( outState );
    }
    
    @Override
    public void onBackPressed() {
        startActivity( new Intent().setClass( this, ShaveDogActivity.class ));
    };

    public class LocalIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive( Context context, Intent intent ) {
            
            Log.d( "XXXX", "friend accepted, inserting into db; username = " + intent.getStringExtra( "user_name" ) + ", address = " + intent.getStringExtra( "address" ) );
            dbAdapter.insertFriend( intent.getStringExtra( "user_name" ), intent.getStringExtra( "address" ), "active" );
        }
    }
}
