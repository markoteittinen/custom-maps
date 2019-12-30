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

import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * InertiaScroller makes it possible for user to "throw" the map display and
 * let it scroll after the finger has been lifted from screen.
 *
 * @author Marko Teittinen
 */
public class ViewInertiaScroller {
  public interface Listener {
    /**
     * Notifies listener about moving the view (in screen pixels).
     *
     * @param xd number of screen pixels to move image towards left
     * @param yd number of screen pixels to move image upwards
     * @return boolean indicating whether the motion can continue, false indicates stop
     */
    boolean move(float xd, float yd);

    /** Notifies listener about scaling focused at given screen point. */
    void scale(float factor, float focusX, float focusY);
  }

  private static final float FRICTION = 6.0f;

  private View view;

  private GestureDetector gestureDetector;
  private ScaleGestureDetector scaleGestureDetector;
  private Listener listener;

  /**
   * Creates an inertia scroller for given view that displays content with
   * given size.
   *
   * @param view View whose display matrix is going to be manipulated by touch
   */
  public ViewInertiaScroller(View view) {
    gestureDetector = new GestureDetector(view.getContext(), gestureListener);
    scaleGestureDetector = new ScaleGestureDetector(view.getContext(), scaleGestureListener);
    scaleGestureDetector.setQuickScaleEnabled(true);

    setView(view);
  }

  public void setListener(Listener listener) {
    this.listener = listener;
  }

  public void setView(View view) {
    if (this.view != null) {
      this.view.setOnTouchListener(null);
    }
    this.view = view;
    if (view != null) {
      view.setOnTouchListener(touchListener);
    }
  }

  //------------------------------------------------------------------------------------------
  // OnTouchListener to track user interaction

  /** touchListener passes all touch events to both gesture detectors. */
  private final View.OnTouchListener touchListener = new View.OnTouchListener() {
    @Override
    public boolean onTouch(View v, MotionEvent event) {
      // Pass touch events to both gesture detectors ('|', not '||')
      return gestureDetector.onTouchEvent(event) | scaleGestureDetector.onTouchEvent(event);
    }
  };

  /** gestureListener receives scroll (drag) and fling gestures from GestureDetector. */
  private final GestureDetector.OnGestureListener gestureListener =
      new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onDown(MotionEvent e) {
          // Touch started, stop possible fling motion immediately
          flingSpeedX = 0;
          flingSpeedY = 0;
          return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
          // Velocities are given in pixels/second in direction of fling. Reverse the direction as
          // we track the speed of viewport, and divide by 30 for animating 30 times/second.
          flingSpeedX = -velocityX / 30;
          flingSpeedY = -velocityY / 30;
          // Divide friction between the X and Y components based on their magnitudes
          float vSum = Math.abs(flingSpeedX) + Math.abs(flingSpeedY);
          frictionX = FRICTION * flingSpeedX / vSum;
          frictionY = FRICTION * flingSpeedY / vSum;
          // Start fling scrolling
          view.post(flingEffect);

          return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
          if (listener != null) {
            listener.move(distanceX, distanceY);
          }
          return true;
        }
      };

  /** scaleGestureListener listens to scale changing events, like pinch */
  private final ScaleGestureDetector.OnScaleGestureListener scaleGestureListener =
      new ScaleGestureDetector.SimpleOnScaleGestureListener() {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
          if (listener != null) {
            listener.scale(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
          }
          return true;
        }
      };

  // Variables tracking fling speed and friction in component directions
  private float flingSpeedX = 0;
  private float flingSpeedY = 0;
  private float frictionX = 0;
  private float frictionY = 0;

  /** flingEffect moves the view at reducing speeds until the speed gets to zero. */
  private final Runnable flingEffect = new Runnable() {
    @Override
    public void run() {
      if (listener != null) {
        if (!listener.move(flingSpeedX, flingSpeedY)) {
          // Listener requests stop in motion
          flingSpeedX = 0;
          flingSpeedY = 0;
        }
      }
      if (Math.abs(flingSpeedX) < Math.abs(frictionX)) {
        // Scrolling stops here, friction exceeds remaining speed
        return;
      }

      // Scrolling has not stopped, reduce speed, and schedule additional scrolling
      flingSpeedX -= frictionX;
      flingSpeedY -= frictionY;
      view.postDelayed(flingEffect, 33);
    }
  };
}
