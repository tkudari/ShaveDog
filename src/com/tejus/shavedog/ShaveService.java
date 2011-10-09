package com.tejus.shavedog;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;

import com.tejus.shavedog.activity.FriendsActivity;
import com.tejus.shavedog.activity.ShaveDogActivity;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class ShaveService extends Service {
    private DatagramSocket mBroadcastSocket, mGenericSocket;
    String mUserName;

    WifiManager wifi;
    DhcpInfo dhcp;

    @Override
    public IBinder onBind( Intent intent ) {
        return mBinder;
    }

    private final IBinder mBinder = new ShaveBinder();

    private NotificationManager mNM;
    private int NOTIFICATION = R.string.shave_service_started;

    public class ShaveBinder extends Binder {
        public ShaveService getService() {
            return ShaveService.this;
        }
    }

    @Override
    public void onCreate() {
        Log.d( "XXXX", "service created, biatch" );
        mNM = ( NotificationManager ) getSystemService( NOTIFICATION_SERVICE );
        showNotification();
        setUpNetworkStuff();
        // setup our request broadcast server:
        new RequestListener().execute( mBroadcastSocket );
        // this's our generic listener:
        new RequestListener().execute( mGenericSocket );
    }

    @Override
    public int onStartCommand( Intent intent, int flags, int startId ) {
        Log.d( "XXXX", "ShaveService Received start id " + startId + ": " + intent );
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mNM.cancel( NOTIFICATION );
        Toast.makeText( this, R.string.shave_service_stopped, Toast.LENGTH_SHORT ).show();
    }

    private void showNotification() {
        CharSequence text = getText( R.string.shave_service_started );
        Notification notification = new Notification( R.drawable.iconshave, text, System.currentTimeMillis() );
        PendingIntent contentIntent = PendingIntent.getActivity( this, 0, new Intent( this, ShaveDogActivity.class ), 0 );
        notification.setLatestEventInfo( this, getText( R.string.this_the_string ), text, contentIntent );
        mNM.notify( NOTIFICATION, notification );
    }

    private class RequestListener extends AsyncTask<DatagramSocket, DatagramPacket, Void> {
        @Override
        protected void onProgressUpdate( DatagramPacket... packet ) {
            dealWithReceivedPacket( packet[ 0 ] );
        }

        @Override
        protected Void doInBackground( DatagramSocket... requestSocket ) {
            byte[] buffer = new byte[ Definitions.COMMAND_BUFSIZE ];
            DatagramPacket packet;
            while ( true ) {
                try {
                    packet = new DatagramPacket( buffer, buffer.length );
                    Log.d( "XXXX", "server listening on : " + requestSocket[ 0 ].getLocalPort() );
                    requestSocket[ 0 ].receive( packet );
                    Log.d( "XXXX", "Stuff received by Server = " + new String( packet.getData() ) );
                    publishProgress( packet );
                    Log.d( "XXXX", "done with publishProgress" );

                } catch ( IOException e ) {
                    Log.d( "XXXX", "Server: Receive timed out.." );
                }
            }
        }
    }

    private void dealWithReceivedPacket( DatagramPacket packet ) {
        String words[] = new String[ Definitions.COMMAND_WORD_LENGTH + 1 ];
        int wordCounter = 0;
        String senderAddress;
        String command = new String( packet.getData() );
        Log.d( "XXXX", "command here = " + command );

        StringTokenizer strTok = new StringTokenizer( command, Definitions.COMMAND_DELIM );
        while ( strTok.hasMoreTokens() ) {
            words[ wordCounter ] = strTok.nextToken();
            Log.d("XXXX", "word here = " + words[wordCounter]);
            ++wordCounter;
        }
        for ( String word : words )
            Log.d( "XXXX", "word = " + word );

        senderAddress = words[ 2 ];

        // this's a broadcast:
        if ( words[ 0 ].equals( Definitions.QUERY_LIST ) ) {
            // check that this isn't our own request, eh:
            Log.d( "XXXX", "cleanedup = " + cleanThisStringUp( words[ 2 ] ) );
            if ( cleanThisStringUp( words[ 2 ] ).equals( cleanThisStringUp( Definitions.IP_ADDRESS_INETADDRESS.toString() ) ) ) {
                Log.d( "XXXX", "yep, it's ours" );
                // TODO:remove this line now here only for testing!!
                // newRequestReceived( new String[] {
                // words[ 1 ],
                // cleanThisStringUp( words[ 2 ] )
                // } );
            } else {
                newRequestReceived( new String[] {
                    words[ 1 ],
                    cleanThisStringUp( words[ 2 ] )
                } );
            }
        }

        if ( words[ 0 ].equals( Definitions.REPLY_ACCEPTED ) ) {
            String userName = words[ 1 ];
            String address = cleanThisStringUp( words[ 2 ] );
            Log.d( "XXXX", "dealing with pkt: " + userName + ", " + address );

            Intent intent = new Intent();
            Log.d( "XXXX", "friend ack received.." );
            intent.putExtra( "user_name", userName );
            intent.putExtra( "address", address );
            intent.setClass( this, FriendsActivity.class );
            intent.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK );
            startActivity( intent );
        }

        // request file listing:
        if ( words[ 0 ].equals( Definitions.REQUEST_LISTING ) ) {
            ArrayList<String> cardListing = getSdCardListing();
            Log.d( "XXXX", "cardListing received = " + cardListing.toString() );
            sendMessage( senderAddress, Definitions.LISTING_REPLY + ":" + cardListing );
        }
        
        if(words[0].equals( Definitions.LISTING_REPLY )) {
            Log.d("XXXX", "eg");
        }

    }

    private ArrayList<String> getSdCardListing() {
        try {
            return new SdCardLister().execute().get();
        } catch ( Exception e ) {
            e.printStackTrace();
            return null;
        }

    }

    private class SdCardLister extends AsyncTask<Void, Void, ArrayList<String>> {

        @Override
        protected ArrayList<String> doInBackground( Void... params ) {
            ArrayList<String> list = new ArrayList<String>();
            File file[] = Environment.getExternalStorageDirectory().listFiles();
            for ( File iFile : file ) {
                list.add( iFile.getName() );
            }
            return list;

        }

    }

    String cleanThisStringUp( String string ) {
        return string.replace( "\\?", "" ).replace( "*", "" ).replace( "//", "" );
    }

    void newRequestReceived( String[] requestString ) {
        Intent intent = new Intent( Definitions.INTENT_QUERY_LIST );
        Log.d( "XXXX", "sending broadcast here:" );
        intent.putExtra( "user_name", requestString[ 0 ] );
        intent.putExtra( "address", requestString[ 1 ] );
        this.sendBroadcast( intent );
    }

    void setUpNetworkStuff() {
        initNetworkStuff();
        try {
            // temp. sockets used only here, that's why the ridiculous names:
            DatagramSocket socket1 = new DatagramSocket( Definitions.BROADCAST_SERVER_PORT );
            socket1.setBroadcast( true );
            socket1.setSoTimeout( Definitions.SOCKET_TIMEOUT );
            mBroadcastSocket = socket1;

            DatagramSocket socket2 = new DatagramSocket( Definitions.GENERIC_SERVER_PORT );
            socket2.setBroadcast( true );
            socket2.setSoTimeout( Definitions.SOCKET_TIMEOUT );
            mGenericSocket = socket2;

        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }

    public void populateList() {
        try {
            // send broadcast:
            mUserName = getOurUserName();
            if ( mUserName.equals( Definitions.defaultUserName ) ) {
                String toastText = getResources().getString( R.string.default_username_used ) + Definitions.defaultUserName;
                Toast toast = Toast.makeText( this, toastText, Toast.LENGTH_SHORT );
                toast.show();
            }
            String searchString = Definitions.QUERY_LIST + ":" + mUserName + ":" + getOurIp().toString().replace( "/", "" ) + Definitions.END_DELIM;
            Log.d( "XXXX", "searchString = " + searchString );
            DatagramPacket sendPacket = new DatagramPacket( searchString.getBytes(), searchString.length(), getBroadcastAddress(),
                    Definitions.BROADCAST_SERVER_PORT );
            Log.d( "XXXX", "gonna send broadcast for : " + Definitions.QUERY_LIST );
            Log.d( "XXXX", "broadcast packet : " + new String( sendPacket.getData() ) );

            mBroadcastSocket.send( sendPacket );
        } catch ( SocketException e ) {
            if ( e.getMessage().equals( "Network is unreachable" ) ) {
                Toast.makeText( this, R.string.no_wifi, Toast.LENGTH_SHORT ).show();
            }
            e.printStackTrace();
        } catch ( Exception e ) {
            Log.d( "XXXX", "populateList error" );
            e.printStackTrace();
        }
    }

    private String getOurUserName() {
        SharedPreferences settings = getSharedPreferences( Definitions.credsPrefFile, Context.MODE_PRIVATE );
        return settings.getString( Definitions.prefUserName, Definitions.defaultUserName );
    }

    private InetAddress getOurIp() {
        Definitions.IP_ADDRESS_INT = dhcp.ipAddress;
        int ourIp = Definitions.IP_ADDRESS_INT;
        byte[] quads = new byte[ 4 ];
        try {
            for ( int k = 0; k < 4; k++ ) {
                quads[ k ] = ( byte ) ( ( ourIp >> k * 8 ) & 0xFF );
            }
            Log.d( "XXXX", "our IP address here = " + InetAddress.getByAddress( quads ).getHostAddress() );
            Definitions.IP_ADDRESS_INETADDRESS = InetAddress.getByAddress( quads );
            return InetAddress.getByAddress( quads );
        } catch ( UnknownHostException e ) {
            e.printStackTrace();
        }
        return null;

    }

    void initNetworkStuff() {
        wifi = ( WifiManager ) this.getSystemService( Context.WIFI_SERVICE );
        if ( !wifi.isWifiEnabled() ) {
            Toast.makeText( this, R.string.no_wifi, Toast.LENGTH_LONG ).show();
        } else {
            Log.d( "XXXX", "wifi exists?" );
        }
        dhcp = wifi.getDhcpInfo();
        getOurIp();
    }

    InetAddress getBroadcastAddress() throws IOException {

        int broadcast = ( dhcp.ipAddress & dhcp.netmask ) | ~dhcp.netmask;
        byte[] quads = new byte[ 4 ];
        for ( int k = 0; k < 4; k++ )
            quads[ k ] = ( byte ) ( ( broadcast >> k * 8 ) & 0xFF );
        Log.d( "XXXX", "broadcast address here = " + InetAddress.getByAddress( quads ).getHostAddress() );
        return InetAddress.getByAddress( quads );
    }

    public void sendMessage( String address, String message ) {
        String sendMessage = message + ":" + getOurUserName() + ":" + getOurIp().toString().replace( "/", "" ) + Definitions.END_DELIM;
        byte[] testArr = sendMessage.getBytes();
        
        Log.d( "XXXX", "sendMessage = " + sendMessage + ", len = " + sendMessage.length() );
        Log.d( "XXXX", "testarr = " + testArr.toString() + ", len = " + testArr.length );
        try {
            Log.d( "XXXX", "destination address = " + InetAddress.getByName( address ) );
            DatagramPacket sendPacket = new DatagramPacket( sendMessage.getBytes(), sendMessage.getBytes().length, InetAddress.getByName( address ),
                    Definitions.GENERIC_SERVER_PORT );
            Log.d( "XXXX", "gonna send out the message:" );
            mGenericSocket.send( sendPacket );
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }

    public void tcpTransact( String address, String command ) {

    }

}
