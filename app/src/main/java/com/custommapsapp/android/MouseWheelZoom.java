package com.custommapsapp.android;

import android.graphics.Point;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;

/**
 * MouseWheelZoom can implement zoom behavior using mouse wheel. It needs to be given a View over
 * which the mouse wheel events are captured and a GoogleMap that is zoomed when mouse wheel is
 * scrolled.
 */
public class MouseWheelZoom {
  private GoogleMap googleMap;

  /**
   * Creates an instance that captures mouse wheel scrolling over given view and zooms a GoogleMap.
   *
   * @param view View over which mouse wheel events are captured. It should be laid out over the
   *     GoogleMap, so that the zoom focus point matches.
   * @param googleMap GoogleMap that is zoomed in or our when mouse wheel events are detected.
   */
  public MouseWheelZoom(View view, GoogleMap googleMap) {
    this.googleMap = googleMap;
    view.setOnGenericMotionListener(this::processMouseWheel);
  }

  private boolean processMouseWheel(View v, MotionEvent event) {
    // Ignore event if it is not a mouse scroll event
    if (event.getSource() != InputDevice.SOURCE_MOUSE ||
        event.getActionMasked() != MotionEvent.ACTION_SCROLL) {
      return false;
    }
    float vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
    // Ignore event if it is not a vertical scroll event
    if (vScroll == 0) {
      return false;
    }
    // Each scroll event zooms in or out by 0.5 zoom levels
    float scale;
    if (vScroll > 0) {
      scale = 0.5f;
    } else {
      scale = -0.5f;
    }
    Point eventPoint = new Point(Math.round(event.getX()), Math.round(event.getY()));
    googleMap.moveCamera(CameraUpdateFactory.zoomBy(scale, eventPoint));
    return true;
  }
}
