package com.ns.greg.mango

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.MotionEvent
import android.widget.Button
import com.ns.greg.library.mango.Microphone
import com.ns.greg.library.mango.codec.CodecFormat.AUDIO_AAC_LC

/**
 * @author gregho
 * @since 2019/2/26
 */
class MicrophoneActivity : AppCompatActivity() {

  /* you need allowed microphone and write external permission */
  private val microphone = Microphone(AUDIO_AAC_LC, true)
  private lateinit var holdBtn: Button
  private val sampleRate = 16_000
  private val channelCount = 1

  @SuppressLint("ClickableViewAccessibility", "MissingPermission")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_microphone)
    holdBtn = findViewById(R.id.hold_btn)
    holdBtn.setOnTouchListener { v, event ->
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> microphone.start(sampleRate, channelCount)
        MotionEvent.ACTION_UP -> microphone.stop()
      }

      true
    }
  }
}