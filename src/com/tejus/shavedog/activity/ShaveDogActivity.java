package com.tejus.shavedog.activity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import com.tejus.shavedog.Definitions;
import com.tejus.shavedog.R;

import com.tejus.shavedog.ShaveService;

import com.tejus.shavedog.ShaveService.ShaveBinder;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.media.ExifInterface;
import android.net.DhcpInfo;
import android.net.ParseException;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore.Video;
import android.telephony.gsm.SmsManager;
import android.text.format.Time;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

public class ShaveDogActivity extends Activity {
    private static final String[] PROJECTION;
    static String algorithm = "SHA1";

    private StringBuilder sBuilder;
    private Context mContext;
    private BroadcastReceiver mShaveReceiver = new ServiceIntentReceiver();

    static int _16MB_ = 16 * 1000000;
    static {
        PROJECTION = new String[] {
            MediaColumns._ID,
            MediaColumns.DATA,
            MediaColumns.DATE_ADDED,
            MediaColumns.DATE_MODIFIED,
            Images.ImageColumns.DATE_TAKEN,
            MediaColumns.DISPLAY_NAME,
            MediaColumns.MIME_TYPE,
            MediaColumns.SIZE
        };
    }
    TextView details, welcome;
    WifiManager wifi;
    DhcpInfo dhcp;
    ShaveService mShaveService;
    ServiceConnection mConnection;

    @Override
    public void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        Log.d( "XXXX", "oncreate called too" );
        setContentView( R.layout.main );
        mContext = this;
        details = ( TextView ) findViewById( R.id.details );
        welcome = ( TextView ) findViewById( R.id.welcome );
        sBuilder = new StringBuilder();
        initShaveServiceStuff();
        initReceiver();
    }

    @Override
    protected void onResume() {
        Log.d( "XXXX", "onresume called.." );
        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu( Menu menu ) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate( R.menu.shave_menu, menu );
        return true;
    }

    @Override
    public boolean onOptionsItemSelected( MenuItem item ) {
        switch ( item.getItemId() ) {

            case R.id.about:
                Intent intent = new Intent();
                intent.setClass( mContext, AboutActivity.class );
                startActivity( intent );
                return true;

            case R.id.quit:
                this.quit();
                return true;

            case R.id.populate_list:
                this.populateList();
                return true;

            case R.id.set_creds:
                this.setCredentials();
                return true;
                
            case R.id.friends_list:
                this.gotoFriendsList();
                return true;

              

            default:
                return super.onOptionsItemSelected( item );
        }
    }

    

    @SuppressWarnings( "deprecation" )
    void send( String address, String body ) {
        SmsManager smsManager = SmsManager.getDefault();

        PendingIntent sentPI = PendingIntent.getBroadcast( this, 0, new Intent( "SENT" ), 0 );
        ArrayList<String> messageArray = smsManager.divideMessage( body );
        ArrayList<PendingIntent> sentIntents = new ArrayList<PendingIntent>();
        for ( int i = 0; i < messageArray.size(); i++ ) {
            sentIntents.add( sentPI );
        }
        Log.d( "XXXX", "gonna sendMultipartTextMessage..." );
        smsManager.sendMultipartTextMessage( address, null, messageArray, sentIntents, null );

    }

    private void gotoFriendsList() {
        Intent intent = new Intent();
        intent.setClass( this, FriendsActivity.class );

        startActivity( intent );
    }

    private void populateList() {
        mShaveService.populateList();

    }

    InetAddress getBroadcastAddress() throws IOException {

        int broadcast = ( dhcp.ipAddress & dhcp.netmask ) | ~dhcp.netmask;
        byte[] quads = new byte[ 4 ];
        for ( int k = 0; k < 4; k++ )
            quads[ k ] = ( byte ) ( ( broadcast >> k * 8 ) & 0xFF );
        Log.d( "XXXX", "broadcast address here = " + InetAddress.getByAddress( quads ).getHostAddress() );
        return InetAddress.getByAddress( quads );
    }

    

    void quit() {
        Log.d( "XXXX", "quit(): Killing ourself.." );
        android.os.Process.killProcess( android.os.Process.myPid() );
    }

    private void setCredentials() {
        Intent intent = new Intent();
        intent.setClass( mContext, CredentialsActivity.class );
        startActivity( intent );
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
                // Toast.makeText( mContext, R.string.shave_service_connected,
                // Toast.LENGTH_SHORT ).show();
            }
        };

        doBindService();

        startService( new Intent().setClass( mContext, ShaveService.class ) );
    }

    void doBindService() {
        bindService( new Intent( this, ShaveService.class ), mConnection, Context.BIND_AUTO_CREATE );
    }

    public class ServiceIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive( Context context, Intent intent ) {
            Log.d( "XXXX", "broadcast intent received, username = " + intent.getStringExtra( "user_name" ) + ", address = " + intent.getStringExtra( "address" ) );
            showDialog( "You have a new Request!", intent.getStringExtra( "user_name" ), intent.getStringExtra( "address" ) );
        }
    }

    void initReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction( Definitions.INTENT_QUERY_LIST );
        registerReceiver( mShaveReceiver, filter );
    }

    void showDialog( String message, final String userName, final String address ) {
        // TODO: cleanup strings, set them from resources!
        new AlertDialog.Builder( mContext ).setIcon( R.drawable.iconshave )
                .setTitle( message )
                .setMessage( userName + " from " + address + " wants to be friends! \n Accept?" )
                .setPositiveButton( "Yes", new DialogInterface.OnClickListener() {
                    public void onClick( DialogInterface dialog, int whichButton ) {
                        // reply to requester:
                        mShaveService.sendMessage( address, Definitions.REPLY_ACCEPTED );

                        // add to & go to friends list:
                        Intent intent = new Intent();
                        Log.d( "XXXX", "sending 'friend accepted here':" );
                        intent.putExtra( "user_name", userName );
                        intent.putExtra( "address", address );
                        intent.setClass( mContext, FriendsActivity.class );
                        startActivity( intent );
                    }
                } )
                .setNegativeButton( "No", new DialogInterface.OnClickListener() {
                    public void onClick( DialogInterface dialog, int whichButton ) {
                        Log.d( "XXXX", "no" );
                        // reply back, saying no.
                    }
                } )
                .create()
                .show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver( mShaveReceiver );
    }

    void testApi2() {
        Uri sms = Uri.parse( "content://sms/" );
        Cursor c = this.getContentResolver().query( sms, null, null, null, null );
        Log.d( "XXXX", "gonna start dumping the cursor" );
        DatabaseUtils.dumpCursor( c );
    }
}

// test code:
// try {
// ExifInterface exif = new ExifInterface(
// mediaCursor.getString( 1 ) );
// String exifDateTime = exif.getAttribute(
// ExifInterface.TAG_DATETIME );
// Log.d( "XXXX", "exifDateTime = " + exifDateTime );
// DateFormat format = new
// SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
// try {
// Date date = format.parse( exifDateTime );
// } catch (java.text.ParseException e) {
// e.printStackTrace();
// }
// /* if ( exifDateTime != null ) {
// Log.d( "XXXX", "parsed date = " + Date.parse(
// exifDateTime ) );
// } else {
// Log.d( "XXXX", "exifDateTime is null" );
// } */
// } catch ( IOException e ) {
// Log.d( "XXXX",
// "MediaAccessor2.readAtCursor: Error trying to create ExifInterface.."
// );
// e.printStackTrace();
// }

/*
 * try { ExifInterface exif = new ExifInterface( filepath ); String exifDateTime
 * = exif.getAttribute( ExifInterface.TAG_DATETIME ); logger.info(
 * "XXXX - exifDateTime = " + exifDateTime ); } catch ( IOException e ) {
 * logger.error(
 * "MediaAccessor2.readAtCursor: Error trying to create ExifInterface.." );
 * e.printStackTrace(); }
 */

// return Base64.encodeToString(md.digest(), Base64.NO_PADDING | Base64.NO_WRAP
// | Base64.URL_SAFE);

