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
package com.custommapsapp.android;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

/**
 * InertiaScroller makes it possible for user to "throw" the map display and
 * let it scroll after the finger has been lifted from screen.
 *
 * @author Marko Teittinen
 */
public class InertiaScroller {
  private static final double FRICTION = 5.0;

  private MapDisplay map;
  private List<View> overlayViews = new ArrayList<>();
  private double xv = 0;
  private double yv = 0;
  private double xFriction = 0;
  private double yFriction = 0;

  public InertiaScroller(MapDisplay map) {
    setMap(map);
  }

  public void setMap(MapDisplay map) {
    if (this.map != null) {
      this.map.setOnTouchListener(null);
    }
    this.map = map;
    if (map != null) {
      map.setOnTouchListener(touchListener);
    }
  }

  public void setOverlayViews(View... views) {
    overlayViews.clear();
    Collections.addAll(overlayViews, views);
  }

  private void startScrolling(float xv, float yv) {
    double speed = Math.sqrt(xv * xv + yv * yv);
    double percent = FRICTION / speed;
    xFriction = percent * xv;
    yFriction = percent * yv;
    this.xv = xv;
    this.yv = yv;
    scrollMap.run();
  }

  private void stopScrolling() {
    xv = yv = xFriction = yFriction = 0f;
    if (map != null) {
      map.removeCallbacks(scrollMap);
    }
  }

  //------------------------------------------------------------------------------------------
  // Runnable to scroll map

  private Runnable scrollMap = new Runnable() {
    public void run() {
      if (!map.translateMap((float) xv, (float) yv)) {
        stopScrolling();
      }
      map.invalidate();
      for (View overlay : overlayViews) {
        overlay.invalidate();
      }

      if (xv == 0 || (xv < 0 && xv >= xFriction) || (xv > 0 && xv <= xFriction)) {
        xv = yv = 0;
      } else {
        xv -= xFriction;
        yv -= yFriction;
        map.postDelayed(scrollMap, 50);
      }
    }
  };

  // Gets the event.point(1), ie, the second touch point.
  private static void getEventPositionOne(MotionEvent event, PointF point) {
    float sx = event.getX(1);
    float sy = event.getY(1);
    point.set(sx, sy);
  }

  private static PointF midpoint(PointF a, PointF b) {
    return new PointF((a.x + b.x) / 2, (a.y + b.y) / 2);
  }

  private static float distance(PointF a, PointF b) {
    return (float) Math.sqrt(distanceSq(a, b));
  }

  private static float distanceSq(PointF a, PointF b) {
    float dx = a.x - b.x;
    float dy = a.y - b.y;
    return dx * dx + dy * dy;
  }

  //------------------------------------------------------------------------------------------
  // OnTouchListener to track user interaction

  private View.OnTouchListener touchListener = new View.OnTouchListener() {
    private VelocityTracker velocityTracker = null;
    private float lastMoveX = 0f;
    private float lastMoveY = 0f;
    private float initialDistance = -1f;
    private float initialMapZoom = 1f;

    @Override
    public boolean onTouch(View v, MotionEvent event) {
      return onMultiTouch(v, event);
    }

    public boolean onMultiTouch(View v, MotionEvent event) {
      switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          startTouch(event);
          break;
        case MotionEvent.ACTION_UP:
          stopTouch(event);
          break;
        case MotionEvent.ACTION_POINTER_DOWN:
          startSecondTouch(event);
          break;
        case MotionEvent.ACTION_POINTER_UP:
          stopSecondTouch(event);
          break;
        case MotionEvent.ACTION_MOVE:
          moveTouch(event);
          break;
      }
      return true;
    }

    private void startTouch(MotionEvent event) {
      // Stop any existing scrolling to allow "catching" a rolling map
      boolean isCatchEvent = (xv > 0 || yv > 0);
      if (isCatchEvent) {
        stopScrolling();
      }
      lastMoveX = event.getX();
      lastMoveY = event.getY();
      velocityTracker = VelocityTracker.obtain();
      velocityTracker.clear();
      velocityTracker.addMovement(event);
    }

    private void startSecondTouch(MotionEvent event) {
      PointF first = new PointF(event.getX(), event.getY());
      PointF second = new PointF();
      getEventPositionOne(event, second);
      // now do translation based on the midpoint:
      PointF mid = midpoint(first, second);
      initialDistance = distance(first, second);
      initialMapZoom = map.getZoomLevel();
      // Abort if initialDistance is really small?
      lastMoveX = mid.x;
      lastMoveY = mid.y;
    }

    private void stopTouch(MotionEvent event) {
      // Don't add the finger lift point as it can cause accidental map move
      // velocityTracker.addMovement(event);
      velocityTracker.computeCurrentVelocity(50); // per 0.05 seconds (50 ms)
      startScrolling(velocityTracker.getXVelocity(), velocityTracker.getYVelocity());
      velocityTracker.recycle();
      velocityTracker = null;
    }

    private void stopSecondTouch(MotionEvent event) {
      initialDistance = -1;  // stop zooming behavior
      int pointerIndex = event.getActionIndex();
      if (pointerIndex == 0) { // was lifted, so switch drag point to one.
        PointF second = new PointF();
        getEventPositionOne(event, second);
        lastMoveX = second.x;
        lastMoveY = second.y;
      } else {  // back to drag point 0
        lastMoveX = event.getX();
        lastMoveY = event.getY();
      }
    }

    private void moveTouch(MotionEvent evt) {
      if (initialDistance > 0) {
        PointF first = new PointF(evt.getX(), evt.getY());
        PointF second = new PointF();
        getEventPositionOne(evt, second);
        PointF mid = midpoint(first, second);
        map.translateMap(mid.x - lastMoveX, mid.y - lastMoveY);
        map.setFollowMode(false);
        lastMoveX = mid.x;
        lastMoveY = mid.y;
        float newDist = distance(first, second);
        // Only adjust if newDist isn't too small?
        float s = newDist / initialDistance;
        map.setZoomLevel(initialMapZoom * s);
      } else {
        map.translateMap(evt.getX() - lastMoveX, evt.getY() - lastMoveY);
        map.setFollowMode(false);
        lastMoveX = evt.getX();
        lastMoveY = evt.getY();
        velocityTracker.addMovement(evt);
      }
      map.invalidate();
    }
  };
}
