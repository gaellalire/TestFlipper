package fr.gaellalire.testflipper;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

public class Flipper {

    public final static UUID RPC_SERVICE_UUID = UUID.fromString("8fe5b3d5-2e7f-4a98-2a48-7acc60fe0000");
    public final static UUID TX = UUID.fromString("19ed82ae-ed21-4c9d-4145-228e62fe0000");
    public final static UUID RX = UUID.fromString("19ed82ae-ed21-4c9d-4145-228e61fe0000");
    public final static UUID OVERFLOW = UUID.fromString("19ed82ae-ed21-4c9d-4145-228e63fe0000");
    public final static UUID RPC_STATE = UUID.fromString("19ed82ae-ed21-4c9d-4145-228e64fe0000");

    public final static UUID BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");

    public final static UUID BATTERY_LEVEL = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
    public final static UUID BATTERY_POWER_STATE = UUID.fromString("00002A1A-0000-1000-8000-00805f9b34fb");


    BluetoothGattCharacteristic rpcStateCharacteristic;

    BluetoothGattCharacteristic txCharacteristic;

    BluetoothGattCharacteristic rxCharacteristic;

    BluetoothGattCharacteristic overFlowCharacteristic;

    BluetoothGatt bluetoothGatt;

    boolean reliableReady = true;

    Object mutex = new Object();

    @SuppressLint("MissingPermission")
    public void connect(Context context, BluetoothDevice bluetoothDevice) {
        disconnect();
        bluetoothDevice.connectGatt(context, false, new BluetoothGattCallback() {

            boolean mtuOK = false;

            boolean serviceOK = false;

            @Override
            public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                mtuOK = true;
                callInitDone(gatt);
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                gatt.executeReliableWrite();
            }

            @SuppressLint("MissingPermission")
            @Override
            public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
                Log.e("BluetoothGattCallback", "onReliableWriteCompleted " + status);
                synchronized (mutex) {
                    reliableReady = true;
                    mutex.notifyAll();
                }
            }

            @Override
            public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
                if (!characteristic.equals(rxCharacteristic)) {
                    Log.e("BluetoothGattCallback", "onCharacteristicChanged ? " + characteristic.getUuid() + " (" + value.length + ") ");
                    return;
                }
                Log.e("BluetoothGattCallback", "onCharacteristicChanged RX " + characteristic.getUuid() + " (" + value.length + ") ");
                try {
                    com.flipperdevices.protobuf.Flipper.Main mainResponse = com.flipperdevices.protobuf.Flipper.Main.parseDelimitedFrom(new ByteArrayInputStream(value));
                    if (mainResponse.getCommandId() == 1) {
                        Log.e("Version", "Got " + mainResponse.getSystemProtobufVersionResponse().getMajor() + "." + mainResponse.getSystemProtobufVersionResponse().getMinor());
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }

            @Override
            public void onCharacteristicRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value, int status) {
                Log.e("BluetoothGattCallback", "onCharacteristicRead (" + value.length + ") " + characteristic.getUuid());
                Log.e("BluetoothGattCallback", "onCharacteristicRead result " + Arrays.toString(value));
            }

            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    bluetoothGatt = gatt;
                    gatt.requestMtu(512);
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                    if (!gatt.discoverServices()) {
                        Log.e("BluetoothGattCallback", "Cannot discoverServices");
                        return;
                    }
                    Log.e("BluetoothGattCallback", "discoverServices called " + gatt.getDevice().getName() + " " + status);
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.e("BluetoothGattCallback", "BLE disconnected");
                    gatt.disconnect();
                    gatt.close();
                }
            }

            public void callInitDone(BluetoothGatt gatt) {
                if (!mtuOK || !serviceOK) {
                    return;
                }

                Log.e("BluetoothGattCallback", "callInitDone");
                synchronized (mutex) {
                    reliableReady = true;
                    mutex.notifyAll();
                }
            }


            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                BluetoothGattService batteryService = gatt.getService(BATTERY_SERVICE_UUID);
                if (batteryService == null) {
                    Log.e("BluetoothGattCallback", "Cannot get batteryService");
                    return;
                }
                BluetoothGattCharacteristic batteryLevelCharacteristic = batteryService.getCharacteristic(BATTERY_LEVEL);
                gatt.setCharacteristicNotification(batteryLevelCharacteristic, true);
                BluetoothGattCharacteristic batteryPowerStateCharacteristic = batteryService.getCharacteristic(BATTERY_POWER_STATE);
                gatt.setCharacteristicNotification(batteryPowerStateCharacteristic, true);

                BluetoothGattService rpcService = gatt.getService(RPC_SERVICE_UUID);
                if (rpcService == null) {
                    Log.e("BluetoothGattCallback", "Cannot get rpcService");
                    return;
                }
                overFlowCharacteristic = rpcService.getCharacteristic(OVERFLOW);
                if (overFlowCharacteristic == null) {
                    Log.e("BluetoothGattCallback", "Cannot get OVERFLOW");
                    return;
                }

                rpcStateCharacteristic = rpcService.getCharacteristic(RPC_STATE);
                if (rpcStateCharacteristic == null) {
                    Log.e("BluetoothGattCallback", "Cannot get RPC_STATE");
                    return;
                }
                txCharacteristic = rpcService.getCharacteristic(TX);
                if (txCharacteristic == null) {
                    Log.e("BluetoothGattCallback", "Cannot get TX");
                    return;
                }
                rxCharacteristic = rpcService.getCharacteristic(RX);
                if (rxCharacteristic == null) {
                    Log.e("BluetoothGattCallback", "Cannot get RX");
                    return;
                }
                if (!gatt.setCharacteristicNotification(rxCharacteristic, true)) {
                    Log.e("BluetoothGattCallback", "Cannot setCharacteristicNotification rxCharacteristic");
                    return;
                }
                if (!gatt.setCharacteristicNotification(txCharacteristic, true)) {
                    Log.e("BluetoothGattCallback", "Cannot setCharacteristicNotification txCharacteristic");
                    return;
                }
                if (!gatt.setCharacteristicNotification(rpcStateCharacteristic, true)) {
                    Log.e("BluetoothGattCallback", "Cannot setCharacteristicNotification rpcStateCharacteristic");
                    return;
                }
                if (!gatt.setCharacteristicNotification(overFlowCharacteristic, true)) {
                    Log.e("BluetoothGattCallback", "Cannot setCharacteristicNotification overFlowCharacteristic");
                    return;
                }


                serviceOK = true;
                callInitDone(gatt);
            }
        });

    }

    @SuppressLint("MissingPermission")
    public void reinitRPC() throws InterruptedException {
        synchronized (mutex) {
            while (!reliableReady) {
                mutex.wait();
            }
            if (!bluetoothGatt.beginReliableWrite()) {
                Log.e("BleFlipperRPC", "beginReliableWrite FAILED");
            } else {
                reliableReady = false;
            }
        }
        byte[] value = new byte[] { 0 };
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            bluetoothGatt.writeCharacteristic(txCharacteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        } else {
            txCharacteristic.setValue(value);
            if (!bluetoothGatt.writeCharacteristic(txCharacteristic)) {
                throw new RuntimeException("writeCharacteristic");
            }
        }
    }

    @SuppressLint("MissingPermission")
    public void connect() throws InterruptedException {
        if (bluetoothGatt == null) {
            return;
        }

            // bluetoothGatt.readCharacteristic(overFlowCharacteristic);
      //  bluetoothGatt.readCharacteristic(batteryLevelCharacteristic);
      //  bluetoothGatt.readCharacteristic(batteryPowerStateCharacteristic);

        com.flipperdevices.protobuf.Flipper.Main main = com.flipperdevices.protobuf.Flipper.Main.newBuilder().setCommandId(1).setCommandStatus(com.flipperdevices.protobuf.Flipper.CommandStatus.OK).setSystemProtobufVersionRequest(com.flipperdevices.protobuf.system.System.ProtobufVersionRequest.newBuilder()).build();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            main.writeDelimitedTo(byteArrayOutputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        byte[] byteArray = byteArrayOutputStream.toByteArray();
        Log.e("BleFlipperRPC", "will write " + byteArray.length + " bytes");

        synchronized (mutex) {
            while (!reliableReady) {
                mutex.wait();
            }
            if (!bluetoothGatt.beginReliableWrite()) {
                Log.e("BleFlipperRPC", "beginReliableWrite FAILED");
            } else {
                reliableReady = false;
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            bluetoothGatt.writeCharacteristic(txCharacteristic, byteArray, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        } else {
            txCharacteristic.setValue(byteArray);
            if (!bluetoothGatt.writeCharacteristic(txCharacteristic)) {
                throw new RuntimeException("writeCharacteristic");
            }
        }

    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        if (bluetoothGatt != null) {
            bluetoothGatt.abortReliableWrite();
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }
}
