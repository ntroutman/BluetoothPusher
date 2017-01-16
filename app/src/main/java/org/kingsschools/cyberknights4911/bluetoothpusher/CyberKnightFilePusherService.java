package org.kingsschools.cyberknights4911.bluetoothpusher;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32;

/**
 * Created by Nathaniel on 1/14/2017.
 */

public class CyberKnightFilePusherService {

    private static final String TAG = "4911FilePusher";
    private Handler mHandler; // handler that gets info from Bluetooth service
    private Set<ConnectedThread> mThreads = new HashSet<>();

    // Defines several constants used when transmitting messages between the
    // service and the UI.
    public interface UIHandlerMessageTypes{
        public static final int SEND_SUCCESS = 0;
        public static final int SEND_FAILED = 1;
        public static final int LOG = 2;
    }


    public interface FilePushMessage {
        public static final byte TYPE_FILE = 1;

        public static final byte COMPRESSION_NONE = 0;
    }

    public CyberKnightFilePusherService(Handler handler) {
        this.mHandler = handler;
    }


    public ConnectedThread connect(BluetoothDevice device) throws IOException {
        BluetoothSocket socket = device.createRfcommSocketToServiceRecord(UUID.fromString("2d31ac7d-0d4a-48dd-8136-2f6a9b71a3f4"));
        ConnectedThread thread = new ConnectedThread(socket);
        thread.start();
        mThreads.add(thread);
        return thread;
    }

    public void stop() {
        for (ConnectedThread thread : mThreads) {
            thread.cancel();
        }
    }


    public class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final DataOutputStream mmOutStream;
        private byte[] mmWriteBuffer; // mmBuffer store for the stream
        private byte[] mmReadBuffer; // mmBuffer store for the stream

        public ConnectedThread(BluetoothSocket socket) throws IOException {
            logIt("Created thread for: " + socket.getRemoteDevice().getName());
            mmSocket = socket;
            mmSocket.connect();
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                tmpIn = socket.getInputStream();
                if (tmpIn == null) {
                    throw new IllegalStateException("InputStream was null");
                }
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating input stream", e);
            }
            try {
                tmpOut = socket.getOutputStream();
                if (tmpOut == null) {
                    throw new IllegalStateException("InputStream was null");
                }
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating output stream", e);
            }

            mmInStream = tmpIn;
            mmOutStream = new DataOutputStream(tmpOut);
            mmWriteBuffer = new byte[1024];


            logIt("Connected: " + mmSocket.isConnected());
        }

        public void run() {
            mmReadBuffer = new byte[1024];
            int numBytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {
                    // Read from the InputStream.
                    numBytes = mmInStream.read(mmReadBuffer);
                    // Send the obtained bytes to the UI activity.
//                    Message readMsg = mHandler.obtainMessage(
//                            UIHandlerMessageTypes.MESSAGE_READ, numBytes, -1,
//                            mmBuffer);
//                    readMsg.sendToTarget();
                } catch (IOException e) {
                    Log.d(TAG, "Input stream was disconnected", e);
                    logIt("Input stream was disconnected", e);
                    break;
                }
            }
        }

        // Call this from the main activity to send data to the remote device.
        public void sendFile(File file) {
            try {
                writeFile(file);

                // Share the sent message with the UI activity.
                Message writtenMsg = mHandler.obtainMessage(
                        UIHandlerMessageTypes.SEND_SUCCESS, -1, -1, mmReadBuffer);
                writtenMsg.sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when sending data", e);
                logIt("Error occured sending data", e);
            }
        }

        private void logIt(String s) {
            Log.i(TAG,s);
            // Send a failure message back to the activity.
            Message writeErrorMsg =
                    mHandler.obtainMessage(UIHandlerMessageTypes.LOG);
            Bundle bundle = new Bundle();
            bundle.putString("msg", s);
            writeErrorMsg.setData(bundle);
            mHandler.sendMessage(writeErrorMsg);
        }

        private void logIt(String s, Throwable e) {
           Log.e(TAG, "Error", e);
           logIt(s + ": " + e.getMessage());
        }

        // Protocol
        // 1 byte - Message Type
        //
        // File Message Type
        // 2 byte - Filename Length
        // n byte - Filename
        // 1 byte - Compression: 0 = None
        // 4 byte - File length
        // n byte - File contents
        // 2 byte - CRC
        private void writeFile(File file) throws IOException {
            logIt("Sending File: " + file);
            String filename = file.getName();
            mmOutStream.writeByte(FilePushMessage.TYPE_FILE);

            // writeChars is 2 byte length + bytes for string
            byte[] fname = file.getName().getBytes(StandardCharsets.UTF_8);
            Log.i(TAG, "fname [" + fname.length + "] " + fname);
            //mmOutStream.writeChars(file.getName());
            mmOutStream.writeShort(fname.length);

            mmOutStream.write(fname);

            mmOutStream.writeByte(FilePushMessage.COMPRESSION_NONE);

            // copy the file to the stream
            CRC32 crc = new CRC32();
            mmOutStream.writeInt(3);
            mmOutStream.write("foo".getBytes());
            crc.update("foo".getBytes());
//            FileInputStream inputStream = new FileInputStream(file);
//            while (true) {
//                int numBytes = inputStream.read(mmWriteBuffer);
//                if (numBytes == -1) {
//                    break;
//                }
//                crc.update(mmWriteBuffer);
//                mmOutStream.write(mmWriteBuffer);
//            }
            mmOutStream.writeLong(crc.getValue());
            logIt("Sent: " + crc.getValue());
        }

        // Call this method from the main activity to shut down the connection.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }

}
