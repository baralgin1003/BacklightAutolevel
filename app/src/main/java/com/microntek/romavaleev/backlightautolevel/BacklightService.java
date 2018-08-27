package com.microntek.romavaleev.backlightautolevel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.format.DateTimeFormat;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

public class BacklightService extends Service {

    public static String MIN_BR_SP = "min_br";
    public static String MAX_BR_SP = "max_br";
    public static String PERIOD_SP = "period";
    public static String DELAY_SP = "delay";
    public static String TYPE_SP = "type";
    public static String ACTIVE_SP = "isActive";

    private static String LAST_SUNRISE_SP = "last_sunrise";
    private static String LAST_SUNSET_SP = "last_sunset";
    private SharedPreferences sp;
    static int Satellites = -1;

    private BootReceiver bootReceiver;
    private Timer mTimer;
    private boolean needByTimer = false;
    private boolean isActive = false;

    // минимальная дистанция изменения
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 150; // meters

    // период проверки
    private static long MIN_TIME_UPDATES = 5; //secs
    private static int MAX_BRIGHTNESS = 255;
    private static int MIN_BRIGHTNESS = 1;

    //период начала регулировки, min
    private static long PERIOD_START = 60 * 60 * 1000;


    // flag for GPS status
    boolean isGPSEnabled = false;

    // flag for network status
    boolean isNetworkEnabled = false;

    // flag for GPS status
    boolean canGetLocation = false;

    Location location;
    double latitude;
    double longitude;

    protected LocationManager locationManager;

    public BacklightService() {
        sp = PreferenceManager.getDefaultSharedPreferences(BaseApp.instance);
        isActive = sp.getBoolean(BacklightService.ACTIVE_SP, true);
        getLocation();
    }

    private void configureTimer() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer.purge();
        }
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!isActive) {
                    Log.e("active", "Service not active. Bye!");
                    BacklightService.this.stopSelf();
                }

                Log.e("sats", "Used satellites: " + Satellites);
                if (Satellites <= 0) {
                    needByTimer = true;
                } else {
                    needByTimer = false;
                }

                if (needByTimer) {
                    getSun(0, 0);
                } else {
                    getSun(latitude, longitude);
                }
            }
        }, 0, MIN_TIME_UPDATES * 60 * 1000);

    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            String CHANNEL_ID = "my_channel_01";
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Channel human readable title",
                    NotificationManager.IMPORTANCE_DEFAULT);

            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("")
                    .setContentText("").build();

            startForeground(1, notification);
        }else{
            startForeground(0, prepareNotification());
        }

      //
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MAX_BRIGHTNESS = sp.getInt(MAX_BR_SP, 255);
        MIN_BRIGHTNESS = sp.getInt(MIN_BR_SP, 10);
        PERIOD_START = sp.getInt(PERIOD_SP, 60) * 60 * 1000;
        MIN_TIME_UPDATES = sp.getInt(DELAY_SP, 5);

        configureTimer();

     //   registerReceiver();

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mTimer.cancel();
        mTimer.purge();
        mTimer = null;
        if (bootReceiver != null)
            unregisterReceiver(bootReceiver);
        Log.e("sun", "bybybye! =(");
    }


    private Notification prepareNotification() {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        // handle build version above android oreo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "bkl";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel("bkl", name, importance);
            channel.enableVibration(false);
            mNotificationManager.createNotificationChannel(channel);
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);


        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);


        // notification builder
        NotificationCompat.Builder notificationBuilder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationBuilder = new NotificationCompat.Builder(this, "bkl");
        } else {
            notificationBuilder = new NotificationCompat.Builder(this);
        }
        notificationBuilder
                .setSmallIcon(R.drawable.ic_stat_name)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            notificationBuilder.setVisibility(Notification.VISIBILITY_PUBLIC);
        }

        return notificationBuilder.build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    public void getLocation() {
        if (!isActive)
            return;

        try {
            locationManager = (LocationManager) BaseApp.instance.getSystemService(Context.LOCATION_SERVICE);

            GpsStatus.Listener gpsStatusListener = new GpsStatus.Listener() {
                @Override
                public void onGpsStatusChanged(int event) {
                    if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS || event == GpsStatus.GPS_EVENT_FIRST_FIX) {
                        GpsStatus status = locationManager.getGpsStatus(null);
                        Iterable<GpsSatellite> sats = status.getSatellites();
                        // Check number of satellites in list to determine fix state
                        Satellites = 0;
                        if (sats != null)
                            for (GpsSatellite sat : sats) {
                                if (sat.usedInFix())
                                    Satellites++;
                            }
                    }
                }
            };

            // getting GPS status
            isGPSEnabled = locationManager
                    .isProviderEnabled(LocationManager.GPS_PROVIDER);

            // getting network status
            isNetworkEnabled = locationManager
                    .isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            //Define custom criteria here

            if (!isGPSEnabled && !isNetworkEnabled) {
                // no network provider is enabled
            } else {
                this.canGetLocation = true;
                // First get location from Network Provider
                if (isNetworkEnabled) {
                    locationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            MIN_TIME_UPDATES,
                            MIN_DISTANCE_CHANGE_FOR_UPDATES, locationListener);
                    Log.d("Network", "Network");
                    if (locationManager != null) {
                        location = locationManager
                                .getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                        if (location != null) {
                            latitude = location.getLatitude();
                            longitude = location.getLongitude();
                            //  getSun(latitude, longitude);
                        }
                    }

                    // if GPS Enabled get lat/long using GPS Services
                    if (isGPSEnabled) {
                        //if (location == null) {
                        locationManager.addGpsStatusListener(gpsStatusListener);
                        locationManager.requestLocationUpdates(
                                LocationManager.GPS_PROVIDER,
                                MIN_TIME_UPDATES,
                                MIN_DISTANCE_CHANGE_FOR_UPDATES, locationListener);
                        Log.d("GPS Enabled", "GPS Enabled");
                        if (locationManager != null) {
                            location = locationManager
                                    .getLastKnownLocation(LocationManager.GPS_PROVIDER);
                            if (location != null) {
                                latitude = location.getLatitude();
                                longitude = location.getLongitude();

                            }
                        }
                    }
                }
                getSun(latitude, longitude);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void getSun(double latitude, double longitude) {
        long sunsetLong = 0;
        long sunriseLong = 0;
        String officialSunrise = "";
        String officialSunset = "";

        if (latitude != 0 && longitude != 0) {
            com.luckycatlabs.sunrisesunset.dto.Location location = new com.luckycatlabs.sunrisesunset.dto.Location(String.valueOf(latitude), String.valueOf(longitude));
            SunriseSunsetCalculator calculator = new SunriseSunsetCalculator(location, TimeZone.getDefault());
            switch (sp.getString(TYPE_SP, "astro")) {
                case "astro":
                    officialSunrise = calculator.getAstronomicalSunriseForDate(Calendar.getInstance());
                    officialSunset = calculator.getAstronomicalSunsetForDate(Calendar.getInstance());
                    sunsetLong = calculator.getAstronomicalSunsetCalendarForDate(Calendar.getInstance()).getTimeInMillis();
                    sunriseLong = calculator.getAstronomicalSunriseCalendarForDate(Calendar.getInstance()).getTimeInMillis();
                    break;
                case "civil":
                    officialSunrise = calculator.getCivilSunriseForDate(Calendar.getInstance());
                    officialSunset = calculator.getCivilSunsetForDate(Calendar.getInstance());
                    sunsetLong = calculator.getCivilSunsetCalendarForDate(Calendar.getInstance()).getTimeInMillis();
                    sunriseLong = calculator.getCivilSunriseCalendarForDate(Calendar.getInstance()).getTimeInMillis();
                    break;
                case "nautical":
                    officialSunrise = calculator.getNauticalSunriseForDate(Calendar.getInstance());
                    officialSunset = calculator.getNauticalSunsetForDate(Calendar.getInstance());
                    sunsetLong = calculator.getNauticalSunsetCalendarForDate(Calendar.getInstance()).getTimeInMillis();
                    sunriseLong = calculator.getNauticalSunriseCalendarForDate(Calendar.getInstance()).getTimeInMillis();
                    break;
            }


            sp.edit().putLong(LAST_SUNSET_SP, sunsetLong).apply();
            sp.edit().putLong(LAST_SUNRISE_SP, sunriseLong).apply();


        } else {
            if (sp.getLong(LAST_SUNRISE_SP, 0) > 0) {
                DateTime lastSunsetD = new DateTime(sp.getLong(LAST_SUNSET_SP, 0));
                DateTime lastSunriseD = new DateTime(sp.getLong(LAST_SUNRISE_SP, 0));

                //если данные слишком старые, отмена
                if (new Duration(lastSunsetD, new DateTime()).getStandardDays() > 30)
                    return;

                sunsetLong = new DateTime().withTime(lastSunsetD.getHourOfDay(), lastSunsetD.getMinuteOfHour(), 0, 0).getMillis();
                sunriseLong = new DateTime().withTime(lastSunriseD.getHourOfDay(), lastSunriseD.getMinuteOfHour(), 0, 0).getMillis();

                officialSunrise = DateTimeFormat.forPattern("HH:mm").print(new DateTime(sunriseLong, DateTimeZone.getDefault()));
                officialSunset = DateTimeFormat.forPattern("HH:mm").print(new DateTime(sunsetLong, DateTimeZone.getDefault()));
            }

            if (sunsetLong == 0 | sunriseLong == 0) {
                Log.e("null", "null data");
                return;
            }
        }


        long limit = PERIOD_START;
        long currTime = System.currentTimeMillis();

        int br;//= MAX_BRIGHTNESS;

        long kf = limit / MAX_BRIGHTNESS;

        //если еще не подходит время
        if (sunriseLong < currTime & sunsetLong > currTime) {
            setBr(MAX_BRIGHTNESS);
            br = MAX_BRIGHTNESS;
        } else {
            br = MIN_BRIGHTNESS;
            setBr(MIN_BRIGHTNESS);
        }

        //подготовка заката
        if (sunsetLong - currTime + limit > 0 && sunsetLong - currTime > 0 && sunsetLong - currTime < limit) {
            br = (int) (((sunsetLong - currTime) / kf));
            if (br < MIN_BRIGHTNESS)
                br = MIN_BRIGHTNESS;

            if (br > MAX_BRIGHTNESS)
                br = MAX_BRIGHTNESS;

            setBr(br);
        }


        //подготовка рассвета
        if (sunriseLong - currTime + limit > 0 && sunriseLong - currTime < limit) {
            br = (int) (((sunriseLong - currTime) / kf) ^ MAX_BRIGHTNESS);
            if (br < MIN_BRIGHTNESS)
                br = MIN_BRIGHTNESS;

            if (br > MAX_BRIGHTNESS)
                br = MAX_BRIGHTNESS;

            setBr(br);
        }

        String couse = (needByTimer ? "by timer" : "by GPS");
        Log.e("sun", couse + "  ---- восход: " + officialSunrise + ", ----закат: " + officialSunset + " -----тек время: " + DateTimeFormat.forPattern("HH:mm").print(new DateTime(currTime, DateTimeZone.getDefault())) + ", -----ярк: " + br);
        //Log.e("sun", "тек время: " + new DateTime(currTime, DateTimeZone.getDefault()) + ",  закат вр: " + new DateTime(sunsetLong, DateTimeZone.getDefault()) + ",  рассвет вр: " + new DateTime(sunriseLong, DateTimeZone.getDefault()));
        BaseApp.instance.sendBroadcast(new Intent("sunData").putExtra("sunset", officialSunset));

        BaseApp.instance.sendBroadcast(new Intent("sunData").putExtra("sunrise", officialSunrise));

    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location pLocation) {
            latitude = pLocation.getLatitude();
            longitude = pLocation.getLongitude();
        }


        @Override
        public void onProviderDisabled(String provider) {
            canGetLocation = false;
            needByTimer = true;
            Satellites = 0;
        }

        @Override
        public void onProviderEnabled(String provider) {
            canGetLocation = true;
            needByTimer = false;
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            //   Log.e("gps", "status ch");
            if (Satellites <= 0) {
                needByTimer = true;
            }
        }
    };


    private void registerReceiver() {
        if(bootReceiver!=null)
            unregisterReceiver(bootReceiver);

        bootReceiver = new BootReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_BOOT_COMPLETED);
        filter.addAction(Intent.ACTION_PROVIDER_CHANGED);
        registerReceiver(bootReceiver, filter);
    }

    private void setBr(int brg) {
        // Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, brg);

        Intent intent = new Intent("com.microntek.BLIGHT_SET");
        intent.putExtra("level", brg);
        BaseApp.instance.sendBroadcast(intent);
    }


}