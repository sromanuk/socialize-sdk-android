package com.socialize.ui.sample.mock;

import android.app.Activity;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.RelativeLayout.LayoutParams;

import com.socialize.ui.actionbar.ActionBarView;

public interface TestUIFactory {
	public RelativeLayout newRelativeLayout(Activity parent);
	public ActionBarView newActionBarView(Activity parent);
	public LayoutParams newLayoutParams(int width, int height);
	public ScrollView newScrollView(Activity parent);
}
