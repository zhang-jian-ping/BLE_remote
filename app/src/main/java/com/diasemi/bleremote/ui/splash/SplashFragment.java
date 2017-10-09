
package com.diasemi.bleremote.ui.splash;

import android.app.Fragment;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.diasemi.bleremote.BusProvider;
import com.diasemi.bleremote.R;

public class SplashFragment extends Fragment {

    private static final int SPLASH_TIME = 3000;
    private static final String TAG = "SplashFragment";
    private Handler mHandler = new Handler();

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_splash, container, false);
        // Set font
        TextView appName = (TextView) view.findViewById(R.id.appName);
        appName.setTypeface(Typeface.createFromAsset(getActivity().getAssets(), "fonts/MyriadPro-Light.otf"));
        // Dismiss timer
        Runnable exitRunnable = new Runnable() {
            @Override
            public void run() {
                BusProvider.getInstance().post(new SplashEvent());
            }
        };
        mHandler.postDelayed(exitRunnable, SPLASH_TIME);
        // Dismiss on click
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BusProvider.getInstance().post(new SplashEvent());
            }
        });
        return view;
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    public class SplashEvent {
        //
    }
}