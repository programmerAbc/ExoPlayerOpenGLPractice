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
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.io.InputStream;

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

  private final Context context;

  private int program;
  @Nullable
  private GlUtil.Attribute[] attributes;
  @Nullable
  private GlUtil.Uniform[] uniforms;

  int uMvpMatrix;

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
    GlUtil.Attribute[] attributes = GlUtil.getAttributes(program);
    GlUtil.Uniform[] uniforms = GlUtil.getUniforms(program);
    for (GlUtil.Attribute attribute : attributes) {
      if (attribute.name.equals("a_position")) {
        attribute.setBuffer(
            new float[]{
                -1.0f, -1.0f, -1, 1.0f,
                1.0f, -1.0f, 0, 1.0f,
                -1.0f, 1.0f, 0, 1.0f,
                1.0f, 1.0f, -1, 1.0f,
            },
            4);
      } else if (attribute.name.equals("a_texcoord")) {
        attribute.setBuffer(
            new float[]{
                0.0f, 1.0f, 1.0f,
                1.0f, 1.0f, 1.0f,
                0.0f, 0.0f, 1.0f,
                1.0f, 0.0f, 1.0f,
            },
            3);
      }
    }
    this.attributes = attributes;
    this.uniforms = uniforms;
    GLES20.glDisable(GLES20.GL_CULL_FACE);
  }

  @Override
  public void setSurfaceSize(int width, int height) {

  }

  @Override
  public void draw(int frameTexture, long frameTimestampUs) {
    long startTime = System.currentTimeMillis();

    // Run the shader program.
    GlUtil.Uniform[] uniforms = Assertions.checkNotNull(this.uniforms);
    GlUtil.Attribute[] attributes = Assertions.checkNotNull(this.attributes);
    GLES20.glUseProgram(program);
    for (GlUtil.Uniform uniform : uniforms) {
      switch (uniform.name) {
        case "tex_sampler_0":
          uniform.setSamplerTexId(frameTexture, /* unit= */ 0);
          break;
        case "uMvpMatrix":
          updateMatrix();
          try {
            GLES20.glUniformMatrix4fv(uMvpMatrix, 1, false, mvpMatrix, 0);
          } catch (Exception e) {

          }
          break;
      }
    }
    for (GlUtil.Attribute copyExternalAttribute : attributes) {
      copyExternalAttribute.bind();
    }
    for (GlUtil.Uniform copyExternalUniform : uniforms) {
      if (copyExternalUniform.name.equals("uMvpMatrix")) {
        continue;
      }
      copyExternalUniform.bind();
    }
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4);
    GlUtil.checkGlError();
    Log.e(TAG, "draw use time:" + (System.currentTimeMillis() - startTime));
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
}
