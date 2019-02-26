package com.ns.greg.library.mango.codec;

import android.media.MediaCodecInfo;

/**
 * @author gregho
 * @since 2018/12/5
 */
public enum CodecFormat {

  /*--------------------------------
   * Audio
   *-------------------------------*/
  AUDIO_AAC_LC("audio/mp4a-latm", MediaCodecInfo.CodecProfileLevel.AACObjectLC),
  /*--------------------------------
   * Video
   *-------------------------------*/
  VIDEO_AVC("video/avc", -1);

  private final String type;
  private final int profile;

  CodecFormat(String type, int profile) {
    this.type = type;
    this.profile = profile;
  }

  public String getType() {
    return type;
  }

  public int getProfile() {
    return profile;
  }
}
