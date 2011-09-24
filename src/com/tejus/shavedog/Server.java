package com.tejus.shavedog;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.URL;
import java.util.StringTokenizer;

import android.os.AsyncTask;
import android.util.Log;

public class Server implements Runnable {
    
    private DatagramSocket mSocket;
    private ShaveDogActivity mCallback;

    public Server( DatagramSocket socket, ShaveDogActivity callback ) {
        mSocket = socket;
        mCallback = callback;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[ Definitions.COMMAND_BUFSIZE ];
        DatagramPacket packet = new DatagramPacket( buffer, buffer.length );
        while ( true ) {
            try {
                mSocket.receive( packet );
                Log.d("XXXX", "Stuff received by Server = " + new String(packet.getData()) );
                dealWithReceivedPacket(new String(packet.getData()));
                
                
            } catch ( IOException e ) {
                Log.d("XXXX", "Server: Receive timed out..");
            }
        }
    }

    private void dealWithReceivedPacket( DatagramPacket packet ) {
        String words[] = new String[Definitions.COMMAND_WORD_LENGTH];
        int wordCounter = 0;
        String command = new String(packet.getData());

        StringTokenizer strTok = new StringTokenizer( command, Definitions.COMMAND_DELIM );
        while(strTok.hasMoreTokens()) {
            words[wordCounter] = strTok.nextToken();
            ++ wordCounter;
        }
        for(String word : words)
            Log.d("XXXX", "word = " + word);
    }

    
    
   
    

}
