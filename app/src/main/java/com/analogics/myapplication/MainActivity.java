package com.analogics.myapplication;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Base64OutputStream;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import id.zelory.compressor.Compressor;

public class MainActivity extends AppCompatActivity {
    String meterPhaseType = "3";
    MeterType meterType = MeterType.kwh;
    Button button;
    ImageView imageView;
    TextView textView;
    String[] permissionsRequired = new String[]{android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.RECEIVE_BOOT_COMPLETED,
            android.Manifest.permission.CHANGE_NETWORK_STATE,
            android.Manifest.permission.ACCESS_NETWORK_STATE,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.CAMERA};
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        button = findViewById(R.id.btn_start);
        imageView = findViewById(R.id.imageMeter);
        textView = findViewById(R.id.textValue);

        if(!checkPermissions()){
            requestPermissions();
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Storage Permission is Granted", Toast.LENGTH_SHORT).show();
        } else {
            requestForStoragePermissions();
        }
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openOcrCamera(false, MeterType.kwh, "2408");
            }
        });
    }
    boolean disableOCr = false;
    String  previousValue = "2408";
    private void openOcrCamera(boolean isOcrDisable,
                               MeterType ocrType, String prevValue) {

        disableOCr = isOcrDisable;
        previousValue = prevValue;
        meterType = ocrType;

        Intent intent = new Intent(MainActivity.this, CameraActivity.class);
        intent.putExtra("disableOcr", isOcrDisable);
        intent.putExtra("type", ocrType.toString());
        intent.putExtra("phase", meterPhaseType);
        intent.putExtra("serviceNumber", "2408 00251");
        intent.putExtra("prevValue", prevValue);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.putExtra("isFromAutoExtract", (ocrType != MeterType.FullPhoto));
        startActivityForResult(intent, 111);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data == null) return;
        String imageUri = data.getStringExtra("imageUri");
        if (imageUri == null) return;
        try {
            imageView.setVisibility(View.VISIBLE);
            imageView.setImageURI(Uri.parse(imageUri));
            imageView.setOnClickListener(view -> {
                ImageSheet imageSheet = new ImageSheet(MeterDetails.fullImageBitmap);
                imageSheet.show(getSupportFragmentManager(), "");
            });
            File actualFile = FileUtil.from(getApplicationContext(), Uri.parse(imageUri));
            File compressImage = new Compressor(getApplicationContext()).setMaxWidth(1080).setMaxHeight(520).setQuality(60).
                    setCompressFormat(Bitmap.CompressFormat.JPEG).
                    setDestinationDirectoryPath(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath()).compressToFile(actualFile);
            String croppedBase64 = convertImageFileToBase64(compressImage);
            hitOCRApi(croppedBase64);

        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Take Picture Catch: " + e, Toast.LENGTH_SHORT).show());

        }
    }
    private String convertImageFileToBase64(File file) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            try (Base64OutputStream base64FilterStream = new Base64OutputStream(outputStream, android.util.Base64.DEFAULT)) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    try (InputStream inputStream = Files.newInputStream(file.toPath())) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            base64FilterStream.write(buffer, 0, bytesRead);
                        }
                    }
                } else {
                    try (InputStream inputStream = new FileInputStream(file)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            base64FilterStream.write(buffer, 0, bytesRead);
                        }
                    }
                }
            }
            return outputStream + "";
        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }
    private void hitOCRApi(String imageBase64) {
        try {

            String dateFormat = "yyyyMMddHHmmss";
            MeterDetails.outputBase64 = null;
            ClipData clipData = ClipData.newPlainText("Google", imageBase64);
            ((ClipboardManager) getApplicationContext().getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(clipData);
            String url = "https://todoapp-d9a67.el.r.appspot.com/fetchMeterNumber";
//            String url = "https://14.99.141.154:3014"; //atil
//            String url = "https://ocr.atil.info"; //atil

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("img", imageBase64);
            RequestQueue requestQueue = Volley.newRequestQueue(this);
            JsonObjectRequest jsonRequest = new JsonObjectRequest(Request.Method.POST, url, jsonObject, jsonObjResp -> {
                try {
                    String meterNumber = jsonObjResp.getJSONObject("data").getString("meterNumber");
                    if (meterNumber.equals("")) {
                        meterNumber = " ";
                    }

                    MeterDetails.outputBase64 = jsonObjResp.getJSONObject("data").getString("outputImage");
                    SimpleDateFormat sdf = new SimpleDateFormat(dateFormat, Locale.getDefault());
                    Date currentDate = new Date();
                    String currentDateFormat = sdf.format(currentDate);

                    MeterData data = new MeterData(meterNumber,
                            currentDateFormat, meterPhaseType, null, false, MeterDetails.outputBase64);

                    if (meterType.toString().equals(MeterType.kwh.toString())) {
                        data.setType(MeterType.kwh);
                        MeterDetails.kwhData = data;
                    } else if (meterType.toString().equals(MeterType.Kvah.toString())) {
                        data.setType(MeterType.Kvah);
                        MeterDetails.kvahData = data;
                    } else if (meterType.toString().equals(MeterType.Rmd.toString())) {
                        data.setType(MeterType.Rmd);
                        MeterDetails.RmdData = data;
                    }

                    textView.setText("" + meterNumber);

                } catch (Exception e) {

                    Toast.makeText(getApplicationContext(), "Meter Not Found", Toast.LENGTH_SHORT).show();

                }
            }, volleyError -> {

                Toast.makeText(getApplicationContext(), "Error 2 " + volleyError.toString(), Toast.LENGTH_SHORT).show();

            });
            jsonRequest.setRetryPolicy(new DefaultRetryPolicy(20000, 5, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            requestQueue.add(jsonRequest);
        } catch (Exception e) {

            Toast.makeText(getApplicationContext(), "Error 3 " + e, Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }
    private boolean checkPermissions() {
        for (String permission : permissionsRequired) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
   private int PERMISSION_REQUEST_CODE =1;
    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, permissionsRequired, PERMISSION_REQUEST_CODE);
    }
    private static final int STORAGE_PERMISSION_CODE = 100; // You can define this constant as needed

    private void requestForStoragePermissions() {
        // Check if the SDK version is Android 11 (R) or above

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                // Intent to open system settings for managing app's access to all files
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", this.getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace(); // Handle exception
            }
        } else {
            // Request storage permissions for versions below Android 11
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                    },
                    STORAGE_PERMISSION_CODE
            );
        }
    }
}