package com.ns.greg.library.mango.codec;

import android.media.MediaCodec;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author gregho
 * @since 2018/10/22
 */
public class VideoDecoder extends BaseCodec {

  /* init defines, do not modify */
  private static final String TAG = "VideoDecoder";

  /* rendered surface */
  private Surface surface;
  /* video CSD-0 data */
  private byte[] csd0;
  private int csd0Size;
  /* video resolution */
  private int width;
  private int height;

  public VideoDecoder(CodecFormat codecFormat) {
    super(codecFormat);
  }

  /*--------------------------------
   * Rendering function
   *-------------------------------*/

  public void setSurface(Surface surface) {
    this.surface = surface;
  }

  public Surface getSurface() {
    return surface;
  }

  /*--------------------------------
   * Codec functions
   *-------------------------------*/

  public void prepare(byte[] csd0, int csd0Size, int width, int height) {
    this.csd0 = csd0;
    this.csd0Size = csd0Size;
    this.width = width;
    this.height = height;
    prepare();
  }

  private void prepare() {
    synchronized (this) {
      setState(CodecState.PREPARING);
      initMediaFormat();
      initCodec();
    }
  }

  @Override
  protected void initMediaFormat() {
    setFormat(MediaFormatBuilder.videoFormat(getMimeType(), width, height)
        .setByteBuffer(CSD_0, ByteBuffer.wrap(csd0, 0, csd0Size))
        .setMaxInputSize(0)
        .build());
    Log.i(TAG, "INIT VIDEO FORMAT -> succeeded");
  }

  @Override
  protected void initCodec() {
    try {
      MediaCodec codec = MediaCodec.createDecoderByType(getMimeType());
      codec.configure(getFormat(), surface, null, 0);
      setCodec(codec);
      startCodec();
    } catch (IOException e) {
      setState(CodecState.FAILED);
      e.printStackTrace();
      Log.i(TAG, "INIT CODEC -> failed, I/O exception");
    }
  }

  /**
   * Decode the raw video data
   *
   * @param content video data
   * @param contentLength length of video data
   * /* @param format video format
   * /* @param iFrame is i-frame flag
   * /* @param width source width
   * /* @param height source height
   * /* @param playTimeMs play time ms
   */
  public void decode(byte[] content, int contentLength/*, TsRtpFormat.VideoFormat format, int iFrame,
      int width, int height, long playTimeMs*/) {
    synchronized (this) {
      setContent(content);
      setContentLength(contentLength);
    }
  }

  @Override
  void process() {
    try {
      byte[] content = getContent();
      int contentLength = getContentLength();
      if (content != null && contentLength > 0) {
        int inputBufferIndex = getCodec().dequeueInputBuffer(TIMEOUT);
        if (inputBufferIndex >= 0) {
          if (isEos()) {
            getCodec().queueInputBuffer(inputBufferIndex, 0, 0, 0,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
          } else {
            ByteBuffer inputBuffer;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
              inputBuffer = getCodec().getInputBuffers()[inputBufferIndex];
            } else {
              inputBuffer = getCodec().getInputBuffer(inputBufferIndex);
            }

            if (inputBuffer != null) {
              inputBuffer.clear();
              inputBuffer.put(content, 0, contentLength);
              getCodec().queueInputBuffer(inputBufferIndex, 0, contentLength, 0L, 0);
            }
          }
        }
        /* clear decode content */
        setContent(null);
        setContentLength(0);
      }
    } catch (Exception ignored) {
    }
  }

  @Override
  void output() {
    try {
      MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
      int outputBufferIndex = getCodec().dequeueOutputBuffer(bufferInfo, TIMEOUT);
      switch (outputBufferIndex) {
        case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
          /* ignored, since using surface view */
          break;

        case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
          /* TODO: should considering this when format changed? */
          break;

        case MediaCodec.INFO_TRY_AGAIN_LATER:
          /* ignored */
          break;

        default:
          getCodec().releaseOutputBuffer(outputBufferIndex, true);
          break;
      }

      if (isEos()) {
        Log.i(TAG, "MEET FLAG -> `END OF STREAM`");
        stopCodec();
        releaseCodec();
      }
    } catch (Exception ignored) {
    }
  }

  @Override
  public void startCodec() throws NullPointerException {
    if (isState(CodecState.PREPARING) || isState(CodecState.STOP)) {
      try {
        setState(CodecState.PREPARED);
        super.startCodec();
        Log.i(TAG, "START CODEC -> succeeded");
      } catch (NullPointerException e) {
        setState(CodecState.UNINITIALIZED);
        Log.i(TAG, "START CODEC -> failed, no instance");
      } catch (IllegalStateException e) {
        setState(CodecState.UNINITIALIZED);
        Log.i(TAG, "START CODEC -> failed, not configured");
      }
    } else {
      Log.i(TAG, "START CODEC -> failed, illegal state");
    }
  }

  @Override
  void stopCodec() {
    /* FIXME: should considering preparing */
    if (isState(CodecState.PREPARED)) {
      try {
        setState(CodecState.STOP);
        super.stopCodec();
        Log.i(TAG, "STOP CODEC -> succeeded");
      } catch (NullPointerException e) {
        setState(CodecState.UNINITIALIZED);
        Log.i(TAG, "STOP CODEC -> failed, no instance");
      } catch (IllegalStateException e) {
        e.printStackTrace();
        Log.i(TAG, "STOP CODEC -> failed, is in release state");
      }
    } else {
      Log.i(TAG, "STOP CODEC -> failed, illegal state");
    }
  }

  @Override
  void releaseCodec() {
    if (isState(CodecState.STOP)) {
      try {
        setState(CodecState.RELEASE);
        super.releaseCodec();
        if (surface != null) {
          surface.release();
        }

        Log.i(TAG, "RELEASE CODEC -> succeeded");
      } catch (NullPointerException e) {
        setState(CodecState.UNINITIALIZED);
        Log.i(TAG, "RELEASE CODEC -> failed, no instance");
      }
    } else {
      Log.i(TAG, "RELEASE CODEC -> failed, illegal state");
    }
  }

  public void switchSurface(Surface surface) {
    if (isState(CodecState.PREPARED)) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        getCodec().setOutputSurface(surface);
      } else {
        stopCodec();
        releaseCodec();
        setSurface(surface);
        prepare();
      }
    } else {
      Log.i(TAG, "SWITCH TARGET VIEW -> failed, state is not prepared");
    }
  }
}