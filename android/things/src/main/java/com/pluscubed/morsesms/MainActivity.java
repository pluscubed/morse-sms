package com.pluscubed.morsesms;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.pio.PeripheralManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends Activity {

    private static final boolean UI_ENABLED = false;

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
    private static final String[] CARRIER_SUFFIXES = {
            "@email.uscc.net",
            "@message.alltel.com",
            "@messaging.sprintpcs.com",
            "@mobile.celloneusa.com",
            "@msg.telus.com",
            "@paging.acswireless.com",
            "@pcs.rogers.com",
            "@qwestmp.com",
            "@sms.mycricket.com",
            "@sms.ntwls.net",
            "@tmomail.net",
            "@txt.att.net",
            "@txt.windmobile.ca",
            "@vtext.com",
            "@text.republicwireless.com",
            "@msg.fi.google.com"
    };
    public String username;
    public String password;
    // Pin 13,  15,  29, 35 - ADC input
    private String[] inputPins = {"GPIO2_IO03", "GPIO1_IO10", "GPIO2_IO01", "GPIO2_IO00"};
    private Gpio[] inputGpios;
    private GpioCallback[] inputCallbacks;
    // Pin 22 - Confirm
    private String[] buttonPins = {"GPIO5_IO00"};
    private Button[] buttons;
    //Pin 16, 18, 40, 38
    private String[] outputPins = {"GPIO6_IO15", "GPIO6_IO13", "GPIO6_IO12", "GPIO6_IO14"};
    private Gpio[] outputGpios;
    private String userNumber = "";
    private String destNumber = "";
    private Handler handler;
    private long timestamp;
    private List<Integer> ditDahs;
    private List<Integer> pauses;

    @BindView(R.id.input)
    EditText inputText;
    @BindView(R.id.text)
    TextView text;

    public static double mean(List<Integer> m) {
        double sum = 0;
        for (int i = 0; i < m.size(); i++) {
            sum += m.get(i);
        }
        return sum / m.size();
    }

    public static double variance(List<Integer> m) {
        double mean = mean(m);
        double temp = 0;
        for (double a : m)
            temp += (a - mean) * (a - mean);
        return temp / (m.size() - 1);
    }

    public void initUi() {
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        inputText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                destNumber = s.toString();
            }
        });
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (UI_ENABLED) {
            initUi();
        }

        username = getString(R.string.gmail_username);
        password = getString(R.string.gmail_password);

        handler = new Handler();

        ditDahs = new ArrayList<>();
        pauses = new ArrayList<>();

        PeripheralManager service = PeripheralManager.getInstance();
        safeIO(() -> {
            outputGpios = new Gpio[outputPins.length];
            for (int i = 0; i < outputPins.length; i++) {
                String ledPin = outputPins[i];
                Gpio ledGpio = service.openGpio(ledPin);
                outputGpios[i] = ledGpio;
                ledGpio.setEdgeTriggerType(Gpio.EDGE_NONE);
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
                GpioCallback inputCallback = gpio -> {
                    safeIO(() -> {
                        Log.e(TAG, "Input " + index + ": " + gpio.getValue());
                        onInputUpdated(index, gpio.getValue());
                        return null;
                    });
                    return true;
                };
                inputGpios[i] = inputGpio;
                inputCallbacks[i] = inputCallback;
                inputGpio.registerGpioCallback(inputCallbacks[i]);
            }

            buttons = new Button[buttonPins.length];
            for (int i = 0; i < buttonPins.length; i++) {
                String pin = buttonPins[i];
                Button button = new Button(pin, Button.LogicState.PRESSED_WHEN_LOW);
                int index = i;
                button.setOnButtonEventListener((button1, pressed) -> {
                    Log.e(TAG, "Button " + index + ": " + pressed);
                    onButtonPressed(index, pressed);
                });
                buttons[i] = button;
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
                case 3:
                    // Update corresponding LEDs
                    if (destNumber.length() < 10) {
                        outputGpios[index].setValue(value);

                        if (readDigit() == 10) {
                            //Force back to 9
                            outputGpios[0].setValue(true);
                            outputGpios[1].setValue(false);
                            outputGpios[2].setValue(false);
                            outputGpios[3].setValue(true);
                        }
                    }
                    break;
            }
            return null;
        });
    }

    private void onButtonPressed(int index, boolean pressed) {
        safeIO(() -> {
            // Enter digit
           /* if (userNumber.length() < 10) {
                userNumber = addDigitToNumber(userNumber);
                blinkLeds(userNumber.length() == 10 ? 3 : 1);
            } else */
           
            if (destNumber.length() < 10) {
                // Only detect falling edge
                if (pressed) {
                    timestamp = System.currentTimeMillis();
                    return null;
                }

                if (System.currentTimeMillis() - timestamp > 5000) {
                    // If pressed longer than 5 seconds, backspace
                    int currentDigit = readDigit();
                    if (destNumber.length() >= 1 && currentDigit == 0) {
                        destNumber = destNumber.substring(0, destNumber.length() - 1);

                        blinkLeds(1000);

                        Log.e(TAG, "Number deleted: " + destNumber);
                    } else if (destNumber.length() == 0) {
                        if (currentDigit == 1) {
                            destNumber += "650";
                            onDestNumberUpdated();
                        } else if (currentDigit == 2) {
                            destNumber += "408";
                            onDestNumberUpdated();
                        }
                    }
                    timestamp = 0;
                    return null;
                }

                destNumber = addDigitToNumber(destNumber);
                onDestNumberUpdated();
            } else {
                outputGpios[0].setValue(pressed);

                if (timestamp != 0) {
                    long elapsed = System.currentTimeMillis() - timestamp;
                    if (pressed) {
                        if (pauses.size() == ditDahs.size() - 1)
                            pauses.add((int) elapsed);
                    } else {
                        //If pressed longer than 5 seconds, send the message
                        if (elapsed >= 5000) {
                            //Remove last extraneous pause
                            pauses.remove(pauses.size() - 1);
                            String message = parseMessage();
                            sendEmail(message);
                        } else {
                            ditDahs.add((int) elapsed);
                        }
                    }
                }

                //Already started or initial press
                if (timestamp != 0 || pressed)
                    timestamp = System.currentTimeMillis();
            }
            return null;
        });
    }

    private void blinkLeds(int duration) throws IOException {
        boolean[] oldValues = new boolean[4];
        for (int i = 0; i < oldValues.length; i++) {
            oldValues[i] = outputGpios[i].getValue();
            outputGpios[i].setValue(true);
        }

        handler.postDelayed(() -> {
            safeIO(() -> {
                //Keep entering
                for (int i = 0; i < oldValues.length; i++) {
                    outputGpios[i].setValue(oldValues[i]);
                }
                return null;
            });
        }, duration);
    }

    private void onDestNumberUpdated() throws IOException {
        if (inputText != null)
            inputText.setText(destNumber);
        Log.e(TAG, "Number: " + destNumber);

        // Blink LEDs

        boolean[] oldValues = new boolean[4];
        for (int i = 0; i < oldValues.length; i++) {
            oldValues[i] = outputGpios[i].getValue();
            outputGpios[i].setValue(true);
        }

        handler.postDelayed(() -> {
            safeIO(() -> {
                if (destNumber.length() == 10) {
                    //Done entering phone number
                    for (int i = 0; i < oldValues.length; i++) {
                        outputGpios[i].setValue(false);
                    }
                } else {
                    //Keep entering
                    for (int i = 0; i < oldValues.length; i++) {
                        outputGpios[i].setValue(oldValues[i]);
                    }
                }

                return null;
            });
        }, 300);
    }

    private String parseMessage() {
        double ditDahSplit = splitData(ditDahs);
        double pauseSplit = splitData(pauses);

        StringBuilder message = new StringBuilder();
        StringBuilder letter = new StringBuilder();
        StringBuilder morse = new StringBuilder();

        for (int i = 0; i < ditDahs.size(); i++) {
            int ditDah = ditDahs.get(i);
            //Dit Dah
            if (ditDah < ditDahSplit) {
                letter.append(".");
            } else {
                letter.append("-");
            }

            //Pause after dit/dah
            //If after the last dit-dah, write to morse and message
            int pause = i == pauses.size() ? Integer.MAX_VALUE : pauses.get(i);
            if (pause > pauseSplit) {
                int index = Arrays.asList(MORSE).indexOf(letter.toString());
                if (index != -1) {
                    message.append(CHARS[index].toUpperCase());
                } else {
                    message.append(letter.toString());
                }
                morse.append(letter).append(" ");
                letter = new StringBuilder();
            }
        }

        timestamp = 0;
        ditDahs.clear();
        pauses.clear();

        return message.toString() + "\n" + morse.toString();
    }

    private void sendEmail(String message) {
        Log.e(TAG, destNumber + ": " + message);

        if (text != null)
            text.setText(message);

        if (destNumber.equalsIgnoreCase("1111111111")) {
            destNumber = getString(R.string.default_dest);
        }

        AsyncTask.execute(() -> {
            Properties props = new Properties();
            props.put("mail.smtp.host", "smtp.gmail.com");
            props.put("mail.smtp.socketFactory.port", "465");
            props.put("mail.smtp.socketFactory.class",
                    "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.port", "465");

            Session session = Session.getDefaultInstance(props,
                    new javax.mail.Authenticator() {
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(username, password);
                        }
                    });

            try {
                MimeMessage msg = new MimeMessage(session);
                msg.setFrom(new InternetAddress(username));
                String[] emails =
                        Arrays.stream(CARRIER_SUFFIXES)
                                .map(s -> destNumber + s)
                                .toArray(String[]::new);
                msg.setSubject("");

                Multipart multiPart = new MimeMultipart("alternative");

                MimeBodyPart textPart = new MimeBodyPart();
                textPart.setText(message, "utf-8");

                MimeBodyPart htmlPart = new MimeBodyPart();
                htmlPart.setContent("<div dir=\"ltr\">" + message + "</div>", "text/html; charset=utf-8");

                multiPart.addBodyPart(textPart); // <-- first
                multiPart.addBodyPart(htmlPart); // <-- second
                msg.setContent(multiPart);

                int interval = 10;
                for (int i = 0; i < emails.length; i += interval) {
                    List<String> sublist = Arrays.asList(emails).subList(i, Math.min(i + interval, emails.length));
                    msg.setRecipients(Message.RecipientType.TO,
                            InternetAddress.parse(TextUtils.join(",", sublist)));
                    Transport.send(msg);
                }

                destNumber = "";
            } catch (MessagingException e) {
                e.printStackTrace();
            }
        });
    }

    private double splitData(List<Integer> data) {
        data = new ArrayList<>(data);
        Collections.sort(data);
        double lastSumVariance = Double.MAX_VALUE;
        double splittingValue = 0;
        for (int i = 1; i < data.size(); i++) {
            List<Integer> group1 = data.subList(0, i);
            List<Integer> group2 = data.subList(i, data.size());
            double variance1 = group1.size() > 1 ? variance(group1) : 0;
            double variance2 = group2.size() > 1 ? variance(group2) : 0;
            if (variance1 + variance2 < lastSumVariance) {
                splittingValue = ((double) group1.get(i - 1) + group2.get(0)) / 2;
                lastSumVariance = variance1 + variance2;
            }
        }
        return splittingValue;
    }

    @NonNull
    private String addDigitToNumber(String number) throws IOException {
        int digit = readDigit();
        if (digit == 10) {
            digit = 9;
        }
        number += digit;
        return number;
    }

    private int readDigit() throws IOException {
        int digit = 0;
        for (int i = 0; i < 4; i++) {
            digit += (int) ((inputGpios[i].getValue() ? 1 : 0) * Math.pow(2, i));
        }
        return digit;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeAll();
    }

    private void closeAll() {
        safeIO(() -> {
            for (Gpio ledGpio : outputGpios) {
                ledGpio.close();
            }

            for (int i = 0; i < inputPins.length; i++) {
                inputGpios[i].unregisterGpioCallback(inputCallbacks[i]);
                inputGpios[i].close();
            }

            for (Button button : buttons) {
                button.close();
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
