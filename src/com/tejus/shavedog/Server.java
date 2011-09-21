package com.tejus.shavedog;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import android.util.Log;

public class Server implements Runnable {
    
    private DatagramSocket mSocket;

    public Server( DatagramSocket socket ) {
        mSocket = socket;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[ Definitions.COMMAND_BUFSIZE ];
        DatagramPacket packet = new DatagramPacket( buffer, buffer.length );
        while ( true ) {
            try {
                mSocket.receive( packet );
                Log.d("XXXX", "Stuff received by Server = " + new String(packet.getData()) );
                
            } catch ( IOException e ) {
                Log.d("XXXX", "Server: Receive timed out..");
            }
        }
    }

    

}
