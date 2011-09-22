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
import android.graphics.Paint;
import android.graphics.Point;
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
  private List<Point> tiePoints;
  private float offsetX = 0;
  private float offsetY = 0;

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

    tiePoints = new ArrayList<Point>();

    pushpin = BitmapFactory.decodeResource(context.getResources(), R.drawable.pushpin);
  }

  /**
   * Adds a collection of points to be marked with a pushpin
   */
  public void addTiePoints(Collection<Point> points) {
    if (points != null) {
      tiePoints.addAll(points);
    }
  }

  /**
   * Sets the display offset to keep pushpins properly located while user drags
   * the image around. Called by ImageDisplay.
   */
  public void setOffset(float offsetX, float offsetY) {
    this.offsetX = offsetX;
    this.offsetY = offsetY;
  }

  @Override
  public void onDraw(Canvas canvas) {
    // Draw existing tiepoints
    for (Point p : tiePoints) {
      canvas.drawBitmap(pushpin, offsetX - 21 + p.x, offsetY - 52 + p.y, null);
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
