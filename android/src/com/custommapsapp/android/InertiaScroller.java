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

import android.graphics.PointF;
import android.util.FloatMath;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * InertiaScroller makes it possible for user to "throw" the map display and
 * let it scroll after the finger has been lifted from screen.
 *
 * @author Marko Teittinen
 */
public class InertiaScroller {
  private static final float FRICTION = 5f;

  private MapDisplay map;
  private float xv = 0f;
  private float yv = 0f;
  private float xFriction = 0f;
  private float yFriction = 0f;
  private boolean useMultitouch = false;

  // This block is used to introspect about 2.1 capabilities while being compatible with 1.6.
  private static int ME_action_mask = 0xFFFFFF;
  private static int ME_action_pointer_up = -Integer.MAX_VALUE;
  private static int ME_action_pointer_down = -Integer.MAX_VALUE;
  private static int ME_action_pointer_index_mask = -Integer.MAX_VALUE;
  private static int ME_action_pointer_index_shift = -Integer.MAX_VALUE;
  private static Method ME_get_x_method = null;
  private static Method ME_get_y_method = null;
  private static final boolean multitouchAvailable;

  static {
    boolean supported = false;
    try {
      ME_action_mask = MotionEvent.class.getField(
          "ACTION_MASK").getInt(MotionEvent.class);
      ME_action_pointer_up = MotionEvent.class.getField(
          "ACTION_POINTER_UP").getInt(MotionEvent.class);
      ME_action_pointer_down = MotionEvent.class.getField(
          "ACTION_POINTER_DOWN").getInt(MotionEvent.class);
      ME_action_pointer_index_mask = MotionEvent.class.getField(
          "ACTION_POINTER_INDEX_MASK").getInt(MotionEvent.class);
      ME_action_pointer_index_shift = MotionEvent.class.getField(
          "ACTION_POINTER_INDEX_SHIFT").getInt(MotionEvent.class);
      ME_get_x_method = MotionEvent.class.getMethod("getX", new Class[] { Integer.TYPE });
      ME_get_y_method = MotionEvent.class.getMethod("getY", new Class[] { Integer.TYPE });
      supported = true;
    } catch (NoSuchFieldException e) {
      // multitouch not supported
    } catch (NoSuchMethodException e) {
      // multitouch not supported
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    }
    multitouchAvailable = supported;
  }

  public static boolean isMultitouchAvailable() {
    return multitouchAvailable;
  }

  /**
   * @param event MotionEvent to be inspected
   * @return {@code true} if event is a multitouch event related to other than
   *         first pointer
   */
  public static boolean isMultitouchEvent(MotionEvent event) {
    if (!isMultitouchAvailable()) {
      return false;
    }
    int action = event.getAction() & ME_action_mask;
    return (action == ME_action_pointer_up || action == ME_action_pointer_down);
  }

  public InertiaScroller(MapDisplay map) {
    setMap(map);
    useMultitouch = isMultitouchAvailable();
  }

  public void setMap(MapDisplay map) {
    if (map != null) {
      map.setOnTouchListener(null);
    }
    this.map = map;
    map.setOnTouchListener(touchListener);
  }

  public void setUseMultitouch(boolean useMultitouch) {
    if (isMultitouchAvailable()) {
      this.useMultitouch = useMultitouch;
    }
  }

  public boolean getUseMultitouch() {
    return useMultitouch;
  }

  private void startScrolling(float xv, float yv) {
    float speed = (float) Math.sqrt(xv * xv + yv * yv);
    float percent = FRICTION / speed;
    xFriction = percent * xv;
    yFriction = percent * yv;
    this.xv = xv;
    this.yv = yv;
    scrollMap.run();
  }

  private void stopScrolling() {
    xv = yv = xFriction = yFriction = 0f;
    map.removeCallbacks(scrollMap);
  }

  //------------------------------------------------------------------------------------------
  // Runnable to scroll map

  private Runnable scrollMap = new Runnable() {
    public void run() {
      if (!map.translateMap(xv, yv)) {
        stopScrolling();
      }
      map.invalidate();

      if (xv == 0 || (xv < 0 && xv >= xFriction) || (xv > 0 && xv <= xFriction)) {
        xv = yv = 0;
      } else {
        xv -= xFriction;
        yv -= yFriction;
        map.postDelayed(scrollMap, 50);
      }
    }
  };

  // This makes calls to event.getX(int) work using introspection so that
  // this code can be android 1.6 compatible but still use 2.1 features.
  private static final Integer one = new Integer(1);
  // Gets the event.point(1), ie, the second touch point.
  private static void getEventPositionOne(MotionEvent event, PointF point) {
    try {
      Float sx = (Float)ME_get_x_method.invoke(event, one);
      Float sy = (Float)ME_get_y_method.invoke(event, one);
      point.set(sx.floatValue(), sy.floatValue());
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    }
  }

  private static PointF midpoint(PointF a, PointF b) {
    return new PointF((a.x + b.x) / 2, (a.y + b.y) / 2);
  }

  private static float distance(PointF a, PointF b) {
    return FloatMath.sqrt(distanceSq(a, b));
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
      if (useMultitouch) {
        onMultiTouch(v, event);
      } else {
        onSingleTouch(v, event);
      }
      return true; // indicate event was handled
    }

    public boolean onMultiTouch(View v, MotionEvent event) {
      int a = event.getAction() & ME_action_mask;
      // can't use case here because pointer_down variable isn't a constant.
      if (a == MotionEvent.ACTION_DOWN) {
        startTouch(event);
      } else if (a == MotionEvent.ACTION_UP) {
        stopTouch(event);
      } else if (a == ME_action_pointer_up) {
        stopSecondTouch(event);
      } else if (a == MotionEvent.ACTION_MOVE) {
        moveTouch(event);
      } else if (a == ME_action_pointer_down) {
        startSecondTouch(event);
      }
      return true;
    }

    public boolean onSingleTouch(View v, MotionEvent event) {
      switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
        startTouch(event);
        break;
      case MotionEvent.ACTION_CANCEL:
        break;
      case MotionEvent.ACTION_UP:
        stopTouch(event);
        break;
      case MotionEvent.ACTION_MOVE:
        moveTouch(event);
      }
      return true;
    }

    private void startTouch(MotionEvent event) {
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
      velocityTracker.addMovement(event);
      velocityTracker.computeCurrentVelocity(50); // per 0.05 seconds (50 ms)
      startScrolling(velocityTracker.getXVelocity(), velocityTracker.getYVelocity());
      velocityTracker.recycle();
      velocityTracker = null;
    }

    private void stopSecondTouch(MotionEvent event) {
      initialDistance = -1;  // stop zooming behavior
      int pointerIndex = (event.getAction() & ME_action_pointer_index_mask) >>
          ME_action_pointer_index_shift;
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
      }
      map.invalidate();
    }
  };
}
