package com.socialize.snippets;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.socialize.Socialize;
import com.socialize.entity.Entity;
import com.socialize.snippets.Snippets.MyContentActivity;
import com.socialize.ui.SocializeEntityLoader;

public class SampleActionBarActivityEntityLoader extends Activity {

	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		// Your entity key. May be passed as a Bundle parameter to your activity
		String entityKey = "http://www.getsocialize.com";

		// Create an entity object including a name
		// The Entity object is Serializable, so you could also store the whole object in the Intent
		Entity entity = Entity.newInstance(entityKey, "Socialize");

		// Set up the entity loader
		Socialize.getSocialize().setEntityLoader(new SocializeEntityLoader() {
			public void loadEntity(Activity activity, Entity entity) {
				// Launch an activity from here...
				Intent intent = new Intent(activity, MyContentActivity.class);

				// Add the key from the entity
				intent.putExtra("some_key", entity.getKey());
				activity.startActivity(intent);
			}
			@Override
			public boolean canLoad(Context arg0, Entity arg1) {
				return true;
			}
		});

		// Wrap your existing view with the action bar.
		// your_layout refers to the resource ID of your current layout.
		View actionBarWrapped = Socialize.getSocializeUI().showActionBar(this, R.layout.your_layout, entity);

		// Now set the view for your activity to be the wrapped view.
		setContentView(actionBarWrapped);
	}
}