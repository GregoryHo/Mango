package com.ns.greg.library.mango.rtsp;

import android.view.Surface;

/**
 * @author gregho
 * @since 2018/10/23
 */
public class RenderOptions {

  static final int DEFAULT_WIDTH = 1280;
  static final int DEFAULT_HEIGHT = 720;

  private final Surface surface;
  private final int width;
  private final int height;

  public RenderOptions(Surface textureView) {
    this(textureView, DEFAULT_WIDTH, DEFAULT_HEIGHT);
  }

  public RenderOptions(Surface surface, int width, int height) {
    this.surface = surface;
    this.width = width;
    this.height = height;
  }

  public RenderOptions switchSurface(Surface targetSurface) {
    return switchSurface(targetSurface, width, height);
  }

  public RenderOptions switchSurface(Surface targetSurface, int width, int height) {
    return new RenderOptions(targetSurface, width, height);
  }

  public Surface getSurface() {
    return surface;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }
}