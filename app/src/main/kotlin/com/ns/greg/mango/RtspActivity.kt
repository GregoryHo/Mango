package com.ns.greg.mango

import android.graphics.SurfaceTexture
import android.os.Bundle
import android.support.annotation.RawRes
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import com.ns.greg.library.mango.codec.CodecState
import com.ns.greg.library.mango.rtsp.RenderOptions
import com.ns.greg.library.mango.rtsp.RtspPlayer
import java.io.BufferedInputStream
import java.io.IOException

/**
 * @author gregho
 * @since 2019/2/25
 */
class RtspActivity : AppCompatActivity() {

  private companion object Constants {
    const val TAG = "RtspActivity"
    const val SHORT_SIZE = 4
  }

  private val rtspPlayer = RtspPlayer()
  private lateinit var textureView: TextureView

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_rtsp)
    textureView = findViewById(R.id.display_tv)
    textureView.surfaceTextureListener = object : SurfaceTextureListener {
      override fun onSurfaceTextureSizeChanged(
        surface: SurfaceTexture?,
        width: Int,
        height: Int
      ) {
      }

      override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
      }

      override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
        return true
      }

      override fun onSurfaceTextureAvailable(
        surface: SurfaceTexture?,
        width: Int,
        height: Int
      ) {
        rtspPlayer.renderOptions = RenderOptions(Surface(surface))
        Thread {
          preparePlayer(R.raw.test)
        }.start()
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    rtspPlayer.stop()
  }

  private fun preparePlayer(@RawRes resourceId: Int) {
    val inputStream = BufferedInputStream(applicationContext.resources.openRawResource(resourceId))
    val header = ByteArray(100)
    var headerSize = 0
    try {
      headerSize = inputStream.read(header)
    } catch (e: IOException) {
      e.printStackTrace()
    } finally {
      inputStream.close()
    }

    if (headerSize > 0) {
      val spsStartIndex = findHead(header, 0, headerSize)
      Log.i(TAG, "found sps: $spsStartIndex")
      if (spsStartIndex == -1) {
        return
      }

      val ppsStartIndex = findHead(header, spsStartIndex + SHORT_SIZE, headerSize)
      Log.i(TAG, "found pps: $ppsStartIndex")
      if (ppsStartIndex == -1) {
        return
      }

      val ppsEndIndex = findHead(header, ppsStartIndex + SHORT_SIZE, headerSize)
      Log.i(TAG, "header length: $ppsEndIndex")
      /* no needs to check end index */
      rtspPlayer.prepareVideoDecoder(header, ppsEndIndex)
      if (rtspPlayer.videoDecoderState == CodecState.PREPARED) {
        readFile(resourceId, ppsEndIndex)
      }
    }
  }

  private fun findHead(
    data: ByteArray,
    offSet: Int,
    length: Int
  ): Int {
    for (i in offSet until length) {
      if (isH264Header(data, i, length)) {
        return i
      }
    }

    return -1
  }

  private fun isH264Header(
    data: ByteArray,
    offset: Int,
    length: Int
  ): Boolean {
    if (offset + 4 > length) {
      return false
    }

    return (data[offset] == 0.toByte()) and (data[offset + 1] == 0.toByte()) and (data[offset + 2] == 0.toByte()) and (data[offset + 3] == 1.toByte())
  }

  private fun readFile(
    @RawRes resourceId: Int,
    headerOffset: Int
  ) {
    val inputStream = BufferedInputStream(applicationContext.resources.openRawResource(resourceId))
    /* skip header */
    inputStream.read(ByteArray(headerOffset))
    var bufferLength = 655_36
    var buffer = ByteArray(bufferLength)
    var offset = 0
    var tempBuffer: ByteArray? = null
    val flag = true
    while (flag) {
      val length = inputStream.available()
      if (length > 0) {
        val read = inputStream.read(buffer, offset, bufferLength - offset)
        /* if has uncompleted frame */
        tempBuffer?.run {
          System.arraycopy(this, 0, buffer, 0, offset)
        }
        var currentFrame = findHead(buffer, 0, read)
        while (currentFrame > -1) {
          val nextFrame = findHead(buffer, currentFrame + SHORT_SIZE, read)
          if (nextFrame > -1) {
            val contentLength = nextFrame - currentFrame
            val content = ByteArray(contentLength)
            System.arraycopy(buffer, currentFrame, content, 0, contentLength)
            rtspPlayer.decodeVideo(content, contentLength)
            /* decode as 60 FPS */
            Thread.sleep(17L)
            /* seek next frame */
            currentFrame = nextFrame
          } else {
            offset = bufferLength - currentFrame
            tempBuffer = ByteArray(offset)
            /* stored the uncompleted frame to temp buffer */
            System.arraycopy(buffer, currentFrame, tempBuffer, 0, offset)
            /* if current frame index equals 0, means buffer size too small */
            if (currentFrame == 0) {
              bufferLength *= 2
              buffer = ByteArray(bufferLength)
            }

            /* seek new buffer */
            break
          }
        }
      } else {
        /* end of stream, close the input stream */
        inputStream.close()
        /* endless loop stream */
        readFile(resourceId, headerOffset)
      }
    }
  }
}