package com.ns.greg.library.mango.codec;

import java.nio.ByteBuffer;

/**
 * @author gregho
 * @since 2018/12/6
 */
class CodecConstants {

  static final int ADTS_SIZE = 7;
  private static final int[] FREQUENCIES = new int[] {
      96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050,
      16000, 12000, 11025, 8000, 7350
  };

  /**
   * Gets the index of specific frequency in {@link CodecConstants#FREQUENCIES}
   *
   * Sampling Frequency Index, 13 supported frequencies:
   * 0: 96000 Hz
   * 1: 88200 Hz
   * 2: 64000 Hz
   * 3: 48000 Hz
   * 4: 44100 Hz
   * 5: 32000 Hz
   * 6: 24000 Hz
   * 7: 22050 Hz
   * 8: 16000 Hz
   * 9: 12000 Hz
   * 10: 11025 Hz
   * 11: 8000 Hz
   * 12: 7350 Hz
   * 13: Reserved
   * 14: Reserved
   * 15: frequency is written explicitly
   */
  static int getFrequencyIndex(int sampleRate) {
    /* search the sampling frequencies */
    int frequencyIndex = -1;
    int length = FREQUENCIES.length;
    for (int i = 0; i < length; ++i) {
      if (sampleRate == FREQUENCIES[i]) {
        frequencyIndex = i;
        break;
      }
    }

    return frequencyIndex;
  }

  /**
   * AAAAAAAA AAAABCCD EEFFFFGH HHIJKLMM MMMMMMMM MMMOOOOO OOOOOOPP (QQQQQQQQ QQQQQQQQ)
   * 11111111 11110001 11011100 01000000 01100000 11111111 11111100  00000001 01000000
   * Letter	Length (bits)	Description
   * A	12	syncword 0xFFF, all bits must be 1
   * B	01	MPEG Version: 0 for MPEG-4, 1 for MPEG-2
   * C	02	Layer: always 0
   * D	01	protection absent, Warning, set to 1 if there is no CRC and 0 if there is CRC
   * E	02	profile, the MPEG-4 Audio Object Type minus 1
   * F	04	MPEG-4 Sampling Frequency Index (15 is forbidden)
   * G	01	private bit, guaranteed never to be used by MPEG, set to 0 when encoding, ignore when
   * decoding
   * H	03	MPEG-4 Channel Configuration (in the case of 0, the channel configuration is sent via an
   * inband PCE)
   * I	01	originality, set to 0 when encoding, ignore when decoding
   * J	01	home, set to 0 when encoding, ignore when decoding
   * K	01	copyrighted id bit, the next bit of a centrally registered copyright identifier, set to 0
   * when encoding, ignore when decoding
   * L	01	copyright id start, signals that this frame's copyright id bit is the first bit of the
   * copyright id, set to 0 when encoding, ignore when decoding
   * M	13	frame length, this value must include 7 or 9 bytes of header length: FrameLength =
   * (ProtectionAbsent == 1 ? 7 : 9) + size(AACFrame)
   * O	11	Buffer fullness
   * P	02	Number of AAC frames (RDBs) in ADTS frame minus 1, for maximum compatibility always use 1
   * AAC frame per ADTS frame
   * Q	16	CRC if protection absent is 0
   */
  static byte[] getAdts(int profile, int frequencyIndex, int channelCount, int packetLength) {
    byte[] adts = new byte[ADTS_SIZE];
    adts[0] = (byte) 0xFF;
    adts[1] = (byte) 0xF9;
    adts[2] =
        (byte) (((profile - 1) /* MPEG-4 minus 1 */ << 6) + (frequencyIndex << 2) + (channelCount
            >> 2));
    adts[3] = (byte) (((channelCount & 0x03) << 6) + (packetLength >> 11));
    adts[4] = (byte) ((packetLength & 0x7FF) >> 3);
    adts[5] = (byte) (((packetLength & 7) << 5) + 0x1F);
    adts[6] = (byte) 0xFC;
    return adts;
  }

  static ByteBuffer getAacCsd0(int sampleRate, int channelCount, int aacProfile) {
    int frequencyIndex = getFrequencyIndex(sampleRate);
    ByteBuffer csd0 = ByteBuffer.allocate(2);
    csd0.put((byte) ((aacProfile << 3) | (frequencyIndex >> 1)));
    csd0.position(1);
    csd0.put((byte) ((frequencyIndex & 0x01) << 7 | (channelCount << 3)));
    csd0.flip();
    return csd0;
  }
}