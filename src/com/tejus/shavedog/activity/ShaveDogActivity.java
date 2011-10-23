package com.tejus.shavedog.activity;

import java.io.IOException;
import java.net.InetAddress;
import com.tejus.shavedog.Definitions;
import com.tejus.shavedog.R;

import com.tejus.shavedog.ShaveService;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;

import android.net.DhcpInfo;

import android.net.wifi.WifiManager;
import android.os.Bundle;

import android.os.IBinder;

import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

public class ShaveDogActivity extends Activity {

    private Context mContext;
    private BroadcastReceiver mShaveReceiver = new ServiceIntentReceiver();

    TextView details, welcome;
    WifiManager wifi;
    DhcpInfo dhcp;
    ShaveService mShaveService;
    ServiceConnection mConnection;

    @Override
    public void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.main );
        mContext = this;
        details = ( TextView ) findViewById( R.id.details );
        welcome = ( TextView ) findViewById( R.id.welcome );
        initShaveServiceStuff();
        initReceiver();
    }

    @Override
    protected void onResume() {
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

    private void gotoFriendsList() {
        Intent intent = new Intent();
        intent.setClass( this, FriendsActivity.class );

        startActivity( intent );
    }

    private void populateList() {
        mShaveService.testPopulateList();

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

}
