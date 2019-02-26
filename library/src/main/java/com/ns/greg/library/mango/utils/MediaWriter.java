package com.ns.greg.library.mango.utils;

import android.media.AudioFormat;
import android.os.Environment;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * @author gregho
 * @since 2018/12/13
 */
public class MediaWriter {

  private final File file;
  private BufferedOutputStream outputStream;

  public MediaWriter(String fileName, String fileType) {
    SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US);
    file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        .getAbsolutePath(), fileName + "_" + sdf.format(Calendar.getInstance().getTime()) + fileType);
    try {
      outputStream = new BufferedOutputStream(new FileOutputStream(file));
    } catch (FileNotFoundException e) {
    }
  }

  public void write(byte[] data) {
    if (outputStream != null) {
      try {
        outputStream.write(data);
      } catch (IOException ignored) {
      }
    }
  }

  public void close() {
    if (outputStream != null) {
      try {
        outputStream.flush();
        outputStream.close();
      } catch (IOException ignored) {
      }
    }
  }

  public void writeWavHeader(int sampleRate, int channelConfig,
      int encoding) throws IllegalArgumentException, IOException {
    short channels;
    switch (channelConfig) {
      case AudioFormat.CHANNEL_IN_MONO:
        channels = 1;
        break;
      case AudioFormat.CHANNEL_IN_STEREO:
        channels = 2;
        break;
      default:
        throw new IllegalArgumentException("Unacceptable channel mask");
    }

    short bitDepth;
    switch (encoding) {
      case AudioFormat.ENCODING_PCM_8BIT:
        bitDepth = 8;
        break;
      case AudioFormat.ENCODING_PCM_16BIT:
        bitDepth = 16;
        break;
      case AudioFormat.ENCODING_PCM_FLOAT:
        bitDepth = 32;
        break;
      default:
        throw new IllegalArgumentException("Unacceptable encoding");
    }

    writeWavHeader(sampleRate, channels, bitDepth);
  }

  public void updateWavHeader() throws Exception {
    byte[] sizes = ByteBuffer
        .allocate(8)
        .order(ByteOrder.LITTLE_ENDIAN)
        // There are probably a bunch of different/better ways to calculate
        // these two given your circumstances. Cast should be safe since if the WAV is
        // > 4 GB we've already made a terrible mistake.
        .putInt((int) (file.length() - 8)) // ChunkSize
        .putInt((int) (file.length() - 44)) // Sub chunk2 Size
        .array();
    RandomAccessFile accessWave = null;
    try {
      accessWave = new RandomAccessFile(file, "rw");
      // ChunkSize
      accessWave.seek(4);
      accessWave.write(sizes, 0, 4);
      // Subchunk2Size
      accessWave.seek(40);
      accessWave.write(sizes, 4, 4);
    } catch (IOException ex) {
      // Rethrow but we still close accessWave in our finally
      throw ex;
    } finally {
      if (accessWave != null) {
        try {
          accessWave.close();
        } catch (IOException ex) {
        }
      }
    }
  }

  /*--------------------------------
   * Private functions
   *-------------------------------*/

  private void writeWavHeader(int sampleRate, short channels,
      short bitDepth) throws IOException {
    // Convert the multi-byte integers to raw bytes in little endian format as required by the spec
    byte[] littleBytes = ByteBuffer
        .allocate(14)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putShort(channels)
        .putInt(sampleRate)
        .putInt(sampleRate * channels * (bitDepth / 8))
        .putShort((short) (channels * (bitDepth / 8)))
        .putShort(bitDepth)
        .array();
    // Not necessarily the best, but it's very easy to visualize this way
    outputStream.write(new byte[] {
        // RIFF header
        'R', 'I', 'F', 'F', // ChunkID
        0, 0, 0, 0, // ChunkSize (must be updated later)
        'W', 'A', 'V', 'E', // Format
        // fmt subchunk
        'f', 'm', 't', ' ', // Subchunk1ID
        16, 0, 0, 0, // Subchunk1Size
        1, 0, // AudioFormat
        littleBytes[0], littleBytes[1], // NumChannels
        littleBytes[2], littleBytes[3], littleBytes[4], littleBytes[5], // SampleRate
        littleBytes[6], littleBytes[7], littleBytes[8], littleBytes[9], // ByteRate
        littleBytes[10], littleBytes[11], // BlockAlign
        littleBytes[12], littleBytes[13], // BitsPerSample
        // data subchunk
        'd', 'a', 't', 'a', // Subchunk2ID
        0, 0, 0, 0, // Subchunk2Size (must be updated later)
    });
  }
}
