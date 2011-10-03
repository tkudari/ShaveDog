package com.tejus.shavedog.activity;

import java.util.HashMap;

import com.tejus.shavedog.Definitions;
import com.tejus.shavedog.R;
import com.tejus.shavedog.resources.ShaveDbAdapter;

import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.widget.SimpleCursorAdapter;

public class FriendsActivity extends ListActivity {
    private BroadcastReceiver mLocalIntentReceiver = new LocalIntentReceiver();
    ShaveDbAdapter dbAdapter;
    Cursor mCursor;
    

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

    public class LocalIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive( Context context, Intent intent ) {
            
            Log.d( "XXXX", "friend accepted, inserting into db; username = " + intent.getStringExtra( "user_name" ) + ", address = " + intent.getStringExtra( "address" ) );
            dbAdapter.insertFriend( intent.getStringExtra( "user_name" ), intent.getStringExtra( "address" ), "active" );
        }
    }
}
