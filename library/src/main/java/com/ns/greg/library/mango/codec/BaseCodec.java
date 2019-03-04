package com.ns.greg.library.mango.codec;

import android.media.MediaCodec;
import android.media.MediaFormat;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author gregho
 * @since 2018/10/22
 */
abstract class BaseCodec {

  /* init defines, do not modify */
  static final long TIMEOUT = 10_000L;
  static final String CSD_0 = "csd-0";

  private final CodecFormat codecFormat;
  private final AtomicBoolean atomicEos;
  private MediaFormat format;
  private volatile MediaCodec codec;
  private volatile CodecState state;
  /* process runnable */
  private volatile ProcessRunnable processRunnable;
  /* media raw data */
  private volatile byte[] content;
  private volatile int contentLength;

  BaseCodec(CodecFormat codecFormat) {
    this.codecFormat = codecFormat;
    this.atomicEos = new AtomicBoolean();
    setState(CodecState.UNINITIALIZED);
  }

  /*--------------------------------
   * Codec format
   *-------------------------------*/

  public CodecFormat getCodecFormat() {
    return codecFormat;
  }

  String getMimeType() {
    return codecFormat.getType();
  }

  int getProfile() {
    return codecFormat.getProfile();
  }

  /*--------------------------------
   * Format functions
   *-------------------------------*/

  MediaFormat getFormat() {
    return format;
  }

  void setFormat(MediaFormat format) {
    this.format = format;
  }

  /*--------------------------------
   * Codec functions
   *-------------------------------*/

  protected abstract void initMediaFormat();

  protected abstract void initCodec();

  MediaCodec getCodec() {
    synchronized (this) {
      return codec;
    }
  }

  void setCodec(MediaCodec codec) {
    synchronized (this) {
      this.codec = codec;
    }
  }

  abstract void process();

  abstract void output();

  protected byte[] getContent() {
    synchronized (this) {
      return content;
    }
  }

  protected void setContent(byte[] content) {
    synchronized (this) {
      this.content = content;
    }
  }

  protected int getContentLength() {
    synchronized (this) {
      return contentLength;
    }
  }

  protected void setContentLength(int contentLength) {
    synchronized (this) {
      this.contentLength = contentLength;
    }
  }

  boolean isEos() {
    return atomicEos.get();
  }

  public void setEos() {
    atomicEos.set(true);
  }

  public void startCodec() throws NullPointerException {
    /* synchronized this to make sure only create one thread at once */
    if (processRunnable == null) {
      synchronized (this) {
        if (processRunnable == null) {
          codec.start();
          atomicEos.set(false);
          processRunnable = new ProcessRunnable(this);
          Thread thread = new Thread(processRunnable);
          thread.setPriority(Thread.MAX_PRIORITY);
          thread.start();
        }
      }
    }
  }

  void stopCodec() throws IllegalStateException {
    codec.stop();
    processRunnable = null;
  }

  void releaseCodec() throws NullPointerException {
    codec.release();
    codec = null;
    setState(CodecState.UNINITIALIZED);
  }

  public void onFormatChanged() {
    stopCodec();
    releaseCodec();
  }

  /*--------------------------------
   * State functions
   *-------------------------------*/

  void setState(CodecState state) {
    synchronized (this) {
      this.state = state;
    }
  }

  public boolean isState(CodecState state) {
    synchronized (this) {
      return this.state == state;
    }
  }

  public CodecState getState() {
    synchronized (this) {
      return state;
    }
  }

  static class ProcessRunnable implements Runnable {

    private final BaseCodec codec;

    ProcessRunnable(BaseCodec reference) {
      this.codec = new WeakReference<>(reference).get();
    }

    @Override public void run() {
      while (codec.isState(CodecState.PREPARED)) {
        codec.process();
        codec.output();
      }
    }
  }
}
