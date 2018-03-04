package com.pluscubed.morsesms;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

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

    private static final String TAG = "MainActivity";

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

        button.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    onInputUpdated(true);
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    onInputUpdated(false);
                }
                return false;
            }
        });

        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.e(TAG, TextUtils.join(",", allElapsed));

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
                            morse.append(letter.toString());
                            letter = new StringBuilder();
                        }
                    }
                }

                text.setText(message.toString() + ":" + morse.toString());

                timestamp = 0;
                allElapsed.clear();
                ditDahs.clear();
                pauses.clear();
            }
        });

        allElapsed = new ArrayList<>();
        ditDahs = new ArrayList<>();
        pauses = new ArrayList<>();
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
