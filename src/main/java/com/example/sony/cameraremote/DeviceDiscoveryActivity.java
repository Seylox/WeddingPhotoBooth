/*
 * Copyright 2014 Sony Corporation
 */

package com.example.sony.cameraremote;

import com.example.sony.cameraremote.ServerDevice.ApiService;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * An Activity class of Device Discovery screen.
 */
public class DeviceDiscoveryActivity extends Activity {
    //==============================================================================================
    //region MemberVariables
    private static final String TAG = DeviceDiscoveryActivity.class.getSimpleName();

    private SimpleSsdpClient mSsdpClient;

    private DeviceListAdapter mListAdapter;

    private boolean mActivityActive;

    private Button mCameraWifiButton;
    private Button mPrinterWifiButton;

    private boolean mCheckWifiConnection = false;
    private String mShouldConnectToWifi = "";
    //endregion MemberVariables
    //==============================================================================================

    //==============================================================================================
    //region ActivityMethods
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_device_discovery);
        setProgressBarIndeterminateVisibility(false);

        mSsdpClient = new SimpleSsdpClient();
        mListAdapter = new DeviceListAdapter(this);

        Log.d(TAG, "onCreate() completed.");
        mCameraWifiButton = (Button) findViewById(R.id.camera_wifi_button);
        mPrinterWifiButton = (Button) findViewById(R.id.printer_wifi_button);
        runPeriodicWifiTask();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mActivityActive = true;
        ListView listView = (ListView) findViewById(R.id.list_device);
        listView.setAdapter(mListAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ListView listView = (ListView) parent;
                ServerDevice device = (ServerDevice) listView.getAdapter().getItem(position);
                launchSampleActivity(device);
            }
        });

        findViewById(R.id.button_search).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Button btn = (Button) v;
                if (!mSsdpClient.isSearching()) {
                    searchDevices();
                    btn.setEnabled(false);
                }
            }
        });

        Log.d(TAG, "onResume() completed.");
    }

    @Override
    protected void onPause() {
        super.onPause();
        mActivityActive = false;
        if (mSsdpClient != null && mSsdpClient.isSearching()) {
            mSsdpClient.cancelSearching();
        }

        Log.d(TAG, "onPause() completed.");
    }
    //endregion ActivityMethods
    //==============================================================================================

    //==============================================================================================
    //region SearchDevices
    /**
     * Start searching supported devices.
     */
    private void searchDevices() {
        mListAdapter.clearDevices();
        setProgressBarIndeterminateVisibility(true);
        mSsdpClient.search(new SimpleSsdpClient.SearchResultHandler() {

            @Override
            public void onDeviceFound(final ServerDevice device) {
                // Called by non-UI thread.
                Log.d(TAG, ">> Search device found: " + device.getFriendlyName());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // TODO: launch other activity upon finding device!
                        mListAdapter.addDevice(device);
                    }
                });
            }

            @Override
            public void onFinished() {
                // Called by non-UI thread.
                Log.d(TAG, ">> Search finished.");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setProgressBarIndeterminateVisibility(false);
                        findViewById(R.id.button_search).setEnabled(true);
                        if (mActivityActive) {
                            Toast.makeText(DeviceDiscoveryActivity.this, //
                                    R.string.msg_device_search_finish, //
                                    Toast.LENGTH_SHORT).show(); //
                        }
                    }
                });
            }

            @Override
            public void onErrorFinished() {
                // Called by non-UI thread.
                Log.d(TAG, ">> Search Error finished.");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setProgressBarIndeterminateVisibility(false);
                        findViewById(R.id.button_search).setEnabled(true);
                        if (mActivityActive) {
                            Toast.makeText(DeviceDiscoveryActivity.this, //
                                    R.string.msg_error_device_searching, //
                                    Toast.LENGTH_SHORT).show(); //
                        }
                    }
                });
            }
        });
    }
    //endregion SearchDevices
    //==============================================================================================

    //==============================================================================================
    //region LaunchActivity
    /**
     * Launch a SampleCameraActivity.
     * 
     * @param device
     */
    private void launchSampleActivity(ServerDevice device) {
        // Go to CameraSampleActivity.
        Toast.makeText(DeviceDiscoveryActivity.this, device.getFriendlyName(), Toast.LENGTH_SHORT) //
                .show();

        // Set target ServerDevice instance to control in Activity.
        SampleApplication app = (SampleApplication) getApplication();
        app.setTargetServerDevice(device);
        Intent intent = new Intent(this, SampleCameraActivity.class);
        startActivity(intent);
    }
    //endregion LaunchActivity
    //==============================================================================================

    //==============================================================================================
    //region ListAdapter
    /**
     * Adapter class for DeviceList
     */
    private static class DeviceListAdapter extends BaseAdapter {

        private final List<ServerDevice> mDeviceList;

        private final LayoutInflater mInflater;

        public DeviceListAdapter(Context context) {
            mDeviceList = new ArrayList<ServerDevice>();
            mInflater = LayoutInflater.from(context);
        }

        public void addDevice(ServerDevice device) {
            mDeviceList.add(device);
            notifyDataSetChanged();
        }

        public void clearDevices() {
            mDeviceList.clear();
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mDeviceList.size();
        }

        @Override
        public Object getItem(int position) {
            return mDeviceList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0; // not fine
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            TextView textView = (TextView) convertView;
            if (textView == null) {
                textView = (TextView) mInflater.inflate(R.layout.device_list_item, parent, false);
            }
            ServerDevice device = (ServerDevice) getItem(position);
            ApiService apiService = device.getApiService("camera");
            String endpointUrl = null;
            if (apiService != null) {
                endpointUrl = apiService.getEndpointUrl();
            }

            // Label
            String htmlLabel =
                    String.format("%s ", device.getFriendlyName()) //
                            + String.format(//
                                    "<br><small>Endpoint URL:  <font color=\"blue\">%s</font></small>", //
                                    endpointUrl);
            textView.setText(Html.fromHtml(htmlLabel));

            return textView;
        }
    }
    //endregion ListAdapter
    //==============================================================================================

    //==============================================================================================
    //region Buttons
    public void onClickCameraWifiButton(View view) {
        String networkSSID = "DIRECT-KIE0:ILCE-5000";
        String networkPass = "N3sN7rTv";

        WifiConfiguration conf = new WifiConfiguration();
        conf.SSID = "\"" + networkSSID + "\"";
        conf.preSharedKey = "\""+ networkPass +"\"";

        WifiManager wifiManager = (WifiManager)
                getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            wifiManager.addNetwork(conf);
        }

        mCheckWifiConnection = true;
        runPeriodicWifiTask();

        List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();
        for( WifiConfiguration i : list ) {
            if(i.SSID != null && i.SSID.equals("\"" + networkSSID + "\"")) {
                Timber.d("--- CameraWiFi: connecting to WiFi! " + networkSSID);
                mShouldConnectToWifi = "\"" + networkSSID + "\"";
                wifiManager.disconnect();
                wifiManager.enableNetwork(i.networkId, true);
                wifiManager.reconnect();

                break;
            }
        }

        // TODO: check which WiFi I'm connected to after pressing the button and successfully
        // connecting to a wifi - might not be the correct one
    }

    public void onClickPrinterWifiButton(View view) {
        String networkSSID = "3WebCube6134";
        String networkPass = "supergine";

        WifiConfiguration conf = new WifiConfiguration();
        conf.SSID = "\"" + networkSSID + "\"";
        conf.preSharedKey = "\""+ networkPass +"\"";

        WifiManager wifiManager = (WifiManager)
                getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            wifiManager.addNetwork(conf);
        }

        mCheckWifiConnection = true;
        runPeriodicWifiTask();

        List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();
        for( WifiConfiguration i : list ) {
            if(i.SSID != null && i.SSID.equals("\"" + networkSSID + "\"")) {
                Timber.d("--- PrinterWiFi: connecting to WiFi! " + networkSSID);
                mShouldConnectToWifi = "\"" + networkSSID + "\"";
                wifiManager.disconnect();
                wifiManager.enableNetwork(i.networkId, true);
                wifiManager.reconnect();

                break;
            }
        }

        // TODO: check which WiFi I'm connected to after pressing the button and successfully
        // connecting to a wifi - might not be the correct one
    }
    //endregion Buttons
    //==============================================================================================

    //==============================================================================================
    //region Tasks
    private void runPeriodicWifiTask() {
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                ConnectivityManager connectivityManager = (ConnectivityManager)
                        getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo networkInfo = connectivityManager
                        .getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                if (networkInfo.isConnected()) {
                    WifiManager wifiManager = (WifiManager) getApplicationContext()
                            .getSystemService(Context.WIFI_SERVICE);
                    WifiInfo connectionInfo = wifiManager.getConnectionInfo();
                    if (connectionInfo != null && !TextUtils.isEmpty(connectionInfo.getSSID())) {
                        Timber.d("--- Connected to: " + connectionInfo.getSSID() +
                                ", should connect to: " + mShouldConnectToWifi);
                        if (mShouldConnectToWifi.equals(connectionInfo.getSSID())) {
                            mCheckWifiConnection = false;

                            if (connectionInfo.getSSID().equals("\"DIRECT-KIE0:ILCE-5000\"")) {
                                if (!mSsdpClient.isSearching()) {
                                    searchDevices();
                                }
                            }
                        }
                    }
                }
                // TODO this keeps running even when the app shuts down, so shut down this task too.
                if (mCheckWifiConnection) {
                    runPeriodicWifiTask();
                }
            }
        },200);
    }
    //endregion Tasks
    //==============================================================================================
}
