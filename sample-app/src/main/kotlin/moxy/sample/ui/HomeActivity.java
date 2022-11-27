package moxy.sample.ui;

import android.location.LocationManager;
import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

import javax.inject.Inject;

import moxy.sample.ActivityModule;
import moxy.sample.DemoApplication;

public class HomeActivity extends FragmentActivity {
  @Inject LocationManager locationManager;
  private HomeComponent component;

  HomeComponent component() {
    if (component == null) {
      component = DaggerHomeComponent.builder()
          .applicationComponent(((DemoApplication) getApplication()).component())
          .activityModule(new ActivityModule(this))
          .build();
    }
    return component;
  }

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    component().inject(this);

    if (savedInstanceState == null) {
      getSupportFragmentManager().beginTransaction()
          .add(android.R.id.content, new HomeFragment())
          .commit();
    }

  }
}
