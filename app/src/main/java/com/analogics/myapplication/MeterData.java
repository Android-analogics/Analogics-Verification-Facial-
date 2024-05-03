package com.analogics.myapplication;


public class MeterData {
    String value;
    String date;
    String phase;
    MeterType type;
    boolean isManual;
    String image;


    public MeterData(String value, String date, String phase, MeterType type, boolean isManual, String image) {
        this.value = value;
        this.date = date;
        this.phase = phase;
        this.type = type;
        this.isManual = isManual;
        this.image = image;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public MeterType getType() {
        return type;
    }

    public void setType(MeterType type) {
        this.type = type;
    }

    public boolean isManual() {
        return isManual;
    }

    public void setManual(boolean manual) {
        isManual = manual;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }
}

