package com.ns.greg.mango

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View

/**
 * @author gregho
 * @since 2019/2/25
 */
class DemoActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_demo)
    findViewById<View>(R.id.rtsp_btn).setOnClickListener {
      startActivity(Intent(this, RtspActivity::class.java))
    }
    findViewById<View>(R.id.microphone_btn).setOnClickListener {
      startActivity(Intent(this, MicrophoneActivity::class.java))
    }
  }
}