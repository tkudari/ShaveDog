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
import java.util.Date;

import com.tejus.shavedog.Definitions;
import com.tejus.shavedog.R;

import com.tejus.shavedog.ShaveService;

import com.tejus.shavedog.ShaveService.ShaveBinder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.ExifInterface;
import android.net.DhcpInfo;
import android.net.ParseException;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore.Video;
import android.text.format.Time;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
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
        // Handle item selection
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

                // case R.id.friends_list:
                // this.gotoFriendsList();
                // return true;

            case R.id.dump_image_data:
                welcome.setVisibility( View.GONE );
                details.setVisibility( View.VISIBLE );
                dumpImageData();
                return true;

                // case R.id.hash_img_file:
                // welcome.setVisibility( View.GONE );
                // details.setVisibility( View.VISIBLE );
                // this.getHashOfImage();

                // build list of folders

                // find buckets

                // case R.id.hash_video_file:
                // welcome.setVisibility( View.GONE );
                // details.setVisibility( View.VISIBLE );
                // this.getHashOfVideo();
                // return true;
                //
                // case R.id.flip_algo:
                // if ( algorithm.equals( "SHA1" ) ) {
                // algorithm = "MD5";
                // } else {
                // algorithm = "SHA1";
                // }

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

    private void dumpImageData() {

        Cursor mediaCursor = getContentResolver().query( Images.Media.EXTERNAL_CONTENT_URI, PROJECTION, MediaColumns.DATA + " like '%/DCIM/%'", null, null );
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

    private void setCredentials() {
        Intent intent = new Intent();
        intent.setClass( mContext, CredentialsActivity.class );
        startActivity( intent );
    }

    private void dumpImageFile() {
        Cursor mediaCursor = getContentResolver().query( Video.Media.EXTERNAL_CONTENT_URI, PROJECTION, MediaColumns.DATA + " like '%/DCIM/%'", null, null );
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
        StringBuilder show = new StringBuilder();

        if ( mediaCursor != null ) {
            if ( mediaCursor.getCount() > 0 ) {
                mediaCursor.moveToFirst();
                do {
                    // show.append( getFinger( imageFile ) );
                    testApi( mediaCursor.getString( 1 ) );
                } while ( mediaCursor.moveToNext() );

                details.setText( show );
            } else {
                String toastText = getResources().getString( R.string.no_images_found );
                Toast toast = Toast.makeText( mContext, toastText, Toast.LENGTH_SHORT );
                toast.show();
            }
        } else {
            String toastText = getResources().getString( R.string.no_images_found );
            Toast toast = Toast.makeText( mContext, toastText, Toast.LENGTH_SHORT );
            toast.show();
        }
    }

    private void getHashOfVideo() {
        Cursor mediaCursor = getContentResolver().query( Video.Media.EXTERNAL_CONTENT_URI, PROJECTION, MediaColumns.DATA + " like '%/DCIM/%'", null, null );
        StringBuilder show = new StringBuilder();

        if ( mediaCursor != null ) {
            if ( mediaCursor.getCount() > 0 ) {
                mediaCursor.moveToFirst();
                do {
                    File videoFile = new File( mediaCursor.getString( 1 ) );
                    // show.append( getFinger( videoFile ) );
                    testApi( mediaCursor.getString( 1 ) );

                } while ( mediaCursor.moveToNext() );
                details.setText( show );
            } else {
                String toastText = getResources().getString( R.string.no_images_found );
                Toast toast = Toast.makeText( mContext, toastText, Toast.LENGTH_SHORT );
                toast.show();
            }
        } else {
            String toastText = getResources().getString( R.string.no_videos_found );
            Toast toast = Toast.makeText( mContext, toastText, Toast.LENGTH_SHORT );
            toast.show();
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

    String getFinger( File file ) {

        StringBuilder logBuffer = new StringBuilder();
        byte[] hashResult;
        long beforeTime = 0, afterTime;
        long fileLength = file.length();

        // Change these params, for different sampling rates:
        int BUFFER_LIMIT = 2000000;
        int NUMBER_OF_SAMPLES = 3;
        int SAMPLE_SIZE_PERCENT = 2;
        // Ensure that this corresponds to NUMBER_OF_SAMPLES!!
        double SAMPLE_OFFSET_PERCENT[] = {
            0,
            0.3,
            0.6
        };

        Log.d( "XXXX", "filename here = " + file.getName() + ", file length = " + fileLength );
        logBuffer.append( "algorithm used = " + algorithm + "\n" );
        logBuffer.append( "filename = " + file.getName() + "\n" );
        logBuffer.append( "filesize = " + file.length() + " bytes" + "\n" );
        logBuffer.append( "chunk size = " + BUFFER_LIMIT + "\n" );

        // mask here is:
        // | 0-2% | 30-32% | 60-62% |

        long sampleSize[] = new long[ NUMBER_OF_SAMPLES ];
        long sampleOffset[] = new long[ NUMBER_OF_SAMPLES ];

        byte buffer[] = new byte[ BUFFER_LIMIT ];

        // sample sizes
        for ( int i = 0; i < NUMBER_OF_SAMPLES; i++ ) {
            sampleSize[ i ] = ( long ) ( ( ( double ) SAMPLE_SIZE_PERCENT / 100 ) * fileLength );
            sampleOffset[ i ] = ( long ) ( SAMPLE_OFFSET_PERCENT[ i ] * fileLength );

            Log.d( "XXXX", "sampleSizes [" + i + "] = " + sampleSize[ i ] );
            logBuffer.append( "sampleSizes [" + i + "] = " + sampleSize[ i ] + "\n" );

            Log.d( "XXXX", "sampleOffset [" + i + "] = " + sampleOffset[ i ] );
            logBuffer.append( "sampleOffset [" + i + "] = " + sampleOffset[ i ] + "\n" );
        }

        try {

            MessageDigest digest = java.security.MessageDigest.getInstance( algorithm );
            FileInputStream fIs = new FileInputStream( file );
            RandomAccessFile ourFile = new RandomAccessFile( file, "r" );
            Date before = new Date();
            beforeTime = before.getTime();
            for ( int i = 0; i < NUMBER_OF_SAMPLES; i++ ) {
                ourFile.seek( sampleOffset[ i ] );
                if ( sampleSize[ i ] > BUFFER_LIMIT ) {
                    long numberOfChunks;
                    numberOfChunks = sampleSize[ i ] / BUFFER_LIMIT;
                    for ( int j = 0; j <= numberOfChunks; j++ ) {
                        Log.d( "XXXX", "gonna start reading chunk #: " + ( ( int ) sampleOffset[ i ] + j * BUFFER_LIMIT ) );

                        ourFile.read( buffer, 0, BUFFER_LIMIT );
                        digest.update( buffer, 0, buffer.length );
                    }
                } else {
                    ourFile.read( buffer, 0, BUFFER_LIMIT );
                    digest.update( buffer, 0, buffer.length );
                }
            }
            hashResult = digest.digest();
            String base64Rresult = Base64.encodeToString( hashResult, Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE );

            /*
             * StringBuffer hexString = new StringBuffer(); for ( int i = 0; i <
             * hashResult.length; i++ ) { hexString.append( Integer.toHexString(
             * 0xFF & hashResult[ i ] ) ); }
             */

            Log.d( "XXXX", "base64Rresult = " + base64Rresult );
            logBuffer.append( "base64Result = " + base64Rresult + "\n" );

        } catch ( IOException e ) {
            e.printStackTrace();
        } catch ( NoSuchAlgorithmException e ) {
            e.printStackTrace();
        } catch ( Exception e ) {
            e.printStackTrace();
        }

        Date after = new Date();
        afterTime = after.getTime();

        Log.d( "XXXX", "time taken = " + ( afterTime - beforeTime ) + " ms" );
        logBuffer.append( "time taken = " + ( afterTime - beforeTime ) + " ms" + "\n" );
        logBuffer.append( "///////////////////////////// " + "\n\n" );

        return logBuffer.toString();
    }

    String testApi( String filePath ) {

        // Change these params, for different sampling rates:
        int BUFFER_LIMIT = 2000000;
        int NUMBER_OF_SAMPLES = 3;
        int SAMPLE_SIZE_PERCENT = 2;
        // Ensure that this corresponds to NUMBER_OF_SAMPLES!!
        double SAMPLE_OFFSET_PERCENT[] = {
            0,
            0.3,
            0.6
        };

        String algorithm = "SHA1";
        String base64Result = null;
        File file = new File( filePath );
        if ( file.exists() == false ) {
            Log.d( "XXXX", "MediaAccessor2.calculateFingerprint: file doesn't exist, returning null.." );
            return null;
        }

        byte[] hashResult;
        long beforeTime = 0, afterTime;
        long fileLength = file.length();

        Log.d( "XXXX", "MediaAccessor2.calculateFingerprint: fingerprinting file - " + file.getName() + ", fileSize = " + fileLength );

        // mask here is:
        // | 0-2% | 30-32% | 60-62% |

        long sampleSize[] = new long[ NUMBER_OF_SAMPLES ];
        long sampleOffset[] = new long[ NUMBER_OF_SAMPLES ];

        byte buffer[] = new byte[ BUFFER_LIMIT ];

        for ( int i = 0; i < NUMBER_OF_SAMPLES; i++ ) {
            sampleSize[ i ] = ( long ) ( ( ( double ) SAMPLE_SIZE_PERCENT / 100 ) * fileLength );
            sampleOffset[ i ] = ( long ) ( SAMPLE_OFFSET_PERCENT[ i ] * fileLength );
            Log.d( "XXXX", "MediaAccessor2.calculateFingerprint: sampleSizes [" + i + "] = " + sampleSize[ i ] );
            Log.d( "XXXX", "MediaAccessor2.calculateFingerprint: sampleOffset [" + i + "] = " + sampleOffset[ i ] );
        }

        try {
            MessageDigest digest = java.security.MessageDigest.getInstance( algorithm );
            RandomAccessFile ourFile = new RandomAccessFile( file, "r" );
            Date before = new Date();
            beforeTime = before.getTime();
            for ( int i = 0; i < NUMBER_OF_SAMPLES; i++ ) {
                ourFile.seek( sampleOffset[ i ] );
                if ( sampleSize[ i ] > BUFFER_LIMIT ) {
                    long numberOfChunks;
                    numberOfChunks = sampleSize[ i ] / BUFFER_LIMIT;
                    for ( int j = 0; j <= numberOfChunks; j++ ) {
                        Log.d( "XXXX", "MediaAccessor2.calculateFingerprint: gonna start reading chunk #: " + ( ( int ) sampleOffset[ i ] + j * BUFFER_LIMIT ) );
                        ourFile.read( buffer, 0, ( int ) sampleSize[ i ] );
                        digest.update( buffer, 0, ( int ) sampleSize[ i ] );
                    }
                } else {
                    ourFile.read( buffer, 0, ( int ) sampleSize[ i ] );
                    digest.update( buffer, 0, ( int ) sampleSize[ i ] );
                }
            }
            hashResult = digest.digest();
            base64Result = Base64.encodeToString( hashResult, Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE );

            Log.d( "XXXX", "MediaAccessor2.calculateFingerprint: base64Rresult = " + base64Result );

        } catch ( IOException e ) {
            e.printStackTrace();
        } catch ( NoSuchAlgorithmException e ) {
            e.printStackTrace();
        } catch ( Exception e ) {
            e.printStackTrace();
        }

        Date after = new Date();
        afterTime = after.getTime();
        Log.d( "XXXX", "MediaAccessor2.calculateFingerprint: time taken to fprint = " + ( afterTime - beforeTime ) + " ms" );

        return base64Result;

    }

    void getFullfinger( File file ) {
        long beforeM, afterM;
        Log.d( "XXXX", "FullFinger!!! filename = " + file.getName() + " fileSize = " + file.length() );

        final int BUFFER_LIMIT = 2000000;
        StringBuffer hexString = new StringBuffer();
        byte buffer[] = new byte[ BUFFER_LIMIT ];
        Date before = new Date();
        beforeM = before.getTime();
        try {
            MessageDigest digest = java.security.MessageDigest.getInstance( "SHA1" );
            FileInputStream fIs = new FileInputStream( file );
            while ( -1 != fIs.read( buffer, 0, buffer.length ) ) {
                digest.update( buffer );
            }
            fIs.close();
            byte messageDigest[] = digest.digest();

            for ( int i = 0; i < messageDigest.length; i++ ) {
                hexString.append( Integer.toHexString( 0xFF & messageDigest[ i ] ) );
            }
            Date after = new Date();
            afterM = after.getTime();
            Log.d( "XXXX", "full finger = " + hexString );
            Log.d( "XXXX", "time taken for this = " + ( afterM - beforeM ) );

        } catch ( Exception e ) {
            Log.d( "XXXX", "dumpImageFile error" );
            e.printStackTrace();
        }
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
                Toast.makeText( mContext, R.string.shave_service_connected, Toast.LENGTH_SHORT ).show();
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
            // showDialog(getResources().getString( R.string.new_request ),
            // intent.getStringExtra( "user_name" ), intent.getStringExtra(
            // "address" ));
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
