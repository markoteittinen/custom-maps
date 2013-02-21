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

import com.custommapsapp.android.PtSizeFixer;
import com.custommapsapp.android.R;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * TiePointAdapter manages the view for presenting TiePoints in a list.
 *
 * @author Marko Teittinen
 */
public class TiePointAdapter extends ArrayAdapter<TiePoint> {
  public TiePointAdapter(Context context, int rowViewResourceId) {
    super(context, rowViewResourceId);
  }

  @Override
  public boolean areAllItemsEnabled() {
    return true;
  }

  public ArrayList<TiePoint> getAllTiePoints() {
    ArrayList<TiePoint> tiepoints = new ArrayList<TiePoint>();
    for (int i = 0; i < getCount(); i++) {
      tiepoints.add(getItem(i));
    }
    return tiepoints;
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    if (position < 0 || position >= getCount()) {
      return convertView;
    }
    if (convertView == null) {
      convertView = LayoutInflater.from(getContext()).inflate(R.layout.tiepointitem, null);
      if (PtSizeFixer.isFixNeeded((Activity) null)) {
        PtSizeFixer.fixView(convertView);
      }
    }
    TiePoint tiepoint = getItem(position);
    ImageView thumbnail = (ImageView) convertView.findViewById(R.id.tiepointImage);
    TextView coordinates = (TextView) convertView.findViewById(R.id.tiepointText);
    thumbnail.setImageBitmap(tiepoint.getImage());
    Point point = tiepoint.getImagePoint();
    coordinates.setText(String.format("(%d, %d)", point.x, point.y));
    return convertView;
  }
}
