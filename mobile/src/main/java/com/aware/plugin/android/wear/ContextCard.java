package com.aware.plugin.android.wear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.aware.Aware;
import com.aware.providers.Battery_Provider;
import com.aware.ui.Stream_UI;
import com.aware.utils.IContextCard;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by denzil on 30/04/15.
 */
public class ContextCard implements IContextCard {
    public ContextCard(){};

    //Set how often your card needs to refresh if the stream is visible (in milliseconds)
    private int refresh_interval = 1 * 60 * 1000; //milliseconds -> every 1 minute

    private Handler uiRefresher = new Handler(Looper.getMainLooper());
    private Runnable uiChanger = new Runnable() {
        @Override
        public void run() {
            //Modify card's content here once it's initialized
            if( card != null ) {
                if( Aware.is_watch(sContext) ) {

                    Cursor last_battery = sContext.getContentResolver().query(Battery_Provider.Battery_Data.CONTENT_URI, null, null, null, Battery_Provider.Battery_Data.TIMESTAMP + " DESC LIMIT 1");
                    if( last_battery != null && last_battery.moveToFirst() ) {
                        battery_left.setText("Battery left: " + last_battery.getInt(last_battery.getColumnIndex(Battery_Provider.Battery_Data.LEVEL)) + "%");
                    }
                    if( last_battery != null && ! last_battery.isClosed() ) last_battery.close();

                } else {

                    //Ask watch for the latest information on the battery level
                    Intent sendMsgWatch = new Intent(Plugin.ACTION_AWARE_WEAR_SEND_MESSAGE);
                    sendMsgWatch.putExtra(Plugin.EXTRA_TOPIC, Plugin.TOPIC_GET_DATA);

                    JSONObject query = new JSONObject();
                    try {
                        query.put(Plugin.DB_CONTENT_URI, Battery_Provider.Battery_Data.CONTENT_URI.toString());
                        query.put(Plugin.DB_COLUMNS, Battery_Provider.Battery_Data.LEVEL);
                        query.put(Plugin.DB_WHERE, null);
                        query.put(Plugin.DB_SORT, Battery_Provider.Battery_Data.TIMESTAMP + " DESC LIMIT 1");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    sendMsgWatch.putExtra(Plugin.EXTRA_MESSAGE, query.toString());

                    //Ask this message to be delivered to watch...
                    sContext.sendBroadcast(sendMsgWatch);
                }
            }
            //Reset timer and schedule the next card refresh
            uiRefresher.postDelayed(uiChanger, refresh_interval);
        }
    };

    //You may use sContext on uiChanger to do queries to databases, etc.
    private Context sContext;

    //Declare here all the UI elements you'll be accessing
    private View card;
    private TextView battery_left;

    @Override
    public View getContextCard(Context context) {
        sContext = context;

        //Tell Android that you'll monitor the stream statuses
        IntentFilter filter = new IntentFilter();
        filter.addAction(Stream_UI.ACTION_AWARE_STREAM_OPEN);
        filter.addAction(Stream_UI.ACTION_AWARE_STREAM_CLOSED);
        filter.addAction(Plugin.ACTION_AWARE_WEAR_RECEIVED_MESSAGE);
        context.registerReceiver(streamObs, filter);

        LayoutInflater sInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        card = sInflater.inflate(R.layout.android_wear, new RelativeLayout(context));

        battery_left = (TextView) card.findViewById(R.id.battery_life);

        uiRefresher.post(uiChanger);
        return card;
    }

    //This is a BroadcastReceiver that keeps track of stream status. Used to stop the refresh when user leaves the stream and restart again otherwise
    private StreamObs streamObs = new StreamObs();
    public class StreamObs extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if( intent.getAction().equals(Stream_UI.ACTION_AWARE_STREAM_OPEN) ) {
                //start refreshing when user enters the stream
                uiRefresher.postDelayed(uiChanger, refresh_interval);
            }

            if( intent.getAction().equals(Stream_UI.ACTION_AWARE_STREAM_CLOSED) ) {
                //stop refreshing when user leaves the stream
                uiRefresher.removeCallbacks(uiChanger);
                uiRefresher.removeCallbacksAndMessages(null);
            }

            if( intent.getAction().equals(Plugin.ACTION_AWARE_WEAR_RECEIVED_MESSAGE) ) {

                String topic = intent.getStringExtra(Plugin.EXTRA_TOPIC);
                String message = intent.getStringExtra(Plugin.EXTRA_MESSAGE);

                if( Aware.DEBUG ) Log.d(Plugin.TAG, "Received " + message + " in " + topic);

                if( message != null && message.length() > 0 && message.contains("battery_level") && message.charAt(0) == '[' ) {
                    try {
                        JSONArray data = new JSONArray(message);
                        if( ! Aware.is_watch(context) && battery_left != null ) {
                            JSONObject last_battery = data.getJSONObject(0);
                            battery_left.setText("Battery left: " + last_battery.getInt("battery_level") + "%");
                        }
                    } catch (JSONException e) {}
                }
            }
        }
    }
}
