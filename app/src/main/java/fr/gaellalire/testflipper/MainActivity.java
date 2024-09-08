package fr.gaellalire.testflipper;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    Flipper flipper = new Flipper();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ActivityResultLauncher<String[]> permissionRequestLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), new ActivityResultCallback<Map<String, Boolean>>() {
            @Override
            public void onActivityResult(Map<String, Boolean> result) {
                for (Boolean b : result.values()) {
                    if (!b.booleanValue()) {
                        return;
                    }
                }
            }

        });

        permissionRequestLauncher.launch(new String[]{android.Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN});


        findViewById(R.id.connectButton).setOnClickListener(new View.OnClickListener() {
            @Override
            @SuppressLint("MissingPermission")
            public void onClick(View v) {
                BluetoothManager bluetoothManager = (BluetoothManager) MainActivity.this.getSystemService(Context.BLUETOOTH_SERVICE);
                Set<BluetoothDevice> pairedDevices = bluetoothManager.getAdapter().getBondedDevices();
                for (BluetoothDevice bluetoothDevice : pairedDevices) {
                    String deviceName = bluetoothDevice.getName();
                    String address = bluetoothDevice.getAddress();
                    if (deviceName.startsWith("Flipper") && address.startsWith("80:E1:")) {
                        flipper.connect(MainActivity.this, bluetoothDevice);
                    }
                }
                Log.e("", "onClick finished");

            }
        });

        findViewById(R.id.reinitRPCButton).setOnClickListener(new View.OnClickListener() {
            @Override
            @SuppressLint("MissingPermission")
            public void onClick(View v) {
                try {
                    flipper.reinitRPC();
                } catch (InterruptedException e) {
                }
            }
        });

        findViewById(R.id.sendButton).setOnClickListener(new View.OnClickListener() {
            @Override
            @SuppressLint("MissingPermission")
            public void onClick(View v) {
                try {
                    flipper.connect();
                } catch (InterruptedException e) {
                }
            }
        });


        findViewById(R.id.disconnectButton).setOnClickListener(new View.OnClickListener() {
            @Override
            @SuppressLint("MissingPermission")
            public void onClick(View v) {
                flipper.disconnect();
            }
        });




        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        flipper.disconnect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        flipper.disconnect();
    }
}
