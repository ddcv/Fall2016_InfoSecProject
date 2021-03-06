package edu.cmu.infosec.privacyfirewall;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.os.IBinder;
import android.os.AsyncTask;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.util.Pair;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.support.v7.widget.RecyclerView;
import android.support.design.widget.FloatingActionButton;

import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.IconicsDrawable;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.interfaces.OnCheckedChangeListener;
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem;
import com.mikepenz.materialdrawer.model.SwitchDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

import edu.cmu.infosec.privacyfirewall.adapter.ApplicationAdapter;
import edu.cmu.infosec.privacyfirewall.entity.AppInfo;
import edu.cmu.infosec.privacyfirewall.itemanimator.CustomItemAnimator;

public class MainActivity extends AppCompatActivity {

    /**
     * VPN Part Variables Start
     */
    private static final int VPN_REQUEST_CODE = 0x0F;    /* VPN Request code, used when start VPN */
    private Intent serviceIntent;    /* The VPN Service Intent */

//    boolean isBindWithService = false;
//    FireWallVPNService myService;
//    public ServiceConnection mServiceConn = new ServiceConnection() {
//        public void onServiceConnected(ComponentName className, IBinder binder) {
//            Log.d("ServiceConnection","connected");
//            myService = ((FireWallVPNService.LocalBinder) binder).getService();
//            isBindWithService = true;
//        }
//
//        public void onServiceDisconnected(ComponentName className) {
//            Log.d("ServiceConnection","disconnected");
//            myService = null;
//            isBindWithService = false;
//        }
//    };

    /**
     * VPN Part Variables End
     */

    /** UI Variables Start */
    private static final int DRAWER_ITEM_SWITCH = 1;
    private static final int DRAWER_ITEM_OPEN_SOURCE = 10;

    private List<AppInfo> applicationList = new ArrayList<AppInfo>();

    private Drawer drawer;

    private ApplicationAdapter mAdapter;
    //private FloatingActionButton mFabButton;
    private RecyclerView mRecyclerView;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private ProgressBar mProgressBar;
    /** UI Variables End */


    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /** init database */
        Monitor.db = new DataBaseController(MainActivity.this);

        /** Load Application info into Database */
        final PackageManager packageManager = getPackageManager();
        List<ApplicationInfo> installedApplications =
                packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
        int permissionCount;

        for (ApplicationInfo appInfo : installedApplications) {
            Cursor c = Monitor.db.getApplicationCursorById(appInfo.uid);

            permissionCount = 0;
            try {
                PackageInfo pack = packageManager.getPackageInfo(
                        appInfo.packageName,PackageManager.GET_PERMISSIONS);
                String[] permissionStrings = pack.requestedPermissions;
                if (permissionStrings != null) {
                    permissionCount = permissionStrings.length;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            /** Not exist */
            if (c.isAfterLast()) {
                Monitor.db.insertApplication(appInfo.loadLabel(packageManager).toString(),
                        appInfo.packageName, appInfo.uid, permissionCount);
            }
        }

        /** Cache filter rule */
        Cursor conCur = Monitor.db.getAllConnectionCursor();
        for (conCur.moveToFirst(); !conCur.isAfterLast(); conCur.moveToNext()) {
            if (conCur.getInt(conCur.getColumnIndex(ConnectionDatabase.FIELD_ACTION)) ==
                    ConnectionDatabase.ACTION_DENY) {
                int rId = conCur.getInt(conCur.getColumnIndex(ConnectionDatabase.FIELD_RULE));
                Cursor ruleCur = Monitor.db.getRuleCursorById(rId);
                if (ruleCur.getCount() > 0) {
                    ruleCur.moveToFirst();
                    FireWallVPNService.blockingIPMap.add(Pair.create(
                            ruleCur.getString(ruleCur.getColumnIndex(RuleDatabase.FIELD_IP_ADD)),
                            conCur.getInt(conCur.getColumnIndex(ConnectionDatabase.FIELD_APP))));
                }
            }
        }

        /** UI Start */

        /** Handle Toolbar */
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Fab Button
//        mFabButton = (FloatingActionButton) findViewById(R.id.fab_normal);
//        mFabButton.setImageDrawable(new IconicsDrawable(this, GoogleMaterial.Icon.gmd_add)
//                .color(Color.WHITE).actionBar());
//        mFabButton.setOnClickListener(new MainAddNewRuleListener(this));

        // Build Drawer
        drawer = new DrawerBuilder(this)
                .withToolbar(toolbar)
                .addDrawerItems(
                        new SecondaryDrawerItem().withIcon(GoogleMaterial.Icon.gmd_settings)
                                .withName(R.string.drawer_vpn_title)
                )
                .addDrawerItems(
                        new SwitchDrawerItem().withOnCheckedChangeListener(
                                new OnCheckedChangeListener() {
                                    @Override
                                    public void onCheckedChanged(IDrawerItem drawerItem,
                                                                 CompoundButton compoundButton, boolean b) {
                                        if (b) {
                                            Log.i("Switch", "click-on");

                                            /** Start VPN */
                                            startVPN();

                                            Snackbar.make(mRecyclerView, "VPN turn on",
                                                    Snackbar.LENGTH_SHORT).show();
                                        } else {
                                            Log.i("Switch", "click-off");
                                            Snackbar.make(mRecyclerView, "VPN turn off",
                                                    Snackbar.LENGTH_SHORT).show();
                                        }
                                    }
                                }).withName(R.string.drawer_switch)
                )
                .addDrawerItems(
                        new SecondaryDrawerItem().withIcon(GoogleMaterial.Icon.gmd_settings)
                                .withName(R.string.drawer_scan_title)
                )
                .addDrawerItems(
                        new SwitchDrawerItem().withOnCheckedChangeListener(
                                new OnCheckedChangeListener() {
                                    @Override
                                    public void onCheckedChanged(IDrawerItem drawerItem,
                                                                 CompoundButton compoundButton, boolean b) {
                                        if (b) {
                                            /** Turn on */
                                            Monitor.scan_strict_phone = true;
                                        } else {
                                            /** Turn off */
                                            Monitor.scan_strict_phone = false;
                                        }
                                    }
                                })
                                .withName(R.string.drawer_strict_phone_switch)
                                .withChecked(true)
                )
                .addDrawerItems(
                        new SwitchDrawerItem().withOnCheckedChangeListener(
                                new OnCheckedChangeListener() {
                                    @Override
                                    public void onCheckedChanged(IDrawerItem drawerItem,
                                                                 CompoundButton compoundButton, boolean b) {
                                        if (b) {
                                            /** Turn on */
                                            Monitor.scan_general_phone = true;
                                        } else {
                                            /** Turn off */
                                            Monitor.scan_general_phone = false;
                                        }
                                    }
                                }).withName(R.string.drawer_general_phone_switch)
                )
                .addDrawerItems(
                        new SwitchDrawerItem().withOnCheckedChangeListener(
                                new OnCheckedChangeListener() {
                                    @Override
                                    public void onCheckedChanged(IDrawerItem drawerItem,
                                                                 CompoundButton compoundButton, boolean b) {
                                        if (b) {
                                            /** Turn on */
                                            Monitor.scan_email = true;
                                        } else {
                                            /** Turn off */
                                            Monitor.scan_email = false;
                                        }
                                    }
                                }).withName(R.string.drawer_email_switch)
                )
                .addDrawerItems(
                        new SwitchDrawerItem().withOnCheckedChangeListener(
                                new OnCheckedChangeListener() {
                                    @Override
                                    public void onCheckedChanged(IDrawerItem drawerItem,
                                                                 CompoundButton compoundButton, boolean b) {
                                        if (b) {
                                            /** Turn on */
                                            Monitor.scan_credit_card = true;
                                        } else {
                                            /** Turn off */
                                            Monitor.scan_credit_card = false;
                                        }
                                    }
                                }).withName(R.string.drawer_credit_card_switch)
                )
                .addDrawerItems(
                        new SwitchDrawerItem().withOnCheckedChangeListener(
                                new OnCheckedChangeListener() {
                                    @Override
                                    public void onCheckedChanged(IDrawerItem drawerItem,
                                                                 CompoundButton compoundButton, boolean b) {
                                        if (b) {
                                            /** Turn on */
                                            Monitor.scan_ssn = true;
                                        } else {
                                            /** Turn off */
                                            Monitor.scan_ssn = false;
                                        }
                                    }
                                }).withName(R.string.drawer_ssn_switch)
                )
                .withSelectedItem(-1)
                .withSavedInstance(savedInstanceState)
                .build();

        // Handle ProgressBar
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);

        // Handle RecyclerView
        mRecyclerView = (RecyclerView) findViewById(R.id.list);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setItemAnimator(new CustomItemAnimator());

        mAdapter = new ApplicationAdapter(new ArrayList<AppInfo>(), R.layout.row_application,
                MainActivity.this);
        mRecyclerView.setAdapter(mAdapter);

        // Handle Refresh
        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_container);
        mSwipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.theme_accent));
        mSwipeRefreshLayout.setRefreshing(true);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                new InitializeApplicationsTask().execute();
            }
        });

        new InitializeApplicationsTask().execute();

        //show progress
        mRecyclerView.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.VISIBLE);

        /** UI End */
    }

//    @Override
//    protected void onStart() {
//        super.onStart();
//        doBindService();
//    }
//
//    @Override
//    protected void onStop() {
//        super.onStop();
//        doUnbindService();
//    }

    /**
     * Start the VPNService
     */
    public void startVPN() {
        serviceIntent = FireWallVPNService.prepare(getApplicationContext());
        if (serviceIntent != null) {
            startActivityForResult(serviceIntent, VPN_REQUEST_CODE);
        } else {
            onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null);
        }
    }

//    public void stopVPN(){
//        if (isBindWithService) {
//            myService.stopVPN();
//        }
//    }

//    private void doUnbindService() {
//        unbindService(mServiceConn);
//        isBindWithService = false;
//    }
//
//    private void doBindService() {
//        if (isBindWithService) {
//            Intent bindIntent = new Intent(this, FireWallVPNService.class);
//            isBindWithService = bindService(bindIntent, mServiceConn, Context.BIND_AUTO_CREATE);
//        }
//    }
    /**
     * OnActivityResult
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
//            Intent intent = new Intent(this, FirewallVpnService.class);
            Intent intent = new Intent(this, FireWallVPNService.class);
            startService(intent);

//            bindService(new Intent("edu.cmu.infosec.privacy.FireWallVPNService.BIND"),
//                    mServiceConn, Context.BIND_AUTO_CREATE);
        }
    }

    /** UI Part Functions Start */

    /**
     * helper class to start the new detailActivity animated
     *
     * @param appInfo
     * @param appIcon
     */
    public void animateActivity(AppInfo appInfo, View appIcon) {
        Intent i = new Intent(this, DetailActivity.class);
        i.putExtra("appInfo", appInfo.getComponentName());

        ActivityOptionsCompat transitionActivityOptions =
                ActivityOptionsCompat.makeSceneTransitionAnimation(
                        this, Pair.create(appIcon, "appIcon"));
        startActivity(i, transitionActivityOptions.toBundle());
    }

    /**
     * A simple AsyncTask to load the list of applications and display them
     */
    private class InitializeApplicationsTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            mAdapter.clearApplications();
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void... params) {
            applicationList.clear();

            //Query the applications
            final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

            List<ResolveInfo> ril = getPackageManager().queryIntentActivities(mainIntent, 0);
            for (ResolveInfo ri : ril) {
                applicationList.add(new AppInfo(MainActivity.this, ri));
            }
            Collections.sort(applicationList);

            for (AppInfo appInfo : applicationList) {
                //load icons before shown. so the list is smoother
                appInfo.getIcon();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            //handle visibility
            mRecyclerView.setVisibility(View.VISIBLE);
            mProgressBar.setVisibility(View.GONE);

            //set data for list
            mAdapter.addApplications(applicationList);
            mSwipeRefreshLayout.setRefreshing(false);

            super.onPostExecute(result);
        }
    }

    /** UI Part Functions End */
}
