package com.pluscubed.morsesms;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;
import java.util.concurrent.Callable;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";

    private static final String[] CHARS = {"a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l",
            "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x",
            "y", "z", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
            ",", ".", "?"};
    private static final String[] MORSE = {".-", "-...", "-.-.", "-..", ".", "..-.", "--.", "....", "..",
            ".---", "-.-", ".-..", "--", "-.", "---", ".---.", "--.-", ".-.",
            "...", "-", "..-", "...-", ".--", "-..-", "-.--", "--..", ".----",
            "..---", "...--", "....-", ".....", "-....", "--...", "---..", "----.",
            "-----", "--..--", ".-.-.-", "..--.."};

    // Pin 13,  15,  29 - ADC input
    // Pin 31 - Confirm
    private String[] inputPins = {"GPIO2_IO03", "GPIO1_IO10", "GPIO2_IO01", "GPIO2_IO02"};
    private Gpio[] inputGpios;
    private GpioCallback[] inputCallbacks;

    //Pin 16, 18
    private String[] outputPins = {"GPIO6_IO13", "GPIO6_IO12"};
    private Gpio[] outputGpios;

    private String userNumber;
    private String destNumber;
    private Handler handler;

    private String message;
    private long timestamp;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_main);

        handler = new Handler();

        PeripheralManagerService service = new PeripheralManagerService();
        safeIO(() -> {
            outputGpios = new Gpio[outputPins.length];
            for (int i = 0; i < outputPins.length; i++) {
                String ledPin = outputPins[i];
                Gpio ledGpio = service.openGpio(ledPin);
                outputGpios[i] = ledGpio;
                ledGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
                ledGpio.setActiveType(Gpio.ACTIVE_HIGH);
                ledGpio.setValue(false);
            }

            inputGpios = new Gpio[inputPins.length];
            inputCallbacks = new GpioCallback[inputPins.length];
            for (int i = 0; i < inputPins.length; i++) {
                String inputPin = inputPins[i];
                Gpio inputGpio = service.openGpio(inputPin);
                inputGpio.setDirection(Gpio.DIRECTION_IN);
                inputGpio.setActiveType(Gpio.ACTIVE_HIGH);
                inputGpio.setEdgeTriggerType(Gpio.EDGE_BOTH);

                final int index = i;
                GpioCallback inputCallback = new GpioCallback() {
                    @Override
                    public boolean onGpioEdge(Gpio gpio) {
                        safeIO(() -> {
                            Log.e(TAG, index + " " + gpio.getValue());
                            onInputUpdated(index, gpio.getValue());

                            return null;
                        });
                        return super.onGpioEdge(gpio);
                    }
                };
                inputGpios[i] = inputGpio;
                inputCallbacks[i] = inputCallback;
                inputGpio.registerGpioCallback(inputCallbacks[i]);
            }
            return null;
        });
    }

    private void onInputUpdated(int index, boolean value) {
        safeIO(() -> {
            switch (index) {
                case 0:
                case 1:
                case 2:
                    // Update corresponding LEDs
                    if (destNumber.length() < 10) {
                        outputGpios[index].setValue(value);
                    }
                    break;
                case 3:
                    if (value) {
                        // Only detect falling edge
                        break;
                    }
                    // Enter digit
                    if (userNumber.length() < 10) {
                        userNumber = addDigitToNumber(userNumber);
                        blinkLeds(userNumber.length() == 10 ? 3 : 1);
                    } else if (destNumber.length() < 10) {
                        destNumber = addDigitToNumber(destNumber);
                        outputGpios[0].setValue(false);
                        outputGpios[1].setValue(false);
                        outputGpios[2].setValue(false);
                    }
                    break;
                case 4:
                    long elapsed = System.currentTimeMillis() - timestamp;
                    if (value) {
                        if (elapsed > 500) {
                            //message += " ";
                        }
                    } else {
                        if (elapsed < 500) {
                            message += ".";
                        } else {
                            message += '_';
                        }
                    }
                    timestamp = System.currentTimeMillis();
                    break;
            }
            return null;
        });
    }

    @NonNull
    private String addDigitToNumber(String number) throws IOException {
        int digit = readDigit();
        number += digit;
        return number;
    }

    private void blinkLeds(int times) throws IOException {
        outputGpios[0].setValue(false);
        outputGpios[1].setValue(false);
        outputGpios[2].setValue(false);
        handler.postDelayed(() -> {
            safeIO(() -> {
                outputGpios[0].setValue(true);
                outputGpios[1].setValue(true);
                outputGpios[2].setValue(true);

                if (times != 0) {
                    blinkLeds(times - 1);
                } else {
                    for (int i = 0; i < 3; i++)
                        onInputUpdated(i, inputGpios[i].getValue());
                }

                return null;
            });
        }, 200);
    }

    private int readDigit() throws IOException {
        int digit = 0;
        for (int i = 0; i < 3; i++) {
            digit += (int) ((inputGpios[i].getValue() ? 1 : 0) * Math.pow(2, i));
        }
        return digit;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        safeIO(() -> {
            for (Gpio ledGpio : outputGpios) {
                ledGpio.setValue(false);
                ledGpio.close();
            }

            for (int i = 0; i < inputPins.length; i++) {
                inputGpios[i].unregisterGpioCallback(inputCallbacks[i]);
                inputGpios[i].close();
            }
            return null;
        });
    }

    private void safeIO(Callable<Void> runnable) {
        try {
            runnable.call();
        } catch (Exception e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
        }
    }

}
