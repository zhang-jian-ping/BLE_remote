package com.diasemi.bleremote.ui.start;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.diasemi.bleremote.BLERemoteApplication;
import com.diasemi.bleremote.Constants;
import com.diasemi.bleremote.R;
import com.diasemi.bleremote.RuntimePermissionChecker;
import com.diasemi.bleremote.Utils;
import com.diasemi.bleremote.manager.CacheManager;
import com.diasemi.bleremote.service.BleRemoteService;
import com.diasemi.bleremote.ui.BleRemoteBaseActivity;
import com.diasemi.bleremote.ui.main.MainActivity;

import java.util.ArrayList;
import java.util.Objects;

import butterknife.ButterKnife;
import butterknife.InjectView;

public class StartActivity extends BleRemoteBaseActivity implements OnItemClickListener {

    private static final String TAG = StartActivity.class.getSimpleName();

    private static final int REQUEST_LOCATION_PERMISSION = 1;

    @InjectView(R.id.view_container)
    LinearLayout mViewContainer;
    @InjectView(R.id.progress_container)
    View mProgressView;
    @InjectView(R.id.status_message)
    TextView mStatusMessageView;
    @InjectView(android.R.id.list)
    ListView mListView;
    @InjectView(android.R.id.empty)
    TextView mEmptyTextView;

    private ScanAdapter mAdapter;
    private Handler mScanHandler = new Handler();
    private ArrayList<ScanItem> mDeviceList;
    private ScanItem pendingConnection;
    private BLERemoteApplication application;
    private boolean isScanning;
    private Runnable scanRunnable;
    private RuntimePermissionChecker permissionChecker;

    // LIFE CYCLE METHOD(S)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
        ButterKnife.inject(this);
        application = (BLERemoteApplication) getApplication();

        permissionChecker = new RuntimePermissionChecker(this, savedInstanceState);
        permissionChecker.setOneTimeRationale(getString(R.string.location_permission_rationale));
        permissionChecker.registerPermissionRequestCallback(REQUEST_LOCATION_PERMISSION, new RuntimePermissionChecker.PermissionRequestCallback() {
            @Override
            public void onPermissionRequestResult(int requestCode, String[] permissions, String[] denied) {
                if (denied == null)
                    startScan();
            }
        });

        mListView.setOnItemClickListener(this);
        mDeviceList = new ArrayList<>();
        CacheManager.clearCaches(this);

        if (mDeviceList.isEmpty()) {
            mListView.setEmptyView(mEmptyTextView);
        }
        mAdapter = new ScanAdapter(this, R.layout.scan_item_row, mDeviceList);
        mListView.setAdapter(mAdapter);
        mAdapter.notifyDataSetChanged();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.app_name);
            actionBar.setSubtitle(R.string.dialog_semiconductor);
        }
    }

    @Override
    public void onServiceConnected(final ComponentName componentName, final IBinder service) {
        super.onServiceConnected(componentName, service);
        startScan();
    }

    @Override
    protected void onDestroy() {
        mBleRemoteService.stopScan();
        mScanHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingConnection == null)
            Utils.showProgress(this, mViewContainer, mProgressView, false);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        permissionChecker.saveState(outState);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        permissionChecker.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onBackPressed() {
        if (pendingConnection != null) {
            cancelPendingConnection();
            return;
        }
        super.onBackPressed();
    }

    // OPTION MENU METHOD(S)

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_start, menu);
        menu.findItem(R.id.action_scan).setTitle(isScanning ? "Stop scan" : "Scan");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will automatically handle clicks on
        // the Home/Up button, so long as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_scan:
                if (isScanning) {
                    stopScan();
                } else {
                    startScan();
                }
                return true;
            case R.id.infoButton:
                Intent intent = new Intent(this, InfoActivity.class);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onItemClick(final AdapterView<?> adapterView, final View view, final int position, final long id) {
        stopScan();
        Utils.showProgress(this, mViewContainer, mProgressView, true);
        mStatusMessageView.setText(R.string.text_connecting);
        pendingConnection = mDeviceList.get(position);
        mBleRemoteService.connect(pendingConnection.btAddress);
    }

    @Override
    protected void onDeviceConnected(BluetoothDevice device) {
        mStatusMessageView.setText(R.string.text_initializing);
        application.scanItem = pendingConnection;
        mBleRemoteService.checkBluetoothPrivileged(new BleRemoteService.BluetoothPrivilegedCallback() {
            @Override
            public void noBluetoothPrivileged() {
                new AlertDialog.Builder(StartActivity.this)
                        .setTitle(R.string.no_bluetooth_privileged_title)
                        .setMessage(R.string.no_bluetooth_privileged_msg)
                        .setPositiveButton(android.R.string.ok, null)
                        .setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                cancelPendingConnection();
                            }
                        })
                        .show();
            }
        });
    }

    @Override
    protected void onDeviceReady(BluetoothDevice device) {
        if (pendingConnection != null) {
            pendingConnection = null;
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            overridePendingTransition(0, 0);
        }
    }

    @Override
    protected void onDeviceDisconnected(BluetoothDevice device) {
        if (pendingConnection != null) {
            pendingConnection = null;
            mBleRemoteService.close();
        }
        Utils.showProgress(this, mViewContainer, mProgressView, false);
    }

    @Override
    public void onDeviceFound(BluetoothDevice device, int rssi) {
        String name = device.getName();
        String address = device.getAddress();
        boolean paired = device.getBondState() == BluetoothDevice.BOND_BONDED;
        boolean remove = !paired && rssi == 0;
        ScanItem knownDevice = null;
        for (ScanItem scanItem : mDeviceList) {
            if (Objects.equals(scanItem.btAddress, address)) {
                scanItem.scanSignal = rssi;
                scanItem.paired = paired;
                knownDevice = scanItem;
                break;
            }
        }
        if (knownDevice == null && !remove)
            mDeviceList.add(new ScanItem(R.drawable.scan_icon, name, address, rssi, address, name, paired));
        else if (knownDevice != null && remove)
            mDeviceList.remove(knownDevice);
        mAdapter.notifyDataSetChanged();
    }

    // PRIVATE METHOD(S)

    private void startScan() {
        cancelPendingConnection();
        mDeviceList.clear();
        mAdapter.notifyDataSetChanged();

        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager != null ? manager.getAdapter() : null;
        // Check bluetooth state
        if (adapter == null || !adapter.isEnabled()) {
            Log.e(TAG, "Bluetooth not enabled");
            Intent intent = new Intent(Constants.ACTION_NO_BLUETOOTH);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            return;
        } else {
            // Add known HID devices to scan list
            for (BluetoothDevice device : mBleRemoteService.getHidDevices()) {
                onDeviceFound(device, 0);
            }
        }

        // Start BLE scan
        if (!permissionChecker.checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION, R.string.location_permission_rationale, REQUEST_LOCATION_PERMISSION))
            return;
        Log.d(TAG, "Start scanning");
        isScanning = true;
        invalidateOptionsMenu();
        mBleRemoteService.startScan();
        scanRunnable = new Runnable() {

            public void run() {
                stopScan();
            }
        };
        mScanHandler.postDelayed(scanRunnable, 10000);
    }

    private void stopScan() {
        if (isScanning) {
            Log.d(TAG, "Stop scanning");
            isScanning = false;
            invalidateOptionsMenu();
            mScanHandler.removeCallbacks(scanRunnable);
            mBleRemoteService.stopScan();
        }
    }

    private void cancelPendingConnection() {
        if (pendingConnection == null)
            return;
        pendingConnection = null;
        mBleRemoteService.disconnect();
        mBleRemoteService.close();
        Utils.showProgress(this, mViewContainer, mProgressView, false);
    }
}
