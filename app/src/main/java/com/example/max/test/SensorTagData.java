package com.example.max.test;

import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Build;

/**
 * Created by Max on 30/08/16.
 */
public class SensorTagData {

    public static double extractMagnetoX(BluetoothGattCharacteristic c) {
        byte[] value = new byte[0];

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            value = c.getValue();
        }

        int x = 0,y = 0,z = 0;

        final float SCALE = (float) (32768 / 4912);
        if (value.length >= 18) {
            x = (value[13] << 8) + value[12];
            y = (value[15] << 8) + value[14];
            z = (value[17] << 8) + value[16];
        }

        return 1.0 * y;
    }
}
