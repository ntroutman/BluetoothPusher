package org.kingsschools.cyberknights4911.bluetoothpusher;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;

/**
 * This is a bluetooth service that allows for pushing files and directories over bluetooth to
 * receiving server.
 */
public class BluetoothPusherService {
    // Uniquely identifies the CyberKnight Pusher Service to the receiving device. These must match on the
    // client and server for  succesfully Bluetooth RFComm connection to be made.
    public static final UUID CYBERKNIGHT_BLUETOOTH_PUSHER_UUID = UUID.fromString("2d31ac7d-0d4a-48dd-8136-2f6a9b71a3f4");

    public static final String TAG = "BluetoothPusher";
    private Handler mHandler;

    /**
     * Defines constants used when transmitting messages between the
     * service and the UI via a Handler.
     */
    public interface StatusMessageTypes {
        public static final int SEND_SUCCESS = 0;
        public static final int SEND_FAILED = 1;
        public static final int LOG = 2;
    }


    /**
     * @param handler a handler service to publish notifications over such as
     *                status updates during file pushes.
     */
    public BluetoothPusherService(Handler handler) {
        this.mHandler = handler;
    }


    public ConnectedDevice connect(BluetoothDevice mDevice) {
        try {
            ConnectedDevice pusher = new ConnectedDevice(mDevice, mHandler);
            return pusher;
        } catch (IOException e) {
            throw new BluetoothConnectionException("Error connecting to device: " + mDevice.getName(), e);
        }
    }
}
