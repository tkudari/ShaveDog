package com.tejus.shavedog.activity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;

import com.tejus.shavedog.Definitions;
import com.tejus.shavedog.R;
import com.tejus.shavedog.ShaveService;

import android.app.Activity;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.IBinder;
import android.text.SpannableString;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class FileList extends ListActivity {

    Context mContext;
    ArrayList<String> mFiles = new ArrayList<String>();
    HashMap<String, String> mFileLengthMap = new HashMap<String, String>();
    ListView lv;
    Button backButton;
    private ShaveService mShaveService;
    private ServiceConnection mConnection;
    String fromAddress;
    String mCurrentDirectory;

    @Override
    public void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.file_list );
        backButton = ( Button ) findViewById( R.id.back );
        
        mContext = this;
        initShaveServiceStuff();
        Bundle bundle = getIntent().getExtras();
        String files = ( String ) bundle.get( "file_list" );
        fromAddress = ( String ) bundle.get( "from_address" );
        Log.d( "XXXX", "filelist - alist = " + files );
        processListing( files );

        MySimpleArrayAdapter adapter = new MySimpleArrayAdapter( this, mFiles );
        setListAdapter( adapter );
        backButton.setOnClickListener( new View.OnClickListener() {

            @Override
            public void onClick( View v ) {
                mShaveService.sendMessage( fromAddress, Definitions.REQUEST_DIRECTORY + ":" + cleanThisStringUp( mShaveService.getPreviousDir() ) );
            }
        } );

    }

    protected boolean isNotADirectory( String filePath ) {
        return !( filePath.trim().startsWith( "#" ) );
    }

    protected String stripLengthOff( String filePath ) {
        return filePath.substring( 0, filePath.lastIndexOf( "^" ) );
    }

    protected String cleanThisStringUp( String string ) {
        return string.replace( "#", "" ).replace( "^", "" );
    }

    void initShaveServiceStuff() {
        mConnection = new ServiceConnection() {
            @Override
            public void onServiceDisconnected( ComponentName className ) {
                mShaveService = null;
                Toast.makeText( mContext, R.string.shave_service_disconnected, Toast.LENGTH_SHORT ).show();
            }

            @Override
            public void onServiceConnected( ComponentName name, IBinder service ) {
                mShaveService = ( ( ShaveService.ShaveBinder ) service ).getService();
            }
        };

        bindService( new Intent( this, ShaveService.class ), mConnection, Context.BIND_AUTO_CREATE );
    }

    // sets the current directory & populates mFiles & mFileLengthMap
    private void processListing( String string ) {
        int index = 0;
        String word;
        String fileList;
        String[] currentDirFinder = new String[ 2 ];
        // find the current directory:
        StringTokenizer dirTok = new StringTokenizer( string, "$" );
        for ( int i = 0; ( dirTok.hasMoreTokens() && i <= 1 ); i++ ) {
            currentDirFinder[ i ] = dirTok.nextToken();
        }
        fileList = currentDirFinder[ 0 ];
        mCurrentDirectory = currentDirFinder[ 1 ];

        // populate mFiles:
        StringTokenizer strTok = new StringTokenizer( fileList, "," );
        while ( strTok.hasMoreTokens() ) {
            word = strTok.nextToken();

            // populate file sizes (mFileLengthMap):
            StringTokenizer lengthTok = new StringTokenizer( word, "^" );
            String[] fileLengthFinder = new String[ 2 ];
            for ( int i = 0; lengthTok.hasMoreTokens(); i++ ) {
                fileLengthFinder[ i ] = lengthTok.nextToken();
            }
            mFiles.add( word.replace( " ", "" ).replace( "[", "" ).replace( "]", "" ) );
            Log.d( "XXXX", "file added = " + mFiles.get( index ) );
            if ( fileLengthFinder[ 1 ] != null ) {
                mFileLengthMap.put( mFiles.get( index ), fileLengthFinder[ 1 ] );
            }
            ++index;
        }

        Log.d( "XXXX", "mFileLengthMap = " + mFileLengthMap.toString() );
        Log.d( "XXXX", "mCurrentDirectory = " + mCurrentDirectory );

    }

    @Override
    public boolean onCreateOptionsMenu( Menu menu ) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate( R.menu.file_list_menu, menu );
        return true;
    }

    @Override
    public boolean onOptionsItemSelected( MenuItem item ) {
        // Handle item selection
        switch ( item.getItemId() ) {
            case R.id.refresh_list:
                mShaveService.sendMessage( fromAddress, Definitions.REQUEST_LISTING );
                return true;

            default:
                return super.onOptionsItemSelected( item );
        }

    }

    @Override
    protected void onListItemClick( ListView l, View v, int position, long id ) {
        Log.d( "XXXX", "item clicked = " + mFiles.get( position ) );

        String filePath = mFiles.get( position ); // if it's a file, fire up the
        if ( isNotADirectory( filePath ) ) {
            filePath = stripLengthOff( mFiles.get( position ) );
            mShaveService.downloadFile( filePath, Long.parseLong( mFileLengthMap.get( mFiles.get( position ) ) ), mContext );
            mShaveService.sendMessage( fromAddress,
                    Definitions.REQUEST_FILE + ":" + cleanThisStringUp( filePath ) + ":" + mFileLengthMap.get( mFiles.get( position ) ) );
        } else { // TODO: deal with dir dloads
            mShaveService.setPreviousDir( mCurrentDirectory );
            mShaveService.sendMessage( fromAddress, Definitions.REQUEST_DIRECTORY + ":" + cleanThisStringUp( filePath ) );
        }
    }

    public class MySimpleArrayAdapter extends ArrayAdapter<String> {
        private final Activity context;
        private final ArrayList<String> fileList;

        public MySimpleArrayAdapter( Activity context, ArrayList<String> fileList ) {
            super( context, R.layout.file_row, fileList );
            this.context = context;
            this.fileList = fileList;
        }

        @Override
        public View getView( int position, View convertView, ViewGroup parent ) {
            LayoutInflater inflater = context.getLayoutInflater();
            View rowView = inflater.inflate( R.layout.file_row, null, true );
            TextView textView = ( TextView ) rowView.findViewById( R.id.item_name );
            if ( fileList.get( position ).startsWith( "#" ) ) {
                Log.d( "XXXX", "setting bold for position = " + position );
                textView.setTypeface( null, Typeface.BOLD );
            } else {
                Log.d( "XXXX", "setting italics for position = " + position );
                textView.setTypeface( null, Typeface.ITALIC );
            }
            textView.setText( mShaveService.getFileNameTrivial( fileList.get( position ) ) );

            return rowView;
        }
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

