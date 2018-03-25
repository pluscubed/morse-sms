package com.pluscubed.morsesms;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.util.*;

public class MainActivity extends AppCompatActivity {

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
    private static final String TAG = "MainActivity";
    public String username;
    public String password;
    @BindView(R.id.button)
    Button button;
    @BindView(R.id.done)
    Button done;
    @BindView(R.id.text)
    TextView text;
    private long timestamp;
    private List<Integer> allElapsed;
    private List<Integer> ditDahs;
    private List<Integer> pauses;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        username = getString(R.string.gmail_username);
        password = getString(R.string.gmail_password);

        button.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                onInputUpdated(true);
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                onInputUpdated(false);
            }
            return false;
        });

        done.setOnClickListener(v -> {
            Log.e(TAG, TextUtils.join(",", allElapsed));

            String finalMessage = parseMessage();

            MainActivity.this.text.setText(finalMessage);
            sendEmail(getString(R.string.default_dest), finalMessage);
        });

        allElapsed = new ArrayList<>();
        ditDahs = new ArrayList<>();
        pauses = new ArrayList<>();
    }

    @NonNull
    private String parseMessage() {
        double ditDahSplit = splitData(ditDahs);
        double pauseSplit = splitData(pauses);

        StringBuilder message = new StringBuilder();
        StringBuilder letter = new StringBuilder();
        StringBuilder morse = new StringBuilder();

        for (int i = 0; i <= allElapsed.size(); i++) {
            int elapsed = i == allElapsed.size() ? Integer.MAX_VALUE : allElapsed.get(i);
            if (i % 2 == 0) {
                //Dit Dah
                if (elapsed < ditDahSplit) {
                    letter.append(".");
                } else {
                    letter.append("-");
                }
            } else {
                //Pause
                if (elapsed > pauseSplit) {
                    int index = Arrays.asList(MORSE).indexOf(letter.toString());
                    if (index != -1) {
                        message.append(CHARS[index]);
                    } else {
                        message.append(letter.toString());
                    }
                    morse.append(letter).append(" ");
                    letter = new StringBuilder();
                }
            }
        }

        timestamp = 0;
        allElapsed.clear();
        ditDahs.clear();
        pauses.clear();

        return message.toString() + "\n" + morse.toString();
    }

    private void sendEmail(String number, String message) {
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
            // set the message content here
            try {
                MimeMessage msg = new MimeMessage(session);
                msg.setFrom(new InternetAddress(username));
                String[] emails =
                        Arrays.stream(CARRIER_SUFFIXES)
                                .map(s -> number + s)
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
            } catch (MessagingException e) {
                e.printStackTrace();
            }
        });
    }

    private void onInputUpdated(boolean value) {
        if (timestamp != 0) {
            long elapsed = System.currentTimeMillis() - timestamp;
            if (value) {
                pauses.add((int) elapsed);
            } else {
                ditDahs.add((int) elapsed);
            }
            allElapsed.add((int) elapsed);
        }
        timestamp = System.currentTimeMillis();
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

    private double[] splitData3(List<Integer> data) {
        Collections.sort(data);
        double lastSumVariance = Double.MAX_VALUE;
        double[] splittingValues = new double[3];
        for (int i = 1; i < data.size() - 1; i++) {
            for (int j = i + 1; j < data.size(); j++) {
                List<Integer> group1 = data.subList(0, i);
                List<Integer> group2 = data.subList(i, j);
                List<Integer> group3 = data.subList(j, data.size());
                double variance1 = group1.size() > 1 ? variance(group1) : 0;
                double variance2 = group2.size() > 1 ? variance(group2) : 0;
                double variance3 = group3.size() > 1 ? variance(group3) : 0;
                if (variance1 + variance2 + variance3 < lastSumVariance) {
                    splittingValues[0] = ((double) group1.get(i - 1) + group2.get(0)) / 2;
                    splittingValues[1] = ((double) group2.get(j - i - 1) + group3.get(0)) / 2;
                    lastSumVariance = variance1 + variance2;
                }
            }
        }
        return splittingValues;
    }


}
