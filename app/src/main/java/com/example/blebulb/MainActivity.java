package com.example.blebulb;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    static String TAG = "debugger";

    boolean beeping = false;

    ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            Log.d(TAG, "onScanResult: " + result.getDevice().toString());

            bluetoothDevice = result.getDevice();
            connect(bluetoothDevice);
        }
    };

    static String UID = "df950776-94fa-7314-a4c3-fbcc424260c9";
    static String BEEP = "EC958823-F26E-43A9-927C-7E17D8F32A90";
    static String BULB = "FB959362-F26E-43A9-927C-7E17D8FB2D8D";
    static String TEMP = "0CED9345-B31F-457D-A6A2-B3DB9B03E39A";

    BluetoothAdapter mBluetoothAdapter;
    BluetoothManager bluetoothManager;
    BluetoothGatt mBluetoothGatt;
    BluetoothGattService bluetoothGattService;
    BluetoothDevice bluetoothDevice;

    boolean mScanning;
    Button on, off, beep, search;
    TextView temp, ble_status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        on = findViewById(R.id.button3);
        off = findViewById(R.id.button4);
        search = findViewById(R.id.button);
        beep = findViewById(R.id.button2);

        temp = findViewById(R.id.tv_temp);
        ble_status = findViewById(R.id.textView);

        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkPermissions();
            }
        });

        on.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bluetoothGattService != null) {
                    writeData(bluetoothGattService.getCharacteristic(UUID.fromString(BULB)), 1);
                } else {
                    Toast.makeText(MainActivity.this, "Please search for peripheral first!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        off.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bluetoothGattService != null) {
                    writeData(bluetoothGattService.getCharacteristic(UUID.fromString(BULB)), 0);
                } else {
                    Toast.makeText(MainActivity.this, "Please search for peripheral first!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        beep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bluetoothGattService != null) {
                    if (beeping) {
                        writeData(bluetoothGattService.getCharacteristic(UUID.fromString(BEEP)), 0);
                    } else {
                        writeData(bluetoothGattService.getCharacteristic(UUID.fromString(BEEP)), 1);
                    }
                    beeping = !beeping;
                } else {
                    Toast.makeText(MainActivity.this, "Please search for peripheral first!", Toast.LENGTH_SHORT).show();
                }
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mBluetoothGatt != null) {
            mBluetoothGatt.setCharacteristicNotification(bluetoothGattService.getCharacteristic(UUID.fromString(TEMP)), true);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Please enable bluetooth and location!", Toast.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN}, 101);
        } else {
            if (!mBluetoothAdapter.isEnabled()) {
                Toast.makeText(MainActivity.this, "Please enable bluetooth and location!", Toast.LENGTH_SHORT).show();
            } else{
                scanLeDevice();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
            bluetoothGattService = null;
        }
    }

    public void writeData(BluetoothGattCharacteristic characteristic, int value) {
        if (mBluetoothGatt == null) return;

        byte[] a = new byte[1];
        a[0] = (byte) value;
        characteristic.setValue(a);

        mBluetoothGatt.writeCharacteristic(characteristic);
    }

    public void readData(BluetoothGattCharacteristic characteristic) {
        String uuid = characteristic.getUuid().toString();
        if (uuid.equalsIgnoreCase(BEEP)) {
            mBluetoothGatt.readCharacteristic(characteristic);
        }
    }

    private void scanLeDevice() {
        if (!mScanning) {

            if(mBluetoothGatt != null){
                Toast.makeText(MainActivity.this, "Already connected!", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(MainActivity.this, "Searching...", Toast.LENGTH_SHORT).show();

            List<ScanFilter> scanFilterList = new ArrayList<>();

            ScanFilter filter = new ScanFilter.Builder()
                    .setServiceUuid(new ParcelUuid(UUID.fromString(UID)))
                    .build();
            scanFilterList.add(filter);

            ScanSettings scanSettings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .build();

            mBluetoothAdapter.getBluetoothLeScanner().startScan(scanFilterList, scanSettings, leScanCallback);

        } else {
            Toast.makeText(MainActivity.this, "Searching stopped...!", Toast.LENGTH_SHORT).show();

            mBluetoothAdapter.getBluetoothLeScanner().stopScan(leScanCallback);
        }

        mScanning = !mScanning;

    }

    public void connect(BluetoothDevice device) {
        mBluetoothAdapter.getBluetoothLeScanner().stopScan(leScanCallback);

        BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        Log.d(TAG, "onConnectionStateChange: connected");
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "Bluetooth Connected", Toast.LENGTH_SHORT).show();
                            ble_status.setText("Connected");
                            search.setEnabled(false);
                        });

                        gatt.discoverServices();
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        Log.d(TAG, "onConnectionStateChange: disconnected");
                        scanLeDevice();
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "Bluetooth Disconnected", Toast.LENGTH_SHORT).show();
                            temp.setText("");
                            search.setEnabled(true);
                            ble_status.setText("Not Connected");
                        });
                        break;
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                bluetoothGattService = mBluetoothGatt.getService(UUID.fromString(UID));
                gatt.setCharacteristicNotification(bluetoothGattService.getCharacteristic(UUID.fromString(TEMP)), true);
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicRead(gatt, characteristic, status);

                byte[] data = characteristic.getValue();
                if (data != null && data.length > 0) {
                    String str = new String(data, StandardCharsets.UTF_8);
                    runOnUiThread(() -> beep.setText(str));

                    Log.d(TAG, "onCharacteristicRead: " + str);
                }
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicWrite(gatt, characteristic, status);

                Log.d(TAG, "onCharacteristicWrite: " + status);
                readData(characteristic);
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);

                byte[] data = characteristic.getValue();
                if (data != null && data.length > 0) {
                    String str = new String(data, StandardCharsets.UTF_8);
                    runOnUiThread(() -> temp.setText(str + " F"));

                    Log.d(TAG, "onCharacteristicChanged: " + str);

                }
            }
        };

        mBluetoothGatt = device.connectGatt(MainActivity.this, false, gattCallback);
    }

}