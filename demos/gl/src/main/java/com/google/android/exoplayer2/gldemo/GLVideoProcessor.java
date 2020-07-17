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
      -1.0f, -1.0f, 0, 1.0f,
      1.0f, -1.0f, 0, 1.0f,
      -1.0f, 1.0f, 0, 1.0f,
      1.0f, 1.0f, 0, 1.0f};
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
  private int vb;

  private int tb;

  private int ib;

  private final Context context;

  private int program;

  int uMvpMatrix;
  int uTexSampler0;

  boolean mirror = true;
  boolean wantMirror = mirror;

  public GLVideoProcessor(Context context) {
    this.context = context.getApplicationContext();
//    Matrix.perspectiveM(projectionMatrix, 0, 90, 1, 1f, 100f);
    Matrix.orthoM(projectionMatrix, 0, -1, 1, -1, 1, 1, 100);
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
        "attribute vec4 a_position;\n"
            + "void main() {\n"
            + " gl_Position = a_position;\n"
            + "}\n";

    String fragmentShaderCode =
        "void main() {\n"
            + "    gl_FragColor =vec4(1,1,0,1);\n"
            + "}\n";
    try {
      program = GlUtil.compileProgram(vertexShaderCode, fragmentShaderCode);
    } catch (Exception e) {
      Log.e(TAG, "initialize:" + Log.getStackTraceString(e));
    }
    GLES20.glDisable(GLES20.GL_CULL_FACE);
    IntBuffer intBuffer = createIntBuffer(3);
    GLES20.glGenBuffers(3, intBuffer);
//    intBuffer.flip();
    vb = intBuffer.get();
    tb = intBuffer.get();
    ib = intBuffer.get();
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vb);
    GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertexs.length * 4,
        createFloatBuffer(vertexs), GLES20.GL_STATIC_DRAW);
    GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ib);
    GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, index.length * 2, createShortBuffer(index),
        GLES20.GL_STATIC_DRAW);

  }

  @Override
  public void setSurfaceSize(int width, int height) {

  }

  @Override
  public void draw(int frameTexture, long frameTimestampUs) {
    try {
      long startTime = System.currentTimeMillis();
      GLES20.glUseProgram(program);
      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
      int a_position = GLES20.glGetAttribLocation(program, "a_position");
      GLES20.glEnableVertexAttribArray(a_position);
      GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vb);
      GLES20.glVertexAttribPointer(a_position, 4, GLES20.GL_FLOAT, false, 0,
          0);
      GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ib);
      GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT,
         0);
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
    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(data.length * 2);
    return (ShortBuffer) byteBuffer.order(ByteOrder.nativeOrder()).asShortBuffer().put(data).flip();
  }

  public static FloatBuffer createFloatBuffer(float[] data) {
    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(data.length * 4);
    return (FloatBuffer) byteBuffer.order(ByteOrder.nativeOrder()).asFloatBuffer().put(data).flip();
  }

  public static IntBuffer createIntBuffer(int size) {
    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(size * 4);
    return (IntBuffer) byteBuffer.order(ByteOrder.nativeOrder()).asIntBuffer();
  }


}
