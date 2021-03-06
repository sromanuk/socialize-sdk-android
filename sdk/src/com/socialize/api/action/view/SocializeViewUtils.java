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
package com.socialize.api.action.view;

import android.app.Activity;
import com.socialize.Socialize;
import com.socialize.entity.Entity;
import com.socialize.entity.User;
import com.socialize.listener.view.ViewAddListener;
import com.socialize.listener.view.ViewGetListener;
import com.socialize.listener.view.ViewListListener;


/**
 * @author Jason Polites
 */
public class SocializeViewUtils implements ViewUtilsProxy {
	
	private ViewSystem viewSystem;
	
	@Override
	public void view(Activity context, Entity e, ViewAddListener listener) {
		viewSystem.addView(Socialize.getSocialize().getSession(), e, null, listener);
	}

	@Override
	public void getView(Activity context, long id, ViewGetListener listener) {
		viewSystem.getView(Socialize.getSocialize().getSession(), id, listener);
	}

	@Deprecated
	@Override
	public void getView(Activity context, Entity e, ViewGetListener listener) {
		viewSystem.getView(Socialize.getSocialize().getSession(), e, listener);
	}

	@Deprecated
	@Override
	public void getViewsByUser(Activity context, User user, int start, int end, ViewListListener listener) {
		viewSystem.getViewsByUser(Socialize.getSocialize().getSession(), user.getId(), start, end, listener);
	}

	@Deprecated
	@Override
	public void getViewsByEntity(Activity context, Entity entity, int start, int end, ViewListListener listener) {
		viewSystem.getViewsByEntity(Socialize.getSocialize().getSession(), entity.getKey(), start, end, listener);
	}

	public void setViewSystem(ViewSystem viewSystem) {
		this.viewSystem = viewSystem;
	}
}
