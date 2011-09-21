package com.tejus.shavedog;

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
import java.util.Date;

import com.tejus.shavedog.R;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.ExifInterface;
import android.net.DhcpInfo;
import android.net.ParseException;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore.Video;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class ShaveDogActivity extends Activity {
    private static DatagramSocket mSocket;
    private static final String[] PROJECTION;
    private StringBuilder sBuilder;
    private Context mContext;
    String mUserName;
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

    @Override
    public void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.main );
        mContext = this;
        initNetworkStuff();
        details = ( TextView ) findViewById( R.id.details );
        welcome = ( TextView ) findViewById( R.id.welcome );
        sBuilder = new StringBuilder();
        try {
            DatagramSocket socket = new DatagramSocket( Definitions.SERVER_PORT );
            socket.setBroadcast( true );
            socket.setSoTimeout( Definitions.SOCKET_TIMEOUT );
            mSocket = socket;
            if ( !Definitions.DASHWIRE ) {
                new Thread( new Server( socket ) ).start();
            }
        } catch ( Exception e ) {
            e.printStackTrace();
        }

    }

    @Override
    public boolean onCreateOptionsMenu( Menu menu ) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate( R.menu.shave_menu, menu );
        return true;
    }

    @Override
    public boolean onOptionsItemSelected( MenuItem item ) {
        // Handle item selection
        switch ( item.getItemId() ) {
            case R.id.dump_image_data:
                welcome.setVisibility( View.GONE );
                details.setVisibility( View.VISIBLE );
                dumpImageData();
                return true;

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

            case R.id.dump_img_file:
                this.dumpImageFile();
                return true;

            case R.id.md5_img_file:
                this.getHashOfImage();
                return true;

            default:
                return super.onOptionsItemSelected( item );
        }
    }

    private void populateList() {
        try {
            // send broadcast:
            mUserName = getOurUserName();
            if ( mUserName.equals( Definitions.defaultUserName ) ) {
                String toastText = getResources().getString( R.string.default_username_used ) + Definitions.defaultUserName;
                Toast toast = Toast.makeText( mContext, toastText, Toast.LENGTH_SHORT );
                toast.show();
            }
            String searchString = Definitions.QUERY_LIST + ":" + mUserName + ":" + getOurIp().toString().replace( "/", "" );
            Log.d( "XXXX", "searchString = " + searchString );
            DatagramPacket sendPacket = new DatagramPacket( Definitions.QUERY_LIST.getBytes(), Definitions.QUERY_LIST.length(), getBroadcastAddress(),
                    Definitions.SERVER_PORT );
            Log.d( "XXXX", "gonna send broadcast for : " + Definitions.QUERY_LIST );
            Log.d( "XXXX", "broadcast packet : " + new String( sendPacket.getData() ) );

            mSocket.send( sendPacket );
        } catch ( Exception e ) {
            Log.d( "XXXX", "populateList error" );
            e.printStackTrace();
        }
    }

    private InetAddress getOurIp() throws UnknownHostException {
        Definitions.IP_ADDRESS_INT = dhcp.ipAddress;
        int ourIp = Definitions.IP_ADDRESS_INT;
        byte[] quads = new byte[ 4 ];
        for ( int k = 0; k < 4; k++ )
            quads[ k ] = ( byte ) ( ( ourIp >> k * 8 ) & 0xFF );
        Log.d( "XXXX", "our IP address here = " + InetAddress.getByAddress( quads ).getHostAddress() );
        Definitions.IP_ADDRESS_INETADDRESS = InetAddress.getByAddress( quads );
        return InetAddress.getByAddress( quads );
    }

    InetAddress getBroadcastAddress() throws IOException {

        int broadcast = ( dhcp.ipAddress & dhcp.netmask ) | ~dhcp.netmask;
        byte[] quads = new byte[ 4 ];
        for ( int k = 0; k < 4; k++ )
            quads[ k ] = ( byte ) ( ( broadcast >> k * 8 ) & 0xFF );
        Log.d( "XXXX", "broadcast address here = " + InetAddress.getByAddress( quads ).getHostAddress() );
        return InetAddress.getByAddress( quads );
    }

    private void dumpImageData() {

        Cursor mediaCursor = getContentResolver().query( Video.Media.EXTERNAL_CONTENT_URI, PROJECTION, MediaColumns.DATA + " like '%/DCIM/%'", null, null );
        // DatabaseUtils.dumpCursor( mediaCursor );
        Log.d( "XXXX", "gonna start printing cursor.. " );
        sBuilder = new StringBuilder();

        if ( mediaCursor != null ) {
            mediaCursor.moveToFirst();
            do {
                File image = new File( mediaCursor.getString( 1 ) );

                Log.d( "XXXX", "ID = " + mediaCursor.getString( 0 ) );
                Log.d( "XXXX", "FILENAME = " + mediaCursor.getString( 1 ) );
                Log.d( "XXXX", "date_added = " + mediaCursor.getString( 2 ) );
                Log.d( "XXXX", "date_modified = " + mediaCursor.getString( 3 ) );
                Log.d( "XXXX", "datetaken = " + mediaCursor.getString( 4 ) );
                Log.d( "XXXX", "display_name = " + mediaCursor.getString( 5 ) );
                Log.d( "XXXX", "mimetype = " + mediaCursor.getString( 6 ) );
                Log.d( "XXXX", "size = " + mediaCursor.getString( 7 ) );
                Log.d( "XXXX", "--Actual File size-- = " + image.length() );
                Log.d( "XXXX", "--Actual File mTime-- = " + image.lastModified() );
                Log.d( "XXXX", "//////////////////////////////////////////////" );

                sBuilder.append( "ID = " + mediaCursor.getString( 0 ) + "\n" );
                sBuilder.append( "FILENAME = " + mediaCursor.getString( 1 ) + "\n" );
                sBuilder.append( "DATE ADDED = " + mediaCursor.getString( 2 ) + "\n" );
                sBuilder.append( "DATE MODIFIED = " + mediaCursor.getString( 3 ) + "\n" );
                sBuilder.append( "DATE TAKEN = " + mediaCursor.getString( 4 ) + "\n" );
                sBuilder.append( "DISPLAY NAME =" + mediaCursor.getString( 5 ) + "\n" );
                sBuilder.append( "MIME TYPE = " + mediaCursor.getString( 6 ) + "\n" );
                sBuilder.append( "SIZE = " + mediaCursor.getString( 7 ) + "\n\n" );
                sBuilder.append( "--Actual file size = " + image.length() + "\n" );
                sBuilder.append( "--Actual file mTime = " + image.lastModified() + "\n\n" + "//////////////////////////////////////////////" );

            } while ( mediaCursor.moveToNext() );
            details.setText( sBuilder );

        } else {
            details.setText( getResources().getString( R.string.not_loaded ) );
        }

    }

    void quit() {
        Log.d( "XXXX", "quit(): Killing ourself.." );
        android.os.Process.killProcess( android.os.Process.myPid() );
    }

    void initNetworkStuff() {
        wifi = ( WifiManager ) ShaveDogActivity.this.getSystemService( Context.WIFI_SERVICE );
        dhcp = wifi.getDhcpInfo();
    }

    private void setCredentials() {
        Intent intent = new Intent();
        intent.setClass( mContext, CredentialsActivity.class );
        startActivity( intent );
    }

    private String getOurUserName() {
        SharedPreferences settings = getSharedPreferences( Definitions.credsPrefFile, Context.MODE_PRIVATE );
        return settings.getString( Definitions.prefUserName, Definitions.defaultUserName );
    }

    private void dumpImageFile() {
        Cursor mediaCursor = getContentResolver().query( Images.Media.EXTERNAL_CONTENT_URI, PROJECTION, MediaColumns.DATA + " like '%/DCIM/%'", null, null );
        if ( mediaCursor != null ) {
            mediaCursor.moveToFirst();
            File imageFile = new File( mediaCursor.getString( 1 ) );
            Log.d( "XXXX", "gonna start converting file = " + imageFile.getName() );
            byte[] data = new byte[ ( int ) imageFile.length() ];
            try {
                FileInputStream fIs = new FileInputStream( imageFile );
                fIs.read( data );
                fIs.close();

            } catch ( Exception e ) {
                Log.d( "XXXX", "dumpImageFile error" );
                e.printStackTrace();
            }

            String hexRep = byteArrayToHexString( data );
            Log.d( "XXXX", "hexRep's length = " + hexRep.length() );
            Log.d( "XXXX", "hexRep = " + hexRep );
        }
    }

    private void getHashOfImage() {
        Cursor mediaCursor = getContentResolver().query( Video.Media.EXTERNAL_CONTENT_URI, PROJECTION, MediaColumns.DATA + " like '%/DCIM/%'", null, null );
        if ( mediaCursor != null ) {
            mediaCursor.moveToFirst();
            File imageFile = new File( mediaCursor.getString( 1 ) );
            getFinger( imageFile );

            /*
             * byte[] data = new byte[ ( int ) imageFile.length() ]; try {
             * FileInputStream fIs = new FileInputStream( imageFile ); fIs.read(
             * data ); fIs.close();
             * 
             * } catch ( Exception e ) { Log.d( "XXXX", "dumpImageFile error" );
             * e.printStackTrace(); } Log.d( "XXXX", "md5 of file : " +
             * imageFile.getName() + " = " + md5( data ) );
             */
        }

    }

    private String byteArrayToHexString( byte[] bArray ) {
        Date now = new Date();
        long startMilli = now.getTime();
        StringBuffer sB = new StringBuffer( bArray.length * 2 );
        for ( int i = 0; i < bArray.length; i++ ) {
            int v = bArray[ i ] & 0xff;
            if ( v < 16 ) {
                sB.append( '0' );
            }
            sB.append( Integer.toHexString( v ) );
        }
        Date after = new Date();
        long afterMilli = after.getTime();
        Log.d( "XXXX", "time taken = " + ( afterMilli - startMilli ) );

        return sB.toString().toUpperCase();
    }

    public String md5( byte[] fileByteArray ) {
        try {
            long beforeMd5Milli, afterMd5Milli;
            Date beforeMd5 = new Date();
            beforeMd5Milli = beforeMd5.getTime();
            // Create MD5 Hash
            MessageDigest digest = java.security.MessageDigest.getInstance( "MD5" );
            digest.update( fileByteArray, 0, fileByteArray.length );
            byte messageDigest[] = digest.digest();

            // Create Hex String
            long beforeMilli, afterMilli;
            Date before = new Date();
            beforeMilli = before.getTime();
            StringBuffer hexString = new StringBuffer();
            for ( int i = 0; i < messageDigest.length; i++ ) {
                hexString.append( Integer.toHexString( 0xFF & messageDigest[ i ] ) );
            }
            Date after = new Date();
            afterMilli = after.getTime();
            Log.d( "XXXX", "just the hex conversion took = " + ( afterMilli - beforeMilli ) );

            Date afterMd5 = new Date();
            afterMd5Milli = afterMd5.getTime();
            Log.d( "XXXX", "the whole md5 conversion took = " + ( afterMd5Milli - beforeMd5Milli ) );
            return hexString.toString();

        } catch ( NoSuchAlgorithmException e ) {
            e.printStackTrace();
        }
        return "";
    }

    void getFinger( File file ) {
        
        byte[] hashResult;
        long beforeTime = 0, afterTime;
        Log.d( "XXXX", "filename here = " + file.getName() );
        long fileLength = file.length();
        Log.d( "XXXX", "fileLength here = " + fileLength );
        // mask here is:
        // | 0-10% | 40-50% | 60-80% | 90-100% |

        long sampleSize[] = new long[ 4 ];
        long sampleOffset[] = new long[ 4 ];
        

        final int BUFFER_LIMIT = 2000000;
        byte buffer[] = new byte[ BUFFER_LIMIT ];

        

        // sample sizes
        sampleSize[ 0 ] = ( long ) ( 0.1 * fileLength );
        sampleSize[ 1 ] = ( long ) ( 0.1 * fileLength );
        sampleSize[ 2 ] = ( long ) ( 0.2 * fileLength );
        sampleSize[ 3 ] = ( long ) ( 0.1 * fileLength );

        for ( int i = 0; i < 4; i++ ) {
            Log.d( "XXXX", "sampleSizes [" + i + "] = " + sampleSize[ i ] );
        }

        // sample offsets
        sampleOffset[ 0 ] = 0;
        sampleOffset[ 1 ] = ( long ) ( 0.3 * fileLength );
        sampleOffset[ 2 ] = ( long ) ( 0.6 * fileLength );
        sampleOffset[ 3 ] = ( long ) ( 0.9 * fileLength );

        for ( int i = 0; i < 4; i++ ) {
            Log.d( "XXXX", "sampleOffset [" + i + "] = " + sampleOffset[ i ] );
        }
        
        
        try {

            MessageDigest digest = java.security.MessageDigest.getInstance( "MD5" );
            FileInputStream fIs = new FileInputStream( file );
            RandomAccessFile ourFile = new RandomAccessFile( file, "r" );
            Date before = new Date();
            beforeTime = before.getTime();
            for ( int i = 0; i < 4; i++ ) {
                ourFile.seek(sampleOffset[i]);
                if ( sampleSize[ i ] > BUFFER_LIMIT ) {
                    long numberOfChunks;
                    numberOfChunks = sampleSize[ i ] / BUFFER_LIMIT;
                    for ( int j = 0; j < numberOfChunks; j++ ) {
                        Log.d("XXXX", "gonna start reading chunk #: " + ( ( int ) sampleOffset[ i ] + j * BUFFER_LIMIT ));
                        //fIs.read( buffer, ( ( int ) sampleOffset[ i ] + j * BUFFER_LIMIT ), BUFFER_LIMIT );
                        ourFile.read(buffer, 0, BUFFER_LIMIT);
                        digest.update(buffer, 0, buffer.length);
                    }
                } else {
                       ourFile.read(buffer, 0, BUFFER_LIMIT);
                      digest.update(buffer, 0, buffer.length);
                }
            }
            hashResult = digest.digest();
            
            StringBuffer hexString = new StringBuffer();
            for ( int i = 0; i < hashResult.length; i++ ) {
                hexString.append( Integer.toHexString( 0xFF & hashResult[ i ] ) );
            }
            
            Log.d("XXXX", "hexString = " + hexString);
            
        } catch ( IOException e ) {
            e.printStackTrace();
        } catch ( NoSuchAlgorithmException e ) {
            e.printStackTrace();
        } catch ( Exception e ) {
            e.printStackTrace();
        }
        
        Date after = new Date();
        afterTime = after.getTime();
        
        Log.d("XXXX", "time taken = " + (afterTime - beforeTime));
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

