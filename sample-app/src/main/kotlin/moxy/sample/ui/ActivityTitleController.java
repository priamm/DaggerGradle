package moxy.sample.ui;

import android.app.Activity;
import javax.inject.Inject;

import moxy.sample.PerActivity;

@PerActivity
public class ActivityTitleController {
  private final Activity activity;

  @Inject public ActivityTitleController(Activity activity) {
    this.activity = activity;
  }

  public void setTitle(CharSequence title) {
    activity.setTitle(title);
  }
}
