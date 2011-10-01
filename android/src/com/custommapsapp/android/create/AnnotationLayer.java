/*
 * Copyright 2011 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.custommapsapp.android.create;

import com.custommapsapp.android.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * AnnotationLayer allows drawing items over other visible components on screen
 *
 * @author Marko Teittinen
 */
public class AnnotationLayer extends View {
  private static final String LOG_TAG = "Custom Maps";

  private Paint blackPaint;
  private Paint whitePaint;
  private Bitmap pushpin;
  private List<PointF> tiePoints;
  private Matrix drawMatrix;

  public AnnotationLayer(Context context, AttributeSet attrs) {
    super(context, attrs);

    blackPaint = new Paint();
    blackPaint.setAntiAlias(true);
    blackPaint.setStyle(Paint.Style.STROKE);
    blackPaint.setColor(0x80000000);
    blackPaint.setStrokeWidth(2f);
    whitePaint = new Paint(blackPaint);
    whitePaint.setColor(0x80FFFFFF);
    whitePaint.setStrokeWidth(4f);

    tiePoints = new ArrayList<PointF>();
    drawMatrix = new Matrix();

    pushpin = BitmapFactory.decodeResource(context.getResources(), R.drawable.pushpin);
  }

  /**
   * Adds a collection of points to be marked with a pushpin
   */
  public void addTiePoints(Collection<PointF> points) {
    if (points != null) {
      tiePoints.addAll(points);
    }
  }

  /**
   * Sets the drawing matrix to be used to keep pushpins properly located while
   * the user pans and zooms around the image.
   */
  public void setDrawMatrix(Matrix drawMatrix) {
    this.drawMatrix.set(drawMatrix);
  }

  @Override
  public void onDraw(Canvas canvas) {
    // Mark existing tiepoints
    float[] pointXY = new float[2];
    for (PointF point : tiePoints) {
      pointXY[0] = point.x;
      pointXY[1] = point.y;
      drawMatrix.mapPoints(pointXY);
      canvas.drawBitmap(pushpin, pointXY[0] - 21, pointXY[1] - 52, null);
    }
    // Draw selection circle in the center
    int x = getWidth() / 2;
    int y = getHeight() / 2;
    canvas.drawCircle(x, y, 50, whitePaint);
    canvas.drawCircle(x, y, 50, blackPaint);
    canvas.drawCircle(x, y, 5, whitePaint);
    canvas.drawCircle(x, y, 5, blackPaint);
  }
}
