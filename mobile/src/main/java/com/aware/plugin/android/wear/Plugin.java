package com.aware.plugin.android.wear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.utils.Aware_Plugin;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

public class Plugin extends Aware_Plugin implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    /**
     * Broadcasted when we get a message
     */
    public static final String ACTION_AWARE_WEAR_RECEIVED_MESSAGE = "ACTION_AWARE_WEAR_RECEIVED_MESSAGE";

    /**
     * Request a message to be sent
     */
    public static final String ACTION_AWARE_WEAR_SEND_MESSAGE = "ACTION_AWARE_WEAR_SEND_MESSAGE";

    /**
     * Message topic. Needs to be path-based (e.g., /path)
     */
    public static final String EXTRA_TOPIC = "topic";

    /**
     * Message content, as a String
     */
    public static final String EXTRA_MESSAGE = "message";

    /**
     * Google API client
     */
    private static GoogleApiClient googleClient;

    /**
     * Reference to the Watch we are connected to
     */
    public static Node peer;

    private static WearMessageListener wearListener = new WearMessageListener();

    @Override
    public void onCreate() {
        super.onCreate();

        googleClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        IntentFilter wearFilter = new IntentFilter();
        wearFilter.addAction(Plugin.ACTION_AWARE_WEAR_SEND_MESSAGE);
//        wearFilter.addAction(Plugin.ACTION_AWARE_WEAR_RECEIVED_MESSAGE);
        wearFilter.addAction(Aware.ACTION_AWARE_SYNC_DATA);

        registerReceiver(wearListener, wearFilter);

        if( Aware.is_watch(this) ) {
            Aware.setSetting(this, Aware_Preferences.STATUS_BATTERY, true);
            sendBroadcast(new Intent(Aware.ACTION_AWARE_REFRESH));
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        TAG = Aware.getSetting(this, Aware_Preferences.DEBUG_TAG);
        DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

        if( ! googleClient.isConnected() ) {
            googleClient.connect();
        }

        //Get peers
        PendingResult<NodeApi.GetConnectedNodesResult> nodes = Wearable.NodeApi.getConnectedNodes(googleClient);
        nodes.setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
            @Override
            public void onResult(NodeApi.GetConnectedNodesResult result) {
                if( result.getNodes().size() > 0 ) {
                    peer = result.getNodes().get(0);
                    if( DEBUG ) Log.d( TAG, "Connected to " + ( Aware.is_watch(getApplicationContext() ) ? "smartphone" : "watch") ); //if we are on the watch, show smartphone and vice-versa.
                }
            }
        });

        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * Sends message to phone/watch
     */
    public static class WearMessageListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if( intent.getAction().equals(ACTION_AWARE_WEAR_SEND_MESSAGE) ) {
                String topic = intent.getStringExtra(EXTRA_TOPIC);
                String message = intent.getStringExtra(EXTRA_MESSAGE);
                if( message != null && topic != null && peer != null ) {
                    if( DEBUG ) {
                        Log.d(TAG, "Sending " + message + " to " + topic);
                    }
                    Wearable.MessageApi.sendMessage(googleClient, peer.getId(), topic, message.getBytes());
                }
            }
//            if( intent.getAction().equals(ACTION_AWARE_WEAR_RECEIVED_MESSAGE) ) {
//
//            }
        }
    }

    public static class WearMessenger extends WearableListenerService {
        @Override
        public void onMessageReceived(MessageEvent messageEvent) {
            super.onMessageReceived(messageEvent);
            Intent message = new Intent(ACTION_AWARE_WEAR_RECEIVED_MESSAGE);
            message.putExtra(EXTRA_TOPIC, messageEvent.getPath());
            message.putExtra(EXTRA_MESSAGE, new String(messageEvent.getData()));
            sendBroadcast(message);
        }

        @Override
        public void onPeerConnected(Node peer) {
            super.onPeerConnected(peer);
            Plugin.peer = peer;
            if(Aware.DEBUG) Log.d(Aware.TAG, "Connected to: " + peer.getDisplayName());
        }

        @Override
        public void onPeerDisconnected(Node peer) {
            super.onPeerDisconnected(peer);
            if(Aware.DEBUG) Log.d(Aware.TAG, "Disconnected from: " + peer.getDisplayName());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(wearListener);
        if( Aware.is_watch(this) ) {
            Aware.setSetting(this, Aware_Preferences.STATUS_BATTERY, false);
            sendBroadcast(new Intent(Aware.ACTION_AWARE_REFRESH));
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        if( Aware.DEBUG ) {
            Log.d(TAG, "Connected to Google APIs!");
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        if( Aware.DEBUG ) {
            Log.d(TAG, "Connection to Google APIs suspended!");
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if( Aware.DEBUG ) {
            Log.d(TAG, "Connection failed to Google APIs!");
        }
    }
}
