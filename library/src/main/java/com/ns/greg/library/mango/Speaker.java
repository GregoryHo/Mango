package com.ns.greg.library.mango;

import android.media.*;
import android.os.Build;
import android.util.Log;
import com.ns.greg.library.mango.codec.AudioDecoder;
import com.ns.greg.library.mango.codec.CodecFormat;
import com.ns.greg.library.mango.codec.CodecState;
import com.ns.greg.library.mango.codec.listener.DecodeListener;

/**
 * @author gregho
 * @since 2018/12/7
 */
public class Speaker implements DecodeListener {

  /* init defines, do not modify */
  private static final String TAG = "Speaker";
  private static final int BYTES_4096 = 4096;
  private static final int STREAM_TYPE = AudioManager.STREAM_MUSIC;
  private static final int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
  private static final int AUDIO_MODE = AudioTrack.MODE_STREAM;

  private final AudioDecoder audioDecoder;
  /* audio speaker */
  private AudioTrack audioTrack;
  /* decides the audio speaker is mute or not */
  private boolean enabled;

  public Speaker(CodecFormat audioFormat) {
    audioDecoder = new AudioDecoder(audioFormat);
    /* default is playing */
    enabled = true;
  }

  public void prepare(int sampleRate, int channelCount) {
    initAudioTrack(sampleRate, channelCount);
    audioDecoder.prepare(sampleRate, channelCount, this);
  }

  public void start() {
    try {
      audioTrack.play();
    } catch (Exception ignored) {
    }

    audioDecoder.startCodec();
  }

  public void stop() {
    audioDecoder.setEos();
    try {
      audioTrack.pause();
      Log.i(TAG, "audio track pause");
      audioTrack.flush();
      Log.i(TAG, "audio track flush");
      audioTrack.stop();
      Log.i(TAG, "audio track stop");
      audioTrack.release();
      Log.i(TAG, "audio track release");
    } catch (Exception ignored) {
    }
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
    try {
      if (enabled) {
        audioTrack.play();
      } else {
        audioTrack.pause();
        audioTrack.flush();
      }
    } catch (Exception ignored) {
    }
  }

  public void decode(byte[] content, int contentLength, int sampleRate, int channelCount,
      long playTimeMs) {
    audioDecoder.decode(content, contentLength, sampleRate, channelCount, playTimeMs);
  }

  public CodecState getDecoderState() {
    return audioDecoder.getState();
  }

  private void initAudioTrack(int sampleRate, int channelCount) {
    int channelConfig =
        channelCount == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
    int customBufferSize = BYTES_4096;
    if (sampleRate == 16000 && channelCount == 2) {
      customBufferSize *= 2;
    }

    int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, AUDIO_ENCODING);
    int bufferSize = Math.max(customBufferSize, minBufferSize);
    try {
      audioTrack = getAudioTrack(sampleRate, channelConfig, bufferSize);
      Log.i(TAG, "init audio track");
    } catch (IllegalArgumentException custom) {
      Log.i(TAG,
          "create audio track with buffer size failed, replace with min buffer size");
      try {
        audioTrack = getAudioTrack(sampleRate, channelConfig, minBufferSize);
      } catch (IllegalArgumentException min) {
        Log.i(TAG, "create audio track with min buffer size failed");
      }
    } finally {
      /* if initialized then play */
      if (audioTrack != null && enabled) {
        audioTrack.play();
        Log.i(TAG, "audio track start playing");
      }
    }
  }

  private AudioTrack getAudioTrack(int sampleRate, int channelConfig, int bufferSize) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return new AudioTrack(
          new AudioAttributes.Builder().setLegacyStreamType(STREAM_TYPE).build(),
          new AudioFormat.Builder().setChannelMask(channelConfig)
              .setSampleRate(sampleRate)
              .setEncoding(AUDIO_ENCODING)
              .build(), bufferSize, AUDIO_MODE, AudioManager.AUDIO_SESSION_ID_GENERATE);
    } else {
      return new AudioTrack(STREAM_TYPE, sampleRate, channelConfig, AUDIO_ENCODING, bufferSize,
          AUDIO_MODE);
    }
  }

  @Override public void onDecode(byte[] chunk, int length) {
    if (enabled) {
      try {
        audioTrack.write(chunk, 0, length);
      } catch (Exception ignored) {
      }
    }
  }

  @Override public void onFormatChanged(MediaFormat format) {
    int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
    int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
    if (audioTrack.getSampleRate() != sampleRate || audioTrack.getChannelCount() != channelCount) {
      Log.i(TAG, "change audio format");
      Log.i(TAG, "current: ["
          + audioTrack.getSampleRate()
          + ", "
          + audioTrack.getChannelCount()
          + "]");
      Log.i(TAG, "new: ["
          + sampleRate
          + ", "
          + channelCount
          + "]");
      audioDecoder.onFormatChanged();
      prepare(sampleRate, channelCount);
    }
  }
}