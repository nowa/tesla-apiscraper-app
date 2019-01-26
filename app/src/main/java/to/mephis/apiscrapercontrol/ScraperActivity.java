package to.mephis.apiscrapercontrol;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;


import android.os.Bundle;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.GridView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TableLayout;
import android.widget.TableRow;

import android.content.SharedPreferences;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import static android.graphics.Color.GRAY;
import static android.graphics.Color.GREEN;
import static android.support.v7.app.AppCompatDelegate.MODE_NIGHT_AUTO;
import static android.support.v7.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
import static android.support.v7.app.AppCompatDelegate.MODE_NIGHT_YES;

/**
 * Mainscreen for setting apiScraper Properties
 */
public class ScraperActivity extends AppCompatActivity  {


    /**
     * Shared pref store..
     */

    private static boolean polling_enabled = false;


    // Set Keys for Prefstore
    public static final String pref_btMac = "btMac";
    public static final String pref_apiUrl = "apiUrl";
    public static final String pref_apiKey = "apiKey";
    public static final String pref_Polling = "polling";
    public static final String pref_StartOnProximity = "startOnProximity";
    public static final String pref_StopOnProximityLost = "stopOnProximityLost";

    //table
    private static final String[] TABLE_HEADERS = { "Item", "Value" };

    //Globals
    public static String apiUrl = "";
    public static String apiKey = "";
    public static boolean disableScraping = false;
    public static String carAsleep = "unknown";
    public static String vin = "unknown";

    // UI references.
    private Button mBtnScraperState;
    private View mProgressView;
    private View mLoginFormView;
    private Switch mEnablePolling;
    private Switch mEnableBTProxmity;
    private Switch mDisableBTProxmity;
    private ProgressBar mpbBtTimeout;

    CountDownTimer countDownTimer;
    int time = 4 * 60 * 1000; // 4 minutes
    int interval = 1000; // 1 second

    //toolbar
    private Toolbar toolbar;


    // We do this to publish it to our other Classes
    private static ScraperActivity instance;
    public static ScraperActivity getInstance() {
        return instance;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_FOLLOW_SYSTEM);

        super.onCreate(savedInstanceState);
        instance = this;
        setContentView(R.layout.activity_main);


        //Look at BT
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        registerReceiver(mAclConnectReceiver, filter);


        mBtnScraperState = (Button) findViewById(R.id.btn_scraperStatus);
        mEnablePolling = (Switch) findViewById(R.id.swEnablePolling);
        mEnableBTProxmity = (Switch) findViewById(R.id.swEnableBTProximity);
        mDisableBTProxmity = (Switch) findViewById(R.id.swEnableBTProximityLost);
        mpbBtTimeout = (ProgressBar) findViewById(R.id.prgProximityTimeout);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);


        mBtnScraperState.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                switchScraper();
            }
        });

        mEnablePolling.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked) {
                Toast toast = Toast.makeText(getApplicationContext(),
                        "Polling ScraperApiController enabled",
                        Toast.LENGTH_SHORT);
                toast.show();
                scheduleAlarm();
            } else {
                Toast toast = Toast.makeText(getApplicationContext(),
                        "Polling ScraperApiController disabled",
                        Toast.LENGTH_SHORT);
                toast.show();
                cancelAlarm();
            }
            writePolling(isChecked);
            }
        });

        mEnableBTProxmity.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked) {
                Toast toast = Toast.makeText(getApplicationContext(),
                        "Activate on Proximity enabled",
                        Toast.LENGTH_SHORT);
                toast.show();
            } else {
                Toast toast = Toast.makeText(getApplicationContext(),
                        "Activate Proxmity disabled",
                        Toast.LENGTH_SHORT);
                toast.show();
            }
            writeStartOnPromity(isChecked);
            }
        });

        mDisableBTProxmity.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    Toast toast = Toast.makeText(getApplicationContext(),
                            "Deactivate on Proximity lost enabled",
                            Toast.LENGTH_SHORT);
                    toast.show();
                } else {
                    Toast toast = Toast.makeText(getApplicationContext(),
                            "Deactivate on Proximity lost disabled",
                            Toast.LENGTH_SHORT);
                    toast.show();
                    stopBtTimeout();
                    mpbBtTimeout.setProgress(0);
                }
                writeStopOnProximityLost(isChecked);
            }
        });

        countDownTimer = new CountDownTimer(time, interval) {
            public void onTick(long millisUntilFinished) {
                //tvTimer.setText(getDateFromMillis(millisUntilFinished));
                int progress = (int) millisUntilFinished / interval;
                mpbBtTimeout.setProgress(mpbBtTimeout.getMax() - progress);
            }

            public void onFinish() {
                setScraper(false);
                mpbBtTimeout.setProgress(0);
            }
        };

        mLoginFormView = findViewById(R.id.main_scrollview);

        //Load perfs
        loadSettings();

        mEnablePolling.setChecked(getPolling());
        if (mEnablePolling.isChecked()) {
            doPoll();
        } else {
            mBtnScraperState.setText("Polling disabled..");
        }
        mEnableBTProxmity.setChecked(getStartOnProximity());
        mDisableBTProxmity.setChecked(getStopOnProximityLost());
    }

    private void setProgressBarValues() {
        mpbBtTimeout.setMax(time / interval);
        mpbBtTimeout.setProgress(time / interval);
    }

    public void startBtTimeout() {
        setProgressBarValues();
        countDownTimer.start();
    }

    public void stopBtTimeout() {
        countDownTimer.cancel();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    public void loadSettings() {
        apiUrl = getapiUrl();
        apiKey = getapiKey();
    }

    public void launchSettings(){
        Intent intent = new Intent(this, Settings.class);
        intent.putExtra("pref_btMac",pref_btMac);
        intent.putExtra("pref_apiUrl",pref_apiUrl);
        intent.putExtra("pref_apiKey",pref_apiKey);
        intent.putExtra("btname",getBtName());
        intent.putExtra("apiurl",getapiUrl());
        intent.putExtra("apikey",getapiKey());
        startActivityForResult(intent,1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1){
            if(resultCode == RESULT_OK){
                String result=data.getStringExtra("result");
                Toast.makeText(getApplicationContext(), result, Toast.LENGTH_SHORT).show();
                loadSettings();
            }
            /**if (resultCode == RESULT_CANCELED) {
                String result=data.getStringExtra("result");
                Toast.makeText(getApplicationContext(), result, Toast.LENGTH_SHORT).show();
            }*/
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh:
                Log.i("Toolbar", "refresh");
                doPoll();
                return true;
            case R.id.settings:
                Log.i("Toolbar","settings");
                launchSettings();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void scheduleAlarm() {
        Intent intent = new Intent(getApplicationContext(), alarmReceiver.class);
        final PendingIntent pIntent = PendingIntent.getBroadcast(this, alarmReceiver.REQUEST_CODE,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        long firstMillis = System.currentTimeMillis(); // alarm is set right away
        AlarmManager alarm = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        alarm.setRepeating(AlarmManager.RTC_WAKEUP,firstMillis,(15*1000),pIntent);
    }

    public void cancelAlarm() {
        Intent intent = new Intent(getApplicationContext(), alarmReceiver.class);
        final PendingIntent pIntent = PendingIntent.getBroadcast(this, alarmReceiver.REQUEST_CODE,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager alarm = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        alarm.cancel(pIntent);
    }

    public void setScrapeState(boolean state)
    {
        if (!state) {
            mBtnScraperState.setText("Scraper is running, car " + carAsleep);
            mBtnScraperState.setBackgroundColor(GREEN);

        } else {
            mBtnScraperState.setText("Scraper is inactive, press to activate..");
            mBtnScraperState.setBackgroundColor(GRAY);
        }
        disableScraping = state;
    }

    public String getBtName()
    {
        SharedPreferences sp = getSharedPreferences(pref_btMac,0);
        String str = sp.getString("myStore","00:00:00:00:00:00");
        return str;
    }

    public String getapiUrl()
    {
        SharedPreferences sp = getSharedPreferences(pref_apiUrl,0);
        String str = sp.getString("myStore","https://yourapiurl/");
        apiUrl = str;
        return str;
    }

    public String getapiKey()
    {
        SharedPreferences sp = getSharedPreferences(pref_apiKey,0);
        String str = sp.getString("myStore","YourApiKey");
        apiKey = str;
        return str;
    }

    public boolean getPolling()
    {
        SharedPreferences sp = getSharedPreferences(pref_Polling,0);
        boolean polling = sp.getBoolean("myStore",false);
        return polling;
    }
    public void writePolling(boolean pref)
    {
        SharedPreferences.Editor editor = getSharedPreferences(pref_Polling,0).edit();
        editor.putBoolean("myStore", pref);
        editor.commit();
    }

    public boolean getStartOnProximity()
    {
        SharedPreferences sp = getSharedPreferences(pref_StartOnProximity,0);
        boolean startOnProximity = sp.getBoolean("myStore",false);
        return startOnProximity;
    }
    public void writeStartOnPromity(boolean pref)
    {
        SharedPreferences.Editor editor = getSharedPreferences(pref_StartOnProximity,0).edit();
        editor.putBoolean("myStore", pref);
        editor.commit();
    }

    public boolean getStopOnProximityLost()
    {
        SharedPreferences sp = getSharedPreferences(pref_StopOnProximityLost,0);
        boolean stopOnProximityLost = sp.getBoolean("myStore",false);
        return stopOnProximityLost;
    }
    public void writeStopOnProximityLost(boolean pref)
    {
        SharedPreferences.Editor editor = getSharedPreferences(pref_StopOnProximityLost,0).edit();
        editor.putBoolean("myStore", pref);
        editor.commit();
    }

    private static void populateDataTable() {
        //todo table with metadata

    }

    public static void doPoll() {
        RequestQueue requestQueue;
        //init rest client
        requestQueue = Volley.newRequestQueue(getInstance());
        // Request a string response from the provided URL.
        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(ScraperActivity.apiUrl + "state",
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    try {
                        JSONObject state = response.getJSONObject(0);
                        disableScraping = state.getBoolean("disablescraping");
                        carAsleep = state.getString("state");
                        ScraperActivity.getInstance().setScrapeState(disableScraping);
                        populateDataTable();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    if (error.networkResponse.statusCode == 400) {
                        Toast toast = Toast.makeText(instance,
                                "Wrong API Key: Code 400: " + error.toString(),
                                Toast.LENGTH_SHORT);
                        toast.show();
                    } else {
                        Log.e("volley", "error" + error.toString());
                        Toast toast = Toast.makeText(instance,
                                "Connection Problem: " + error.toString(),
                                Toast.LENGTH_SHORT);
                        toast.show();
                    }
                }
            }) {
            @Override
            public Map<String,String> getHeaders() throws AuthFailureError {
                HashMap<String, String> headers = new HashMap<String, String>();
                //headers.put("Content-Type", "application/json");
                headers.put("apikey", ScraperActivity.apiKey);
                return headers;
            }
        };
        requestQueue.add(jsonArrayRequest);
    }

    public static void setScraper(boolean doScrape) {
        //Perform http post
        RequestQueue requestQueue;
        requestQueue = Volley.newRequestQueue(getInstance());
        JSONObject json = new JSONObject();
        try {
            json.put("command","scrape");
            json.put("value",doScrape);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, apiUrl + "switch", json,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.i("POST Response", response.toString());

                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                //serverResp.setText("Error getting response");
                Log.e("POST Error", "Post error: " + error.getMessage());
            }
        }) {
            @Override
            public Map<String,String> getHeaders() throws AuthFailureError {
                HashMap<String, String> headers = new HashMap<String, String>();
                //headers.put("Content-Type", "application/json");
                headers.put("apikey", ScraperActivity.apiKey);
                return headers;
            }
        };
        //jsonObjectRequest.setTag(REQ_TAG);
        requestQueue.add(jsonObjectRequest);
        SystemClock.sleep(1000);
        doPoll();
    }

    private void switchScraper() {
        // Reset errors.
        // Store values at the time of the login attempt.
        setScraper(!disableScraping);
    }



    private BroadcastReceiver mAclConnectReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) {
            Log.i("btDevice", "ACL Connect Device: "+device.getName() + " " + device.getAddress());
            //btConnectNotification.notify(getApplicationContext(),"BT Connect",1);
            if ((device.getName().equals(getBtName())) & mEnableBTProxmity.isChecked()) {
                Toast toast = Toast.makeText(getApplicationContext(),
                        "Proximity Detected...",
                        Toast.LENGTH_SHORT);
                toast.show();
                setScraper(true);
            }
        }
        if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())
                || BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED.equals(intent.getAction())) {
            Log.i("btDevice", "ACL Disconnect Device: "+device.getName() + " " + device.getAddress());
            if ((device.getName().equals(getBtName())) & mDisableBTProxmity.isChecked()) {
                Toast toast = Toast.makeText(getApplicationContext(),
                        "Proximity lost detected...",
                        Toast.LENGTH_SHORT);
                toast.show();
                setScraper(false);
                setProgressBarValues();
                startBtTimeout();
            }
        }
        }
    };
}

