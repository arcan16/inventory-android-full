package com.example.myapplication;

import android.app.Application;

import com.example.myapplication.api.ApiClient;
import com.example.myapplication.db.AppDatabase;

public class InventoryApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppDatabase.init(this);
        ApiClient.init(this);
    }
}
