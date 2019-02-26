package com.ns.greg.library.mango;

import android.Manifest;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.support.annotation.RequiresPermission;
import android.util.Log;
import com.ns.greg.library.mango.codec.AudioEncoder;
import com.ns.greg.library.mango.codec.CodecFormat;
import com.ns.greg.library.mango.codec.CodecState;
import com.ns.greg.library.mango.codec.listener.EncodeListener;
import com.ns.greg.library.mango.utils.MediaWriter;

import java.lang.ref.WeakReference;

/**
 * @author gregho
 * @since 2018/12/5
 */
public class Microphone implements EncodeListener {

  public interface RecordListener {

    void onSpeaking(byte[] chunk, int length);

    void onTurnOff();
  }

  /* init defines, do not modify */
  private static final String TAG = "Microphone";
  private static final int BIT_RATE = 16000;
  private static final int BYTES_2048 = 2048;
  private static final int AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_COMMUNICATION;
  private static final int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

  private final AudioEncoder audioEncoder;
  private AudioRecord audioRecord;
  private RecordListener listener;
  /* recording runnable */
  private RecordingRunnable recordingRunnable;
  /* recording flag */
  private boolean recording;
  private int bufferSize = 0;
  /* write file for debug */
  private final boolean saved;
  private MediaWriter aacWriter;
  private MediaWriter wavWriter;

  public Microphone() {
    this(false);
  }

  public Microphone(boolean saved) {
    this(null, saved);
  }

  public Microphone(CodecFormat audioFormat) {
    this(audioFormat, false);
  }

  public Microphone(CodecFormat audioFormat, boolean saved) {
    if (audioFormat != null) {
      this.audioEncoder = new AudioEncoder(audioFormat);
    } else {
      this.audioEncoder = null;
    }

    this.saved = saved;
  }

  @RequiresPermission(Manifest.permission.RECORD_AUDIO)
  public boolean start(int sampleRate, int channelCount) {
    return start(sampleRate, channelCount, null);
  }

  @RequiresPermission(Manifest.permission.RECORD_AUDIO)
  public boolean start(int sampleRate, int channelCount, RecordListener listener) {
    if (recordingRunnable == null) {
      synchronized (this) {
        if (recordingRunnable == null) {
          this.listener = listener;
          initAudioRecord(sampleRate, channelCount);
          if (audioRecord == null) {
            return false;
          }

          if (audioEncoder != null) {
            /*  aac */
            audioEncoder.prepare(sampleRate, channelCount, BIT_RATE, this);
            if (audioEncoder.getState() != CodecState.PREPARED) {
              return false;
            }

            if (saved) {
              aacWriter = new MediaWriter("encode", ".aac");
            }
          } else {
            /* pcm */
            if (saved) {
              wavWriter = new MediaWriter("pcm", ".wav");
              try {
                wavWriter.writeWavHeader(sampleRate, channelCount == 1 ? AudioFormat.CHANNEL_IN_MONO
                    : AudioFormat.CHANNEL_IN_STEREO, AUDIO_ENCODING);
              } catch (Exception e) {
                e.printStackTrace();
              }
            }
          }

          recording = true;
          recordingRunnable = new RecordingRunnable(this);
          Thread thread = new Thread(recordingRunnable);
          thread.setPriority(Thread.MAX_PRIORITY);
          thread.start();
          return true;
        }
      }
    }

    return false;
  }

  public boolean stop() {
    if (recording) {
      synchronized (this) {
        if (recording) {
          /* end audio encoder */
          if (audioEncoder != null) {
            audioEncoder.setEos();
          } else if (saved) {
            try {
              wavWriter.close();
              wavWriter.updateWavHeader();
            } catch (Exception ignored) {
            }
          }

          /* stop runnable */
          recording = false;
          /* stop and release audio record */
          audioRecord.stop();
          Log.i(TAG, "audio record stop");
          audioRecord.release();
          Log.i(TAG, "audio record release");
          /* release runnable */
          recordingRunnable = null;
          return true;
        }
      }
    }

    return false;
  }

  private void initAudioRecord(int sampleRate, int channelCount) {
    int channelConfig =
        channelCount == 1 ? AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO;
    int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, AUDIO_ENCODING);
    bufferSize = Math.max(BYTES_2048, minBufferSize);
    try {
      audioRecord = getAudioRecord(sampleRate, channelConfig, bufferSize);
      Log.i(TAG, "init audio record");
    } catch (IllegalArgumentException custom) {
      Log.i(TAG, "create audio record with buffer size failed, replace with min buffer size");
      bufferSize = minBufferSize;
      try {
        audioRecord = getAudioRecord(sampleRate, channelConfig, bufferSize);
      } catch (IllegalArgumentException min) {
        Log.i(TAG, "create audio record with min buffer size failed");
      }
    } finally {
      if (audioRecord != null) {
        audioRecord.startRecording();
        Log.i(TAG, "audio record start recording");
      }
    }
  }

  private AudioRecord getAudioRecord(int sampleRate, int channelConfig, int bufferSize) {
    return new AudioRecord(AUDIO_SOURCE, sampleRate, channelConfig, AUDIO_ENCODING, bufferSize);
  }

  private void record(byte[] buffer) {
    int bufferSize = buffer.length;
    int offset = 0;
    /* collect audio data until the buffer size */
    while (true) {
      if (!recording) {
        /* break when not recording */
        break;
      }

      int read = audioRecord.read(buffer, offset, bufferSize - offset);
      if (read > 0) {
        offset += read;
      } else {
        break;
      }

      if (offset >= bufferSize) {
        break;
      }
    }

    if (offset != AudioRecord.ERROR_INVALID_OPERATION) {
      if (audioEncoder != null) {
        audioEncoder.encode(buffer, offset, 0);
      } else if (listener != null) {
        listener.onSpeaking(buffer, offset);
        wavWriter.write(buffer);
      }
    }
  }

  @Override public void onEncode(byte[] chunk, int length) {
    if (listener != null) {
      listener.onSpeaking(chunk, length);
    }

    if (saved) {
      aacWriter.write(chunk);
    }
  }

  @Override public void onStop() {
    if (listener != null) {
      listener.onTurnOff();
    }

    if (saved) {
      aacWriter.close();
    }
  }

  private static class RecordingRunnable implements Runnable {

    private final Microphone instance;
    private final byte[] buffer;

    RecordingRunnable(Microphone reference) {
      instance = new WeakReference<>(reference).get();
      buffer = new byte[instance.bufferSize];
    }

    @Override public void run() {
      Log.i(TAG, "RECORDING -> start");
      while (instance.recording) {
        instance.record(buffer);
      }

      Log.i(TAG, "RECORDING -> stop");
    }
  }
}