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
package com.socialize.ui.actionbar;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import org.json.JSONObject;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.Toast;
import com.socialize.EntityUtils;
import com.socialize.LikeUtils;
import com.socialize.ShareUtils;
import com.socialize.ViewUtils;
import com.socialize.android.ioc.IBeanFactory;
import com.socialize.api.action.comment.CommentUtilsProxy;
import com.socialize.api.action.share.ShareUtilsProxy;
import com.socialize.api.action.share.SocialNetworkDialogListener;
import com.socialize.entity.Entity;
import com.socialize.entity.EntityStats;
import com.socialize.entity.Like;
import com.socialize.entity.View;
import com.socialize.error.SocializeApiError;
import com.socialize.error.SocializeException;
import com.socialize.listener.entity.EntityGetListener;
import com.socialize.listener.like.LikeAddListener;
import com.socialize.listener.like.LikeDeleteListener;
import com.socialize.listener.like.LikeGetListener;
import com.socialize.listener.view.ViewAddListener;
import com.socialize.log.SocializeLogger;
import com.socialize.networks.SocialNetwork;
import com.socialize.ui.actionbar.OnActionBarEventListener.ActionBarEvent;
import com.socialize.ui.cache.CacheableEntity;
import com.socialize.ui.cache.EntityCache;
import com.socialize.ui.dialog.ProgressDialogFactory;
import com.socialize.util.DisplayUtils;
import com.socialize.util.Drawables;
import com.socialize.view.BaseView;
import com.socialize.config.SocializeConfig;
import java.util.Properties;
import android.content.Context;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;


/**
 * @author Jason Polites
 */
public class ActionBarLayoutView extends BaseView {
	
	static final NumberFormat countFormat = new DecimalFormat("##0.0K");

	private ActionBarButton commentButton;
	private ActionBarButton likeButton;
	private ActionBarButton shareButton;
	private ActionBarTicker ticker;
	
	private ActionBarItem viewsItem;
	private ActionBarItem commentsItem;
	private ActionBarItem likesItem;
	private ActionBarItem sharesItem;
	
	private Drawables drawables;
	private EntityCache entityCache;
	private SocializeLogger logger;
	
	private Drawable likeIcon;
	private Drawable likeIconHi;
	private Drawable commentIcon;
	private Drawable viewIcon;
	private Drawable shareIcon;
	
	private IBeanFactory<ActionBarButton> buttonFactory;
	private IBeanFactory<ActionBarTicker> tickerFactory;
	private IBeanFactory<ActionBarItem> itemFactory;
	
	private ProgressDialogFactory progressDialogFactory;
	
	private DisplayUtils displayUtils;
	
	private ShareUtilsProxy shareUtils;

	private CommentUtilsProxy commentUtils;
	
	private ActionBarView actionBarView;
	
	private ActionBarOptions options;
	
	final String loadingText = "...";
	
	private OnActionBarEventListener onActionBarEventListener;

	private static final int ACTION_BAR_BUTTONS_COUNT = 4;

	private static Activity contextActivity;

	
	public ActionBarLayoutView(Activity context, ActionBarView actionBarView) {
		this(context, actionBarView, new ActionBarOptions());
	}
	
	public ActionBarLayoutView(Activity context, ActionBarView actionBarView, ActionBarOptions options) {
		super(context);
		contextActivity = context;
		this.actionBarView = actionBarView;
		this.options = options;
	}
	
	private Drawable getIcon(Integer resourceId, String defaultName) {
		return (resourceId == null) ? drawables.getDrawable(defaultName) : getContext().getResources().getDrawable(resourceId);
	}
	
	public void init() {
		
		if(logger != null && logger.isDebugEnabled()) {
			logger.debug("init called on " + getClass().getSimpleName());
		}
		
		LayoutParams masterParams = new LayoutParams(LayoutParams.FILL_PARENT, displayUtils.getDIP(ActionBarView.ACTION_BAR_HEIGHT));
		masterParams.gravity = options.getGravity() | Gravity.CENTER_VERTICAL;
		setLayoutParams(masterParams);
		setGravity(options.getGravity());
			
		if(!options.isHideLike() || !options.isHideTicker()) {
			likeIcon = getIcon(options.getLikeIconResourceId(), "icon_like.png");
			likeIconHi = getIcon(options.getLikeIconActiveResourceId(), "icon_like_hi.png");
		}
		
		if(!options.isHideComment() || !options.isHideTicker()) {
			commentIcon = getIcon(options.getCommentIconResourceId(), "icon_comment.png");
		}
		
		if(!options.isHideShare() || !options.isHideTicker()) {
			shareIcon = getIcon(options.getShareIconResourceId(), "icon_share.png");
		}
	
		if(!options.isHideTicker()) {
			viewIcon = getIcon(options.getViewIconResourceId(), "icon_view.png");
		}

		boolean showShareButton = true;
		boolean showLikeButton = true;
		boolean showCommentButton = true;
		boolean showAlreadyLikedButton = true;

		Properties props = new Properties();
		InputStream is = null;
		try {
			is = contextActivity.getAssets().open(SocializeConfig.SOCIALIZE_PROPERTIES_PATH);
			props.load(is);

			String propertyString = props.getProperty(SocializeConfig.SOCIALIZE_SHARING_ENABLED);
			showShareButton = (propertyString == null) ? true : Boolean.parseBoolean(propertyString);
			Log.v("Socialize: ActionBarLayoutView", "should show share button? " + ((showShareButton) ? "YES" : "NO"));

			propertyString = props.getProperty(SocializeConfig.SOCIALIZE_LIKE_ENABLED);
			showLikeButton = (propertyString == null) ? true : Boolean.parseBoolean(propertyString);
			Log.v("Socialize: ActionBarLayoutView", "should show like button? " + ((showLikeButton) ? "YES" : "NO"));

			propertyString = props.getProperty(SocializeConfig.SOCIALIZE_COMMENTS_ENABLED);
			showCommentButton = (propertyString == null) ? true : Boolean.parseBoolean(propertyString);
			Log.v("Socialize: ActionBarLayoutView", "should show comment button? " + ((showCommentButton) ? "YES" : "NO"));

			propertyString = props.getProperty(SocializeConfig.SOCIALIZE_ALREADY_LIKED_ENABLED);
			showAlreadyLikedButton = (propertyString == null) ? true : Boolean.parseBoolean(propertyString);
			Log.v("Socialize: ActionBarLayoutView", "should show already liked button? " + ((showAlreadyLikedButton) ? "YES" : "NO"));
		} catch (IOException ignored) {
			// ignored IOException
		} finally {
			props = null;
			contextActivity = null;

			if (is != null) {
				try {
					is.close();
				} catch (IOException ignored) {
					// ignored IOException
				}
			}
		}

		int enabledButtonsCount = showShareButton ? 1 : 0;
		enabledButtonsCount += showLikeButton ? 1 : 0;
		enabledButtonsCount += showCommentButton ? 1 : 0;
		enabledButtonsCount += showAlreadyLikedButton ? 1 : 0;

		int width = ActionBarView.ACTION_BAR_BUTTON_WIDTH * ACTION_BAR_BUTTONS_COUNT / enabledButtonsCount + 1;

		int accentHeight = displayUtils.getDIP(4);
		int strokeWidth = displayUtils.getDIP(1);
//		int width = ActionBarView.ACTION_BAR_BUTTON_WIDTH;
		
		int likeWidth = width - 5;
		int commentWidth = width + 15;
		int shareWidth = width - 5;
		
		if(!options.isHideTicker()) {
			ticker = tickerFactory.getBean(options.getBackgroundColor());
		}
		
		int textColor = Color.WHITE;
		
		if(options.getTextColor() != null) {
			textColor = options.getTextColor();
		}
		
		if(!options.isHideComment() || !options.isHideTicker()) {
			commentsItem = itemFactory.getBean(textColor);
			commentsItem.setIcon(commentIcon);
			if(!options.isHideComment()) commentButton = buttonFactory.getBean();
		}
		
		if(!options.isHideLike() || !options.isHideTicker()) {
			likesItem = itemFactory.getBean(textColor);
			likesItem.setIcon(likeIcon);
			if(!options.isHideLike()) likeButton = buttonFactory.getBean();
		}
		
		if(!options.isHideShare() || !options.isHideTicker()) {
			sharesItem = itemFactory.getBean(textColor);
			sharesItem.setIcon(shareIcon);
			if(!options.isHideShare()) shareButton = buttonFactory.getBean();
		}
		
		if(!options.isHideTicker())  {
			viewsItem = itemFactory.getBean(textColor);
			viewsItem.setIcon(viewIcon);
			
			ticker.addTickerView(viewsItem);
			ticker.addTickerView(commentsItem);
			ticker.addTickerView(likesItem);
			ticker.addTickerView(sharesItem);
		}
		
		ActionBarButtonBackground bg = new ActionBarButtonBackground(
				accentHeight, 
				strokeWidth, 
				options.getStrokeColor(), 
				options.getAccentColor(), 
				options.getFillColor(), 
				options.getHighlightColor(), 
				options.getColorLayout());
		
		if(commentButton != null) {
			commentButton.setIcon(commentIcon);
			commentButton.setBackgroundDrawable(bg);
			
			commentButton.setListener(new ActionBarButtonListener() {
				@Override
				public void onClick(ActionBarButton button) {
					boolean consumed = false;
					
					if(onActionBarEventListener != null) {
						consumed = onActionBarEventListener.onClick(actionBarView, ActionBarEvent.COMMENT);
					}
					
					if(!consumed) {
						commentUtils.showCommentView(getActivity(), actionBarView.getEntity());
					}
				}
			});			
		}
		
		if(likeButton != null) {
			likeButton.setIcon(likeIcon);
			likeButton.setBackgroundDrawable(bg);
			
			likeButton.setListener(new ActionBarButtonListener() {
				@Override
				public void onClick(ActionBarButton button) {
					
					boolean consumed = false;
					
					if(onActionBarEventListener != null) {
						consumed = onActionBarEventListener.onClick(actionBarView, ActionBarEvent.LIKE);
					}
					
					if(!consumed) {
						doLike(likeButton);
					}
				}
			});			
		}
	
		
		if(shareButton != null) {
			shareButton.setIcon(shareIcon);
			shareButton.setBackgroundDrawable(bg);
			
			shareButton.setListener(new ActionBarButtonListener() {
				@Override
				public void onClick(ActionBarButton button) {
					
					boolean consumed = false;
					
					if(onActionBarEventListener != null) {
						consumed = onActionBarEventListener.onClick(actionBarView, ActionBarEvent.SHARE);
					}
					if(!consumed) {
						ShareUtils.showShareDialog(getActivity(), actionBarView.getEntity(), new SocialNetworkDialogListener() {

							@Override
							public void onError(SocializeException error) {
								Toast.makeText(getActivity(), "Share Failed!  Please try again", Toast.LENGTH_SHORT).show();
							}

							@Override
							public void onNetworkError(Activity context, SocialNetwork network, Exception error) {
								Toast.makeText(context, "Share Failed!  Please try again", Toast.LENGTH_SHORT).show();
							}

							@Override
							public void onAfterPost(Activity parent, SocialNetwork socialNetwork, JSONObject responseObject) {
								Toast.makeText(parent, "Share Successful", Toast.LENGTH_SHORT).show();
							}
						});
					}
				}
			});
		}
		
		if(ticker != null) {
			ticker.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(android.view.View v) {
					
					boolean consumed = false;
					
					if(onActionBarEventListener != null) {
						consumed = onActionBarEventListener.onClick(actionBarView, ActionBarEvent.VIEW);
					}	
					
					if(!consumed) {
						ticker.skipToNext();
					}
				}
			});
		}

		if(viewsItem != null) {
			viewsItem.init();
			viewsItem.setText(loadingText);
		}
		
		if(commentsItem != null) {
			commentsItem.init();
			commentsItem.setText(loadingText);
			
			commentButton.init(commentWidth, 0.0f, textColor);
			commentButton.setText("Comment");
		}
		
		if(likesItem != null) {
			likesItem.init();
			likesItem.setText(loadingText);
			
			likeButton.init(likeWidth, 0.0f, textColor);
			likeButton.setText(loadingText);
		}
		
		if(sharesItem != null) {
			sharesItem.init();
			sharesItem.setText(loadingText);
			
			shareButton.init(shareWidth, 0.0f, textColor);
			shareButton.setText("Share");
		}
		
		if(ticker != null) {
			ticker.init(LayoutParams.FILL_PARENT, 1.0f);
		}

		if (ticker != null && showAlreadyLikedButton) addView(ticker);
		if (likeButton != null && showLikeButton) addView(likeButton);
		if (shareButton != null && showShareButton) addView(shareButton);
		if (commentButton != null && showCommentButton) addView(commentButton);

//		if(ticker != null) addView(ticker);
//		if(likeButton != null) addView(likeButton);
//		if(shareButton != null) addView(shareButton);
//		if(commentButton != null) addView(commentButton);
	}
	
	@Override
	public void onViewLoad() {
		super.onViewLoad();
		doLoadSequence(false);
	}
	
	@Override
	public void onViewUpdate() {
		super.onViewUpdate();
		doLoadSequence(true);
	}
	
	protected void doLoadSequence(boolean reload) {
		
		// Pre-load dialogs
		shareUtils.preloadShareDialog(getActivity());
		shareUtils.preloadLinkDialog(getActivity());
		
		final Entity userProvidedEntity = actionBarView.getEntity();
		if(ticker != null) ticker.resetTicker();
		if(userProvidedEntity != null) {
			if(reload) {
				if(viewsItem != null) viewsItem.setText(loadingText);
				if(commentsItem != null) commentsItem.setText(loadingText);
				if(likesItem != null) likesItem.setText(loadingText);
				if(sharesItem != null) sharesItem.setText(loadingText);
				if(likeButton != null) likeButton.setText(loadingText);
				
				if(onActionBarEventListener != null) {
					onActionBarEventListener.onUpdate(actionBarView);
				}	
			}
			else {
				if(ticker != null) ticker.startTicker();
				
				if(onActionBarEventListener != null) {
					onActionBarEventListener.onLoad(actionBarView);
				}	
			}
			
			updateEntity(userProvidedEntity, reload);
		}
		else {
			if(logger != null) {
				logger.warn("No entity provided to ActionBar.  Load sequence aborted.");
			}
		}
	}
	
	protected void updateEntity(final Entity entity, boolean reload) {

		CacheableEntity localEntity = getLocalEntity();
		
		if(localEntity == null) {
			ViewUtils.view(getActivity(), entity, new ViewAddListener() {
				@Override
				public void onError(SocializeException error) {
					SocializeLogger.e(error.getMessage(), error);
					getLike(entity.getKey());
				}
				
				@Override
				public void onCreate(View view) {
					// Entity will be set in like
					getLike(view.getEntity().getKey());
				}
			});
		}
		else {
			if(reload) {
				if(localEntity.isLiked()) {
					getLike(entity.getKey());
				}
				else {
					getEntity(entity.getKey());
				}
			}
			else {
				// Just set everything from the cached version
				if(onActionBarEventListener != null) {
					onActionBarEventListener.onGetEntity(actionBarView, localEntity.getEntity());
				}					
				setEntityData(localEntity);
			}
		}
	}
	
	public void reload() {
		if(actionBarView.getEntity() != null) {
			entityCache.remove(actionBarView.getEntity().getKey());
		}
		doLoadSequence(true);
	}

	protected void doLike(final ActionBarButton button) {
		final CacheableEntity localEntity = getLocalEntity();
		
		if(localEntity != null && localEntity.isLiked()) {
			// Unlike
			doUnLike(button, localEntity);
			return;
		}
		
		button.showLoading();
		
		LikeUtils.like(getActivity(), actionBarView.getEntity(), new LikeAddListener() {
			
			@Override
			public void onCancel() {
				button.hideLoading();
			}

			@Override
			public void onError(SocializeException error) {
				logError("Error posting like", error);
				button.hideLoading();
			}
			
			@Override
			public void onCreate(Like like) {
				CacheableEntity localEntity = setLocalEntity(like.getEntity());
				localEntity.setLiked(true);
				localEntity.setLikeId(like.getId());
				setEntityData(localEntity);
				
				button.hideLoading();
				
				if(onActionBarEventListener != null) {
					onActionBarEventListener.onPostLike(actionBarView, like);
				}
			}
		});
	}
	
	protected void doUnLike(final ActionBarButton button, final CacheableEntity localEntity) {
		button.showLoading();
		
		LikeUtils.unlike(getActivity(), localEntity.getKey(), new LikeDeleteListener() {
			@Override
			public void onError(SocializeException error) {
				logError("Error deleting like", error);
				
				if(localEntity != null) {
					localEntity.setLiked(false);
					setEntityData(localEntity);
				}

				button.hideLoading();
			}
			
			@Override
			public void onDelete() {
				if(localEntity != null) {
					localEntity.setLiked(false);
					setEntityData(localEntity);
				}

				button.hideLoading();
				if(onActionBarEventListener != null) {
					onActionBarEventListener.onPostUnlike(actionBarView);
				}
			}
		});
	}

	protected CacheableEntity getLocalEntity() {
		if(entityCache != null && actionBarView != null && actionBarView.getEntity() != null) {
			return entityCache.get(actionBarView.getEntity().getKey());
		}
		return null;
	}
	
	protected CacheableEntity setLocalEntity(Entity entity) {
		// Don't override the action bar entity if it has changed.
		if(actionBarView.getEntity() != null) {
			if(entity.getKey().equals(actionBarView.getEntity().getKey())) {
				return entityCache.putEntity(entity);
			}
			else {
				return entityCache.putEntity(actionBarView.getEntity());
			}
		}
		return entityCache.putEntity(entity);
	}
	
	protected void getLike(final String entityKey) {
		
		// Get the like
		LikeUtils.getLike(getActivity(), entityKey, new LikeGetListener() {
			
			@Override
			public void onGet(Like like) {
				if(like != null) {
					CacheableEntity putEntity = setLocalEntity(like.getEntity());
					putEntity.setLiked(true);
					putEntity.setLikeId(like.getId());
					setEntityData(putEntity);
					
					if(onActionBarEventListener != null) {
						onActionBarEventListener.onGetLike(actionBarView, like);
					}
					
					if(onActionBarEventListener != null) {
						onActionBarEventListener.onGetEntity(actionBarView, like.getEntity());
					}	
				}
				else {
					getEntity(entityKey);
				}
			}
			
			@Override
			public void onError(SocializeException error) {
				if(error instanceof SocializeApiError) {
					if(((SocializeApiError)error).getResultCode() == 404) {
						// no like
						getEntity(entityKey);
						// Don't log error
						return;
					}
				}
				
				logError("Error retrieving entity data", error);
			}
		});
	}
	
	protected void getEntity(String entityKey) {
		EntityUtils.getEntity(getActivity(), entityKey, new EntityGetListener() {
			@Override
			public void onGet(Entity entity) {
				CacheableEntity putEntity = setLocalEntity(entity);
				setEntityData(putEntity);
				
				if(onActionBarEventListener != null) {
					onActionBarEventListener.onGetEntity(actionBarView, entity);
				}	
			}
			
			@Override
			public void onError(SocializeException error) {
				if(logger != null && logger.isDebugEnabled()) {
					logger.debug("Error retrieving entity data.  This may be ok if the entity is new", error);
				}
			}
		});
	}
	
	protected void setEntityData(CacheableEntity ce) {
		Entity entity = ce.getEntity();
		
		actionBarView.setEntity(entity);
		
		EntityStats stats = entity.getEntityStats();
		
		if(stats != null) {
			if(viewsItem != null) viewsItem.setText(getCountText(stats.getViews()));
			if(commentsItem != null) commentsItem.setText(getCountText(stats.getComments()));
			if(likesItem != null) likesItem.setText(getCountText(stats.getLikes() + ((ce.isLiked()) ? 1 : 0)));
			if(sharesItem != null) sharesItem.setText(getCountText(stats.getShares()));
		}
		
		if(likeButton != null) {
			if(ce.isLiked()) {
				likeButton.setText("Unlike");
				likeButton.setIcon(likeIconHi);
			}
			else {
				likeButton.setText("Like");
				likeButton.setIcon(likeIcon);
			}
		}
	}
	
	protected String getCountText(Integer value) {
		String viewText = "0";
		
		if(value != null) {
			int iVal = value.intValue();
			if(iVal >= 1000) {
				float fVal = (float) iVal / 1000.0f;
				viewText = countFormat.format(fVal);
			}
			else {
				viewText = value.toString();
			}
		}

		return viewText;
	}
	
	protected void logError(String msg, Exception error) {
		if(logger != null) {
			logger.error(msg, error);
		}
		else {
			SocializeLogger.e(msg, error);
		}
	}
	
	public ActionBarButton getShareButton() {
		return shareButton;
	}

	public ActionBarButton getCommentButton() {
		return commentButton;
	}

	public ActionBarButton getLikeButton() {
		return likeButton;
	}

	public void setDrawables(Drawables drawables) {
		this.drawables = drawables;
	}

	public void setLogger(SocializeLogger logger) {
		this.logger = logger;
	}

	public void setButtonFactory(IBeanFactory<ActionBarButton> buttonFactory) {
		this.buttonFactory = buttonFactory;
	}

	public void setEntityCache(EntityCache entityCache) {
		this.entityCache = entityCache;
	}

	public ProgressDialogFactory getProgressDialogFactory() {
		return progressDialogFactory;
	}

	public void setProgressDialogFactory(ProgressDialogFactory progressDialogFactory) {
		this.progressDialogFactory = progressDialogFactory;
	}

	public void setDisplayUtils(DisplayUtils deviceUtils) {
		this.displayUtils = deviceUtils;
	}

	public void setTickerFactory(IBeanFactory<ActionBarTicker> tickerFactory) {
		this.tickerFactory = tickerFactory;
	}

	public void setItemFactory(IBeanFactory<ActionBarItem> itemFactory) {
		this.itemFactory = itemFactory;
	}
	
	
	public void setShareUtils(ShareUtilsProxy shareUtils) {
		this.shareUtils = shareUtils;
	}
	
	public void setCommentUtils(CommentUtilsProxy commentUtils) {
		this.commentUtils = commentUtils;
	}

	public void stopTicker() {
		if(ticker != null) {
			ticker.stopTicker();
		}
		
	}

	public void startTicker() {
		if(ticker != null) {
			ticker.startTicker();
		}
	}

	public void setOnActionBarEventListener(OnActionBarEventListener onActionBarEventListener) {
		this.onActionBarEventListener = onActionBarEventListener;
	}
	
	public OnActionBarEventListener getOnActionBarEventListener() {
		return onActionBarEventListener;
	}
}
