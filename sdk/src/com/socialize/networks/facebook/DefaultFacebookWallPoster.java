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
package com.socialize.networks.facebook;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.socialize.ConfigUtils;
import org.json.JSONException;
import org.json.JSONObject;
import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import com.socialize.Socialize;
import com.socialize.SocializeService;
import com.socialize.android.ioc.IBeanFactory;
import com.socialize.api.action.ShareType;
import com.socialize.auth.AuthProviderType;
import com.socialize.auth.facebook.FacebookSessionStore;
import com.socialize.config.SocializeConfig;
import com.socialize.entity.Entity;
import com.socialize.entity.PropagationInfo;
import com.socialize.entity.PropagationInfoResponse;
import com.socialize.entity.Share;
import com.socialize.error.SocializeException;
import com.socialize.facebook.AsyncFacebookRunner;
import com.socialize.facebook.AsyncFacebookRunner.RequestListener;
import com.socialize.facebook.Facebook;
import com.socialize.facebook.FacebookError;
import com.socialize.log.SocializeLogger;
import com.socialize.networks.DefaultPostData;
import com.socialize.networks.PostData;
import com.socialize.networks.SocialNetwork;
import com.socialize.networks.SocialNetworkListener;
import com.socialize.networks.SocialNetworkPostListener;
import com.socialize.util.ImageUtils;
import com.socialize.util.StringUtils;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import android.util.Log;
import java.util.Properties;
import java.io.FileInputStream;


/**
 * Posts to the Facebook wall.
 * @author Jason Polites
 */
@Deprecated
public class DefaultFacebookWallPoster implements FacebookWallPoster {
	
	private SocializeLogger logger;
	private ImageUtils imageUtils;
	private FacebookUtilsProxy facebookUtils;
	private IBeanFactory<AsyncFacebookRunner> facebookRunnerFactory;

	private static final String FACEBOOK_COMMENT_MESSAGE = "socialize.facebook.comment.message";
	private static final String FACEBOOK_LIKE_MESSAGE = "socialize.facebook.like.message";
	private static final String FACEBOOK_LINK_NAME = "socialize.facebook.like.link.name";
	private static final String FACEBOOK_LINK_DESCRIPTION = "socialize.facebook.like.link.description";
	private static final String FACEBOOK_PICTURE = "socialize.sharing.picture";
	private static final String FACEBOOK_PREVIEW = "socialize.sharing.preview";
	private static final String SHARING_EFFECT_NAME = "socialize.sharing.effect.name";

	private SocializeConfig config;
	

	@Override
	public void postLike(Activity parent, Entity entity, PropagationInfo propInfo, SocialNetworkListener listener) {
		
		if(config.isOGLike()) {
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("object", propInfo.getEntityUrl());
			post(parent, "me/og.likes",  params, listener);			
		}
		else {
			post(parent, entity, "", propInfo, listener);	
		}
	}

	@Override
	public void postComment(Activity parent, Entity entity, String comment, PropagationInfo propInfo, SocialNetworkListener listener) {
		post(parent, entity, comment, propInfo, listener);	
	}

	@Override
	public void postOG(Activity parent, Entity entity, String message, String action, PropagationInfo propInfo, SocialNetworkListener listener) {

		String entityUrl = propInfo.getEntityUrl();
		String linkName = entityUrl;
		String link = entityUrl;

		String appId = ConfigUtils.getConfig(parent).getProperty(SocializeConfig.FACEBOOK_APP_ID);
		String pictureURL = null;
		String previewURL = null;
		String description = null;

		if(entity != null) {
			linkName = entity.getDisplayName();
		}

		String type = "feed";
		if (!StringUtils.isEmpty(message)) {
			type = "photos";
		}

		Properties prop = new Properties();
 
    	try {
    		prop.load(parent.openFileInput("socialize_sharing.properties"));
			if (prop.getProperty(FACEBOOK_PICTURE) != null) {
            	pictureURL = prop.getProperty(FACEBOOK_PICTURE);
            	if (StringUtils.isEmpty(message)) {
	            	previewURL = prop.getProperty(FACEBOOK_PREVIEW);
	            }
        	}
    		String effect_name = prop.getProperty(SHARING_EFFECT_NAME);

    		prop.load(parent.getResources().getAssets().open("socialize.properties"));
    		linkName = prop.getProperty(FACEBOOK_LINK_NAME);

        if (StringUtils.isEmpty(message)) { // it is a like post.

          if (prop.getProperty(FACEBOOK_LIKE_MESSAGE) != null) {
            message = prop.getProperty(FACEBOOK_LIKE_MESSAGE);
            if (effect_name != null) {
              message = message.replace("%EFFECT_NAME%", effect_name);
              linkName = linkName.replace("%EFFECT_NAME%", effect_name);
            }
          }

        } else { // comment post

          if (prop.getProperty(FACEBOOK_COMMENT_MESSAGE) != null) {
            message = prop.getProperty(FACEBOOK_COMMENT_MESSAGE) + "\n" + message;
            message = message.replace("%EFFECT_NAME%", effect_name);

            // linkName = linkName.replace("%EFFECT_NAME%", effect_name);
            linkName = message;
          }
        }

        description = prop.getProperty(FACEBOOK_LINK_DESCRIPTION);
    	} catch (IOException ex) {
    		ex.printStackTrace();
        } catch (NullPointerException ex) {
        	ex.printStackTrace();
        }
		
		if(!StringUtils.isEmpty(appId)) {
			
			final Map<String, Object> params = new HashMap<String, Object>();
			params.put("name", linkName);
			params.put("message", message);
			params.put("link", link);
			params.put("type", type);
			
			if (description != null) {
				params.put("description", description);
			}

			if (previewURL != null) {
				params.put("picture", previewURL);
			} else if (pictureURL != null) {
				byte[] data = null;

				try {
					Log.v("DefaultFacebookWallPoster", "will get a bitmap for the " + pictureURL + " result file.");
					Bitmap imgBitmap = ImageUtils.getBitmapFromURL(pictureURL);
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					imgBitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
					data = baos.toByteArray();
				} catch (Exception ignore) {
					Log.v("DefaultFacebookWallPoster", "can not get bitmap for filename " + pictureURL);
				}

				if (data != null) {
					params.put("image", data);
				} else {
					params.put("picture", pictureURL);
				}
			} else {
				Log.v("DefaultFacebookWallPoster", "pictureURL == null and previewURL == null");
			}

      Log.v("DefaultFacebookWallPoster", params.toString());

			DefaultPostData postData = new DefaultPostData();
			postData.setPostValues(params);
			postData.setPropagationInfo(propInfo);			
			
//			post(parent, appId, listener, postData);
      Uri.Builder builder = new Uri.Builder();
      builder = builder.path(previewURL != null ? previewURL : pictureURL);
      postPhoto(parent, link, description, builder.build(), listener);
		}
		else {
			String msg = "Cannot post message to Facebook.  No app id found.  Make sure you specify facebook.app.id in socialize.properties";
			onError(parent, msg, new SocializeException(msg), listener);
		}		
	}


	/*@Deprecated
	public void post(final Activity parent, String appId, String linkName, String message, String link, String caption, final SocialNetworkListener listener) {
		
//		if(entity != null) {
//			linkName = entity.getDisplayName();
//		}
			
		final Map<String, Object> params = new HashMap<String, Object>();
		params.put("name", linkName);
		params.put("message", message);
		params.put("link", link);
		params.put("type", "link");

		params.put("caption", caption);
		DefaultPostData postData = new DefaultPostData();
		postData.setPostValues(params);
//		post(parent, appId, listener, postData);
    Uri.Builder builder = new Uri.Builder();
    builder = builder.path(link);
    postPhoto(parent, link, caption, builder.build(), listener);
	}

	public void postShare(Activity parent, Share share, SocialNetworkListener listener) {
	
		String linkName = null;
//		if(entity != null) {
//			linkName = entity.getDisplayName();
//		}

	
		PropagationInfo propInfo = share.getPropagationInfoResponse().getPropagationInfo(ShareType.FACEBOOK);
		final Map<String, Object> params = new HashMap<String, Object>();
		params.put("name", linkName == null ? share.getEntityDisplayName() : linkName);
		params.put("link", propInfo.getEntityUrl());
		params.put("message", share.getText());
		params.put("type", "link");


		
		DefaultPostData postData = new DefaultPostData();
		postData.setPostValues(params);
//		postData.setEntity(entity);
		postData.setPropagationInfo(propInfo);

		post(parent, listener, postData);
	} */

	@Override
	public void post(Activity parent, Entity entity, String message, PropagationInfo propInfo, SocialNetworkListener listener) {
		postOG(parent, entity, message, null, propInfo, listener);
	}

	@Override
	public void post(Activity parent, SocialNetworkListener listener, PostData postData) {
		
		boolean okToGo = true;
		
		String type = "feed";
		Set<Entry<String, Object>> entries = postData.getPostValues().entrySet();

		if(listener != null) {
			okToGo = !listener.onBeforePost(parent, SocialNetwork.FACEBOOK, postData);
		}
		
		if(okToGo) {
			Bundle bundle = new Bundle();
			
			Map<String, Object> postValues = postData.getPostValues();
			
			if(postValues != null) {
				
				Set<Entry<String, Object>> _entries = postValues.entrySet();
				
				for (Entry<String, Object> entry : _entries) {
					if(entry != null) {
						Object value = entry.getValue();
						String key = entry.getKey();
						
						if(key != null && value != null) {
							if(value instanceof byte[]) {
								bundle.putByteArray(entry.getKey(), (byte[]) value);
							}
							else {
								bundle.putString(entry.getKey(), value.toString());
							}
						}

            if (entry.getKey().equals("type") && value != null && !StringUtils.isEmpty((String) value)) {
              type = value.toString();
            }
					}
				}
			}

			Facebook fb = getFacebook(parent);
			
			final FacebookSessionStore store = newFacebookSessionStore();
			
			store.restore(fb, parent);
			
			AsyncFacebookRunner runner = newAsyncFacebookRunner(fb);
			
			RequestListener requestListener = newRequestListener(parent, listener);
			
			String path = postData.getPath();
			
			if(StringUtils.isEmpty(path)) {
				path = "me/links";
			}
			
			runner.request(path, bundle, "POST", requestListener, null);
		}
		
//		Facebook fb = newFacebook(appId);
//
//		final FacebookSessionStore store = newFacebookSessionStore();
//
//		store.restore(fb, parent);
//
//		AsyncFacebookRunner runner = newAsyncFacebookRunner(fb);
//
//		RequestListener requestListener = newRequestListener(parent, listener);
//
//		runner.request("me/" + type, bundle, "POST", requestListener, null);

	}
	

	@Override
	public void postPhoto(Activity parent, Share share, String comment, Uri photoUri, SocialNetworkListener listener) {
		PropagationInfoResponse propagationInfoResponse = share.getPropagationInfoResponse();
		PropagationInfo propInfo = propagationInfoResponse.getPropagationInfo(ShareType.FACEBOOK);
		
		if(propInfo != null) {
			String link = propInfo.getAppUrl();
			String appId = getFacebookAppId();
			
			if(!StringUtils.isEmpty(appId)) {
				postPhoto(parent, link, comment, photoUri, listener);
			}
			else {
				String msg = "Cannot post message to Facebook.  No app id found.  Make sure you specify facebook.app.id in socialize.properties";
				onError(parent, msg, new SocializeException(msg), listener);
			}	
		}
		else {
			String msg = "Cannot post message to Facebook.  No propagation info found";
			onError(parent, msg, new SocializeException(msg), listener);
		}
	}
	
	@Override
	public void postPhoto(Activity parent, String link, String caption, Uri photoUri, SocialNetworkListener listener) {

		try {
			Bundle params = new Bundle();
			params.putString("caption", caption + ": " + link);
			params.putByteArray("photo", imageUtils.scaleImage(parent, photoUri));
			
			Facebook fb = getFacebook(parent);
			
			final FacebookSessionStore store = newFacebookSessionStore();
			
			store.restore(fb, parent);
			
			AsyncFacebookRunner runner = newAsyncFacebookRunner(fb);
			
			RequestListener requestListener = newRequestListener(parent, listener);
			
			runner.request("me/photos", params, "POST", requestListener, null);			
		}
		catch (IOException e) {
			if(listener != null) {
				listener.onNetworkError(parent, SocialNetwork.FACEBOOK, e);
			}
			 
			if(logger != null) {
				logger.error("Unable to scale image for upload", e);
			}
			else {
				SocializeLogger.e(e.getMessage(), e);
			}
		}
	}

	@Override
	public void post(Activity parent, String graphPath, Map<String, Object> postData, SocialNetworkPostListener listener) {
		doFacebookCall(parent, postData, graphPath, "POST", listener);
	}

	@Override
	public void get(Activity parent, String graphPath, Map<String, Object> postData, SocialNetworkPostListener listener) {
		doFacebookCall(parent, postData, graphPath, "GET", listener);
	}

	@Override
	public void delete(Activity parent, String graphPath, Map<String, Object> postData, SocialNetworkPostListener listener) {
		doFacebookCall(parent, postData, graphPath, "DELETE", listener);
	}
	
	protected void doFacebookCall(Activity parent, Map<String, Object> postData, String graphPath, String method, SocialNetworkPostListener listener) {
		Bundle bundle = new Bundle();
		
		if(postData != null) {
			Set<Entry<String, Object>> entries = postData.entrySet();
			for (Entry<String, Object> entry : entries) {
				
				Object value = entry.getValue();
				
				if(value instanceof byte[]) {
					bundle.putByteArray(entry.getKey(), (byte[]) value);
				}
				else {
					bundle.putString(entry.getKey(), value.toString());
				}
			}	
		}

		doFacebookCall(parent, bundle, graphPath, method, listener);
	}
	
	public void getCurrentPermissions(final Activity parent, String token, final FacebookPermissionCallback callback) {
		Facebook fb = new Facebook(getFacebookAppId());
		fb.setAccessToken(token);
		AsyncFacebookRunner runner = newAsyncFacebookRunner(fb);
		runner.request("me/permissions", new RequestListener() {
			
			@Override
			public void onMalformedURLException(MalformedURLException e, Object state) {
				handlePermissionError(parent, callback, e);
			}
			
			@Override
			public void onIOException(IOException e, Object state) {
				handlePermissionError(parent, callback, e);
			}
			
			@Override
			public void onFileNotFoundException(FileNotFoundException e, Object state) {
				handlePermissionError(parent, callback, e);
			}
			
			@Override
			public void onFacebookError(FacebookError e, Object state) {
				handlePermissionError(parent, callback, e);
			}
			
			@Override
			public void onComplete(final String response, final Object state) {
				if(callback != null) {
					parent.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							callback.onComplete(response, state);
						}
					});
				}
			}
		});		
	}	
	
	protected void handlePermissionError(Activity parent, final FacebookPermissionCallback callback, final Exception e) {
		if(callback != null) {
			parent.runOnUiThread(new Runnable() {
				
				@Override
				public void run() {
					callback.onError(SocializeException.wrap(e));
				}
			});
		}
	}
	
	protected void doFacebookCall(Activity parent, Bundle data, String graphPath, String method, SocialNetworkPostListener listener) {
		Facebook fb = getFacebook(parent);
		FacebookSessionStore store = newFacebookSessionStore();
		store.restore(fb, parent);
		AsyncFacebookRunner runner = newAsyncFacebookRunner(fb);
		RequestListener requestListener = newRequestListener(parent, listener);
		runner.request(graphPath, data, method, requestListener, null);			
	}

	// So we can mock
	protected Facebook getFacebook(Context context) {
		return facebookUtils.getFacebook(context);
	}
	
	// So we can mock
	protected RequestListener newRequestListener(final Activity parent, final SocialNetworkPostListener listener) {
		final String defaultErrorMessage = "Facebook Error";
		
		return new RequestListener() {
			public void onMalformedURLException(MalformedURLException e, Object state) {
				handleFacebookError(parent, 0, defaultErrorMessage, e, listener);
			}
			public void onIOException(IOException e, Object state) {
				handleFacebookError(parent, 0, defaultErrorMessage, e, listener);
			}
			public void onFileNotFoundException(final FileNotFoundException e, Object state) {
				handleFacebookError(parent, 0, defaultErrorMessage, e, listener);
			}
			public void onFacebookError(FacebookError e, Object state) {
				handleFacebookError(parent, 0, defaultErrorMessage, e, listener);
			}
			public void onComplete(final String response, Object state) {
				
				JSONObject responseObject = null;
				if(!StringUtils.isEmpty(response)) {
					try {
						responseObject = newJSONObject(response);
						
						if(responseObject.has("error")) {
							
							JSONObject error = responseObject.getJSONObject("error");
							
							int code = 0;
							
							if(error.has("code") && !error.isNull("code")) {
								code = error.getInt("code");
							}
							
							
							if(error.has("message") && !error.isNull("message")) {
								String msg = error.getString("message");
								if(logger != null) {
									logger.error(msg);
								}
								else {
									System.err.println(msg);
								}
								
								handleFacebookError(parent, code, msg, new SocializeException(msg), listener);
							}
							else {
								handleFacebookError(parent, code, defaultErrorMessage, new SocializeException("Facebook Error (Unknown)"), listener);
							}
							
							return;
						}
					}
					catch (JSONException e) {
						onError(parent, defaultErrorMessage, e, listener);
						return;
					}
				}
				
				if(listener != null) {
					final JSONObject fResponse = responseObject;
					parent.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							listener.onAfterPost(parent, SocialNetwork.FACEBOOK, fResponse);
						}
					});
				}
			}
		};
	}
	
	protected void handleFacebookError(final Activity parent, int code, String msg, Throwable e, SocialNetworkPostListener listener) {
	
		// Check for token error:
		// http://fbdevwiki.com/wiki/Error_codes
		if(code == 190) {
			// Clear the session cache
			getSocialize().clear3rdPartySession(parent, AuthProviderType.FACEBOOK);
		}
		
		onError(parent, msg, e, listener);
	}
	
	protected JSONObject newJSONObject(String response) throws JSONException {
		return new JSONObject(response);
	}
	
	// So we can mock
	protected AsyncFacebookRunner newAsyncFacebookRunner(Facebook fb) {
		if(facebookRunnerFactory != null) {
			return facebookRunnerFactory.getBean(fb);
		}
		return  new AsyncFacebookRunner(fb);
	
	}

	// So we can mock
	protected FacebookSessionStore newFacebookSessionStore() {
		return new FacebookSessionStore();
	}
	
	// So we can mock
	protected SocializeService getSocialize() {
		return Socialize.getSocialize();
	}
	
	// So we can mock
	protected String getFacebookAppId() {
		return config.getProperty(SocializeConfig.FACEBOOK_APP_ID);
	}
	
	public void setLogger(SocializeLogger logger) {
		this.logger = logger;
	}

	protected void onError(final Activity parent, final String msg, final Throwable e, final SocialNetworkPostListener listener) {
		
		if(logger != null) {
			if(e != null) {
				logger.error(msg, e);
			}
			else {
				logger.error(msg);
			}
		}
		else {
			if(e != null) {
				SocializeLogger.e(msg, e);
			}
			else {
				System.err.println(msg);
			}
		}
		
		if(listener != null) {
			parent.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					listener.onNetworkError(parent, SocialNetwork.FACEBOOK, SocializeException.wrap(e));
				}
			});
		}
	}

	public void setImageUtils(ImageUtils imageUtils) {
		this.imageUtils = imageUtils;
	}
	
	public void setFacebookRunnerFactory(IBeanFactory<AsyncFacebookRunner> facebookRunnerFactory) {
		this.facebookRunnerFactory = facebookRunnerFactory;
	}

	public void setFacebookUtils(FacebookUtilsProxy facebookUtils) {
		this.facebookUtils = facebookUtils;
	}
	
	public void setConfig(SocializeConfig config) {
		this.config = config;
	}
}
