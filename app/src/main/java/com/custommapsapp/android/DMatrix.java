package com.custommapsapp.android;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.Log;

import java.util.Arrays;
import java.util.Locale;

/**
 * Matrix (3x3) class that uses doubles instead of floats for more accurate calculations. The API of
 * this class matches android.graphics.Matrix. All the methods in this class are implemented in
 * Java, so the computations may not be as fast as using android.graphics.Matrix. This class also
 * does not take advantage of matrix type based optimizations like android.graphics.Matrix.
 *
 * <p>Some operations in this class are adapted from google/skia project in GitHub. Others are
 * implemented as simple non-optimized operations.
 *
 * <p>See Skia-LICENSE.txt for more information about Skia license which applies to this file.
 */
public class DMatrix {
  private static final String LOG_TAG = DMatrix.class.getSimpleName();

  public static final int SCALE_X = 0;   // use to get/set data values
  public static final int SKEW_X = 1;    // use to get/set data values
  public static final int TRANS_X = 2;   // use to get/set data values
  public static final int SKEW_Y = 3;    // use to get/set data values
  public static final int SCALE_Y = 4;   // use to get/set data values
  public static final int TRANS_Y = 5;   // use to get/set data values
  public static final int PERSP_0 = 6;   // use to get/set data values
  public static final int PERSP_1 = 7;   // use to get/set data values
  public static final int PERSP_2 = 8;   // use to get/set data values

  /** double[9] array that contains the data of the 3x3 matrix in row-major order. */
  private double[] data;

  /** Constructs a new identity matrix. */
  public DMatrix() {
    data = new double[9];
    reset();
  }

  /** Constructs a new matrix that is a deep copy of the given matrix. */
  public DMatrix(DMatrix src) {
    data = Arrays.copyOf(src.data, src.data.length);
  }

  /** Constructs a new matrix that is a copy of the given android.graphics.Matrix. */
  public DMatrix(Matrix src) {
    this();
    set(src);
  }

  /** Reset this matrix to identity matrix. */
  public void reset() {
    Arrays.fill(data, 0);
    data[SCALE_X] = 1;
    data[SCALE_Y] = 1;
    data[PERSP_2] = 1;
  }

  /**
   * Copies the 9 values in this matrix into the given array of doubles. The values are ordered by
   * rows, so that all values from the first row are first in the array, then the second row, and
   * finally the third.
   */
  public void getValues(double[] values) {
    if (values.length != this.data.length) {
      throw new IllegalArgumentException("Invalid size of values array: " + values.length);
    }
    System.arraycopy(this.data, 0, values, 0, this.data.length);
  }

  /**
   * Sets all the 9 values in this matrix from the given array of doubles. The values are applied by
   * rows, first 3 values are set to the first row, and so on.
   */
  public void setValues(double[] values) {
    if (values.length != this.data.length) {
      throw new IllegalArgumentException("Invalid size of values array: " + values.length);
    }
    System.arraycopy(values, 0, this.data, 0, this.data.length);
  }

  /** Copies the values from the given matrix to this matrix. */
  public void set(DMatrix from) {
    data = Arrays.copyOf(from.data, from.data.length);
  }

  /** Copies the values from the given android.graphics.Matrix to this matrix. */
  public void set(Matrix from) {
    float[] fromData = new float[9];
    from.getValues(fromData);
    for (int i = 0; i < fromData.length; i++) {
      data[i] = fromData[i];
    }
  }

  /**
   * Returns a new android.graphics.Matrix that contains same values as this matrix. Since
   * android.graphics.Matrix uses floats instead of doubles, the values do not match exactly.
   */
  public Matrix toMatrix() {
    float[] floatData = new float[data.length];
    for (int i = 0; i < data.length; i++) {
      floatData[i] = (float) data[i];
    }
    Matrix m = new Matrix();
    m.setValues(floatData);
    return m;
  }

  /** Returns true if this matrix is identity matrix. */
  public boolean isIdentity() {
    return data[SCALE_X] == 1 && data[SCALE_Y] == 1 && data[PERSP_2] == 1
        && data[SKEW_X] == 0 && data[TRANS_X] == 0
        && data[SKEW_Y] == 0 && data[TRANS_Y] == 0
        && data[PERSP_0] == 0 && data[PERSP_1] == 0;
  }

  /** Returns true if this matrix contains an affine transformation. */
  public boolean isAffine() {
    return data[PERSP_0] == 0 && data[PERSP_1] == 0 && data[PERSP_2] == 1
        && ((data[SCALE_X] != 0 && data[SCALE_Y] != 0 && data[SKEW_X] == 0 && data[SKEW_Y] == 0)
        || (data[SCALE_X] == 0 && data[SCALE_Y] == 0 && data[SKEW_X] != 0 && data[SKEW_Y] != 0));
  }

  /**
   * Applies this matrix to the given array of 2D points, and writes the transformed points back
   * into the array.
   *
   * @param points Array of points (x0, y0, x1, y1, ...) to be transformed
   */
  public void mapPoints(double[] points) {
    mapPoints(points, points);
  }

  /**
   * Applies this matrix to the source array of 2D points, and writes the transformed points into
   * the destination array. Both arrays will present the points as pairs (x0, y0, x1, y1, ...).
   *
   * @param dst destination array that will contain the transformed points
   * @param src source array of points to be transformed
   */
  public void mapPoints(double[] dst, double[] src) {
    mapPoints(dst, 0, src, 0, src.length / 2);
  }

  /**
   * Applies this matrix to the source points and writes the transformed points to destination
   * array. All points are stored as pairs (x0, y0, x1, y1, ...).
   *
   * @param dst destination array to which transformed points are stored
   * @param dstIndex index in destination array to which the first point's x-value will be
   *     stored
   * @param src source array containing points to be transformed
   * @param srcIndex index in source array containing the first point's x-value
   * @param pointCount number of points (x,y pairs) to transform
   */
  public void mapPoints(double[] dst, int dstIndex, double[] src, int srcIndex, int pointCount) {
    PointArray srcPoints = new PointArray(src, srcIndex);
    PointArray dstPoints = new PointArray(dst, dstIndex);
    if (srcPoints.length() < pointCount || dstPoints.length() < pointCount) {
      throw new IllegalArgumentException("Array is too small");
    }
    boolean hasPerspective = this.hasPerspective();

    for (int i = 0; i < pointCount; i++) {
      // Use local x, y variables in case source and destination arrays are the same
      double sx = srcPoints.getX(i);
      double sy = srcPoints.getY(i);
      double x = data[SCALE_X] * sx + data[SKEW_X] * sy + data[TRANS_X];
      double y = data[SKEW_Y] * sx + data[SCALE_Y] * sy + data[TRANS_Y];
      if (!hasPerspective) {
        dstPoints.setX(i, x);
        dstPoints.setY(i, y);
      } else {
        double z = data[PERSP_0] * sx + data[PERSP_1] * sy + data[PERSP_2];
        if (!isZero(z)) {
          z = 1 / z;
        }
        dstPoints.setX(i, x * z);
        dstPoints.setY(i, y * z);
      }
    }
  }

  /**
   * Invert this matrix and store the result into the given matrix. If this matrix cannot be
   * inverted, the given matrix is left unmodified.
   *
   * @return true if this matrix was successfully inverted, false otherwise
   */
  public boolean invert(DMatrix inverse) {
    // Identity matrix is its own inverse
    if (this.isIdentity()) {
      inverse.set(this);
      return true;
    }
    // Compute Matrix of Minors first (each cell is replaced with its determinant)
    DMatrix temp = computeMinors();
    // Cofactors (invert signs of every other value: (0, 1) (1, 0) (1, 2) and (2, 1))
    temp.data[1] = -temp.data[1];
    temp.data[3] = -temp.data[3];
    temp.data[5] = -temp.data[5];
    temp.data[7] = -temp.data[7];
    // Remember the cofactors on the first row
    double cofactor0 = temp.data[0];
    double cofactor1 = temp.data[1];
    double cofactor2 = temp.data[2];
    // Create adjugate matrix (reflect over diagonal)
    temp.swapDataCells(1, 3);
    temp.swapDataCells(2, 6);
    temp.swapDataCells(5, 7);
    // Multiply the adjugate by 1/Determinant
    double determinant = data[0] * cofactor0 + data[1] * cofactor1 + data[2] * cofactor2;
    if (isZero(determinant)) {
      // Inverse matrix does not exist
      return false;
    }
    double multiplier = 1.0 / determinant;
    for (int i = 0; i < temp.data.length; i++) {
      temp.data[i] *= multiplier;
    }
    // Copy result to inverse, and return true to indicate success
    inverse.set(temp);
    return true;
  }

  /** Sets this matrix to be a translation matrix by the given values. */
  public void setTranslate(double tx, double ty) {
    reset();
    data[TRANS_X] = tx;
    data[TRANS_Y] = ty;
  }

  /** Preconcats this matrix with a translation matrix for tx and ty. M' = M * T(tx, ty). */
  public void preTranslate(double tx, double ty) {
    DMatrix m = new DMatrix();
    m.setTranslate(tx, ty);
    preConcat(m);
  }

  /** Postconcats this matrix with a translation matrix for tx and ty. M' = T(tx, ty) * M. */
  public void postTranslate(double tx, double ty) {
    DMatrix m = new DMatrix();
    m.setTranslate(tx, ty);
    postConcat(m);
  }

  /** Sets this matrix to be a plain scaling matrix with the given values. */
  public void setScale(double sx, double sy) {
    reset();
    data[SCALE_X] = sx;
    data[SCALE_Y] = sy;
  }

  /**
   * Sets this matrix to scale by sx and sy, with a pivot point (px, py). The pivot point
   * coordinates remain unchanged by the scaling transformation.
   */
  public void setScale(double sx, double sy, double px, double py) {
    if (sx == 1 && sy == 1) {
      reset();
    } else {
      setScaleTranslate(sx, sy, px - sx * px, py - sy * py);
    }
  }

  /** Preconcats this matrix with the given scaling matrix for sx and sy. M' = M * S(sx, sy). */
  public void preScale(double sx, double sy) {
    preScale(sx, sy, 0, 0);
  }

  /**
   * Preconcats this matrix with the given scaling matrix for sx and sy with pivot point (px, py).
   * M' = M * S(sx, sy, px, py).
   */
  public void preScale(double sx, double sy, double px, double py) {
    DMatrix m = new DMatrix();
    m.setScale(sx, sy, px, py);
    preConcat(m);
  }

  /** Postconcats this matrix with the given scaling matrix for sx and sy. M' = S(sx, sy) * M. */
  public void postScale(double sx, double sy) {
    postScale(sx, sy, 0, 0);
  }

  /**
   * Postconcats this matrix with the given scaling matrix for sx and sy with pivot point (px, py).
   * M' = S(sx, sy, px, py) * M.
   */
  public void postScale(double sx, double sy, double px, double py) {
    DMatrix m = new DMatrix();
    m.setScale(sx, sy, px, py);
    postConcat(m);
  }

  /** Sets this matrix to rotate about point (0, 0) by the specified number of degrees. */
  public void setRotate(double degrees) {
    setRotate(degrees, 0, 0);
  }

  /** Sets this matrix to rotate about point (px, py) by the specified number of degrees. */
  public void setRotate(double degrees, double px, double py) {
    double radians = Math.toRadians(degrees);
    setSinCos(Math.sin(radians), Math.cos(radians), px, py);
  }

  /** Preconcats this matrix with the specified rotation. M' = M * R(degrees). */
  public void preRotate(double degrees) {
    preRotate(degrees, 0, 0);
  }

  /** Preconcats this matrix with the specified rotation. M' = M * R(degrees, px, py). */
  public void preRotate(double degrees, double px, double py) {
    DMatrix m = new DMatrix();
    m.setRotate(degrees, px, py);
    preConcat(m);
  }

  /** Postconcats this matrix with the specified rotation. M' = R(degrees) * M. */
  public void postRotate(double degrees) {
    postRotate(degrees, 0, 0);
  }

  /** Postconcats this matrix with the specified rotation. M' = R(degrees, px, py) * M. */
  public void postRotate(double degrees, double px, double py) {
    DMatrix m = new DMatrix();
    m.setRotate(degrees, px, py);
    postConcat(m);
  }

  /** Sets this matrix to rotate by the specified sine and cosine values. */
  public void setSinCos(double sinValue, double cosValue) {
    setSinCos(sinValue, cosValue, 0, 0);
  }

  /**
   * Sets this matrix to rotate by the specified sine and cosine values with the pivot point at
   * (px, py). The pivot point remains unchanged and other points rotate around it.
   */
  public void setSinCos(double sinValue, double cosValue, double px, double py) {
    double oneMinusCos = 1 - cosValue;

    data[SCALE_X] = cosValue;
    data[SKEW_X] = -sinValue;
    data[TRANS_X] = sinValue * py + oneMinusCos * px;

    data[SKEW_Y] = sinValue;
    data[SCALE_Y] = cosValue;
    data[TRANS_Y] = -sinValue * px + oneMinusCos * py;

    data[PERSP_0] = 0;
    data[PERSP_1] = 0;
    data[PERSP_2] = 1;
  }

  /** Sets this matrix to skew points by sx and sy. */
  public void setSkew(double kx, double ky) {
    setSkew(kx, ky, 0, 0);
  }

  /** Sets this matrix to skew points by sx and sy keeping pivot point (px, py) unchanged. */
  public void setSkew(double kx, double ky, double px, double py) {
    data[SCALE_X] = 1;
    data[SKEW_X] = kx;
    data[TRANS_X] = -kx * py;

    data[SKEW_Y] = ky;
    data[SCALE_Y] = 1;
    data[TRANS_Y] = -ky * px;

    data[PERSP_0] = 0;
    data[PERSP_1] = 0;
    data[PERSP_2] = 1;
  }

  /** Preconcats this matrix with the specified skew matrix. M' = M * K(kx, ky). */
  public void preSkew(double kx, double ky) {
    preSkew(kx, ky, 0, 0);
  }

  /** Preconcats this matrix with the specified skew matrix. M' = M * K(kx, ky, px, py). */
  public void preSkew(double kx, double ky, double px, double py) {
    DMatrix m = new DMatrix();
    m.setSkew(kx, ky, px, py);
    preConcat(m);
  }

  /** Postconcats this matrix with the specified skew matrix. M' = M * K(kx, ky). */
  public void postSkew(double kx, double ky) {
    postSkew(kx, ky, 0, 0);
  }

  /** Postconcats this matrix with the specified skew matrix. M' = M * K(kx, ky, px, py). */
  public void postSkew(double kx, double ky, double px, double py) {
    DMatrix m = new DMatrix();
    m.setSkew(kx, ky, px, py);
    postConcat(m);
  }

  /**
   * Sets this matrix to the result of concatenating matrix A and matrix B. It is allowed for either
   * parameter to be 'this' matrix.
   */
  public void setConcat(DMatrix a, DMatrix b) {
    // Use temp matrix, just in case a or b is 'this'
    DMatrix temp = new DMatrix();
    for (int r = 0; r < 3; r++) {
      for (int c = 0; c < 3; c++) {
        double value = 0;
        for (int i = 0; i < 3; i++) {
          value += a.getValueAt(r, i) * b.getValueAt(i, c);
        }
        temp.setValueAt(r, c, value);
      }
    }
    this.set(temp);
  }

  /**
   * Sets this matrix to the scale and translate values that map the source rectangle to the
   * destination rectangle, returning true if the result can be represented.
   *
   * @param src source rectangle to map from
   * @param dst destination rectangle to map to
   * @param fit Matrix.ScaleToFit option
   * @return true if the matrix can be represented by the rectangle mapping
   */
  public boolean setRectToRect(RectF src, RectF dst, Matrix.ScaleToFit fit) {
    if (src == null || dst == null) {
      throw new NullPointerException();
    }
    if (src.isEmpty()) {
      reset();
      return false;
    } else if (dst.isEmpty()) {
      Arrays.fill(data, 0);
      data[PERSP_2] = 1;
    } else {
      double sx = dst.width() / src.width();
      double sy = dst.height() / src.height();

      boolean xLarger = false;
      if (fit != Matrix.ScaleToFit.FILL) {
        if (sx > sy) {
          xLarger = true;
          sx = sy;
        } else {
          sy = sx;
        }
      }

      double tx = dst.left - src.left * sx;
      double ty = dst.top - src.top * sy;
      if (fit == Matrix.ScaleToFit.CENTER || fit == Matrix.ScaleToFit.END) {
        double diff;
        if (xLarger) {
          diff = dst.width() - src.width() * sy;
        } else {
          diff = dst.height() - src.height() * sy;
        }
        if (fit == Matrix.ScaleToFit.CENTER) {
          diff /= 2;
        }
        if (xLarger) {
          tx += diff;
        } else {
          ty += diff;
        }
      }
      setScaleTranslate(sx, sy, tx, ty);
    }
    return true;
  }

  /**
   * Sets the matrix such that the specified src points map to the specified dst points. The points
   * are represented as an array of doubles arranged (x0, y0, x1, y1, ...) where each point consists
   * of two consecutive double values.
   *
   * @param src array of (x, y) source points
   * @param srcIndex index of the first x-coordinate value
   * @param dst array of (x, y) destination points
   * @param dstIndex index of the first x-coordinate value
   * @param count number of points (x,y pairs) to be used. Must be [0..4].
   * @return true if this matrix was successfully set to the specified transformation.
   */
  public boolean setPolyToPoly(double[] src, int srcIndex, double[] dst, int dstIndex, int count) {
    if (count < 0 || 4 < count) {
      Log.e(LOG_TAG, "Invalid point count: " + count);
      return false;
    }
    // If no points were passed in, set to identity matrix
    if (count == 0) {
      reset();
      return true;
    }
    // If only one point was passed in, set to translation matrix
    if (count == 1) {
      setTranslate(dst[dstIndex] - src[srcIndex], dst[dstIndex + 1] - src[srcIndex + 1]);
      return true;
    }
    // Adjust point arrays to start from index 0 (by using temporary arrays) if they don't already
    if (srcIndex > 0) {
      double[] temp = new double[2 * count];
      System.arraycopy(src, srcIndex, temp, 0, temp.length);
      src = temp;
    }
    if (dstIndex > 0) {
      double[] temp = new double[2 * count];
      System.arraycopy(dst, dstIndex, temp, 0, temp.length);
      dst = temp;
    }
    PointArray srcPts = new PointArray(src);
    PointArray dstPts = new PointArray(dst);

    PolyProcessor processor =
        count == 2 ? this::poly2Process : (count == 3 ? this::poly3Process : this::poly4Process);
    DMatrix temp = new DMatrix();
    if (!processor.process(srcPts, temp)) {
      Log.d(LOG_TAG, "First poly-to-poly process failed");
      return false;
    }
    DMatrix result = new DMatrix();
    if (!temp.invert(result)) {
      Log.d(LOG_TAG, "Failed to invert matrix (poly-to-poly)");
      return false;
    }
    if (!processor.process(dstPts, temp)) {
      Log.d(LOG_TAG, "Second poly-to-poly process failed");
      return false;
    }
    this.setConcat(temp, result);
    return true;
  }

  /** Preconcats this matrix with the specified matrix. M' = M * other. */
  public void preConcat(DMatrix other) {
    if (!other.isIdentity()) {
      setConcat(this, other);
    }
  }

  /** Postconcats this matrix with the specified matrix. M' = other * M. */
  public void postConcat(DMatrix matrix) {
    if (!matrix.isIdentity()) {
      setConcat(matrix, this);
    }
  }

  /**
   * Returns true if this matrix is equal to the given object. For the object to be equal, it must
   * be a DMatrix object and all the values in its cells must match the values in this matrix.
   */
  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof DMatrix)) {
      return false;
    }
    DMatrix other = (DMatrix) obj;
    return Arrays.equals(this.data, other.data);
  }

  @Override
  public int hashCode() {
    int hashCode = 0;
    for (double value : data) {
      long bits = Double.doubleToLongBits(value);
      int valueHash = (int) (bits ^ (bits >>> 32));
      hashCode ^= valueHash;
    }
    return hashCode;
  }

  // ---------------------------------------------------------------------------
  // Private methods

  /** Returns true if this matrix includes perspective transformation. */
  private boolean hasPerspective() {
    return data[PERSP_0] != 0 || data[PERSP_1] != 0 || data[PERSP_2] != 1;
  }

  /** Resets this matrix to contain only given scale and translate transformation. */
  private void setScaleTranslate(double sx, double sy, double tx, double ty) {
    data[SCALE_X] = sx;
    data[SKEW_X] = 0;
    data[TRANS_X] = tx;

    data[SKEW_Y] = 0;
    data[SCALE_Y] = sy;
    data[TRANS_Y] = ty;

    data[PERSP_0] = 0;
    data[PERSP_1] = 0;
    data[PERSP_2] = 1;
  }

  /** Computes "matrix of minors" for this matrix. */
  private DMatrix computeMinors() {
    DMatrix minors = new DMatrix();
    for (int r = 0; r < 3; r++) {
      for (int c = 0; c < 3; c++) {
        minors.setValueAt(r, c, compute2x2Determinant(r, c));
      }
    }
    return minors;
  }

  /** Computes 2x2 determinant for cell (row, col). */
  private double compute2x2Determinant(int row, int col) {
    int r1 = row == 0 ? 1 : 0;
    int r2 = row == 2 ? 1 : 2;
    int c1 = col == 0 ? 1 : 0;
    int c2 = col == 2 ? 1 : 2;
    return getValueAt(r1, c1) * getValueAt(r2, c2) - getValueAt(r2, c1) * getValueAt(r1, c2);
  }

  /** Swaps data in cells index1 and index2. */
  private void swapDataCells(int index1, int index2) {
    double v = data[index1];
    data[index1] = data[index2];
    data[index2] = v;
  }

  /** Returns the value in cell (row, col). */
  private double getValueAt(int row, int col) {
    return data[3 * row + col];
  }

  /** Sets the value in cell (row, col). */
  private void setValueAt(int row, int col, double value) {
    data[3 * row + col] = value;
  }

  /** Helper class to treat an array of doubles as an array of points (x,y pairs). */
  private static class PointArray {
    private double[] pointData;
    private int startIndex;

    PointArray(double[] pointData) {
      this(pointData, 0);
    }

    PointArray(double[] pointData, int startIndex) {
      this.pointData = pointData;
      this.startIndex = startIndex;
    }

    /** Returns number of points in the array. */
    int length() {
      return (pointData.length - startIndex) / 2;
    }

    /** Returns x-coordinate for a point at the given index. */
    double getX(int index) {
      return pointData[startIndex + 2 * index];
    }

    /** Returns y-coordinate for a point at the given index. */
    double getY(int index) {
      return pointData[startIndex + 2 * index + 1];
    }

    /** Sets x-coordinate for a point at the given index. */
    void setX(int index, double value) {
      pointData[startIndex + 2 * index] = value;
    }

    /** Sets y-coordinate for a point at the given index. */
    void setY(int index, double value) {
      pointData[startIndex + 2 * index + 1] = value;
    }
  }

  /** Helper interface to specify processes used in solving poly-to-poly mappings. */
  interface PolyProcessor {
    boolean process(PointArray points, DMatrix dest);
  }

  /** Process used in solving poly-to-poly mapping when two point mappings are given. */
  private boolean poly2Process(PointArray points, DMatrix dst) {
    dst.data[SCALE_X] = points.getY(1) - points.getY(0);
    dst.data[SKEW_Y] = points.getX(0) - points.getX(1);
    dst.data[PERSP_0] = 0;

    dst.data[SKEW_X] = points.getX(1) - points.getX(0);
    dst.data[SCALE_Y] = points.getY(1) - points.getY(0);
    dst.data[PERSP_1] = 0;

    dst.data[TRANS_X] = points.getX(0);
    dst.data[TRANS_Y] = points.getY(0);
    dst.data[PERSP_2] = 1;
    return true;
  }

  /** Process used in solving poly-to-poly mapping when three point mappings are given. */
  private boolean poly3Process(PointArray points, DMatrix dst) {
    dst.data[SCALE_X] = points.getX(2) - points.getX(0);
    dst.data[SKEW_Y] = points.getY(2) - points.getY(0);
    dst.data[PERSP_0] = 0;

    dst.data[SKEW_X] = points.getX(1) - points.getX(0);
    dst.data[SCALE_Y] = points.getY(1) - points.getY(0);
    dst.data[PERSP_1] = 0;

    dst.data[TRANS_X] = points.getX(0);
    dst.data[TRANS_Y] = points.getY(0);
    dst.data[PERSP_2] = 1;
    return true;
  }

  /** Process used in solving poly-to-poly mapping when four point mappings are given. */
  private boolean poly4Process(PointArray points, DMatrix dst) {
    double a1, a2;
    double x0, y0, x1, y1, x2, y2;

    x0 = points.getX(2) - points.getX(0);
    y0 = points.getY(2) - points.getY(0);
    x1 = points.getX(2) - points.getX(1);
    y1 = points.getY(2) - points.getY(1);
    x2 = points.getX(2) - points.getX(3);
    y2 = points.getY(2) - points.getY(3);

    // Check if abs(x2) > abs(y2)
    if (x2 > 0 ? y2 > 0 ? x2 > y2 : x2 > -y2 : y2 > 0 ? -x2 > y2 : x2 < y2) {
      double denom = (x1 * y2) / x2 - y1;
      if (isZero(denom)) {
        return false;
      }
      a1 = (((x0 - x1) * y2 / x2) - y0 + y1) / denom;
    } else {
      double denom = x1 - (y1 * x2) / y2;
      if (isZero(denom)) {
        return false;
      }
      a1 = (x0 - x1 - ((y0 - y1) * x2) / y2) / denom;
    }
    // Check if abs(x1) > abs(y1)
    if (x1 > 0 ? y1 > 0 ? x1 > y1 : x1 > -y1 : y1 > 0 ? -x1 > y1 : x1 < y1) {
      double denom = y2 - (x2 * y1) / x1;
      if (isZero(denom)) {
        return false;
      }
      a2 = (y0 - y2 - ((x0 - x2) * y1) / x1) / denom;
    } else {
      double denom = (y2 * x1) / y1 - x2;
      if (isZero(denom)) {
        return false;
      }
      a2 = (((y0 - y2) * x1) / y1 - x0 + x2) / denom;
    }

    dst.data[SCALE_X] = a2 * points.getX(3) + points.getX(3) - points.getX(0);
    dst.data[SKEW_Y] = a2 * points.getY(3) + points.getY(3) - points.getY(0);
    dst.data[PERSP_0] = a2;

    dst.data[SKEW_X] = a1 * points.getX(1) + points.getX(1) - points.getX(0);
    dst.data[SCALE_Y] = a1 * points.getY(1) + points.getY(1) - points.getY(0);
    dst.data[PERSP_1] = a1;

    dst.data[TRANS_X] = points.getX(0);
    dst.data[TRANS_Y] = points.getY(0);
    dst.data[PERSP_2] = 1;

    return true;
  }

  /** Method used to decide if a double value is (close enough to) zero. */
  private boolean isZero(double d) {
    return d * d == 0;
  }

  /** Helper method to write contents of the matrix into the log. */
  private void dumpMatrix(String msg) {
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < data.length; i++) {
      if (i % 3 == 0) {
        buf.append('\n');
      }
      buf.append(String.format(Locale.getDefault(), "%.6f   ", data[i]));
    }
    Log.d(LOG_TAG, msg + buf);
  }
}
