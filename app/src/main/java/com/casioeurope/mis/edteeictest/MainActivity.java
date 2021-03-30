package com.casioeurope.mis.edteeictest;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.casioeurope.mis.edt.EeicLibrary;
import com.casioeurope.mis.edt.constant.EeicLibraryConstant;
import com.casioeurope.mis.edteeictest.databinding.ActivityMainBinding;

import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class MainActivity extends AppCompatActivity {

    private final static String TAG = "EDT EEIC TEST";
    private ActivityMainBinding activityMainBinding;
    private final List<String> log = new ArrayList<>();
    ArrayAdapter<String> adapter;
    private static boolean monitorSwitchChanges = false;
    private Switch[] switchGpioHighLow;
    private Switch[] switchGpioInOut;
    private TextView[] textViewGpioHigh;
    private TextView[] textViewGpioHighLow;
    private final int[] gpioDevice = {EeicLibraryConstant.GPIO_DEVICE.GPIO1,
            EeicLibraryConstant.GPIO_DEVICE.GPIO2,
            EeicLibraryConstant.GPIO_DEVICE.GPIO3,
            EeicLibraryConstant.GPIO_DEVICE.GPIO4,
            EeicLibraryConstant.GPIO_DEVICE.GPIO5};

    private final EeicLibrary.InterruptCallback myInterruptCallback = new EeicLibrary.InterruptCallback() {
        @Override
        public void onChanged(int gpio) {
            try {
                int gpioValue = EeicLibrary.GpioDevice.getValue(gpio);
                log("InterruptCallback onChanged(" + gpio + ") = " + gpioValue);
                if (textViewGpioHighLow != null) textViewGpioHighLow[gpio-1].setBackgroundColor(gpioValue!=0?Color.RED:Color.BLACK);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };

    private final Handler randomOutputHandler = new Handler();
    Runnable randomOutputUpdates = new Runnable() {
        @Override
        public void run() {
            if (switchGpioInOut != null && switchGpioHighLow != null && textViewGpioHighLow != null) {
                List<Switch> outputSwitches = new ArrayList<>();
                for (int index = 0; index < switchGpioInOut.length; index++) {
                    if (switchGpioInOut[index].isChecked())
                        outputSwitches.add(switchGpioHighLow[index]);
                }
                if (!outputSwitches.isEmpty()) {
                    int randomSwitch = ThreadLocalRandom.current().nextInt(0, outputSwitches.size());
                    outputSwitches.get(randomSwitch).setChecked(!outputSwitches.get(randomSwitch).isChecked());
                }
            }
            startRandomOutputTimer();
        }
    };

    public void startRandomOutputTimer() {
//        randomOutputHandler.postDelayed(randomOutputUpdates, 100);
        randomOutputHandler.postDelayed(randomOutputUpdates, 10);
    }

    public void cancelRandomOutputTimer() {
        randomOutputHandler.removeCallbacks(randomOutputUpdates);
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityMainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        View view = activityMainBinding.getRoot();
        setContentView(view);
        log("MainActivity Started!");
        adapter = new ArrayAdapter<>(this,
                R.layout.mylogview,
                log);
        activityMainBinding.listViewLog.setAdapter(adapter);
        activityMainBinding.listViewLog.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
        try {
            EeicLibrary.onLibraryReady(this::onEdtReady);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("DefaultLocale")
    private void onEdtReady() {
        Log.d(TAG, "onEdtReady()");
        switchGpioInOut = new Switch[]{activityMainBinding.switchGpioInOut1,
                activityMainBinding.switchGpioInOut2,
                activityMainBinding.switchGpioInOut3,
                activityMainBinding.switchGpioInOut4,
                activityMainBinding.switchGpioInOut5};
        switchGpioHighLow = new Switch[] {activityMainBinding.switchGpioHighLow1,
                activityMainBinding.switchGpioHighLow2,
                activityMainBinding.switchGpioHighLow3,
                activityMainBinding.switchGpioHighLow4,
                activityMainBinding.switchGpioHighLow5};
        textViewGpioHigh = new TextView[] {activityMainBinding.textViewGpioHigh1,
                activityMainBinding.textViewGpioHigh2,
                activityMainBinding.textViewGpioHigh3,
                activityMainBinding.textViewGpioHigh4,
                activityMainBinding.textViewGpioHigh5};
        textViewGpioHighLow = new TextView[] {activityMainBinding.textViewGpioHighLow1,
                activityMainBinding.textViewGpioHighLow2,
                activityMainBinding.textViewGpioHighLow3,
                activityMainBinding.textViewGpioHighLow4,
                activityMainBinding.textViewGpioHighLow5};

        try {
            log("EEIC Library Version: " + EeicLibrary.getLibraryVersion());
            boolean isEeicPowered = EeicLibrary.isPowerOn();
            activityMainBinding.switchEeicPower.setChecked(isEeicPowered);
            if (isEeicPowered) this.initGpio();
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        activityMainBinding.switchEeicPower.setOnCheckedChangeListener((buttonView, isChecked) -> {
            try {
                log(String.format("setPower(%b) = %b",
                        isChecked,
                        EeicLibrary.setPower(isChecked)));
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            if (isChecked) initGpio(); else deInitGpio();
        });
        activityMainBinding.switchRndOutputs.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!monitorSwitchChanges) return;
            log(String.format("Random Outputs = %b", isChecked));
            if (isChecked) startRandomOutputTimer(); else cancelRandomOutputTimer();
        });

        for (int i=0; i<gpioDevice.length; i++) {
            int finalI = i;
            switchGpioInOut[i].setOnCheckedChangeListener((buttonView, isChecked) -> onSwitchGpioInOutChecked(finalI, isChecked));

            switchGpioHighLow[i].setOnCheckedChangeListener((buttonView, isChecked) -> onSwitchGpioHighLowChecked(finalI, isChecked));
        }

        initSerial();
    }

    @SuppressLint("DefaultLocale")
    private void initSerial() {
        try {
            log(String.format("SerialDevice.open(9600, false, 8, N, 1) = %d",
                EeicLibrary.SerialDevice.open(EeicLibraryConstant.SERIAL_DEVICE.BAUDRATE_9600,
                        false,
                        EeicLibraryConstant.SERIAL_DEVICE.BIT_LENGTH_8,
                        EeicLibraryConstant.SERIAL_DEVICE.PARITY_NONE,
                        EeicLibraryConstant.SERIAL_DEVICE.STOP_BIT_1,
                        false)));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        Runnable runnable = () -> {
            byte[] buffer = new byte[4096];
            //noinspection InfiniteLoopStatement
            while(true){
                try {
                    int readBytes = EeicLibrary.SerialDevice.read(buffer, buffer.length);
                    if (readBytes > 0) {
                        String readData = new String(buffer, 0, readBytes, StandardCharsets.UTF_8);
                        runOnUiThread(() -> log(String.format("Serial Data Received: %s", readData)));
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        };
        new Thread(runnable).start();
        activityMainBinding.buttonSendSerialTestData.setOnClickListener(v -> sendSerialTestMessage());
    }

    private void sendSerialTestMessage() {
        Runnable runnable = () -> {
            String s = String.format("%s - EDT EEIC Test Message", LocalTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME));
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            runOnUiThread(() -> {
                try {
                    log(String.format("SerialDevice.write(%s) = %b",
                            s,
                            EeicLibrary.SerialDevice.write(b)));
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            });
        };
        new Thread(runnable).start();
    }

    @SuppressLint("DefaultLocale")
    private void onSwitchGpioInOutChecked(int index, boolean isChecked) {
        if (!monitorSwitchChanges) return;
        Log.d(TAG, String.format("onSwitchGpioInOutChecked(%d, %b)", index, isChecked));
        try {
            if (isChecked) {
                switchGpioHighLow[index].setVisibility(View.VISIBLE);
                textViewGpioHigh[index].setVisibility(View.VISIBLE);
                log(String.format("setInterruptEdge(%d, EDGE_NOTHING) = %s",
                        gpioDevice[index],
                        EeicLibrary.GpioDevice.setInterruptEdge(gpioDevice[index],
                                EeicLibraryConstant.GPIO_DEVICE.EDGE_NOTHING)));
                log(String.format("setOutputDirection(%d, %s) = %d",
                        gpioDevice[index],
                        switchGpioHighLow[index].isChecked()?"High":"Low",
                        EeicLibrary.GpioDevice.setOutputDirection(gpioDevice[index],
                                switchGpioHighLow[index].isChecked()?EeicLibraryConstant.GPIO_DEVICE.OUTPUT_HIGH:EeicLibraryConstant.GPIO_DEVICE.OUTPUT_LOW)
                        ));
                textViewGpioHighLow[index].setBackgroundColor(switchGpioHighLow[index].isChecked()? Color.RED:Color.BLACK);
            } else {
                switchGpioHighLow[index].setVisibility(View.INVISIBLE);
                textViewGpioHigh[index].setVisibility(View.INVISIBLE);
                log(String.format("setInputDirection(%d, INPUT_FLOATING) = %s",
                        gpioDevice[index],
                        EeicLibrary.GpioDevice.setInputDirection(gpioDevice[index],
                                EeicLibraryConstant.GPIO_DEVICE.INPUT_FLOATING)));
                log(String.format("setInterruptEdge(%d, BOTH) = %s",
                        gpioDevice[index],
                        EeicLibrary.GpioDevice.setInterruptEdge(gpioDevice[index],
                                EeicLibraryConstant.GPIO_DEVICE.EDGE_BOTH)));
                int curVal = EeicLibrary.GpioDevice.getValue(gpioDevice[index]);
                log(String.format("getValue(%d) = %d",
                        gpioDevice[index],
                        curVal));
                textViewGpioHighLow[index].setBackgroundColor(curVal == EeicLibraryConstant.GPIO_DEVICE.OUTPUT_HIGH?Color.RED:Color.BLACK);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("DefaultLocale")
    private void onSwitchGpioHighLowChecked(int index, boolean isChecked) {
        if (!monitorSwitchChanges) return;
        Log.d(TAG, String.format("onSwitchGpioHighLowChecked(%d, %b)", index, isChecked));
        try {
            String logString = String.format("setValue(%d, %s) = %d",
                    gpioDevice[index],
                    isChecked?"High":"Low",
                    EeicLibrary.GpioDevice.setValue(gpioDevice[index],
                            isChecked?EeicLibraryConstant.GPIO_DEVICE.OUTPUT_HIGH:EeicLibraryConstant.GPIO_DEVICE.OUTPUT_LOW));
            if (activityMainBinding.switchRndOutputs.isChecked())
                Log.d(TAG, logString);
            else
                log(logString);
            textViewGpioHighLow[index].setBackgroundColor(isChecked? Color.RED:Color.BLACK);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("DefaultLocale")
    private void initGpio() {
        try {
            log("registerCallback = " + EeicLibrary.GpioDevice.registerCallback(myInterruptCallback, new Handler(Looper.getMainLooper())));
            for (int index=0; index<gpioDevice.length; index++) {
                if (switchGpioInOut[index].isChecked()) {
                    switchGpioHighLow[index].setVisibility(View.VISIBLE);
                    textViewGpioHigh[index].setVisibility(View.VISIBLE);
                    log(String.format("setInterruptEdge(%d, EDGE_NOTHING) = %s",
                            gpioDevice[index],
                            EeicLibrary.GpioDevice.setInterruptEdge(gpioDevice[index],
                                    EeicLibraryConstant.GPIO_DEVICE.EDGE_NOTHING)));
                    log(String.format("setOutputDirection(%d, %s) = %d",
                            gpioDevice[index],
                            switchGpioHighLow[index].isChecked()?"High":"Low",
                            EeicLibrary.GpioDevice.setOutputDirection(gpioDevice[index],
                                    switchGpioHighLow[index].isChecked()?EeicLibraryConstant.GPIO_DEVICE.OUTPUT_HIGH:EeicLibraryConstant.GPIO_DEVICE.OUTPUT_LOW)
                    ));
                    textViewGpioHighLow[index].setBackgroundColor(switchGpioHighLow[index].isChecked()? Color.RED:Color.BLACK);
                } else {
                    switchGpioHighLow[index].setVisibility(View.INVISIBLE);
                    textViewGpioHigh[index].setVisibility(View.INVISIBLE);
                    log(String.format("setInputDirection(%d, INPUT_FLOATING) = %s",
                            gpioDevice[index],
                            EeicLibrary.GpioDevice.setInputDirection(gpioDevice[index],
                                    EeicLibraryConstant.GPIO_DEVICE.INPUT_FLOATING)));
                    log(String.format("setInterruptEdge(%d, BOTH) = %s",
                            gpioDevice[index],
                            EeicLibrary.GpioDevice.setInterruptEdge(gpioDevice[index],
                                    EeicLibraryConstant.GPIO_DEVICE.EDGE_BOTH)));
                    int curVal = EeicLibrary.GpioDevice.getValue(gpioDevice[index]);
                    log(String.format("getValue(%d) = %d",
                            gpioDevice[index],
                            curVal));
                    textViewGpioHighLow[index].setBackgroundColor(curVal == EeicLibraryConstant.GPIO_DEVICE.OUTPUT_HIGH?Color.RED:Color.BLACK);
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        activityMainBinding.switchRndOutputs.setVisibility(View.VISIBLE);
        activityMainBinding.switchRndOutputsOn.setVisibility(View.VISIBLE);
        if (activityMainBinding.switchRndOutputs.isChecked()) this.startRandomOutputTimer();
        monitorSwitchChanges = true;
    }

    private void deInitGpio() {
        monitorSwitchChanges = false;
        activityMainBinding.switchRndOutputs.setVisibility(View.INVISIBLE);
        activityMainBinding.switchRndOutputsOn.setVisibility(View.INVISIBLE);
        this.cancelRandomOutputTimer();
        try {
            if (myInterruptCallback != null) EeicLibrary.GpioDevice.unregisterCallback(myInterruptCallback);
            if (gpioDevice != null) for (int value : gpioDevice) {
                Log.d(TAG, String.format("setInputDirection(%d, INPUT_PULL_DOWN) = %s",
                        value,
                        EeicLibrary.GpioDevice.setInputDirection(value,
                                EeicLibraryConstant.GPIO_DEVICE.INPUT_PULL_DOWN)));
                Log.d(TAG, String.format("setInterruptEdge(%d, EDGE_NOTHING) = %s",
                        value,
                        EeicLibrary.GpioDevice.setInterruptEdge(value,
                                EeicLibraryConstant.GPIO_DEVICE.EDGE_NOTHING)));
            }
            for (TextView textView : textViewGpioHighLow) {
                textView.setBackgroundColor(Color.BLACK);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void onDestroy(){
        this.deInitGpio();
        try {
            EeicLibrary.SerialDevice.close();
            EeicLibrary.setPower(false);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        onDestroy();
        finishAffinity();
        finish();
        System.exit(0);
    }

    private void log(String logline) {
        Log.d(TAG, logline);
        log.add(logline);
        if (adapter != null) {
            activityMainBinding.listViewLog.setSelection(adapter.getCount() - 1);
            adapter.notifyDataSetChanged();
        }
    }

}