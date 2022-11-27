package moxy.sample;

import android.app.Application;
import android.location.LocationManager;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = DemoApplicationModule.class)
public interface ApplicationComponent {

  void inject(DemoApplication application);

  Application application();
  LocationManager locationManager();
}