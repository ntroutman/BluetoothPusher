package org.kingsschools.cyberknights4911.bluetoothpusher;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.appindexing.Thing;
import com.google.android.gms.common.api.GoogleApiClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final String TAG = "CyberKnightBluePusher";
    public static final String SINGLE_TEST_FILE = "single-file.txt";
    public static final String IMG_FILE = "img.png";
    public static final String STATS_JSON_FILE = "stats.json";
    public static final String MATCH_23_DIR = "2017-01-16_match-23";
    private TextView textView_log;
    private TextView textView_targetDevice;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothPusherService filePusher;
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // used for debugging
        this.textView_log = (TextView) findViewById(R.id.textView_Log);
        this.textView_targetDevice = (TextView) findViewById(R.id.textView_TargetDevice);

        // get the bluetooth adapter so that we can use it
        this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        // create our new file pusher with a really basic handler that
        // just logs all the messages it receives back
        this.filePusher = new BluetoothPusherService(new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                log(msg.getData().getString("msg"));
            }
        });

        createTestFiles();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }

    /**
     * Create some test files to use for testing pushing a file and directory if they don't exist
     */
    private void createTestFiles() {
        try {
            File matchDir = new File(getFilesDir(), MATCH_23_DIR);
            if (matchDir.exists()) {
                Log.i(TAG, "Match Dir Exists: " + matchDir);
                return;
            }

            matchDir.mkdir();
            File jsonMatch = new File(matchDir, STATS_JSON_FILE);
            try (FileOutputStream stream = new FileOutputStream(jsonMatch)) {
                stream.write("{\"date\":\"2017-01-16\"}".getBytes(StandardCharsets.UTF_8));
            }


            File matchImg = new File(matchDir, IMG_FILE);
            try (FileOutputStream stream = new FileOutputStream(matchImg)) {
                writeRandomImage(stream);
            }

            matchDir.mkdir();
            File singleFile = new File(getFilesDir(), SINGLE_TEST_FILE);
            try (FileOutputStream stream = new FileOutputStream(singleFile)) {
                stream.write("I'm a random file in the root directory!".getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed writing test data", e);
        }
    }

    private void writeRandomImage(FileOutputStream out) {
        //image dimension
        int width = 640;
        int height = 320;
        //create buffered image object img
        Bitmap img = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        //file object
        File f = null;
        //create random image pixel by pixel
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int a = (int) (Math.random() * 256); //alpha
                int r = (int) (Math.random() * 256); //red
                int g = (int) (Math.random() * 256); //green
                int b = (int) (Math.random() * 256); //blue

                int p = (a << 24) | (r << 16) | (g << 8) | b; //pixel

                img.setPixel(x, y, p);
            }
        }
        //write image
        img.compress(Bitmap.CompressFormat.PNG, 100, out);
    }

    /**
     * Finds a device to connect to with the given name
     *
     * @return
     */
    protected BluetoothDevice findHardcodedDevice(String targetDeviceName) {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address


                if (deviceName.equals(targetDeviceName)) {
                    textView_targetDevice.setText(deviceName + " - " + deviceHardwareAddress);
                    log(deviceName + " - " + deviceHardwareAddress);
                    return device;
                }
            }
        }

        throw new IllegalStateException("Unable to find targe device");
    }

    protected void sendButton_Clicked(View v) {
//        new BluetoothDeviceManager(getApplicationContext()).pickDevice(
//                new BluetoothDeviceManager.BluetoothDevicePickResultHandler() {
//                    @Override
//                    public void onDevicePicked(BluetoothDevice device) {
//                        BluetoothPusherService.ConnectedPusher thread = filePusher.connect(device);
//                        thread.sendFile(new File(getFilesDir(), SINGLE_TEST_FILE));
//                    }
//                });

        BluetoothPusherService.ConnectedPusher thread = filePusher.connect(findHardcodedDevice("TROUTDESK"));
        thread.sendDirectory(new File(getFilesDir(), MATCH_23_DIR));
    }

    private void log(String line) {
        textView_log.setText(new Date() + ": " + line + "\n" + textView_log.getText());
    }

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    public Action getIndexApiAction() {
        Thing object = new Thing.Builder()
                .setName("Main Page") // TODO: Define a title for the content shown.
                // TODO: Make sure this auto-generated URL is correct.
                .setUrl(Uri.parse("http://[ENTER-YOUR-URL-HERE]"))
                .build();
        return new Action.Builder(Action.TYPE_VIEW)
                .setObject(object)
                .setActionStatus(Action.STATUS_TYPE_COMPLETED)
                .build();
    }

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        AppIndex.AppIndexApi.start(client, getIndexApiAction());
    }

    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        AppIndex.AppIndexApi.end(client, getIndexApiAction());
        client.disconnect();
    }
}
