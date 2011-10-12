package com.tejus.shavedog.activity;

import java.util.ArrayList;
import java.util.StringTokenizer;

import com.tejus.shavedog.R;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

public class FileList extends Activity {

    Context mContext;
    ArrayList<String> mFiles = new ArrayList<String>();
    ListView lv;
    Button backButton;
    
    
    @Override
    public void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.file_list );
        mContext = this;

        Bundle bundle = getIntent().getExtras();
        String files = ( String ) bundle.get( "file_list" );
        Log.d( "XXXX", "filelist - alist = " + files );
        processListing( files );

        lv = ( ListView ) findViewById( R.id.files_listview );        
        lv.setAdapter( new ArrayAdapter<String>( this, android.R.layout.simple_list_item_1, mFiles ) );
        backButton = ( Button ) findViewById( R.id.back );
        backButton.setOnClickListener( new View.OnClickListener() {            
            @Override
            public void onClick( View v ) {
                Intent intent = new Intent();
                intent.setClass( mContext, ShaveDogActivity.class );
                startActivity( intent );
            }
        });
        

    }

     
     
    private void processListing( String string ) {
        int index = 0;
        String word;
        StringTokenizer strTok = new StringTokenizer( string, "," );
        while ( strTok.hasMoreTokens() ) {
            word = strTok.nextToken();
            mFiles.add( word.replace( " ", "" ).replace( "[", "" ).replace( "]", "" ) );
            Log.d( "XXXX", "file added = " + mFiles.get( index ) );
            ++index;
        }

    }
    
    

    // a beauty, aint' it?:
    // private void retrieveFileMap( String files ) {
    // String word;
    // StringTokenizer strTok = new StringTokenizer( files, "," );
    // while ( strTok.hasMoreTokens() ) {
    // word = strTok.nextToken();
    // StringTokenizer mapTok = new StringTokenizer( word, "=" );
    // String keyValue[] = new String[ 2 ];
    // for ( int i = 0; mapTok.hasMoreTokens(); i++ ) {
    // keyValue[ i ] = mapTok.nextToken();
    // Log.d( "XXXX", "keyValue [" + i + "] =" + keyValue[ i ] );
    // if ( i > 0 ) {
    // mFiles.put( keyValue[ i - 1 ], keyValue[ i ] );
    // }
    // }
    // }
    // Log.d( "XXXX", "after proc, mfiles = " + mFiles.toString() );
    // }

}
