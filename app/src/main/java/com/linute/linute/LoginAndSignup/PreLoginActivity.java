package com.linute.linute.LoginAndSignup;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import androidx.fragment.app.FragmentPagerAdapter;
import android.os.Bundle;

import android.view.View;

import com.linute.linute.LoginAndSignup.SignUpFragments.SignUpParentFragment;
import com.linute.linute.MainContent.MainActivity;
import com.linute.linute.R;
import com.linute.linute.UtilsAndHelpers.LinuteConstants;

public class PreLoginActivity extends AppCompatActivity {

    /**
     * The {@link androidx.viewpager.widget.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link androidx.fragment.app.FragmentStatePagerAdapter}.
     */

    public static String TAG = PreLoginActivity.class.getSimpleName();

    private OnBackPressed mOnBackPressed;

    private View vChat;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pre_login);

        vChat = findViewById(R.id.chat_button);

        if (vChat != null)
            vChat.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(Intent.ACTION_SENDTO);
                    intent.setData(Uri.parse("mailto:")); // only email apps should handle this
                    intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"support@tapt.io"});
                    intent.putExtra(Intent.EXTRA_SUBJECT, "Help Me Plz");
                    intent.putExtra(android.content.Intent.EXTRA_TEXT,
                            "If you are having issues with your signin (ie. not receiving pincode, " +
                                    "email not working), swap this text out with the problem you are having.");
                    startActivity(Intent.createChooser(intent, "Send email"));
                }
            });

        if (savedInstanceState == null)
            replaceFragment(new PreloginChoicesFragment());

    }


    public void showChat(boolean show) {
        vChat.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();

        Intent intent = getIntent();
        if (intent != null) {
            String text = intent.getStringExtra("BANNED");
            if (text != null) {
                new AlertDialog.Builder(this)
                        .setTitle("Banned")
                        .setMessage(text)
                        .setPositiveButton("okay", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        }).show();
            }
            intent.removeExtra("BANNED");
        }
    }

    public void replaceFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out);
        transaction.replace(R.id.login_activity_fragment_frame, fragment);
        transaction.commit();
    }

    public void addFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.setCustomAnimations(R.anim.frag_fade_in, R.anim.frag_fade_out, android.R.anim.fade_in, android.R.anim.fade_out);
        transaction.replace(R.id.login_activity_fragment_frame, fragment).addToBackStack(null);
        transaction.commit();
    }


    public void selectedSignup() {
        addFragment(new SignUpParentFragment());
    }

    public void selectForgotPassword() {
        addFragment(new ForgotPasswordFragment());
    }

    public void selectSignIn() {
        addFragment(new LinuteLoginFragment());
    }

    public void goToNextActivity() {
        final SharedPreferences sharedPreferences = getSharedPreferences(LinuteConstants.SHARED_PREF_NAME, MODE_PRIVATE);


        //college set, go to college
        final Runnable nextActivityRunnable = new Runnable() {
            @Override
            public void run() {
                Class nextActivity;

                if (sharedPreferences.getString("collegeName", null) != null && sharedPreferences.getString("collegeId", null) != null) {
                    nextActivity = MainActivity.class;
                    //college picker is not set. go to college picker
                } else {
                    nextActivity = CollegePickerActivity.class;
                }
                Intent i = new Intent(PreLoginActivity.this, nextActivity);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); //clear stack
                startActivity(i); //start new activity
                finish();
            }
        };

        nextActivityRunnable.run();
    }


    @Override
    public void onBackPressed() {
        if (mOnBackPressed != null) {
            mOnBackPressed.onBack();
        } else {
            super.onBackPressed();
        }
    }

    public void setOnBackPressed(OnBackPressed onBackPressed) {
        mOnBackPressed = onBackPressed;
    }


    public interface OnBackPressed {
        void onBack();
    }

}

