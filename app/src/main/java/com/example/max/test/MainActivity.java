package com.example.max.test;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class MainActivity extends AppCompatActivity {

    /***********************
     *  Layout parameters  *
     ***********************/

    // frame width
    private static final int FRAME_W = 450;
    // frame height
    private static final int FRAME_H = 450;
    // number of frames
    private static final int NB_FRAMES = 8;
    // nb of frames in x
    private static final int COUNT_X = 1;
    // nb of frames in y
    private static final int COUNT_Y = 8;
    // frame duration
    // we can slow animation by changing frame duration
    private static final int FRAME_DURATION = 200; // in ms !
    // scale factor for each frame
    private static final int SCALE_FACTOR = 5;
    private ImageView img;
    // stores each frame
    private Bitmap[] bmps;

    private int totalTurns = 0;
    private int finished = 0;
    private int prevFinished = 0;
    private Stopwatch timer = new Stopwatch();
    private Stopwatch turnTimer = new Stopwatch();

    /***********************
     *    BLE parameters   *
     ***********************/

    private static final String TAG = "MainActivity";

    private static final String DEVICE_NAME = "SensorTag";

    /* magneto Service */
    private static final UUID MAGNETO_SERVICE = UUID.fromString("F000AA80-0451-4000-B000-000000000000");
    private static final UUID MAGNETO_DATA_CHAR = UUID.fromString("f000aa81-0451-4000-b000-000000000000");
    private static final UUID MAGNETO_CONFIG_CHAR = UUID.fromString("f000aa82-0451-4000-b000-000000000000");
    /* Client Configuration Descriptor */
    private static final UUID CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private BluetoothAdapter mBluetoothAdapter;
    private SparseArray<BluetoothDevice> mDevices;

    private BluetoothGatt mGatt;

    private TextView mTurns;

    private ProgressDialog mProgress;

    private int REQUEST_ENABLE_BT = 1;
    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;


    @SuppressLint("NewApi") @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /************
         *  Layout  *
         ************/

        setContentView(R.layout.activity_main);
        img = (ImageView) findViewById(R.id.img);

        // load bitmap from assets
        Bitmap birdBmp = getBitmapFromAssets(this, "spritesheet.png");

        if (birdBmp != null) {
            // cut bitmaps from bird bmp to array of bitmaps
            bmps = new Bitmap[NB_FRAMES];
            int currentFrame = 0;

            for (int i = 0; i < COUNT_Y; i++) {
                for (int j = 0; j < COUNT_X; j++) {
                    bmps[currentFrame] = Bitmap.createBitmap(birdBmp, FRAME_W
                            * j, FRAME_H * i, FRAME_W, FRAME_H);

                    if (++currentFrame >= NB_FRAMES) {
                        break;
                    }
                }
            }

            // create animation programmatically
            final AnimationDrawable animation = new AnimationDrawable();
            animation.setOneShot(false); // repeat animation

            for (int i = 0; i < NB_FRAMES; i++) {
                animation.addFrame(new BitmapDrawable(getResources(), bmps[i]),
                        FRAME_DURATION);
            }

            // load animation on image
            if (Build.VERSION.SDK_INT < 16) {
                img.setBackgroundDrawable(animation);
            } else {
                img.setBackground(animation);
            }

            // start animation on image
            img.post(new Runnable() {

                @Override
                public void run() {
                    animation.start();
                }

            });

            ImageView replay = (ImageView) findViewById(R.id.replay);
            replay.setOnClickListener(new Button.OnClickListener() {
                @Override
                public void onClick(View v) {
                    reset();
                }
            });

            /*********
             *  BLE  *
             *********/

            setProgressBarIndeterminate(true);

            // Find the toolbar view inside the activity layout
            Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
            // Sets the Toolbar to act as the ActionBar for this Activity window.
            // Make sure the toolbar exists in the activity and is not null
            setSupportActionBar(toolbar);


            /*
             * We are going to display the results in some text fields
             */
            mTurns = (TextView) findViewById(R.id.turns);

            //Check if BLE is supported
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                Toast.makeText(this, "BLE Not Supported",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
            final BluetoothManager bluetoothManager =
                    (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = bluetoothManager.getAdapter();

            mDevices = new SparseArray<BluetoothDevice>();


            /*
             * A progress dialog will be needed while the connection process is
             * taking place
             */
            mProgress = new ProgressDialog(this);
            mProgress.setIndeterminate(true);
            mProgress.setCancelable(false);

        }
    }

    /***********************
     *  Layout functions  *
     ***********************/

    private Bitmap getBitmapFromAssets(MainActivity mainActivity,
                                       String filepath) {

        Drawable spritesheet = ResourcesCompat.getDrawable(getResources(), R.drawable.spritesheet, null);
        Bitmap bitmap = ((BitmapDrawable)spritesheet).getBitmap();

        return bitmap;
    }

    /*******************
     *  BLE functions  *
     *******************/

    @Override
    protected void onResume() {
        super.onResume();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            if (Build.VERSION.SDK_INT >= 21) {
                mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
                settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
                filters = new ArrayList<ScanFilter>();
            }
        }

        clearDisplayValues();
    }

    @Override
    protected void onPause() {
        super.onPause();

        //Make sure dialog is hidden
        mProgress.dismiss();
        //Cancel any scans in progress
        mHandler.removeCallbacks(mStopRunnable);
        mHandler.removeCallbacks(mStartRunnable);

        if (Build.VERSION.SDK_INT < 21) {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        } else {
            mLEScanner.stopScan(mScanCallback);

        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        //Disconnect from any active tag connection
        if (mGatt != null) {
            mGatt.close();
            mGatt = null;
        }
    }

    @Override
    protected void onDestroy() {
        if (mGatt == null) {
            return;
        }
        mGatt.close();
        mGatt = null;
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                //Bluetooth not enabled.
                finish();
                return;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    // Menu icons are inflated just as they were with actionbar
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        //Add any device elements we've discovered to the overflow menu
        for (int i=0; i < mDevices.size(); i++) {
            BluetoothDevice device = mDevices.valueAt(i);
            menu.add(0, mDevices.keyAt(i), 0, device.getName());
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.bluetoothSearch:
                mDevices.clear();
                startScan();
                //scanLeDevice(true);

                return true;
            default:
                //Obtain the discovered device to connect with
                BluetoothDevice device = mDevices.get(item.getItemId());
                Log.i(TAG, "Connecting to " + device.getName());
                /*
                 * Make a connection with the device
                 */

                connectToDevice(device);
                //Display progress UI
                mHandler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Connecting to " + device.getName() + "..."));
                return super.onOptionsItemSelected(item);
        }
    }

    private void clearDisplayValues() {
        mTurns.setText("");
    }

    private Runnable mStopRunnable = new Runnable() {
        @Override
        public void run() {
            stopScan();
        }
    };
    private Runnable mStartRunnable = new Runnable() {
        @Override
        public void run() {
            startScan();
        }
    };

    private void startScan() {
        if (Build.VERSION.SDK_INT < 21) {
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mLEScanner.startScan(filters, settings, mScanCallback);
        }
        setProgressBarIndeterminateVisibility(true);

        mHandler.postDelayed(mStopRunnable, 2500);
    }

    private void stopScan() {
        if (Build.VERSION.SDK_INT < 21) {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        } else {
            mLEScanner.stopScan(mScanCallback);

        }
        setProgressBarIndeterminateVisibility(false);
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    Log.i("onLeScan", device.toString());

                    mDevices.put(device.hashCode(), device);
                    //Update the overflow menu
                    invalidateOptionsMenu();
                }
            };

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.i("callbackType", String.valueOf(callbackType));
            Log.i("result", result.toString());
            BluetoothDevice btDevice = result.getDevice();

            mDevices.put(btDevice.hashCode(), btDevice);
            //Update the overflow menu
            invalidateOptionsMenu();
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult sr : results) {
                Log.i("ScanResult - Results", sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("Scan Failed", "Error Code: " + errorCode);
        }
    };


    public void connectToDevice(BluetoothDevice device) {
        if (mGatt == null) {
            mGatt = device.connectGatt(this, false, gattCallback);
            stopScan();
        }
    }


    /*
     * In this callback, we've created a bit of a state machine to enforce that only
     * one characteristic be read or written at a time until all of our sensors
     * are enabled and we are registered to get notifications.
     */
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        /* State Machine Tracking */
        private int mState = 0;

        private void reset() { mState = 0; }

        private void advance() { mState++; }

        /*
         * Send an enable command to each sensor by writing a configuration
         * characteristic.  This is specific to the SensorTag to keep power
         * low by disabling sensors you aren't using.
         */
        private void enableNextSensor(BluetoothGatt gatt) {
            BluetoothGattCharacteristic characteristic;
            switch (mState) {
                case 0:
                    Log.d(TAG, "Enabling magneto");
                    characteristic = gatt.getService(MAGNETO_SERVICE)
                            .getCharacteristic(MAGNETO_CONFIG_CHAR);
                    characteristic.setValue(new byte[] {(byte)0x7F, (byte)0x00});
                    break;
                default:
                    mHandler.sendEmptyMessage(MSG_DISMISS);
                    Log.i(TAG, "All Sensors Enabled");
                    return;
            }

            gatt.writeCharacteristic(characteristic);
        }

        /*
         * Read the data characteristic's value for each sensor explicitly
         */
        private void readNextSensor(BluetoothGatt gatt) {
            BluetoothGattCharacteristic characteristic;
            switch (mState) {
                case 0:
                    Log.d(TAG, "Reading magneto");
                    characteristic = gatt.getService(MAGNETO_SERVICE)
                            .getCharacteristic(MAGNETO_DATA_CHAR);
                    break;
                default:
                    mHandler.sendEmptyMessage(MSG_DISMISS);
                    Log.i(TAG, "All Sensors Enabled");
                    return;
            }

            gatt.readCharacteristic(characteristic);
        }


        /*
         * Enable notification of changes on the data characteristic for each sensor
         * by writing the ENABLE_NOTIFICATION_VALUE flag to that characteristic's
         * configuration descriptor.
         */
        private void setNotifyNextSensor(BluetoothGatt gatt) {
            BluetoothGattCharacteristic characteristic;
            switch (mState) {
                case 0:
                    Log.d(TAG, "Set notify magneto");
                    characteristic = gatt.getService(MAGNETO_SERVICE)
                            .getCharacteristic(MAGNETO_DATA_CHAR);
                    break;
                default:
                    mHandler.sendEmptyMessage(MSG_DISMISS);
                    Log.i(TAG, "All Sensors Enabled");
                    return;
            }

            //Enable local notifications
            gatt.setCharacteristicNotification(characteristic, true);
            //Enabled remote notifications
            BluetoothGattDescriptor desc = characteristic.getDescriptor(CONFIG_DESCRIPTOR);
            desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(desc);
        }


        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i("onConnectionStateChange", "Status: " + status);
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                /*
                 * Once successfully connected, we must next discover all the services on the
                 * device before we can read and write their characteristics.
                 */
                gatt.discoverServices();
                mHandler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Discovering Services..."));
            } else if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED) {
                /*
                 * If at any point we disconnect, send a message to clear the weather values
                 * out of the UI
                 */
                mHandler.sendEmptyMessage(MSG_CLEAR);
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                /*
                 * If there is a failure at any stage, simply disconnect
                 */
                gatt.close();
            }

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            List<BluetoothGattService> services = gatt.getServices();
            Log.i("onServicesDiscovered", services.toString());

            mHandler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Enabling Sensors..."));
            /*
             * With services discovered, we are going to reset our state machine and start
             * working through the sensors we need to enable
             */
            reset();
            enableNextSensor(gatt);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.i("onCharacteristicRead", characteristic.toString());
            //For each read, pass the data up to the UI thread to update the display
            if (MAGNETO_DATA_CHAR.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_MAGNETO, characteristic));
            }

            //After reading the initial value, next we enable notifications
            setNotifyNextSensor(gatt);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            //After writing the enable flag, next we read the initial value
            readNextSensor(gatt);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            /*
             * After notifications are enabled, all updates from the device on characteristic
             * value changes will be posted here.  Similar to read, we hand these up to the
             * UI thread to update the display.
             */
            if (MAGNETO_DATA_CHAR.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_MAGNETO, characteristic));
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            //Once notifications are enabled, we move to the next sensor and start over with enable
            advance();
            enableNextSensor(gatt);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            Log.d(TAG, "Remote RSSI: " + rssi);
        }

        private String connectionState(int status) {
            switch (status) {
                case BluetoothProfile.STATE_CONNECTED:
                    return "Connected";
                case BluetoothProfile.STATE_DISCONNECTED:
                    return "Disconnected";
                case BluetoothProfile.STATE_CONNECTING:
                    return "Connecting";
                case BluetoothProfile.STATE_DISCONNECTING:
                    return "Disconnecting";
                default:
                    return String.valueOf(status);
            }
        }
    };


    /*
     * We have a Handler to process event results on the main thread
     */
    private static final int MSG_HUMIDITY = 101;
    private static final int MSG_PRESSURE = 102;
    private static final int MSG_PRESSURE_CAL = 103;
    private static final int MSG_MAGNETO = 104;
    private static final int MSG_PROGRESS = 201;
    private static final int MSG_DISMISS = 202;
    private static final int MSG_CLEAR = 301;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            BluetoothGattCharacteristic characteristic;
            switch (msg.what) {

                case MSG_MAGNETO:
                    characteristic = (BluetoothGattCharacteristic) msg.obj;
                    if (characteristic.getValue() == null) {
                        Log.w(TAG, "Error obtaining magneto value");
                        return;
                    }
                    updateMagnetoValues(characteristic);
                    break;
                case MSG_PROGRESS:
                    mProgress.setMessage((String) msg.obj);
                    if (!mProgress.isShowing()) {
                        mProgress.show();
                    }
                    break;
                case MSG_DISMISS:
                    mProgress.hide();
                    break;
                case MSG_CLEAR:
                    clearDisplayValues();
                    break;
            }
        }
    };

    /* Methods to extract sensor data and update the UI */

    ArrayList<Double> magnets = new ArrayList<>();
    double average = 0;
    int wait5 = 0;
    long prevTurnTimer = 0;

    private void updateMagnetoValues(BluetoothGattCharacteristic characteristic) {

        if (totalTurns < 471) {
            double magnet = SensorTagData.extractMagnetoX(characteristic);
            magnet = Math.abs(magnet);

            if (wait5 == 0) {

                if (magnet > average + 300 ||  magnet < average - 300) {
                    if(totalTurns == 1){
                        timer.start();
                        turnTimer.start();
                    }

                    Boolean skippedTurn = false;
                    turnTimer.stop();
                    if(turnTimer.getElapsedTime() < 3 * prevTurnTimer && turnTimer.getElapsedTime() > 1.5 * prevTurnTimer){
                        skippedTurn = true;
                    }
                    else {
                        prevTurnTimer = turnTimer.getElapsedTime();
                    }
                    turnTimer.start();

                    if(skippedTurn) {
                        totalTurns ++;
                    }

                    wait5 = 6;

                    totalTurns ++;

                    ViewGroup.MarginLayoutParams lpimg = (ViewGroup.MarginLayoutParams) img.getLayoutParams();
                    lpimg.leftMargin = (int) (Math.round(totalTurns * 1.7) -100);
                    img.setLayoutParams(lpimg);

                    mTurns.setText("" + totalTurns);
                }
            }
            else {
                wait5 --;
            }

            if (magnets.size() >= 5) {
                magnets.remove(0);
            }
            magnets.add(magnet);
            double sum = 0;
            for (int i = 0; i < magnets.size(); i++) {
                sum += magnets.get(i);
            }
            average = sum/magnets.size();
        }
        else {
            //finished
            finished = 1;
            if (finished == 1 && prevFinished == 0) {
                prevFinished = 1;

                timer.stop();

                showDialog();
            }
        }
    }

    protected void showDialog() {

        // get prompts.xml view
        LayoutInflater layoutInflater = LayoutInflater.from(MainActivity.this);
        View promptView = layoutInflater.inflate(R.layout.popup, null);
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
        alertDialogBuilder.setView(promptView);

        TextView distance = (TextView) promptView.findViewById(R.id.dist);
        TextView time = (TextView) promptView.findViewById(R.id.time);

        distance.setText((totalTurns * 2.125) + "m");
        time.setText(timer.getElapsedTime() + "ms");

        // setup a dialog window
        alertDialogBuilder.setCancelable(false)
                .setPositiveButton("Opnieuw", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        reset();
                    }
                });

        // create an alert dialog
        AlertDialog alert = alertDialogBuilder.create();
        alert.show();
    }

    protected void reset() {
        finished = 0;
        prevFinished = 0;
        totalTurns = 0;
        wait5 = 0;

        timer.stop();

        ViewGroup.MarginLayoutParams lpimg = (ViewGroup.MarginLayoutParams) img.getLayoutParams();
        lpimg.leftMargin = (int) (Math.round(totalTurns * 1.7) -100);
        img.setLayoutParams(lpimg);

    }
}