package com.github.kny.nfc;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcF;
import android.nfc.tech.NfcV;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private MyAdapter adapter;
    private NfcAdapter nfcAdapter;
    private String lastDetectUid = "";
    private IntentFilter[] mFilters;
    private PendingIntent mPendingIntent;
    private AlertDialog alertDialog;
    List<RfidRecord> recordList = new ArrayList<>();
    private  static final String STORAGE_FILENAME = "RfidTagRecords";
    private static final int WRITE_EXTERNAL_STORAGE_REQUEST_CODE = 0;
    private static final int READ_EXTERNAL_STORAGE_REQUEST_CODE = 1;
    private static final int MANAGE_EXTERNAL_STORAGE_REQUEST_CODE = 2;
    private final String[][] mTechLists = new String[][] {
            new String[] {
                    NfcA.class.getName(),
                    NfcB.class.getName(),
                    NfcF.class.getName(),
                    NfcV.class.getName(),
                    IsoDep.class.getName(),
                    MifareClassic.class.getName(),
                    MifareUltralight.class.getName(), Ndef.class.getName()
            }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //request permission
        requestPermission();

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(view -> ShowAddNewDialog());

        recordList = new ArrayList<>();
        //StorageWrite("Name1,00000001\nName2,00000000\nName3,01000000\n");
        String res = StorageRead();
        if (res != null) {
            String[] sArray = res.split("\n");
            for(String line : sArray){
                String[] recordInfo = line.split(",");
                if(recordInfo.length == 2){
                    recordList.add(new RfidRecord(recordInfo[0], recordInfo[1]));
                }
            }
        }
        ListView list = (ListView)findViewById(R.id.listView);
        adapter = new MyAdapter(this, recordList);
        list.setAdapter(adapter);
        list.setOnItemClickListener((arg0, arg1, arg2, arg3) -> {
            final RfidRecord record = (RfidRecord)adapter.getItem(arg2);
            final String[] ListStr = {"Emulate UID","Edit","Remove"};
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setItems(ListStr, (dialog, which) -> {
                switch (which){
                    case 0: // Simulate.
                        Toast.makeText(MainActivity.this, "Overwriting...", Toast.LENGTH_SHORT).show();
                        if (WriteUid(record.GetUidFormat())) {
                            Toast.makeText(MainActivity.this, "Finish.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "Failed, Need Root.", Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case 1: // Edit record.
                        ShowEditDialog(record);
                        break;
                    case 2: // Remove record.
                        recordList.remove(record);
                        StorageWrite(ConvertRecord(recordList));
                        adapter.notifyDataSetChanged();
                        break;
                }
            });
            AlertDialog alert = builder.create();
            alert.show();
        });
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        mPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE);
        IntentFilter filter = new IntentFilter();
        filter.addAction(NfcAdapter.ACTION_TAG_DISCOVERED);
        filter.addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
        filter.addAction(NfcAdapter.ACTION_TECH_DISCOVERED);
        try {
            filter.addDataType("*/*");
        } catch (IntentFilter.MalformedMimeTypeException e) {
            throw new RuntimeException("fail", e);
        }
        mFilters = new IntentFilter[] {filter,};
        Intent intent = getIntent();
        resolveIntent(intent);
    }
    /** Custom Dialog **/
    EditText dialogRecordName, dialogRecordUid;
    public void ShowAddNewDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // Get the layout inflater
        LayoutInflater inflater = this.getLayoutInflater();

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setTitle("新增Uid")
                .setView(inflater.inflate(R.layout.custom_dialog, findViewById(android.R.id.content), false))
                // Add action buttons
                .setPositiveButton("新增", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        String uid = dialogRecordUid.getText().toString().trim();
                        String name= dialogRecordName.getText().toString().trim();
                        if (uid.length() != 8 || name.isEmpty()) return;
                        recordList.add( new RfidRecord(dialogRecordName.getText().toString(), dialogRecordUid.getText().toString()));
                        adapter.notifyDataSetChanged();
                        StorageWrite(ConvertRecord(recordList));
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {

                    }
                });
        if (alertDialog != null) {
            alertDialog.dismiss();
            alertDialog = null;
        }
        alertDialog = builder.create();
        alertDialog.show();
        dialogRecordName = ((EditText) alertDialog.findViewById(R.id.recordName));
        dialogRecordUid = ((EditText) alertDialog.findViewById(R.id.recordUid));
        if (dialogRecordUid != null) {
            dialogRecordUid.setText(lastDetectUid);
        }
    }
    public void ShowEditDialog(final RfidRecord record){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // Get the layout inflater
        LayoutInflater inflater = this.getLayoutInflater();

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setTitle("編輯Uid")
                .setView(inflater.inflate(R.layout.custom_dialog, findViewById(android.R.id.content), false))
                // Add action buttons
                .setPositiveButton("儲存", (dialog, id) -> {
                    String name = dialogRecordName.getText().toString();
                    String uid = dialogRecordUid.getText().toString();
                    if(!name.isEmpty() && uid.length() == 8) {
                        record.SetName(name);
                        record.SetUid(uid);
                        adapter.notifyDataSetChanged();
                        StorageWrite(ConvertRecord(recordList));
                    } else { Toast.makeText(MainActivity.this, "必須填寫名稱，以及uid必須為8碼", Toast.LENGTH_SHORT).show(); }
                })
                .setNegativeButton("取消", (dialog, id) -> {});
        if (alertDialog != null) {
            alertDialog.dismiss();
            alertDialog = null;
        }
        alertDialog = builder.create();
        alertDialog.show();
        dialogRecordUid = ((EditText) alertDialog.findViewById(R.id.recordUid));
        dialogRecordName = ((EditText) alertDialog.findViewById(R.id.recordName));
        if (dialogRecordName != null) dialogRecordName.setText(record.GetName());
        if (dialogRecordUid != null) dialogRecordUid.setText(record.GetUidOrigin());
    }

    /** NFC **/
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.i("Foreground dispatch", "Discovered tag with intent: " + intent);
        resolveIntent(intent);
    }
    @Override
    protected void onPause() {
        super.onPause();
        if (alertDialog != null) alertDialog.dismiss();
        nfcAdapter.disableForegroundDispatch(this);
    }
    @Override
    protected void onResume() {
        super.onResume();
        nfcAdapter.enableForegroundDispatch(this, mPendingIntent, mFilters, mTechLists);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (alertDialog != null) alertDialog.dismiss();
    }
    void resolveIntent(Intent intent) {
        Log.i("test", "start method");
        // 1) Parse the intent and get the action that triggered this intent
        String action = intent.getAction();
        // 2) Check if it was triggered by a tag discovered interruption.
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)) {
            Log.i("test", "in if");
            //  3) Get an instance of the TAG from the NfcAdapter
            try {
                Tag tagFromIntent = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                byte[] id = tagFromIntent.getId();
                lastDetectUid = getHexString(id);
                // 讀到tag即自動填寫uid欄位
                if (dialogRecordUid != null && dialogRecordUid.isShown()){
                    dialogRecordUid.setText(lastDetectUid);
                } else {
                    ShowAddNewDialog();
                }
            } catch (Exception e1) {
                Log.e("Error", e1.getMessage(), e1);
            }
        }
        Log.i("test", "end method");
    }

    public static String getHexString(byte[] b) {
        StringBuilder result = new StringBuilder();
        for (byte value : b) {
            result.append(Integer.toString((value & 0xff) + 0x100, 16).substring(1));
        }
        return result.toString();
    }

    /** 改寫UID **/
    public boolean WriteUid(String uid) {
        String RfidInfo = readInfo("RfidRecord/default.conf");
        // 用Regex查找並更改UID 待改善
        String replaced = RfidInfo.replaceAll("( {8}33, 04,) [A-Z0-9]{2}, [A-Z0-9]{2}, [A-Z0-9]{2}, [A-Z0-9]{2},", String.format("$1%s,", uid));
        writeInfo("RfidRecord/copy.conf", replaced);
        // Overwrite uid.
        boolean mountRet = ExecuteAsRoot.execute(new ArrayList<String>(
                Arrays.asList(
                        "mount -o remount,rw /system",
                        "cp -f /storage/emulated/0/RfidRecord/copy.conf /vendor/odm/etc/libnfc-nxp_RF.conf",
                        "mount -o remount,ro /system"
                )));
        // NFC reboot.
        boolean rebootRet = ExecuteAsRoot.execute(new ArrayList<String>(
                Arrays.asList(
                        "svc nfc disable",
                        "svc nfc enable"
                )));
        return mountRet && rebootRet;
    }
    /** 內部儲存空間 I/O **/
    public void StorageWrite(String writeStr) {
        try {
            FileOutputStream fos = openFileOutput(STORAGE_FILENAME, Context.MODE_PRIVATE);
            fos.write(writeStr.getBytes());
            fos.close();
        } catch(Exception e) {
            Log.e("Error", e.getMessage(), e);
        }
    }
    public String StorageRead() {
        try {
            FileInputStream fis = openFileInput(STORAGE_FILENAME);
            byte[] data = new byte[fis.available()];
            int size = fis.read(data);
            fis.close();
            return new String(data, StandardCharsets.UTF_8);
        } catch (FileNotFoundException e) {
            return "";
        } catch(Exception e) {
            Log.e("Error", e.getMessage(), e);
        }
        return "";
    }
    public String ConvertRecord(List<RfidRecord> recordList) {
        StringBuilder convertString = new StringBuilder();
        for(RfidRecord record : recordList){
            convertString.append(String.format("%s,%s\n", record.GetName(), record.GetUidOrigin()));
        }
        return convertString.toString();
    }

    /** 寫入資料**/
    public void writeInfo(String fileName, String strWrite) {
        //WRITE_EXTERNAL_STORAGE
        try {
            String fullPath = Environment.getExternalStorageDirectory().getAbsolutePath();
            String savePath = fullPath + File.separator + "/" + fileName;
            File file = new File(savePath);
            if (!file.exists() && !file.createNewFile()) {
                Log.e("Error", "create file failed");
                return;
            }
            FileWriter fw = new FileWriter(file.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(strWrite);
            bw.close();
        } catch (IOException e) {
            Log.e("Error", e.getMessage(), e);
        }
    }


    /** 讀取資料**/
    public String readInfo(String fileName) {
        //READ_EXTERNAL_STORAGE
        BufferedReader br = null;
        String response = null;
        try {
            StringBuilder output = new StringBuilder();
            String fullPath = Environment.getExternalStorageDirectory().getAbsolutePath();
            String savePath = fullPath + File.separator + "/" + fileName;
            br = new BufferedReader(new FileReader(savePath));
            String line = "";
            while ((line = br.readLine()) != null) {
                output.append(line).append("\n");
            }
            response = output.toString();
            br.close();
        } catch (IOException e) {
            Log.e("Error", e.getMessage(), e);
            return null;
        }
        return response;
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + this.getBaseContext().getPackageName()));
                startActivityForResult(intent, MANAGE_EXTERNAL_STORAGE_REQUEST_CODE);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, MANAGE_EXTERNAL_STORAGE_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            doNext(requestCode);
        }
    }
    private void doNext(int requestCode) {
        switch(requestCode){
            case MANAGE_EXTERNAL_STORAGE_REQUEST_CODE, WRITE_EXTERNAL_STORAGE_REQUEST_CODE, READ_EXTERNAL_STORAGE_REQUEST_CODE:
                Toast.makeText(MainActivity.this, "请允许文件管理权限", Toast.LENGTH_SHORT).show();
                break;
        }
    }
}