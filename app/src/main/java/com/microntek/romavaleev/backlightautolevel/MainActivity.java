package com.microntek.romavaleev.backlightautolevel;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Browser;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity {
    private SharedPreferences sp;
    private EditText minBrEt, maxBrEt, periodEt, delayTime;
    private TextView sunrise, sunset;
    private Button okBtn, cnsBtn;
    private BroadcastReceiver dataReceiver;
    private RadioButton type_astro, type_civil, type_nautical;
    private String selectedType = "astro";
    private CheckBox chIsActive;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        methodRequiresTwoPermission();
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        initView();


        okBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sp.edit().putInt(BacklightService.MAX_BR_SP, Integer.parseInt(maxBrEt.getText().toString())).apply();
                sp.edit().putInt(BacklightService.MIN_BR_SP, Integer.parseInt(minBrEt.getText().toString())).apply();
                sp.edit().putInt(BacklightService.PERIOD_SP, Integer.parseInt(periodEt.getText().toString())).apply();
                sp.edit().putInt(BacklightService.DELAY_SP, Integer.parseInt(delayTime.getText().toString())).apply();

                restartService();

                // startService(new Intent(MainActivity.this, BacklightService.class));
            }
        });
        cnsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        if (dataReceiver == null) {
            IntentFilter filter = new IntentFilter("sunData");

            dataReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String sunsetS = intent.getStringExtra("sunset");
                    String sunriseS = intent.getStringExtra("sunrise");
                    if (sunsetS != null)
                        sunset.setText(sunsetS);
                    if (sunriseS != null)
                        sunrise.setText(sunriseS);
                }
            };
            registerReceiver(dataReceiver, filter);
        }


        // startService(new Intent(MainActivity.this, BacklightService.class));
        startService(this, new Intent(MainActivity.this, BacklightService.class));
    }

    private void initView() {
        getSupportActionBar().setTitle("АВТОМАТИЧЕСКОЕ ИЗМЕНЕНИЕ ЯРКОСТИ ПОДСВЕТКИ");

        minBrEt = findViewById(R.id.minBr);
        maxBrEt = findViewById(R.id.maxBr);
        periodEt = findViewById(R.id.startTime);
        delayTime = findViewById(R.id.delayTime);
        okBtn = findViewById(R.id.btn_apply);
        cnsBtn = findViewById(R.id.btn_cancel);
        sunrise = findViewById(R.id.sunrise);
        sunset = findViewById(R.id.sunset);
        chIsActive = findViewById(R.id.ch_active);

        type_astro = findViewById(R.id.type_astro);
        type_civil = findViewById(R.id.type_civil);
        type_nautical = findViewById(R.id.type_nautical);

        if (!sp.contains(BacklightService.MAX_BR_SP)) {
            sp.edit().putInt(BacklightService.MAX_BR_SP, Integer.parseInt(maxBrEt.getText().toString())).apply();
            sp.edit().putInt(BacklightService.MIN_BR_SP, Integer.parseInt(minBrEt.getText().toString())).apply();
            sp.edit().putInt(BacklightService.PERIOD_SP, Integer.parseInt(periodEt.getText().toString())).apply();
            sp.edit().putInt(BacklightService.DELAY_SP, Integer.parseInt(delayTime.getText().toString())).apply();
            sp.edit().putString(BacklightService.TYPE_SP, selectedType).apply();
            sp.edit().putBoolean(BacklightService.ACTIVE_SP, true).apply();
        }

        minBrEt.setText(String.valueOf(sp.getInt(BacklightService.MIN_BR_SP, 10)));
        maxBrEt.setText(String.valueOf(sp.getInt(BacklightService.MAX_BR_SP, 255)));
        periodEt.setText(String.valueOf(sp.getInt(BacklightService.PERIOD_SP, 60)));
        delayTime.setText(String.valueOf(sp.getInt(BacklightService.DELAY_SP, 5)));
        selectedType = sp.getString(BacklightService.TYPE_SP, "astro");
        chIsActive.setChecked(sp.getBoolean(BacklightService.ACTIVE_SP, true));

        switch (selectedType) {
            case "astro":
                type_astro.setChecked(true);
                type_civil.setChecked(false);
                type_nautical.setChecked(false);
                break;
            case "civil":
                type_astro.setChecked(false);
                type_civil.setChecked(true);
                type_nautical.setChecked(false);
                break;
            case "nautical":
                type_astro.setChecked(false);
                type_civil.setChecked(false);
                type_nautical.setChecked(true);
                break;
        }


        type_astro.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    selectedType = "astro";
                    type_civil.setChecked(false);
                    type_nautical.setChecked(false);
                    sp.edit().putString(BacklightService.TYPE_SP, selectedType).apply();
                    restartService();
                }
            }
        });

        type_civil.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    selectedType = "civil";
                    type_astro.setChecked(false);
                    type_nautical.setChecked(false);
                    sp.edit().putString(BacklightService.TYPE_SP, selectedType).apply();
                    restartService();
                }
            }
        });

        type_nautical.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    selectedType = "nautical";
                    type_civil.setChecked(false);
                    type_astro.setChecked(false);
                    sp.edit().putString(BacklightService.TYPE_SP, selectedType).apply();
                    restartService();
                }
            }
        });

        chIsActive.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    sp.edit().putBoolean(BacklightService.ACTIVE_SP, true).apply();
                    restartService();
                } else {
                    sp.edit().putBoolean(BacklightService.ACTIVE_SP, false).apply();
                    stopService(new Intent(MainActivity.this, BacklightService.class));
                }
            }
        });
    }

    private void restartService() {
        if (!sp.getBoolean(BacklightService.ACTIVE_SP, true)) {
            stopService(new Intent(MainActivity.this, BacklightService.class));
            return;
        }

        try {
            //startService(new Intent(MainActivity.this, BacklightService.class));
            startService(this, new Intent(MainActivity.this, BacklightService.class));
        } catch (Exception e) {
            Log.e("main", "cant stop service");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dataReceiver != null)
            unregisterReceiver(dataReceiver);
    }

    public boolean checkSystemWritePermission() {
        boolean retVal = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            retVal = Settings.System.canWrite(this);
            //  Log.d(TAG, "Can Write Settings: " + retVal);
            if (!retVal) {
                Toast.makeText(this, "Необходимо дать разрешение", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }

        if (retVal)
            startService(this, new Intent(this, BacklightService.class));
        return retVal;
    }


    @AfterPermissionGranted(999)
    private void methodRequiresTwoPermission() {
        String[] perms = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_SETTINGS};
        if (EasyPermissions.hasPermissions(this, perms)) {
            checkSystemWritePermission();
        } else {
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(this, "надо",
                    999, perms);
        }
    }

    private void startService(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT < 26) {
            context.startService(intent);
        } else {
            ContextCompat.startForegroundService(context, intent);
        }
    }
}
