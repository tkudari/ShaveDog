package com.tejus.shavedog;

import java.net.InetAddress;

public class Definitions {
    public static int IP_ADDRESS_INT;
    public static InetAddress IP_ADDRESS_INETADDRESS;
    public static int BROADCAST_SERVER_PORT = 5555;
    public static int GENERIC_SERVER_PORT = 5565;
    
    
    public static final int COMMAND_BUFSIZE = 40;
    public static String QUERY_LIST = "query_list";
    public static final String TAG = "XXXX";
    public static final boolean DASHWIRE = true;
    public static final int SOCKET_TIMEOUT = 500;
    public static String USERNAME;
    public static String credsPrefFile = "credsPrefFile";
    public static String prefUserName = "pUserName";
    public static String defaultUserName = "defaultUserName";
    public static int MIN_USERNAME_LENGTH = 3;
    public static String END_DELIM = "*";
    public static String COMMAND_DELIM = ":*";
    public static int COMMAND_WORD_LENGTH = 4;
    public static String REPLY_ACCEPTED = "yes";
    
    
    //Intent Names:
    public static String INTENT_QUERY_LIST= "com.tejus.shavedog.query_list";
    public static String INTENT_FRIEND_ACCEPTED= "com.tejus.shavedog.friend_accepted";
    

    //
  

}
