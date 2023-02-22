package com.example.helloworld;

import android.app.*;
import android.os.*;
import android.widget.*;
import android.view.*;
import android.util.*;

public class HelloWorld extends Activity {

    public void onCreate(Bundle b) {
        super.onCreate(b);
        TextView tv = new TextView(this);
        tv.setText("Hello world!");
        tv.setGravity(Gravity.CENTER);
        setContentView(tv);
    }
}
