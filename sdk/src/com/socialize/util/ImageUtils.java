/*
 * Copyright (c) 2012 Socialize Inc.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.socialize.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.net.HttpURLConnection;
import java.io.InputStream;
import java.net.URL;


/**
 * @author Jason Polites
 *
 */
public class ImageUtils {

	private static int MAX_IMAGE_DIMENSION = 720;
	
	public byte[] scaleImage(Context context, Bitmap bitmap, Bitmap.CompressFormat format) {
		
		int width = bitmap.getWidth();
		
		if(width > MAX_IMAGE_DIMENSION) {
			float ratio = (float) MAX_IMAGE_DIMENSION / (float) width;
			
			width = MAX_IMAGE_DIMENSION;
			int height = (int)((float)bitmap.getHeight() * ratio);
			bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
		}

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		bitmap.compress(format, 100, stream);
		return stream.toByteArray();
	}

  private InputStream retrieveStream (Uri url) {
    String path = url == null ? null : url.getPath();
    if (path == null) {
      Log.w(getClass().getSimpleName(), "Error in received url: it's null => " + url);
      return null;
    } else {
      Log.w(getClass().getSimpleName(), "Path to the picture received => " + path);
    }

    DefaultHttpClient client = new DefaultHttpClient();
    HttpGet httpRequest = new HttpGet(path);

    try {
      HttpResponse httpResponse = client.execute(httpRequest);
      final int statusCode = httpResponse.getStatusLine().getStatusCode();

      if (statusCode != HttpStatus.SC_OK) {
        Log.w(getClass().getSimpleName(), "Error => " + statusCode + " => for URL " + url);
        return null;
      }

      HttpEntity httpEntity = httpResponse.getEntity();
      return httpEntity.getContent();

    } catch (IOException e) {
      httpRequest.abort();
      Log.w(getClass().getSimpleName(), "Error for URL =>" + url, e);
    }

    return null;
  }

  private InputStream getStreamToUri(Context context, Uri photoUri) {
    InputStream is = null;
    try {
      is = context.getContentResolver().openInputStream(photoUri);
    } catch (Exception ex) { // URI is not local but the web
      is = retrieveStream(photoUri);
    }

    return is;
  }

	public byte[] scaleImage(Context context, Uri photoUri) throws IOException {

		BitmapFactory.Options dbo = new BitmapFactory.Options();
		dbo.inJustDecodeBounds = true;
    InputStream is = getStreamToUri(context, photoUri);
		BitmapFactory.decodeStream(is, null, dbo);
		is.close();

		int rotatedWidth, rotatedHeight;
		int orientation = getOrientation(context, photoUri);

		if (orientation == 90 || orientation == 270) {
			rotatedWidth = dbo.outHeight;
			rotatedHeight = dbo.outWidth;
		}
		else {
			rotatedWidth = dbo.outWidth;
			rotatedHeight = dbo.outHeight;
		}

		Bitmap bitmap;
		is = getStreamToUri(context, photoUri);
		if (rotatedWidth > MAX_IMAGE_DIMENSION || rotatedHeight > MAX_IMAGE_DIMENSION) {
			float widthRatio = ((float) rotatedWidth) / ((float) MAX_IMAGE_DIMENSION);
			float heightRatio = ((float) rotatedHeight) / ((float) MAX_IMAGE_DIMENSION);
			float maxRatio = Math.max(widthRatio, heightRatio);

			// Create the bitmap from file
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inSampleSize = (int) maxRatio;
			bitmap = BitmapFactory.decodeStream(is, null, options);
		}
		else {
			bitmap = BitmapFactory.decodeStream(is);
		}
		is.close();

		/*
		 * if the orientation is not 0 (or -1, which means we don't know), we
		 * have to do a rotation.
		 */
		if (orientation > 0) {
			Matrix matrix = new Matrix();
			matrix.postRotate(orientation);

			bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
		}

		String type = "image/png";
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		if (type.equals("image/png")) {
			bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
		}
		else if (type.equals("image/jpg") || type.equals("image/jpeg")) {
			bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
		}
		byte[] bMapArray = baos.toByteArray();
		baos.close();
		return bMapArray;
	}

	public int getOrientation(Context context, Uri photoUri) {
		/* it's on the external media. */
		Cursor cursor = context.getContentResolver().query(photoUri, new String[] { MediaStore.Images.ImageColumns.ORIENTATION }, null, null, null);

    if (cursor == null) {
      return 0;
    }

		if (cursor.getCount() != 1) {
			return -1;
		}

		cursor.moveToFirst();
		return cursor.getInt(0);
	}

	public static Bitmap getBitmapFromURL(String src) {
		Bitmap myBitmap = null;
		HttpURLConnection connection = null;

	    try {
	        URL url = new URL(src);
	        connection = (HttpURLConnection) url.openConnection();
	        connection.setDoInput(true);
	        connection.connect();
	        InputStream input = connection.getInputStream();
	        myBitmap = BitmapFactory.decodeStream(input);
	    } catch (IOException e) {
	        e.printStackTrace();
	        myBitmap = null;
	    } finally {
	    	try {
	    		connection.disconnect();
	    	} catch (Exception ignored) {}
	    }

	    return myBitmap;
	}
}
