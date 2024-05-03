package com.analogics.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class MeterDetails {

    public static String outputBase64 = null;
    public static Bitmap fullImageBitmap = null;

    public static MeterData kwhData = new MeterData("", "", "", MeterType.kwh, false, "");
    public static MeterData kvahData = new MeterData("", "", "", MeterType.Kvah, false, "");
    public static MeterData RmdData = new MeterData("", "", "", MeterType.Rmd, false, "");
    public static MeterData FullImageData = new MeterData("", "", "", MeterType.FullPhoto, false, "");


    public static void saveOfflineImages(Context context, String serviceNumber, ArrayList<Pair<String, String>> imageUris) {
        try {
            String sdcardPath = Environment.getExternalStorageDirectory() + "/Android/TSSPDCL_OFFLINE_IMAGES/" + serviceNumber;
            File customDir = new File(sdcardPath);
            deleteFolder(customDir);
            for (Pair<String, String> meterData : imageUris) {
                if (!customDir.exists()) {
                    customDir.mkdir();
                }
                byte[] imageBytes = Base64.decode(meterData.second, Base64.DEFAULT);
                String fileName = "" + meterData.first + ".png";
                File imageFile = new File(customDir, fileName);
                FileOutputStream outputStream = new FileOutputStream(imageFile);
                outputStream.write(imageBytes);
                outputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.d("meterApp", e.toString());
            Toast.makeText(context, "Failed to download images " + e.toString(), Toast.LENGTH_SHORT).show();
        }
    }


    public static void saveBase64ToImageFile(Context context, String serviceNumber) {
        try {
            // Decode the Base64 string to binary data
            ArrayList<MeterData> mList = new ArrayList<>();
            mList.add(kwhData);
            mList.add(kvahData);
            mList.add(RmdData);
            mList.add(FullImageData);
            String sdcardPath = Environment.getExternalStorageDirectory() + "/Android/TSSPDCL_METER_IMAGES/" + serviceNumber;
            File customDir = new File(sdcardPath);
            deleteFolder(customDir);
            for (MeterData meterData : mList) {
                if (!customDir.exists()) {
                    customDir.mkdir();
                }
                byte[] imageBytes = Base64.decode(meterData.image, Base64.DEFAULT);
                String fileName = "";
                if (meterData.isManual) {
                    fileName = meterData.type + "_" + meterData.value + "_" + meterData.phase + "_" + meterData.date + "_Manual.png";
                } else {
                    fileName = meterData.type + "_" + meterData.value + "_" + meterData.phase + "_" + meterData.date + ".png";
                }
                File imageFile = new File(customDir, fileName);
                FileOutputStream outputStream = new FileOutputStream(imageFile);
                outputStream.write(imageBytes);
                outputStream.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
            Log.d("meterApp", e.toString());
            Toast.makeText(context, "Failed to save " + e.toString(), Toast.LENGTH_SHORT).show();
        }
    }

    public static void deleteFolder(File folder) {
        if (folder == null || !folder.exists()) {
            return;
        }

        if (folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    // Recursively delete files and subdirectories
                    if (file.isDirectory()) {
                        deleteFolder(file);
                    } else {
                        file.delete();
                    }
                }
            }
        }

        folder.delete();
    }
}
