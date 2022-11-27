package moxy.sample.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import javax.inject.Inject;

import static android.view.Gravity.CENTER;

import androidx.fragment.app.Fragment;

public class HomeFragment extends Fragment {
  @Inject ActivityTitleController titleController;

  @Override public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    ((HomeActivity) getActivity()).component().inject(this);
  }

  @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    TextView tv = new TextView(getActivity());
    tv.setGravity(CENTER);
    tv.setText("Hello, World");
    return tv;
  }

  @Override public void onResume() {
    super.onResume();
    titleController.setTitle("Home Fragment");
  }
}
