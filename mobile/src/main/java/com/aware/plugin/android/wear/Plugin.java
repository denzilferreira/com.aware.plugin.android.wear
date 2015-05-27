package com.aware.plugin.android.wear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
     * Set this as topic for query watch data
     */
    public static final String TOPIC_GET_DATA = "/get/data";

    /**
     * Needed for querying watch data
     * Database table CONTENT_URI
     */
    public static final String DB_CONTENT_URI = "content_uri";

    /**
     * Database columns
     */
    public static final String DB_COLUMNS = "db_columns";

    /**
     * Database where condition
     */
    public static final String DB_WHERE = "db_where";

    /**
     * Database sorting condition
     */
    public static final String DB_SORT = "db_sort";

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
        registerReceiver(wearListener, wearFilter);

        if( Aware.is_watch(this) ) {
            Aware.setSetting(this, Aware_Preferences.STATUS_BATTERY, true);
        }

        Aware.setSetting(this, Settings.STATUS_PLUGIN_ANDROID_WEAR, true);
        Aware.startPlugin(this, getPackageName());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        TAG = Aware.getSetting(this, Aware_Preferences.DEBUG_TAG);
        DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

        googleClient.connect();

        //fetch connected peers
        PendingResult<NodeApi.GetConnectedNodesResult> nodes = Wearable.NodeApi.getConnectedNodes(googleClient);
        nodes.setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
            @Override
            public void onResult(NodeApi.GetConnectedNodesResult result) {
                if (result.getNodes().size() > 0) {
                    for (Node p : result.getNodes()) {
                        if (!p.getDisplayName().equals("cloud")) {
                            peer = p;
                            break;
                        }
                    }
                    if (DEBUG) Log.d(TAG, "Connected to " + peer.getDisplayName());
                }
            }
        });

        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * Sends message from phone->watch and watch->phone
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
                } else {
                    Log.d(TAG, "Not connected. Failed to send " + message + " to " + topic);
                }
            }
        }
    }

    public static class WearMessenger extends WearableListenerService {
        @Override
        public void onMessageReceived(MessageEvent messageEvent) {
            super.onMessageReceived(messageEvent);

            //Phone is requesting data
            if( Aware.is_watch(getApplicationContext()) && messageEvent.getPath().equals(TOPIC_GET_DATA) ) {
                try {
                    JSONObject query = new JSONObject(new String(messageEvent.getData()));
                    Cursor requestedData = getContentResolver().query(Uri.parse(query.getString(DB_CONTENT_URI)), new String[]{query.optString(DB_COLUMNS)}, query.optString(DB_WHERE), null, query.optString(DB_SORT));
                    if( requestedData != null ) {
                        String result = cursorToString(requestedData);

                        //Ask the message to be send back to the phone
                        Intent sendMessage = new Intent(ACTION_AWARE_WEAR_SEND_MESSAGE);
                        sendMessage.putExtra(EXTRA_TOPIC, TOPIC_GET_DATA);
                        sendMessage.putExtra(EXTRA_MESSAGE, result);
                        sendBroadcast(sendMessage);
                    }
                    if( requestedData != null && ! requestedData.isClosed()) requestedData.close();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

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

        private String cursorToString(Cursor crs) {
            JSONArray arr = new JSONArray();
            crs.moveToFirst();
            while (!crs.isAfterLast()) {
                int nColumns = crs.getColumnCount();
                JSONObject row = new JSONObject();
                for (int i = 0 ; i < nColumns ; i++) {
                    String colName = crs.getColumnName(i);
                    if (colName != null) {
                        try {
                            switch (crs.getType(i)) {
                                case Cursor.FIELD_TYPE_BLOB   : row.put(colName, crs.getBlob(i).toString()); break;
                                case Cursor.FIELD_TYPE_FLOAT  : row.put(colName, crs.getDouble(i))         ; break;
                                case Cursor.FIELD_TYPE_INTEGER: row.put(colName, crs.getLong(i))           ; break;
                                case Cursor.FIELD_TYPE_NULL   : row.put(colName, null)                     ; break;
                                case Cursor.FIELD_TYPE_STRING : row.put(colName, crs.getString(i))         ; break;
                            }
                        } catch (JSONException e) {}
                    }
                }
                arr.put(row);
                if (!crs.moveToNext()) break;
            }
            return arr.toString();
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
        Aware.setSetting(this, Settings.STATUS_PLUGIN_ANDROID_WEAR, false);
        Aware.stopPlugin(this, getPackageName());
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
