package com.blogspot.developersu.ns_usbloader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.blogspot.developersu.ns_usbloader.Model.NsResultReciever;
import com.blogspot.developersu.ns_usbloader.Service.CommunicationsService;
import com.blogspot.developersu.ns_usbloader.View.NSPElement;
import com.blogspot.developersu.ns_usbloader.View.NspItemsAdapter;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.HashMap;

// TODO: add ImageAsset for notification icon instead of SVG-like
// TODO NOTE: If service execution has been finished in background, then no UI updates will come

public class MainActivity extends AppCompatActivity implements NsResultReciever.Receiver,
        NavigationView.OnNavigationItemSelectedListener  {  //TODO: FIX?

    private static final int ADD_NSP_INTENT_CODE = 1;

    private RecyclerView recyclerView;
    private NspItemsAdapter mAdapter;
    private ArrayList<NSPElement> mDataset;

    private BroadcastReceiver innerBroadcastReceiver;

    private UsbDevice usbDevice;
    private boolean isUsbDeviceAccessible;

    private Button selectBtn;
    private Button uploadToNsBtn;
    private ProgressBar progressBarMain;

    private NavigationView drawerNavView;

    private NsResultReciever nsResultReciever;

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList("DATASET_LIST", mDataset);
        outState.putParcelable("USB_DEVICE", usbDevice);
        outState.putBoolean("IS_USB_DEV_ACCESSIBLE", isUsbDeviceAccessible);
        if (drawerNavView.getCheckedItem() != null)
            outState.putInt("PROTOCOL", drawerNavView.getCheckedItem().getItemId());
        else
            outState.putInt("PROTOCOL", R.id.nav_tf_usb);
        outState.putParcelable("RECEIVER", nsResultReciever);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Configure intent to receive attached NS
        innerBroadcastReceiver = new InnerBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        intentFilter.addAction(NsConstants.REQUEST_NS_ACCESS_INTENT);
        intentFilter.addAction(NsConstants.SERVICE_TRANSFER_TASK_FINISHED_INTENT);
        registerReceiver(innerBroadcastReceiver, intentFilter);
        nsResultReciever.setReceiver(this);
        blockUI(CommunicationsService.isServiceActive());
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(innerBroadcastReceiver);
        SharedPreferences.Editor preferencesEditor = getSharedPreferences("NSUSBloader", MODE_PRIVATE).edit();
        if (drawerNavView.getCheckedItem() != null){
            switch (drawerNavView.getCheckedItem().getItemId()){
                case R.id.nav_tf_usb:
                    preferencesEditor.putInt("PROTOCOL", NsConstants.PROTO_TF_USB);
                    break;
                case R.id.nav_tf_net:
                    preferencesEditor.putInt("PROTOCOL", NsConstants.PROTO_TF_NET);
                    break;
                case R.id.nav_gl:
                    preferencesEditor.putInt("PROTOCOL", NsConstants.PROTO_GL_USB);
                    break;
            }
        }
        else
            preferencesEditor.putInt("PROTOCOL", NsConstants.PROTO_TF_USB);
        preferencesEditor.apply();
        nsResultReciever.setReceiver(null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main_toolbar, menu);
        MenuItem selectAllBtn = menu.findItem(R.id.select_all);
        selectAllBtn.setOnMenuItemClickListener(menuItem -> {
            if (drawerNavView.getCheckedItem() != null) {
                if (drawerNavView.getCheckedItem().getItemId() == R.id.nav_gl)
                    Snackbar.make(findViewById(android.R.id.content), getString(R.string.one_item_for_gl_notification), Snackbar.LENGTH_LONG).show();
                else {
                    if (mDataset.isEmpty())
                        return true;
                    for (NSPElement element: mDataset){
                        element.setSelected(true);
                    }
                    mAdapter.notifyDataSetChanged();
                }
            }
            return true;
        });
        return true;
    }
    //*/
    // Handle back button push when drawer opened
    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
    // Drawer actions
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_tf_usb) {
            // TODO: make something  useful
        }
        else if (id == R.id.nav_tf_net) {
            // TODO: implement
        }
        else if (id == R.id.nav_gl) {                                                                     // TODO: SET LISTENER
            if (! mDataset.isEmpty()){
                for (NSPElement element: mDataset){
                    element.setSelected(false);
                }
                mAdapter.notifyDataSetChanged();
            }
        }
        else if (id == R.id.nav_about) {
            //Log.i("LPR", "ABOUT");
            startActivity(new Intent(this, AboutActivity.class));
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Initialize ToolBar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawerNavView = findViewById(R.id.nav_view);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        drawerNavView.setNavigationItemSelectedListener(this);

        // Initialize Progress Bar
        progressBarMain = findViewById(R.id.mainProgressBar);
        // Configure data set in case it's restored from screen rotation or something
        if (savedInstanceState != null){
            //Log.i("LPR", "NOT EMPTY INSTANCE-"+getIntent().getAction());
            mDataset = savedInstanceState.getParcelableArrayList("DATASET_LIST");
            // Restore USB device information
            usbDevice = savedInstanceState.getParcelable("USB_DEVICE");
            isUsbDeviceAccessible = savedInstanceState.getBoolean("IS_USB_DEV_ACCESSIBLE", false);
            drawerNavView.setCheckedItem(savedInstanceState.getInt("PROTOCOL", R.id.nav_tf_usb));
            nsResultReciever = savedInstanceState.getParcelable("RECEIVER");
        }
        else {
            mDataset = new ArrayList<>();
            usbDevice = getIntent().getParcelableExtra(UsbManager.EXTRA_DEVICE);    // If it's started initially, then check if it's started from notification.
            if (usbDevice != null){
                UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
                // If somehow we can't get system service
                if (usbManager == null) {
                    NsNotificationPopUp.getAlertWindow(this, getResources().getString(R.string.popup_error), "Internal issue: getSystemService(Context.USB_SERVICE) returned null");
                    return; // ??? HOW ???
                }
                isUsbDeviceAccessible = usbManager.hasPermission(usbDevice);
            }
            else
                isUsbDeviceAccessible = false;
            switch (getSharedPreferences("NSUSBloader", MODE_PRIVATE).getInt("PROTOCOL", NsConstants.PROTO_TF_USB)){
                case NsConstants.PROTO_TF_USB:
                    drawerNavView.setCheckedItem(R.id.nav_tf_usb);
                    break;
                case NsConstants.PROTO_TF_NET:
                    drawerNavView.setCheckedItem(R.id.nav_tf_net);
                    break;
                case NsConstants.PROTO_GL_USB:
                    drawerNavView.setCheckedItem(R.id.nav_gl);
                    break;
                default:
            }
            nsResultReciever = new NsResultReciever(new Handler()); // We will set callback in onResume and unset onPause
        }

        recyclerView = findViewById(R.id.recyclerView);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        mAdapter = new NspItemsAdapter(mDataset);
        recyclerView.setAdapter(mAdapter);
        ItemTouchHelper.Callback ithCallBack = new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                mAdapter.move(viewHolder.getAdapterPosition(), target.getAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                NspItemsAdapter.NspViewHolder nspViewHolder = (NspItemsAdapter.NspViewHolder) viewHolder;
                mDataset.remove(nspViewHolder.getData());
                mAdapter.notifyDataSetChanged();
                updateUploadBtnState();
            }
        };
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(ithCallBack);
        itemTouchHelper.attachToRecyclerView(recyclerView);
        // Select files button
        selectBtn = findViewById(R.id.buttonSelect);
        selectBtn.setOnClickListener(e->{
            Intent fileChooser = new Intent(Intent.ACTION_GET_CONTENT);
            //fileChooser.setType("*/*");
            fileChooser.setType("application/octet-stream");

            if (fileChooser.resolveActivity(getPackageManager()) != null)
                startActivityForResult(
                        Intent.createChooser(fileChooser, getString(R.string.select_file_btn))
                                            , ADD_NSP_INTENT_CODE);
            else
                NsNotificationPopUp.getAlertWindow(this, getResources().getString(R.string.popup_error), getResources().getString(R.string.install_file_explorer));
        });
        // Upload to NS button
        uploadToNsBtn = findViewById(R.id.buttonUpload);

        updateUploadBtnState();
    }

    private void updateUploadBtnState(){
        if (mAdapter.getItemCount() > 0)
            uploadToNsBtn.setEnabled(true);
        else
            uploadToNsBtn.setEnabled(false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        //Snackbar.make(findViewById(android.R.id.content), requestCode+" "+resultCode, Snackbar.LENGTH_SHORT).show();
        if (requestCode != ADD_NSP_INTENT_CODE || data == null)
            return;

        Uri uri = data.getData();

        if (uri == null)
            return;
        if (uri.getScheme() == null || ! uri.getScheme().equals("content"))
            return;

        String fileName = LoperHelpers.getFileNameFromUri(uri, this);
        long fileSize = LoperHelpers.getFileSizeFromUri(uri, this);

        if (fileName == null || fileSize < 0) {
            NsNotificationPopUp.getAlertWindow(this, getResources().getString(R.string.popup_error), getResources().getString(R.string.popup_not_supported));
            return;
        }

        if (! fileName.endsWith(".nsp")){
            NsNotificationPopUp.getAlertWindow(this, getResources().getString(R.string.popup_error), getResources().getString(R.string.popup_wrong_file));
            return;
        }

        boolean shouldBeAdded = true;
        for (NSPElement element: mDataset){
            if (element.getFilename().equals(fileName)) {
                shouldBeAdded = false;
                break;
            }
        }

        NSPElement element;
        if (shouldBeAdded){
            element = new NSPElement(uri, fileName, fileSize);
            if (drawerNavView.getCheckedItem() != null && drawerNavView.getCheckedItem().getItemId() != R.id.nav_gl)
                element.setSelected(true);
            mDataset.add(element);
        }
        else
            return;
        mAdapter.notifyDataSetChanged();
        // Enable upload button
        updateUploadBtnState();
    }
    /*
    private void uploadFilesTemp(){
        ArrayList<NSPElement> toSentArray = new ArrayList<>();
        for (NSPElement element: mDataset){
            if (element.isSelected())
                toSentArray.add(element);
        }
        if (toSentArray.isEmpty()) {
            Snackbar.make(findViewById(android.R.id.content), getString(R.string.nothing_selected_message), Snackbar.LENGTH_LONG).show();
            return;
        }
        Intent serviceStartIntent = new Intent(this, CommunicationsService.class);
        serviceStartIntent.putExtra(NsConstants.NS_RESULT_RECEIVER, nsResultReciever);
        serviceStartIntent.putParcelableArrayListExtra(NsConstants.SERVICE_CONTENT_NSP_LIST, toSentArray);
        if (drawerNavView.getCheckedItem() == null) {
            Snackbar.make(findViewById(android.R.id.content), getString(R.string.no_protocol_selected_message), Snackbar.LENGTH_LONG).show();
            return;
        }
        switch (drawerNavView.getCheckedItem().getItemId()){
            case R.id.nav_tf_usb:
                serviceStartIntent.putExtra(NsConstants.SERVICE_CONTENT_PROTOCOL, NsConstants.PROTO_TF_USB);
                break;
            case R.id.nav_tf_net:
                serviceStartIntent.putExtra(NsConstants.SERVICE_CONTENT_PROTOCOL, NsConstants.PROTO_TF_NET);
                break;
            case R.id.nav_gl:
                if (toSentArray.size() > 1){
                    Snackbar.make(findViewById(android.R.id.content), getString(R.string.one_item_for_gl_notification), Snackbar.LENGTH_LONG).show();
                    return;
                }
                serviceStartIntent.putExtra(NsConstants.SERVICE_CONTENT_PROTOCOL, NsConstants.PROTO_GL_USB);
                break;
        }

        startService(serviceStartIntent);
        blockUI(true);
    }
    */
    private void uploadFiles(){
        //**************************************************************************************************************************************
        try{
            int id = drawerNavView.getCheckedItem().getItemId();
            if (id == R.id.nav_tf_net) {
                Toast.makeText(getApplicationContext(), "Not supported yet", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        catch (NullPointerException npe){
            Snackbar.make(findViewById(android.R.id.content), getString(R.string.no_protocol_selected_message), Snackbar.LENGTH_LONG).show();
        }
        //**************************************************************************************************************************************
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        // If somehow we can't get system service
        if (usbManager == null) {
            NsNotificationPopUp.getAlertWindow(this, getResources().getString(R.string.popup_error), "Internal issue: getSystemService(Context.USB_SERVICE) returned null");
            return; // ??? HOW ???
        }
        // If device not connected
        if (usbDevice == null){
            HashMap<String, UsbDevice> deviceHashMap;

            deviceHashMap = usbManager.getDeviceList();

            for (UsbDevice device : deviceHashMap.values()) {
                if (device.getVendorId() == 1406 && device.getProductId() == 12288) {
                    usbDevice = device;
                }
            }
            // If it's still not connected then it's really not connected.
            if (usbDevice == null) {
                NsNotificationPopUp.getAlertWindow(this, getResources().getString(R.string.popup_error), getResources().getString(R.string.ns_not_found_in_connected));
                return;
            }
            // If we have NS connected check for permissions
            if (! usbManager.hasPermission(usbDevice)){
                usbManager.requestPermission(usbDevice, PendingIntent.getBroadcast(this, 0, new Intent(NsConstants.REQUEST_NS_ACCESS_INTENT), 0));
                return;
            }
        }
        if (! isUsbDeviceAccessible){
            usbManager.requestPermission(usbDevice, PendingIntent.getBroadcast(this, 0, new Intent(NsConstants.REQUEST_NS_ACCESS_INTENT), 0));
            return;
        }

        Intent serviceStartIntent;
        ArrayList<NSPElement> toSentArray = new ArrayList<>();
        for (NSPElement element: mDataset){
            if (element.isSelected())
                toSentArray.add(element);
        }
        if (toSentArray.isEmpty()) {
            Snackbar.make(findViewById(android.R.id.content), getString(R.string.nothing_selected_message), Snackbar.LENGTH_LONG).show();
            return;
        }
        serviceStartIntent = new Intent(this, CommunicationsService.class);
        serviceStartIntent.putExtra(NsConstants.NS_RESULT_RECEIVER, nsResultReciever);
        serviceStartIntent.putParcelableArrayListExtra(NsConstants.SERVICE_CONTENT_NSP_LIST, toSentArray);
        if (drawerNavView.getCheckedItem() == null) {
            Snackbar.make(findViewById(android.R.id.content), getString(R.string.no_protocol_selected_message), Snackbar.LENGTH_LONG).show();
            return;
        }
        switch (drawerNavView.getCheckedItem().getItemId()){
            case R.id.nav_tf_usb:
                serviceStartIntent.putExtra(NsConstants.SERVICE_CONTENT_PROTOCOL, NsConstants.PROTO_TF_USB);
                break;
            case R.id.nav_tf_net:
                serviceStartIntent.putExtra(NsConstants.SERVICE_CONTENT_PROTOCOL, NsConstants.PROTO_TF_NET);
                break;
            case R.id.nav_gl:
                if (toSentArray.size() > 1){
                    Snackbar.make(findViewById(android.R.id.content), getString(R.string.one_item_for_gl_notification), Snackbar.LENGTH_LONG).show();
                    return;
                }
                serviceStartIntent.putExtra(NsConstants.SERVICE_CONTENT_PROTOCOL, NsConstants.PROTO_GL_USB);
                break;
        }
        serviceStartIntent.putExtra(NsConstants.SERVICE_CONTENT_NS_DEVICE, usbDevice);
        startService(serviceStartIntent);
        blockUI(true);
    }

    private void blockUI(boolean shouldBeBlocked){
        if (shouldBeBlocked){
            selectBtn.setEnabled(false);
            recyclerView.setLayoutFrozen(true);
            uploadToNsBtn.setCompoundDrawablesWithIntrinsicBounds(null, getResources().getDrawable(R.drawable.ic_cancel), null, null);
            uploadToNsBtn.setText(R.string.interrupt_btn);
            uploadToNsBtn.setOnClickListener(e -> {
                CommunicationsService.cancel();
            });
            progressBarMain.setVisibility(ProgressBar.VISIBLE);
            progressBarMain.setIndeterminate(true);//TODO
        }
        else {
            selectBtn.setEnabled(true);
            recyclerView.setLayoutFrozen(false);
            uploadToNsBtn.setCompoundDrawablesWithIntrinsicBounds(null, getResources().getDrawable(R.drawable.ic_upload_btn), null, null);
            uploadToNsBtn.setText(R.string.upload_btn);
            uploadToNsBtn.setOnClickListener(e -> {
                //this.uploadFiles();
                this.uploadFiles();
            });
            progressBarMain.setVisibility(ProgressBar.INVISIBLE);
        }
    }
    // Handle service updates
    @Override
    public void onRecieveResults(int code, Bundle bundle) {
        if (code == NsConstants.NS_RESULT_PROGRESS_INDETERMINATE)
            progressBarMain.setIndeterminate(true);
        else{                                                                       // Else NsConstants.NS_RESULT_PROGRESS_VALUE ; ALSO THIS PART IS FULL OF SHIT
            if (progressBarMain.isIndeterminate())
                progressBarMain.setIndeterminate(false);
            progressBarMain.setProgress(bundle.getInt("POSITION"));
        }
    }
    // Deal with global broadcast intents
    private class InnerBroadcastReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null)
                return;
            switch (intent.getAction()) {
                case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                    usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
                    if (usbManager == null)
                        break;  // ???
                    if (usbManager.hasPermission(usbDevice))
                        isUsbDeviceAccessible = true;
                    else {
                        isUsbDeviceAccessible = false;
                        usbManager.requestPermission(usbDevice, PendingIntent.getBroadcast(context, 0, new Intent(NsConstants.REQUEST_NS_ACCESS_INTENT), 0));
                    }
                    break;
                case NsConstants.REQUEST_NS_ACCESS_INTENT:
                    isUsbDeviceAccessible = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    break;
                case UsbManager.ACTION_USB_DEVICE_DETACHED:
                    usbDevice = null;
                    isUsbDeviceAccessible = false;
                    CommunicationsService.cancel();
                    break;
                case NsConstants.SERVICE_TRANSFER_TASK_FINISHED_INTENT:
                    ArrayList<NSPElement> nspElements = intent.getParcelableArrayListExtra(NsConstants.SERVICE_CONTENT_NSP_LIST);
                    for (int i=0; i < mDataset.size(); i++){
                        for (NSPElement recievedNSPe : nspElements)
                            if (recievedNSPe.getFilename().equals(mDataset.get(i).getFilename()))
                                mDataset.get(i).setStatus(recievedNSPe.getStatus());
                    }
                    mAdapter.notifyDataSetChanged();
                    blockUI(false);
                    break;
            }
            //Log.i("LPR", "INTERNAL BR REC: " + intent.getAction()+" "+isUsbDeviceAccessible);
        }
    }
}