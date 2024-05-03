package com.analogics.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.common.util.concurrent.ListenableFuture;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import id.zelory.compressor.Compressor;

public class CameraActivity extends AppCompatActivity {
    ExecutorService cameraExecutor;
    PreviewView cameraView;
    ImageCapture imageCapture;
    Button captureBtn;
    LinearLayout progressLayout;
    String serviceNumber = "";
    String dateFormat = "yyyyMMddHHmmss";
    private static final int PERMISSION_REQUEST_CODE=1;
    boolean disableOCr = false;
    String previousValue = "30";
    MeterType meterType= MeterType.kwh;
    String[] permissionsRequired = new String[]{android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.RECEIVE_BOOT_COMPLETED,
            android.Manifest.permission.CHANGE_NETWORK_STATE,
            android.Manifest.permission.ACCESS_NETWORK_STATE,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.CAMERA};

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        serviceNumber = getIntent().getExtras().getString("serviceNumber");
        captureBtn = findViewById(R.id.captureBtn);
        boolean isFromFullImage = (getIntent().getExtras() != null &&
                getIntent().getExtras().getBoolean("disableOcr", false));
        if (isFromFullImage) {
            findViewById(R.id.cameraView).setVisibility(View.GONE);
            cameraView = findViewById(R.id.bigCameraView);
        } else {
            findViewById(R.id.bigCameraView).setVisibility(View.GONE);
            cameraView = findViewById(R.id.cameraView);
        }
        progressLayout = findViewById(R.id.progressLayout);
        cameraExecutor = Executors.newSingleThreadExecutor();
        startCamera();
        captureBtn.setOnClickListener(view -> takePicture());
    }


    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(cameraView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setTargetAspectRatio(AspectRatio.RATIO_4_3).build();
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePicture() {
        File myFile = createImageFile();
        if (myFile == null) {
            Toast.makeText(getApplicationContext(), "Unable to create File", Toast.LENGTH_SHORT).show();
        }
        progressLayout.setVisibility(View.VISIBLE);
        captureBtn.setVisibility(View.GONE);
        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(myFile).build();
        imageCapture.takePicture(outputFileOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {

                MeterDetails.fullImageBitmap = null;
                Uri savedUri = outputFileResults.getSavedUri();
                Bitmap savedBitmap = toBitmap(savedUri);
                if (savedBitmap == null) {
                    Toast.makeText(getApplicationContext(), "Unable to get Image", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (savedBitmap.getWidth() > savedBitmap.getHeight()) {
                    Matrix matrix = new Matrix();
                    matrix.postRotate(90);
                    savedBitmap = Bitmap.createBitmap(savedBitmap, 0, 0, savedBitmap.getWidth(), savedBitmap.getHeight(), matrix, true);
                }
                MeterDetails.fullImageBitmap = savedBitmap;
                File actualFile;
                Bitmap croppedBitmap;
                boolean isFromAutoExtract = (getIntent().getExtras() != null && getIntent().getExtras().getBoolean("isFromAutoExtract", false));
                if(isFromAutoExtract){
                    Intent intent = new Intent();
                    croppedBitmap = Bitmap.createBitmap(savedBitmap, 0, (savedBitmap.getHeight() / 2) - 650, savedBitmap.getWidth(), 1300);
                    Uri croppedUri = bitmapToUri(croppedBitmap);
                    intent.putExtra("imageUri", croppedUri.toString());
                    setResult(1, intent);
                    finish();
                    return;
                }
                boolean isFromFullImage = (getIntent().getExtras() != null && getIntent().getExtras().getBoolean("disableOcr", false));
                if (isFromFullImage) {
                    try {
                        actualFile = FileUtil.from(getApplicationContext(), savedUri);
                        File fullImage = new Compressor(getApplicationContext()).setQuality(60).setCompressFormat(Bitmap.CompressFormat.JPEG).setDestinationDirectoryPath(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath()).compressToFile(actualFile);
                        String phase = getIntent().getExtras().getString("phase");
                        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat, Locale.getDefault());
                        Date currentDate = new Date();
                        String currentDateFormat = sdf.format(currentDate);
                        MeterDetails.outputBase64 = convertImageFileToBase64(fullImage);
                        MeterDetails.FullImageData = new MeterData("", currentDateFormat, phase, MeterType.FullPhoto, false, MeterDetails.outputBase64);
                        Intent intent = new Intent();
                        Uri croppedUri = bitmapToUri(savedBitmap);
                        intent.putExtra("imageUri", croppedUri.toString());
                        setResult(1, intent);
                        finish();
                        return;
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(CameraActivity.this, "" + e, Toast.LENGTH_SHORT).show());
                        return;
                    }

                    //   croppedBitmap = Bitmap.createBitmap(savedBitmap, 0, 0, savedBitmap.getWidth(), savedBitmap.getHeight());
                } else {
                    croppedBitmap = Bitmap.createBitmap(savedBitmap, 0, (savedBitmap.getHeight() / 2) - 650, savedBitmap.getWidth(), 1300);
                }

                Uri croppedUri = bitmapToUri(croppedBitmap);
                boolean isFromOfflineMode = (getIntent().getExtras() != null && getIntent().getExtras().getBoolean("isFromOfflineMode", false));
                if (isFromOfflineMode){
                    Intent intent = new Intent();
                    intent.putExtra("imageUri", croppedUri.toString());
                    setResult(1, intent);
                    finish();
                    return;
                }

                try {
                    actualFile = FileUtil.from(getApplicationContext(), croppedUri);
                    File compressImage = new Compressor(getApplicationContext()).setMaxWidth(1080).setMaxHeight(520).setQuality(60).setCompressFormat(Bitmap.CompressFormat.JPEG).setDestinationDirectoryPath(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath()).compressToFile(actualFile);
                    String croppedBase64 = convertImageFileToBase64(compressImage);

                    hitOCRApi(croppedBase64);
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Take Picture Catch: " + e, Toast.LENGTH_SHORT).show());
                    Log.e("Take Picture Catch:",e.getMessage());
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Take Picture Error: " + exception.toString(), Toast.LENGTH_SHORT).show());
            Log.e("Take Picture Error:",exception.getMessage());
            }
        });
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
        return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT);
    }

    private void hitOCRApi(String imageBase64) {
        try {

            MeterDetails.outputBase64 = null;
            ClipData clipData = ClipData.newPlainText("Google", imageBase64);
            ((ClipboardManager) getApplicationContext().getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(clipData);
            String url = "https://todoapp-d9a67.el.r.appspot.com/fetchMeterNumber";
//            String url = "https://14.99.141.154:3014"; //atil
//            String url = "https://ocr.atil.info"; //atil

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("img", imageBase64);
            RequestQueue requestQueue = Volley.newRequestQueue(this);

            String prevValue = "320";

            JsonObjectRequest jsonRequest = new JsonObjectRequest(Request.Method.POST, url, jsonObject, jsonObjResp -> {
                try {
                    Intent intent = new Intent();
                    String meterNumber = jsonObjResp.getJSONObject("data").getString("meterNumber");
                    if (meterNumber.equals("")) {
                        meterNumber = " ";
                    }

                    MeterDetails.outputBase64 = jsonObjResp.getJSONObject("data").getString("outputImage");
                    String ocrType = getIntent().getExtras().getString("type");
                    String phase = getIntent().getExtras().getString("phase");
                    SimpleDateFormat sdf = new SimpleDateFormat(dateFormat, Locale.getDefault());
                    Date currentDate = new Date();
                    String currentDateFormat = sdf.format(currentDate);

                    if(StringUtils.equalsIgnoreCase(ocrType, MeterType.kwh.toString()) ||
                            StringUtils.equalsIgnoreCase(ocrType, MeterType.Kvah.toString())){
                        meterNumber = compareValues(prevValue, meterNumber);
                    }

                    MeterData data = new MeterData(meterNumber,
                            currentDateFormat, phase, null, false, MeterDetails.outputBase64);
                    if (ocrType.equals(MeterType.kwh.toString())) {
                        data.setType(MeterType.kwh);
                        MeterDetails.kwhData = data;
                    } else if (ocrType.equals(MeterType.Kvah.toString())) {
                        data.setType(MeterType.Kvah);
                        MeterDetails.kvahData = data;
                    } else if (ocrType.equals(MeterType.Rmd.toString())) {
                        data.setType(MeterType.Rmd);
                        MeterDetails.RmdData = data;
                    }

                    //   saveBase64ToImageFile(MeterDetails.outputBase64, serviceNumber + "_" + ocrType + "_" + meterNumber + "_" + phase + "_" + currentDateFormat + ".png");
                    intent.putExtra("meterNumber", meterNumber);
                    setResult(1, intent);
                    finish();
                } catch (Exception e) {
                    Toast.makeText(getApplicationContext(), "Meter Number Not Found", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }, volleyError -> {
                Toast.makeText(getApplicationContext(), "Error 2 " + volleyError.toString(), Toast.LENGTH_SHORT).show();
                finish();
            });
            jsonRequest.setRetryPolicy(new DefaultRetryPolicy(20000, 5, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            requestQueue.add(jsonRequest);
        } catch (Exception e) {
            finish();
            Toast.makeText(getApplicationContext(), "Error 3 " + e, Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private File createImageFile() {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            return File.createTempFile("JPED_" + timestamp + "_", ".jpg", storageDir);
        } catch (Exception e) {
            return null;
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }

    Uri bitmapToUri(Bitmap bitmap) {
        Uri convertedUri = null;
        String displayName = UUID.randomUUID().toString();
        ContentResolver resolver = getApplicationContext().getContentResolver();

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
        }


        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);

        if (uri != null) {
            try {
                OutputStream outputStream = resolver.openOutputStream(uri);

                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
                convertedUri = uri;
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), "Unable To Convert Bitmap to Uri", Toast.LENGTH_SHORT).show();
            }
        }
        return convertedUri;
    }


    public String convertImageFileToBase64(File file) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            try (Base64OutputStream base64FilterStream = new Base64OutputStream(outputStream, Base64.DEFAULT)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

    private Bitmap toBitmap(Uri uri) {
        try {
            return android.graphics.BitmapFactory.decodeStream(getContentResolver().openInputStream(uri) // Converts URI to Bitmap
            );
        } catch (Exception e) {
            return null;
        }
    }


    private String compareValues(String prevValue, String presValue){
        try{
            double presentValueDb = Double.parseDouble(presValue);
            double prevValueDb = Double.parseDouble(prevValue);

            if(presentValueDb > 1000){
                while(presentValueDb/prevValueDb > 4){
                    presValue = StringUtils.substring(presValue, 1, presValue.length());
                    presentValueDb = Double.parseDouble(presValue);
                }
            }
        }catch(Exception ex){

        }
        return presValue;
    }


}