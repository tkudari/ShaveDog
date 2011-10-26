package com.tejus.shavedog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;

import com.tejus.shavedog.activity.FileList;
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
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

public class ShaveService extends Service {
    private DatagramSocket mBroadcastSocket, mGenericSocket, mTestSocket;
    String mUserName;
    int mPreviousProgress = 0;

    // FileList values:

    String mCurrentDir = null;
    String mPreviousDir = null;
    TestSearchMethod mFinder;

    private static String DEFAULT_DOWNLOAD_LOC = null;
    // This is the top-most directory you can visit. To modify this, change
    // Definitions.HOME_DIRECTORY.
    public static String mHomeDirectory = null;

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
        initDirectoryStuff();
        // setup our request broadcast server:
        new RequestListener().execute( mBroadcastSocket );
        // this's our generic listener:
        new RequestListener().execute( mGenericSocket );

        new TestRequestListener().execute( mTestSocket );
    }

    private void initDirectoryStuff() {
        // doing this coz of the '/':
        if ( Definitions.HOME_DIRECTORY.length() > 0 ) {
            mHomeDirectory = Environment.getExternalStorageDirectory().toString() + "/" + Definitions.HOME_DIRECTORY;
        } else {
            mHomeDirectory = Environment.getExternalStorageDirectory().toString();
        }
        SharedPreferences settings = getSharedPreferences( Definitions.credsPrefFile, Context.MODE_PRIVATE );
        SharedPreferences.Editor editor = settings.edit();
        editor.putString( Definitions.prefHomeDirectory, mHomeDirectory );
        editor.commit();
        DEFAULT_DOWNLOAD_LOC = mHomeDirectory + "/ShaveDog";
        new File( DEFAULT_DOWNLOAD_LOC ).mkdirs();

        Log.d( "XXXX", "ShaveService.initDirectoryStuff - set mHomeDirectory = " + mHomeDirectory );
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

    // private void showProgressNotification( String filePath, int progress ) {
    // RemoteViews contentView = new RemoteViews( getPackageName(),
    // R.layout.custom_notification_layout );
    // CharSequence text = getText( R.string.downloading ) + " " +
    // getFileNameTrivial( filePath );
    // Notification notification = new Notification( R.drawable.iconshave, null,
    // System.currentTimeMillis() );
    // notification.flags = Notification.FLAG_ONGOING_EVENT;
    //
    // contentView.setProgressBar( R.id.download_progress, 100, progress, false
    // );
    // notification.contentView = contentView;
    // PendingIntent contentIntent = PendingIntent.getActivity( this, 0, new
    // Intent( this, ShaveDogActivity.class ), 0 );
    //
    // notification.setLatestEventInfo( this, text, null, contentIntent );
    // mNM.notify( NOTIFICATION, notification );
    //
    // }

    private void showProgressNotification( Context context, String filePath, int progress ) {
        Intent notificationIntent = new Intent( this, ShaveDogActivity.class );
        PendingIntent contentIntent = PendingIntent.getActivity( this, 0, notificationIntent, 0 );

        Notification notification = new Notification( R.drawable.iconshave, "bleh", System.currentTimeMillis() );
        notification.setLatestEventInfo( context, null, null, contentIntent );
        notification.flags = Notification.FLAG_ONGOING_EVENT;

        RemoteViews contentView = new RemoteViews( getPackageName(), R.layout.custom_notification_layout );

        contentView.setImageViewResource( R.id.image, R.drawable.iconshave );
        contentView.setTextViewText( R.id.title, getResources().getString( R.string.downloading ) );
        contentView.setTextViewText( R.id.text, getFileNameTrivial( filePath ) );
        contentView.setTextViewText( R.id.progress, progress + " %" );
        if ( progress == -1 ) {
            contentView.setViewVisibility( R.id.download_progress, View.GONE );
            contentView.setTextViewText( R.id.title, getResources().getString( R.string.download_interrupted ) );
            mPreviousProgress = 0;
        } else if ( progress == 100 ) {
            Log.d( "XXXX", "gonna dispel" );
            contentView.setViewVisibility( R.id.download_progress, View.GONE );
            contentView.setTextViewText( R.id.title, getResources().getString( R.string.done_downloading ) );
            mPreviousProgress = 0;
        } else {
            // smoothen the progressbar:
            // progress = ( progress >= 0 && progress <= 10 ) ? 0 : ( ( progress
            // > 10 && progress <= 25 ) ? 25 : ( progress > 25 && progress <= 50
            // ) ? 50
            // : ( progress > 50 && progress <= 75 ) ? 75 : 90 );
            if ( mPreviousProgress != progress ) {
                mPreviousProgress = progress;
                Log.d( "XXXX", "ShaveService.showProgressNotification(): setting download progress = " + progress );
                contentView.setProgressBar( R.id.download_progress, 100, progress, false );
            }

        }

        notification.contentView = contentView;
        notification.contentIntent = contentIntent;
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

    private class TestRequestListener extends AsyncTask<DatagramSocket, DatagramPacket, Void> {
        @Override
        protected void onProgressUpdate( DatagramPacket... packet ) {
            Log.d( "XXXX", "test packet received from = " + packet[ 0 ].getAddress().getHostAddress() );
            processTestPacket( packet[ 0 ] );
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
                    Log.d( "XXXX", "Stuff received by test Server = " + new String( packet.getData() ) );
                    dealWithReceivedPacket( packet );
                    publishProgress( packet );
                    Log.d( "XXXX", "done with publishProgress" );

                } catch ( IOException e ) {
                    Log.d( "XXXX", "Server: Receive timed out.." );
                }
            }
        }
    }

    void processTestPacket( DatagramPacket packet ) {
        String data = packet.getData().toString();
        Log.d( "XXXX", "processTestPacket received = " + data );
    }

    private void dealWithReceivedPacket( DatagramPacket packet ) {
        String words[] = new String[ Definitions.COMMAND_WORD_LENGTH + 1 ];
        int wordCounter = 0;
        String senderAddress;
        String command = new String( packet.getData() );
        Log.d( "XXXX", "command here = " + command );

        StringTokenizer strTok = new StringTokenizer( command, Definitions.COMMAND_DELIM );
        while ( strTok.hasMoreTokens() && wordCounter <= Definitions.COMMAND_WORD_LENGTH ) {
            words[ wordCounter ] = strTok.nextToken();
            Log.d( "XXXX", "word here = " + words[ wordCounter ] );
            ++wordCounter;
        }
        for ( String word : words )
            Log.d( "XXXX", "word = " + word );

        senderAddress = words[ 2 ];

        if ( words[ 0 ].equals( Definitions.DISCOVER ) ) {
            Log.d( "XXXX", "DISCOVER packet received...." );
            Log.d( "XXXX", "cleanedup = " + cleanThisStringUp( words[ 2 ] ) );
            if ( cleanThisStringUp( words[ 2 ] ).equals( cleanThisStringUp( Definitions.IP_ADDRESS_INETADDRESS.toString() ) ) ) {
                Log.d( "XXXX", "yep, it's ours" );
            } else {
                newRequestReceived( new String[] {
                    words[ 1 ],
                    cleanThisStringUp( words[ 2 ] )
                } );
            }
        }

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
                    words[ 1 ], //username
                    cleanThisStringUp( words[ 2 ] ) //address
                } );
            }
        }

        if ( words[ 0 ].equals( Definitions.REPLY_ACCEPTED ) ) {
            // stop the finder:
            if ( mFinder != null ) {

                Log.d( "XXXX", "dealWithReceivedPacket(): Cancelling  mFinder, since a friend replied = " + mFinder.cancel( true ) );
            }
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
            ArrayList<String> cardListing = getSdCardListing( "" );
            Log.d( "XXXX", "cardListing received = " + cardListing.toString() );
            sendMessage( senderAddress, Definitions.LISTING_REPLY + ":" + cardListing + "$" + mHomeDirectory );
        }

        // listing received (this is for REQUEST_LISTING & REQUEST_DIRECTORY):
        if ( words[ 0 ].equals( Definitions.LISTING_REPLY ) ) {
            Log.d( "XXXX", "eg" );
            startActivity( ( new Intent().setClass( this, FileList.class ).setFlags( Intent.FLAG_ACTIVITY_NEW_TASK ).putExtra( "file_list", words[ 1 ] ).putExtra(
                    "from_address", words[ 3 ] ) ) );
        }

        if ( words[ 0 ].equals( Definitions.REQUEST_FILE ) ) {
            handleDownloadRequest( words[ 1 ], words[ 2 ], words[ 4 ] );
        }

        if ( words[ 0 ].equals( Definitions.REQUEST_DIRECTORY ) ) {
            Log.d( "XXXX", "dir dload request for = " + words[ 1 ] );
            String dirRequested = words[ 1 ].substring( words[ 1 ].lastIndexOf( mHomeDirectory ) + 1 );
            Log.d( "XXXX", "dirReq = " + dirRequested );

            ArrayList<String> cardListing = getSdCardListing( dirRequested );
            Log.d( "XXXX", "dir listing for: " + words[ 1 ] + " = " + cardListing.toString() );
            sendMessage( words[ 3 ], Definitions.LISTING_REPLY + ":" + cardListing + "$" + words[ 1 ] );

        }

    }

    private void handleDownloadRequest( String filePath, String fileLength, String destinationAddress ) {
        Log.d( "XXXX", "dload req received for = " + filePath + ", length = " + fileLength );
        uploadFile( destinationAddress, filePath, Long.parseLong( fileLength ), getApplicationContext() );
    }

    private ArrayList<String> getSdCardListing( String directoryName ) {
        try {
            return new SdCardLister( directoryName ).execute().get();
        } catch ( Exception e ) {
            e.printStackTrace();
            return null;
        }

    }

    private class SdCardLister extends AsyncTask<Void, Void, ArrayList<String>> {
        String directoryName;

        public SdCardLister( String directoryName ) {
            this.directoryName = directoryName;
        }

        @Override
        protected ArrayList<String> doInBackground( Void... params ) {
            ArrayList<String> files = new ArrayList<String>();
            File file[] = null;
            if ( directoryName.length() > 0 ) {
                Log.d( "XXXX", "SdCardLister: searching directory - " + directoryName );
                file = new File( directoryName ).listFiles();
            } else {
                Log.d( "XXXX", "SdCardLister: searching directory - " + mHomeDirectory );
                file = new File( mHomeDirectory ).listFiles();
            }
            // if the folder isn't empty:
            if ( file != null ) {
                for ( File iFile : file ) {
                    if ( iFile.isDirectory() ) {
                        files.add( "#" + iFile.getAbsolutePath() );
                    } else {
                        files.add( iFile.getAbsolutePath() + "^" + iFile.length() );
                    }
                }
            } else {
                // the folder's empty
                files.add( "" );
            }
            return files;
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

            DatagramSocket socket3 = new DatagramSocket( Definitions.TEST_SERVER_PORT );
            socket3.setBroadcast( true );
            socket3.setSoTimeout( Definitions.SOCKET_TIMEOUT );
            mTestSocket = socket3;

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
            DatagramPacket sendPacket = new DatagramPacket( searchString.getBytes(), searchString.length(), InetAddress.getByName( "255.255.255.255" ),
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

    public void testPopulateList() {
        mFinder = new TestSearchMethod();
        mFinder.execute();
    }

    private class TestSearchMethod extends AsyncTask<Void, DatagramPacket, Void> {
        @Override
        protected void onProgressUpdate( DatagramPacket... packet ) {
            dealWithReceivedPacket( packet[ 0 ] );
        }

        @Override
        protected Void doInBackground( Void... sad ) {
            String ourIp = getOurIp().getHostAddress();
            String subnet = ( String ) ourIp.subSequence( 0, ourIp.lastIndexOf( "." ) );
            String parentSubnet = ( String ) ourIp.subSequence( 0, subnet.lastIndexOf( "." ) );
            // search our subnet first:
            for ( int i = 0; i < 256; i++ ) {
                try {
                    if ( isCancelled() ) {
                        break;
                    }
                    String destinationAddress = subnet + "." + String.valueOf( i );
                    String searchString = Definitions.DISCOVER + ":" + getOurUserName() + ":" + getOurIp().toString().replace( "/", "" )
                            + Definitions.END_DELIM;

                    Log.d( "XXXX", "sending DISCOVER to = " + destinationAddress );
                    DatagramPacket sendPacket = new DatagramPacket( searchString.getBytes(), searchString.getBytes().length,
                            InetAddress.getByName( destinationAddress ), Definitions.TEST_SERVER_PORT );

                    mTestSocket.send( sendPacket );
                } catch ( Exception e ) {
                    e.printStackTrace();
                }
            }

            // search other subnets under our parent's subnet:
            for ( int j = 0; j < 256; j++ ) {
                String parentAddress = parentSubnet + "." + String.valueOf( j );
                for ( int i = 0; i < 256; i++ ) {
                    try {
                        if ( isCancelled() ) {
                            break;
                        }
                        String destinationAddress = parentAddress + "." + String.valueOf( i );
                        String searchString = Definitions.DISCOVER + ":" + getOurUserName() + ":" + getOurIp().toString().replace( "/", "" )
                                + Definitions.END_DELIM;

                        Log.d( "XXXX", "sending DISCOVER to = " + destinationAddress );
                        DatagramPacket sendPacket = new DatagramPacket( searchString.getBytes(), searchString.getBytes().length,
                                InetAddress.getByName( destinationAddress ), Definitions.TEST_SERVER_PORT );

                        mTestSocket.send( sendPacket );
                    } catch ( Exception e ) {
                        e.printStackTrace();
                    }
                }
                if ( isCancelled() ) {
                    Log.d( "XXXX", "TestSearchMethod: stopping search, since a friend replied.. " );
                    break;
                }
            }

            return null;
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
            // Log.d( "XXXX", "our IP address here = " +
            // InetAddress.getByAddress( quads ).getHostAddress() );
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

        Log.d( "XXXX", "dhcp netmask = " + dhcp.netmask );
        int broadcast = ( dhcp.ipAddress & dhcp.netmask ) | ~dhcp.netmask;
        byte[] quads = new byte[ 4 ];
        for ( int k = 0; k < 4; k++ )
            quads[ k ] = ( byte ) ( ( broadcast >> k * 8 ) & 0xFF );
        Log.d( "XXXX", "broadcast address here = " + InetAddress.getByAddress( quads ).getHostAddress() );
        return InetAddress.getByAddress( quads );
    }

    public void sendMessage( String destinationAddress, String message ) {
        String sendMessage = message + ":" + getOurUserName() + ":" + getOurIp().toString().replace( "/", "" ) + Definitions.END_DELIM;
        byte[] testArr = sendMessage.getBytes();

        Log.d( "XXXX", "sendMessage = " + sendMessage + ", len = " + sendMessage.length() );
        Log.d( "XXXX", "testarr = " + testArr.toString() + ", len = " + testArr.length );
        try {
            Log.d( "XXXX", "destination address = " + InetAddress.getByName( destinationAddress ) );
            DatagramPacket sendPacket = new DatagramPacket( sendMessage.getBytes(), sendMessage.getBytes().length, InetAddress.getByName( destinationAddress ),
                    Definitions.GENERIC_SERVER_PORT );
            Log.d( "XXXX", "gonna send out the message:" );
            mGenericSocket.send( sendPacket );
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }

    public void downloadFile( String filePath, long fileSize, Context context ) {
        new Downloader( filePath, fileSize ).execute( context );
    }

    public void uploadFile( String destinationAddress, String filePath, long fileSize, Context context ) {
        new Uploader( destinationAddress, filePath, fileSize ).execute( context );
    }

    void updateDownloadProgress( Context context, String filePath, int progress ) {
        showProgressNotification( context, filePath, progress );
    }

    // ///////////////////////////////////////////////

    private class Downloader extends AsyncTask<Context, Integer, Boolean> {
        String filePath;
        Context context;
        long fileSize;

        public Downloader( String filePath, long fileSize ) {
            this.filePath = filePath;
            this.fileSize = fileSize;
        }

        @Override
        protected void onProgressUpdate( Integer... progress ) {
            updateDownloadProgress( this.context, filePath, progress[ 0 ] );
        }

        @Override
        protected Boolean doInBackground( Context... context ) {
            ServerSocket serverSocket;
            Socket connection;
            this.context = context[ 0 ];
            serverSocket = null;
            try {
                serverSocket = new ServerSocket( Definitions.DOWNLOAD_TRANSFER_PORT );
                while ( true ) {
                    Log.d( "XXXX", "Downloader - gonna start waiting on accept()" );
                    connection = serverSocket.accept();
                    InputStream iStream = connection.getInputStream();
                    FileOutputStream oStream = new FileOutputStream( new File( DEFAULT_DOWNLOAD_LOC + "/" + getFileNameTrivial( filePath ) ) );
                    Log.d( "XXXX", "Downloader - will start dloading to : " + DEFAULT_DOWNLOAD_LOC + "/" + getFileNameTrivial( filePath ) );
                    byte[] readByte = new byte[ Definitions.DOWNLOAD_BUFFER_SIZE ];
                    int size, previousProgress = 0;
                    long count = 0;
                    while ( ( size = iStream.read( readByte ) ) > 0 ) {
                        oStream.write( readByte, 0, size );
                        count += ( long ) size;
                        if ( fileSize > Definitions.DOWNLOAD_BUFFER_SIZE ) {
                            int progress = ( int ) ( ( count * 100 ) / fileSize );
                            if ( progress < 100 ) {
                                Log.d( "XXXX", "Downloader - download count = " + count + ", size here = " + size + ", progress = " + progress );
                                if ( previousProgress != progress ) {
                                    previousProgress = progress;
                                    publishProgress( progress );
                                }

                            }
                        } else {
                            Log.d( "XXXX", "Downloader - download count = " + count + ", progress = 100" );
                        }
                    }
                    if ( count < fileSize ) {
                        publishProgress( -1 );
                    }
                    publishProgress( 100 );
                    Log.d( "XXXX", "Downloader - done dloading : " + filePath );
                    iStream.close();
                    oStream.close();
                    serverSocket.close();
                    return true;
                }
            } catch ( IOException e ) {
                e.printStackTrace();
                return false;
            }
        }
    }

    // public void downloadFile( String filePath, long fileSize ) {
    // ServerSocket serverSocket;
    // Socket connection;
    //
    // try {
    // serverSocket = new ServerSocket( Definitions.FILE_TRANSFER_PORT );
    // while ( true ) {
    // connection = serverSocket.accept();
    // InputStream iStream = connection.getInputStream();
    // FileOutputStream oStream = new FileOutputStream( new File( filePath ) );
    // byte[] readByte = new byte[ Definitions.DOWNLOAD_BUFFER_SIZE ];
    // int size;
    // while ( ( size = iStream.read( readByte ) ) > 0 ) {
    // oStream.write( readByte, 0, size );
    // }
    // iStream.close();
    // oStream.close();
    // }
    // } catch ( IOException e ) {
    // e.printStackTrace();
    // }
    // }

    private class Uploader extends AsyncTask<Context, Integer, Boolean> {
        String filePath, destinationAddress;
        long fileSize;

        public Uploader( String destinationAddress, String filePath, long fileSize ) {
            this.filePath = filePath;
            this.destinationAddress = destinationAddress;
            this.fileSize = fileSize;
            Log.d( "XXXX", "uploader's destination address = " + destinationAddress );
        }

        @Override
        protected Boolean doInBackground( Context... params ) {
            Socket socket = null;
            FileInputStream iStream = null;
            OutputStream oStream = null;

            try {
                socket = new Socket( destinationAddress, Definitions.DOWNLOAD_TRANSFER_PORT );
                oStream = ( OutputStream ) socket.getOutputStream();
                iStream = new FileInputStream( new File( filePath ) );
            } catch ( Exception e ) {
                e.printStackTrace();
            }
            try {
                socket.sendUrgentData( 100 );
                byte[] readArray = new byte[ Definitions.DOWNLOAD_BUFFER_SIZE ];
                int size;
                long count = 0;
                while ( ( size = iStream.read( readArray ) ) > 0 ) {
                    oStream.write( readArray, 0, size );
                    ++count;
                    if ( fileSize > Definitions.DOWNLOAD_BUFFER_SIZE ) {
                        Log.d( "XXXX", "Uploader - upload progress percent = " + ( int ) ( ( count * Definitions.DOWNLOAD_BUFFER_SIZE * 100 ) / fileSize ) );
                    } else {
                        Log.d( "XXXX", "Uploader - upload count = " + count + ", progress = 100" );
                    }
                }
                Log.d( "XXXX", "uploader's done uploading : " + filePath + " !!" );

                oStream.close();
                iStream.close();
                socket.close();
                return true;
            } catch ( Exception e ) {
                e.printStackTrace();
                return false;
            }
        }
    }

    // public void uploadFile( String destinationAddress, String filePath, long
    // fileSize ) {
    // Socket socket = null;
    // FileInputStream iStream = null;
    // FileOutputStream oStream = null;
    //
    // try {
    // socket = new Socket( destinationAddress, Definitions.FILE_TRANSFER_PORT
    // );
    // oStream = ( FileOutputStream ) socket.getOutputStream();
    // iStream = new FileInputStream( new File( filePath ) );
    // } catch ( Exception e ) {
    // e.printStackTrace();
    // }
    // try {
    // socket.sendUrgentData( 100 );
    // byte[] a = new byte[ Definitions.DOWNLOAD_BUFFER_SIZE ];
    // int size;
    // while ( ( size = iStream.read( a ) ) > 0 ) {
    // oStream.write( a, 0, size );
    // }
    // oStream.close();
    // iStream.close();
    // socket.close();
    // } catch ( Exception e ) {
    // e.printStackTrace();
    // }
    // }

    public String getFileNameTrivial( String filePath ) {
        return filePath.substring( filePath.lastIndexOf( "/" ) + 1 );
    }

    public void setCurrentDir( String dir ) {
        mCurrentDir = dir;
    }

    public void setPreviousDir( String dir ) {
        mPreviousDir = dir;
    }

    public String getCurrentDir() {
        return mCurrentDir;
    }

    public String getPreviousDir() {
        return mPreviousDir;
    }

    public String getHomeDirectory() {
        SharedPreferences settings = getSharedPreferences( Definitions.credsPrefFile, Context.MODE_PRIVATE );
        Log.d( "XXXX", "home directory returned as = " + settings.getString( Definitions.prefHomeDirectory, "" ) );
        return settings.getString( Definitions.prefHomeDirectory, "" );
    }

}
