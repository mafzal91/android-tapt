package com.linute.linute.MainContent.DiscoverFragment;

import android.content.Context;
import com.google.android.material.navigation.NavigationView;
import android.util.AttributeSet;

/**
 * Created by mikhail on 9/6/16.
 */
public class CustomNavigationView extends NavigationView{
    public CustomNavigationView(Context context) {
        super(context);
    }

    public CustomNavigationView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomNavigationView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void inflateMenu(int resId) {
        super.inflateMenu(resId);

    }


}
