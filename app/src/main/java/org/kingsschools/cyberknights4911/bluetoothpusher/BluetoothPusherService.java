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
 * This is a bluetooth service that allows for pushing files and directories over bluetooth to
 * receiving server.
 */
public class BluetoothPusherService {
    // Uniquely identifies the CyberKnight Pusher Service to the receiving device. These must match on the
    // client and server for  succesfully Bluetooth RFComm connection to be made.
    public static final UUID CYBERKNIGHT_BLUETOOTH_PUSHER_UUID = UUID.fromString("2d31ac7d-0d4a-48dd-8136-2f6a9b71a3f4");

    private static final String TAG = "BluetoothPusher";
    private Handler mHandler;
    private Set<ConnectedPusher> mThreads = new HashSet<>();

    /**
     * Defines constants used when transmitting messages between the
     * service and the UI via a Handler.
     */
    public interface UIHandlerMessageTypes {
        public static final int SEND_SUCCESS = 0;
        public static final int SEND_FAILED = 1;
        public static final int LOG = 2;
    }


    /**
     * Constants for message types for the internal protocol
     */
    private interface FilePushMessage {
        public static final byte TYPE_FILE = 1;
        public static final byte TYPE_DIRECTORY = 2;
        public static final byte TYPE_STOP = 3;

        public static final byte COMPRESSION_NONE = 0;
    }

    /**
     * @param handler a handler service to publish notifications over such as
     *                status updates during file pushes.
     */
    public BluetoothPusherService(Handler handler) {
        this.mHandler = handler;
    }


    public ConnectedPusher connect(BluetoothDevice device) {
        try {

            BluetoothSocket socket = device.createRfcommSocketToServiceRecord(UUID.fromString("2d31ac7d-0d4a-48dd-8136-2f6a9b71a3f4"));
            ConnectedPusher thread = new ConnectedPusher(socket);
            thread.start();
            mThreads.add(thread);
            return thread;
        } catch (IOException e) {
            throw new BluetoothConnectionException();
        }
    }

    public void stop() {
        for (ConnectedPusher thread : mThreads) {
            thread.cancel();
        }
    }


    public class ConnectedPusher extends Thread {
        private final BluetoothSocket mmSocket;
        private InputStream mmInStream;
        private DataOutputStream mmOutStream;
        private byte[] mmWriteBuffer; // mmBuffer store for the stream
        private byte[] mmReadBuffer; // mmBuffer store for the stream

        public ConnectedPusher(BluetoothSocket socket) throws IOException {

            Log.d(TAG, "Created thread for: " + socket.getRemoteDevice().getName());
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
                mmInStream = tmpIn;
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating input stream", e);
            }

            try {
                tmpOut = socket.getOutputStream();
                if (tmpOut == null) {
                    throw new IllegalStateException("InputStream was null");
                }
                mmOutStream = new DataOutputStream(tmpOut);
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating output stream", e);
            }

            mmWriteBuffer = new byte[1024];
        }

        /**
         * Starts the pusher listening for responses from the server.
         * (Currently the server never responds with anything)
         */
        public void run() {
            mmReadBuffer = new byte[1024];
            int numBytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {
                    // Read from the InputStream.
                    numBytes = mmInStream.read(mmReadBuffer);
                    // Handle the message...when there is one
                } catch (IOException e) {
                    Log.d(TAG, "Input stream was disconnected", e);
                    break;
                }
            }
        }

        /**
         * Sends a file to the connected device. On success or failure publishes a message to the
         * handler associated with the pusher.
         */
        public void sendFile(File file) {
            try {
                writeFile(file, null);
                sendSuccessMessage(file);
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when sending data", e);
                sendFailureMessage(file);
            }
        }

        public void sendDirectory(File directory) {
            try {
                writeDirectory(directory, new File("/"));
                sendSuccessMessage(directory);
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when sending data", e);
                sendFailureMessage(directory);
            }
        }

        // TODO Use the file name in the message
        // TODO Get rid of the mmReadBuffer and use a dedicated buffer for message sending...if needed
        private void sendSuccessMessage(File file) {
            // Share the sent message with the UI activity.
            Message writtenMsg = mHandler.obtainMessage(UIHandlerMessageTypes.SEND_SUCCESS, -1, -1, mmReadBuffer);
            writtenMsg.sendToTarget();
        }

        // TODO Use the file name in the message
        // TODO Get rid of the mmReadBuffer and use a dedicated buffer for message sending...if needed
        private void sendFailureMessage(File file) {
            // Share the sent message with the UI activity.
            Message writtenMsg = mHandler.obtainMessage(UIHandlerMessageTypes.SEND_FAILED, -1, -1, mmReadBuffer);
            writtenMsg.sendToTarget();
        }

        // TODO Break out messages to seperate classes

        // File Pusher Protocol
        // The file pusher protocol is a simple order dependent protocol for copying files
        // over bluetooth. Files and directories are relative to a virtual root on the 
        // recieving server. 
        //
        // 1 byte - Message Type
        // variable - Dependent on message type
        //
        // File Message Type
        // 1 byte - Message Type (FILE = 0x01)
        // 2 byte - Filename Length
        // n byte - Filename
        // 2 byte - Container Name (Target Directory) Length
        // n byte - Container Name (Target Directory)
        // 1 byte - Compression: 0 = None
        // 4 byte - File length
        // n byte - File contents
        // 8 byte - CRC
        //
        // Directory Message Type (UN-USED)
        // 1 byte - Message Type (DIRECTORY = 0x02)
        // 2 byte - Directory Name Length
        // n byte - Directory Name
        // 2 byte - Child Count
        //
        // Stop Message Type
        // 1 byte - Message Type (STOP = 0x03)
        private void writeFile(File file, File destinationDirectory) throws IOException {
            Log.d(TAG, "Sending File: " + file);

            // type
            mmOutStream.writeByte(FilePushMessage.TYPE_FILE);

            // filename chunk
//            byte[] fname = file.getName().getBytes(StandardCharsets.UTF_8);
//            mmOutStream.writeShort(fname.length);
//            mmOutStream.write(fname);
            mmOutStream.writeChars(file.getName());

            if (destinationDirectory != null) {
                mmOutStream.writeChars(destinationDirectory.getName());
            } else {
                mmOutStream.writeShort(0);
            }

            // compression
            mmOutStream.writeByte(FilePushMessage.COMPRESSION_NONE);

            // file chunk
            mmOutStream.writeInt((int) file.length());
            // copy the file to the stream
            CRC32 crc = new CRC32();
            FileInputStream inputStream = new FileInputStream(file);
            while (true) {
                int numBytes = inputStream.read(mmWriteBuffer);
                if (numBytes == -1) {
                    break;
                }
                crc.update(mmWriteBuffer, 0, numBytes);
                mmOutStream.write(mmWriteBuffer, 0, numBytes);
            }
            mmOutStream.writeLong(crc.getValue());
            Log.d(TAG, "Sent: " + crc.getValue());
        }

        private void writeDirectory(File dir, File destinationContainer) throws IOException {
            File[] children = dir.listFiles();
            mmOutStream.writeInt(FilePushMessage.TYPE_DIRECTORY);
            mmOutStream.writeChars(destinationContainer.getName());
            mmOutStream.writeShort(children.length);
            for (File subfile : children) {
                if (subfile.isFile()) {
                    writeFile(subfile, destinationContainer);
                } else if (subfile.isDirectory()) {
                    writeDirectory(subfile, new File(destinationContainer, subfile.getName()));
                }
            }
        }

        private void writeStop() throws IOException {
            mmOutStream.writeInt(FilePushMessage.TYPE_STOP);
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
