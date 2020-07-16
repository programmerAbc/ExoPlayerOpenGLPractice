/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.gldemo;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import javax.microedition.khronos.opengles.GL;

/**
 * Video processor that demonstrates how to overlay a bitmap on video output using a GL shader. The
 * bitmap is drawn using an Android {@link Canvas}.
 */
/* package */ final class GLVideoProcessor
    implements VideoProcessingGLSurfaceView.VideoProcessor {

  public static final String TAG = GLVideoProcessor.class.getSimpleName();

  private final float[] projectionMatrix = new float[16];
  private final float[] modelMatrix = new float[16];
  private final float[] mvpMatrix = new float[16];
  private final float[] vertexs = {
      -1.0f, -1.0f, -1, 1.0f,
      1.0f, -1.0f, 0, 1.0f,
      -1.0f, 1.0f, 0, 1.0f,
      1.0f, 1.0f, -1, 1.0f};
  private final float[] texCoords = {
      0.0f, 1.0f,
      1.0f, 1.0f,
      0.0f, 0.0f,
      1.0f, 0.0f,
  };

  private final short[] index = {
      0, 1, 2,
      1, 3, 2
  };
  private int[] vertexsBuffer = {0};

  private int[] texCoordsBuffer = {0};

  private int[] indexBuffer = {0};

  private final Context context;

  private int program;

  int uMvpMatrix;
  int uTexSampler0;

  boolean mirror = true;
  boolean wantMirror = mirror;

  public GLVideoProcessor(Context context) {
    this.context = context.getApplicationContext();
    Matrix.perspectiveM(projectionMatrix, 0, 90, 1, 1f, 100f);
//    Matrix.orthoM(projectionMatrix, 0, -1, 1, -1, 1, 1, 100);
    Matrix.setIdentityM(modelMatrix, 0);
    if (mirror) {
      makeMirrorMatrix();
    } else {
      makeNotMirrorMatrix();
    }
  }


  public void setMirror(boolean value) {
    wantMirror = value;
  }

  private void makeMirrorMatrix() {
    Matrix.setIdentityM(modelMatrix, 0);
    Matrix.translateM(modelMatrix, 0, 0, 0, -1f);
    Matrix.rotateM(modelMatrix, 0, 180, 0, 1, 0);
    Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0);
  }

  private void makeNotMirrorMatrix() {
    Matrix.setIdentityM(modelMatrix, 0);
    Matrix.translateM(modelMatrix, 0, 0, 0, -1f);
    Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0);
  }

  void updateMatrix() {
    if (mirror == wantMirror) {
      return;
    }
    mirror = wantMirror;
    if (mirror) {
      makeMirrorMatrix();
    } else {
      makeNotMirrorMatrix();
    }
  }

  @Override
  public void initialize() {
    String vertexShaderCode =
        loadAssetAsString(context, "bitmap_overlay_video_processor_vertex.glsl");
    String fragmentShaderCode =
        loadAssetAsString(context, "bitmap_overlay_video_processor_fragment.glsl");
    try {
      program = GlUtil.compileProgram(vertexShaderCode, fragmentShaderCode);
    } catch (Exception e) {
      Log.e(TAG, "initialize:" + Log.getStackTraceString(e));
    }
    uMvpMatrix = GLES20.glGetUniformLocation(program, "uMvpMatrix");
    uTexSampler0 = GLES20.glGetUniformLocation(program, "tex_sampler_0");
    GLES20.glGenBuffers(1, IntBuffer.wrap(vertexsBuffer));
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexsBuffer[0]);
    GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertexs.length*Float.BYTES,
        GlUtil.createBuffer(vertexs), GLES20.GL_STATIC_DRAW);

    GLES20.glGenBuffers(1, IntBuffer.wrap(texCoordsBuffer));
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, texCoordsBuffer[0]);
    GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, texCoords.length*Float.BYTES,
        GlUtil.createBuffer(texCoords), GLES20.GL_STATIC_DRAW);

    GLES20.glGenBuffers(1, IntBuffer.wrap(indexBuffer));
    GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBuffer[0]);
    GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, index.length*Short.BYTES,
        createShortBuffer(index), GLES20.GL_STATIC_DRAW);

    GLES20.glEnableVertexAttribArray(0);
    GLES20.glEnableVertexAttribArray(1);
    GLES20.glBindAttribLocation(program, 0, "a_position");
    GLES20.glBindAttribLocation(program, 1, "a_texcoord");
    GLES20.glDisable(GLES20.GL_CULL_FACE);

    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glUseProgram(program);
  }

  @Override
  public void setSurfaceSize(int width, int height) {

  }

  @Override
  public void draw(int frameTexture, long frameTimestampUs) {
    try {
      long startTime = System.currentTimeMillis();
      updateMatrix();
      try {
        GLES20.glUniformMatrix4fv(uMvpMatrix, 1, false, mvpMatrix, 0);
      } catch (Exception e) {

      }
      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, frameTexture);
      GLES20.glUniform1i(uTexSampler0, 0);
      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
      GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexsBuffer[0]);
      GLES20.glVertexAttribPointer(0, 4, GLES20.GL_FLOAT, false, 0, 0);
      GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, texCoordsBuffer[0]);
      GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 0, 0);
      GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBuffer[0]);
      GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, 0);
      GlUtil.checkGlError();
      Log.e(TAG, "draw use time:" + (System.currentTimeMillis() - startTime));
    } catch (Exception e) {
      Log.e(TAG, "draw:" + Log.getStackTraceString(e));
    }
  }

  private static String loadAssetAsString(Context context, String assetFileName) {
    @Nullable InputStream inputStream = null;
    try {
      inputStream = context.getAssets().open(assetFileName);
      return Util.fromUtf8Bytes(Util.toByteArray(inputStream));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } finally {
      Util.closeQuietly(inputStream);
    }
  }

  public static ShortBuffer createShortBuffer(short[] data) {
    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(data.length * Short.BYTES);
    return (ShortBuffer) byteBuffer.order(ByteOrder.nativeOrder()).asShortBuffer().put(data).flip();
  }
}
