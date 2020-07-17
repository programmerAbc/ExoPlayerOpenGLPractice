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
import android.graphics.Canvas;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;
import androidx.annotation.Nullable;
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

  private final int[] index = {
      0, 1, 2,
      1, 3, 2
  };
  private int vb;

  private int tcb;

  private int ib;

  private final Context context;

  private int program;

  int uMvpMatrix;

  boolean mirror = true;
  boolean wantMirror = mirror;

  int a_position;
  int a_texcoord;
  int tex_sampler_0;

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
    String vertexShaderCode = "uniform mat4 uMvpMatrix;\n"
        + "attribute vec4 a_position;\n"
        + "attribute vec3 a_texcoord;\n"
        + "varying vec2 v_texcoord;\n"
        + "void main() {\n"
        + " gl_Position =uMvpMatrix * a_position;\n"
        + " v_texcoord = a_texcoord.xy;\n"
        + "}\n";

    String fragmentShaderCode = "#extension GL_OES_EGL_image_external : require\n"
        + "precision mediump float;\n"
        + "// External texture containing video decoder output.\n"
        + "uniform samplerExternalOES tex_sampler_0;\n"
        + "\n"
        + "varying vec2 v_texcoord;\n"
        + "void main() {\n"
        + "    vec4 c=texture2D(tex_sampler_0, v_texcoord);\n"
        + "    gl_FragColor =c;\n"
        + "}";
    try {
      program = GlUtil.compileProgram(vertexShaderCode, fragmentShaderCode);
    } catch (Exception e) {
      Log.e(TAG, "initialize:" + Log.getStackTraceString(e));
    }
    GLES20.glDisable(GLES20.GL_CULL_FACE);
    IntBuffer intBuffer = createIntBuffer(3);
    GLES20.glGenBuffers(3, intBuffer);
    vb = intBuffer.get();
    tcb = intBuffer.get();
    ib = intBuffer.get();
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vb);
    GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertexs.length * FLOAT_BYTES,
        createFloatBuffer(vertexs), GLES20.GL_STATIC_DRAW);

    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, tcb);
    GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, texCoords.length * FLOAT_BYTES,
        createFloatBuffer(texCoords), GLES20.GL_STATIC_DRAW);

    GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ib);
    GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, index.length * INT_BYTES,
        createIntBuffer(index),
        GLES20.GL_STATIC_DRAW);
    a_position = GLES20.glGetAttribLocation(program, "a_position");
    GLES20.glEnableVertexAttribArray(a_position);
    a_texcoord = GLES20.glGetAttribLocation(program, "a_texcoord");
    GLES20.glEnableVertexAttribArray(a_texcoord);
    tex_sampler_0 = GLES20.glGetUniformLocation(program, "tex_sampler_0");
    uMvpMatrix = GLES20.glGetUniformLocation(program, "uMvpMatrix");
    GLES20.glUseProgram(program);
  }

  @Override
  public void setSurfaceSize(int width, int height) {

  }

  @Override
  public void draw(int frameTexture, long frameTimestampUs) {
    try {
      long startTime = System.currentTimeMillis();
      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
      updateMatrix();
      GLES20.glUniformMatrix4fv(uMvpMatrix,1,false,mvpMatrix,0);
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, frameTexture);
      GLES20.glUniform1i(tex_sampler_0, 0);

      GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vb);
      GLES20.glVertexAttribPointer(a_position, 4, GLES20.GL_FLOAT, false, 0,
          0);
      GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, tcb);
      GLES20.glVertexAttribPointer(a_texcoord, 2, GLES20.GL_FLOAT, false, 0,
          0);

      GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ib);
      GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_INT,
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
    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(data.length * SHORT_BYTES);
    return (ShortBuffer) byteBuffer.order(ByteOrder.nativeOrder()).asShortBuffer().put(data).flip();
  }

  public static IntBuffer createIntBuffer(int[] data) {
    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(data.length * INT_BYTES);
    return (IntBuffer) byteBuffer.order(ByteOrder.nativeOrder()).asIntBuffer().put(data).flip();
  }

  public static FloatBuffer createFloatBuffer(float[] data) {
    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(data.length * FLOAT_BYTES);
    return (FloatBuffer) byteBuffer.order(ByteOrder.nativeOrder()).asFloatBuffer().put(data).flip();
  }

  public static IntBuffer createIntBuffer(int size) {
    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(size * 4);
    return (IntBuffer) byteBuffer.order(ByteOrder.nativeOrder()).asIntBuffer();
  }

  public static final int INT_BYTES = 4;
  public static final int SHORT_BYTES = 2;
  public static final int FLOAT_BYTES = 4;

}
