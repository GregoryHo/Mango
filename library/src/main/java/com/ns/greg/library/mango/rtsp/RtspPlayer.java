package com.ns.greg.library.mango.rtsp;

import android.util.Log;
import android.view.Surface;
import com.ns.greg.library.mango.Speaker;
import com.ns.greg.library.mango.codec.CodecFormat;
import com.ns.greg.library.mango.codec.CodecState;
import com.ns.greg.library.mango.codec.VideoDecoder;

/**
 * @author gregho
 * @since 2018/10/11
 */
public class RtspPlayer {

  private static final String TAG = "RtspHandler";

  private final VideoDecoder videoDecoder;
  private final Speaker speaker;
  private RenderOptions renderOptions;

  public RtspPlayer() {
    this(CodecFormat.VIDEO_AVC, CodecFormat.AUDIO_AAC_LC);
  }

  public RtspPlayer(CodecFormat videoFormat, CodecFormat audioFormat) {
    videoDecoder = new VideoDecoder(videoFormat);
    speaker = new Speaker(audioFormat);
  }

  /*--------------------------------
   * Rendering function
   *-------------------------------*/

  public void setRenderOptions(RenderOptions renderOptions) {
    this.renderOptions = renderOptions;
    if (renderOptions != null) {
      Surface surface = renderOptions.getSurface();
      if (surface != null) {
        videoDecoder.setSurface(renderOptions.getSurface());
      }
    }
  }

  public RenderOptions getRenderOptions() {
    return renderOptions;
  }

  public void switchTargetView(Surface surface) {
    if (renderOptions != null) {
      renderOptions.switchSurface(surface);
      videoDecoder.switchSurface(surface);
    } else {
      Log.i(TAG, "SWITCH TARGET VIEW -> failed, there is no render options to switch.");
    }
  }

  /*--------------------------------
   * Codec functions
   *-------------------------------*/

  public void prepareVideoDecoder(byte[] data, int size) {
    if (renderOptions != null) {
      videoDecoder.prepare(data, size, renderOptions.getWidth(), renderOptions.getHeight());
    } else {
      videoDecoder.prepare(data, size, RenderOptions.DEFAULT_WIDTH, RenderOptions.DEFAULT_HEIGHT);
    }
  }

  public void prepareSpeaker(int sampleRate, int channelCount) {
    speaker.prepare(sampleRate, channelCount);
  }

  public Boolean formatChanged(CodecFormat videoFormat, CodecFormat audioFormat) {
    return videoDecoder.getCodecFormat() != videoFormat || speaker.getCodecFormat() != audioFormat;
  }

  public void start() {
    videoDecoder.startCodec();
    speaker.start();
  }

  public void stop() {
    videoDecoder.setEos();
    speaker.stop();
  }

  public void setSpeakerState(boolean state) {
    speaker.setEnabled(state);
  }

  public boolean getSpeakerState() {
    return speaker.isEnabled();
  }

  public void decodeVideo(byte[] content, int contentLength/*, int iFrame, int width, int height,
      long playTimeMs*/) {
    videoDecoder.decode(content, contentLength/*, iFrame, width, height, playTimeMs*/);
  }

  public void decodeAudio(byte[] content, int contentLength, int sampleRate, int channelCount,
      long playTimeMs) {
    speaker.decode(content, contentLength, sampleRate, channelCount, playTimeMs);
  }

  /*--------------------------------
   * State functions
   *-------------------------------*/

  public CodecState getVideoDecoderState() {
    return videoDecoder.getState();
  }

  public CodecState getSpeakerDecoderState() {
    return speaker.getDecoderState();
  }
}
