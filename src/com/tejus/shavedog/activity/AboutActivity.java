package com.tejus.shavedog.activity;

import com.tejus.shavedog.R;
import com.tejus.shavedog.R.id;
import com.tejus.shavedog.R.layout;
import com.tejus.shavedog.R.menu;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

public class AboutActivity extends Activity {

    @Override
    public void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.about );
    }

    @Override
    public boolean onCreateOptionsMenu( Menu menu ) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate( R.menu.about_menu, menu );
        return true;
    }

    @Override
    public boolean onOptionsItemSelected( MenuItem item ) {
        // Handle item selection
        switch ( item.getItemId() ) {
            case R.id.goto_shave_activity:
                Intent intent = new Intent();
                intent.setClass( this, ShaveDogActivity.class );
                startActivity( intent );
                return true;

            default:
                return super.onOptionsItemSelected( item );
        }
    }
    
    @Override
    public void onBackPressed() {
        Intent intent = new Intent();
        intent.setClass( this, ShaveDogActivity.class );
        startActivity( intent );
        super.onBackPressed();
    }

}
