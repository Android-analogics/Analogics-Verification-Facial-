package com.analogics.myapplication;


public enum MeterType {
    kwh("kwh"),
    Kvah("kvah"),
    Rmd("rmd"),
    FullPhoto("fullPhoto");

    private String stringValue;
    private MeterType(String toString) {
        stringValue = toString;
    }
    @Override
    public String toString() {
        return stringValue;
    }
}