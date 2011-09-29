package com.tejus.shavedog.activity;

import java.util.HashMap;

import android.app.Activity;
import android.os.Bundle;

public class FriendsActivity extends Activity {
    static HashMap<String, String> friendMap = new HashMap<String, String>();
    @Override
    public void onCreate( Bundle savedInstanceState ) {
        Bundle bundle = getIntent().getExtras();
        String userName = bundle.get( "username" ).toString();
        String address = bundle.get( "address" ).toString();
        friendMap.put( userName, address );
        
    }
    
    
    @Override
    protected void onSaveInstanceState( Bundle outState ) {
        super.onSaveInstanceState( outState );
        
    }
}
