package com.linute.linute.SquareCamera;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import androidx.annotation.Nullable;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCase;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.google.common.util.concurrent.ListenableFuture;
import com.linute.linute.MainContent.EditScreen.Dimens;
import com.linute.linute.MainContent.EditScreen.EditFragment;
import com.linute.linute.MainContent.EditScreen.PostOptions;
import com.linute.linute.R;
import com.linute.linute.UtilsAndHelpers.StoragePermissions;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Consumer;

import static io.reactivex.rxjava3.schedulers.Schedulers.io;
import static io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread;

@SuppressWarnings("deprecation")
public class CameraFragment extends Fragment {

    public static final String TAG = CameraFragment.class.getSimpleName();

    private static final long MAX_VIDEO_MILLIS = 15000;
    private static final long MAX_VIDEO_BYTES = 50000000;
    //anything shorter becomes a photo
    private static final long MIN_VIDEO_MILLIS = 2000;

    public static final String CAMERA_ID_KEY = "camera_id";
    public static final String CAMERA_FLASH_KEY = "flash_on";
    public static final String KEY_LAST_CAMERA = "last_camera";

    private CameraType mCameraType;
    private int mCameraID;
    private boolean mFlashOn;

    private ProcessCameraProvider mCameraProvider;
    private Camera mCamera;
    private VideoCapture<Recorder> mVideoCapture;
    private Recording mRecording;
    private CustomCameraPreview mPreviewView;

    private ImageView mTakePhotoBtn;
    private Dimens mVideoDimen;
    private View mFlashContainer;
    private View mReverseContainer;
    private CustomView mFlashTop;

    private View mFocusCircle;

    private Uri mVideoUri;

    private ProgressBar mRecordProgress;
    private ObjectAnimator mProgressAnimator;

    private Handler mRecordHandler = new Handler();

    private Disposable mProcessImageSubscriptions;
    private Disposable mStartCameraSubscription;

    private boolean mIsRecording = false;
    private boolean mIsSafeToTakePhoto = false;
    private boolean mVideoProcessing = false;
    //the screen was left mid recording, so don't act on the result
    private boolean mDiscardRecording = false;

    private PostOptions mPostOptions;

    private static final String ARG_POST_OPTIONS = "content_type";
    private Toolbar mToolbar;


    public static Fragment newInstance(PostOptions options) {
        CameraFragment cameraFragment = new CameraFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_POST_OPTIONS, options);
        cameraFragment.setArguments(args);
        return cameraFragment;
    }

    public CameraFragment() {
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Restore your state here because a double rotation with this fragment
        // in the backstack will cause improper state restoration
        // onCreate() -> onSavedInstanceState() instead of going through onCreateView()
        if (savedInstanceState == null) {
            mCameraID = PreferenceManager.getDefaultSharedPreferences(getContext()).getInt(KEY_LAST_CAMERA, getBackCameraID());
            mFlashOn = CameraSettingPreferences.getCameraFlashMode(getActivity());
        } else {
            mCameraID = savedInstanceState.getInt(CAMERA_ID_KEY);
            mFlashOn = savedInstanceState.getBoolean(CAMERA_FLASH_KEY);
        }

        Bundle args = getArguments();
        if(args != null){
            mPostOptions = args.getParcelable(ARG_POST_OPTIONS);
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.square_camera_take_photo, container, false);

        mFlashTop = (CustomView) root.findViewById(R.id.flash_top);

        //make square if doesn't meet our requirements
        if (!ScreenSizeSingleton.getSingleton().mHasRatioRequirement) {
            mFlashTop.setMakeSquare(true);
            root.requestLayout();
        }

        mCameraType = ((CameraActivity) getActivity()).getCameraType();

        mTakePhotoBtn = (ImageView) root.findViewById(R.id.capture_image_button);

        mToolbar = (Toolbar) root.findViewById(R.id.toolbar);
        mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsSafeToTakePhoto && !mIsRecording && !mVideoProcessing)
                    getActivity().finish();
            }
        });

        mRecordProgress = (ProgressBar) root.findViewById(R.id.record_progress);
        mFocusCircle = root.findViewById(R.id.focus);

        setOnClickListeners(root);

        return root;
    }

    @Override
    public void onViewCreated(final View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mPreviewView = (CustomCameraPreview) view.findViewById(R.id.camera_preview_view);
        mPreviewView.getPreviewStreamState().observe(getViewLifecycleOwner(),
                state -> setSafeToTakePhoto(state == PreviewView.StreamState.STREAMING));

        ListenableFuture<ProcessCameraProvider> provider = ProcessCameraProvider.getInstance(requireContext());
        provider.addListener(() -> {
            try {
                mCameraProvider = provider.get();
                bindCamera();
            } catch (ExecutionException | InterruptedException e) {
                Log.d(TAG, "Can't start CameraX", e);
            }
        }, ContextCompat.getMainExecutor(requireContext()));

        mPreviewView.setOnTouched(new CustomCameraPreview.OnFocus() {
            @Override
            public void onFocusStart(float x, float y) {
                float center = (float) mFocusCircle.getWidth() / 2;
                mFocusCircle.setX(x - center);
                mFocusCircle.setY(y - center);
                mFocusCircle.setAlpha(0);
                mFocusCircle.setScaleX(0.75f);
                mFocusCircle.setScaleY(0.75f);
                mFocusCircle.setVisibility(View.VISIBLE);
                mFocusCircle.animate()
                        .alpha(1.f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(300)
                        .start();
            }

            @Override
            public void onFocusFinished() {
                mFocusCircle.clearAnimation();
                mFocusCircle.setVisibility(View.GONE);
            }
        });

        setupFlashMode();
    }

    //buttons will only be set after camera view has appeared
    private void setOnClickListeners(final View view) {
        mFlashContainer = view.findViewById(R.id.flash_container);
        mReverseContainer = view.findViewById(R.id.reverse_container);

        mReverseContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsSafeToTakePhoto) {
                    if (mCameraID == CameraSelector.LENS_FACING_FRONT) {
                        mCameraID = getBackCameraID();
                    } else {
                        mCameraID = getFrontCameraID();
                    }
                    bindCamera();
                    PreferenceManager.getDefaultSharedPreferences(getContext()).edit().putInt(KEY_LAST_CAMERA, mCameraID).apply();
                }
            }
        });

        mFlashContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsSafeToTakePhoto) {
                    mFlashOn = !mFlashOn;
                    setupFlashMode();
                }
            }
        });

        if (!mCameraType.contains(CameraType.CAMERA_VIDEO)) {
            mTakePhotoBtn.setImageResource(R.drawable.camera_button);
            mTakePhotoBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    takePicture();
                }
            });
        } else {
            mTakePhotoBtn.setOnTouchListener(new View.OnTouchListener() {
                Runnable mRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (mIsSafeToTakePhoto && !mIsRecording) {
                            startRecording();
                        }
                    }
                };

                float mMovement;
                float mYPosition;
                int mMovementThreshold = 30;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_UP:
                            mRecordHandler.removeCallbacks(mRunnable);
                            mTakePhotoBtn.setImageResource(R.drawable.square_camera_unselected);
                            if (!mVideoProcessing) {
                                if (mIsRecording) {
                                    stopRecording();
                                } else {
                                    takePicture();
                                }
                            }
                            break;

                        case MotionEvent.ACTION_DOWN:
                            mYPosition = event.getY();
                            mMovement = 0;
                            mMovementThreshold = 30;
                            if (!mIsRecording && !mVideoProcessing) {
                                mTakePhotoBtn.setImageResource(R.drawable.square_camera_selected);
                                mRecordHandler.removeCallbacks(mRunnable);
                                mRecordHandler.postDelayed(mRunnable, 600);
                            }
                            break;

                        case MotionEvent.ACTION_MOVE:
                            if (mIsRecording) {
                                mMovement += mYPosition - event.getY();
                                mYPosition = event.getY();
                                if (Math.abs(mMovement) > mMovementThreshold) {
                                    mPreviewView.zoom(mMovement > 0 ? 2 : -2);
                                    mMovement = 0;
                                    mMovementThreshold = 5;
                                }
                            }
                            break;
                    }
                    return true;
                }
            });
        }
    }


    //hides the gallery, flash, and reverse camera button
    private void hideCameraButtons(boolean hide) {
        if (hide) {
            mFlashContainer.setVisibility(View.INVISIBLE);
            mReverseContainer.setVisibility(View.INVISIBLE);
        } else {
            mFlashContainer.setVisibility(View.VISIBLE);
            mReverseContainer.setVisibility(View.VISIBLE);
        }
    }


    private void showVideoTooShortDialog() {
        if (getActivity() == null) return;
        ///Log.i(TAG, "showVideoTooShortDialog: ");
        new AlertDialog.Builder(getActivity())
                .setTitle("Video is too short")
                .setMessage("Videos must be 3 seconds or longer.")
                .setPositiveButton("Okay", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    private void goToVideoEditFragment(Uri uri, Dimens videoDimen) {
        if (getActivity() != null) {
            try {
                final int returnType = ((CameraActivity) getActivity()).getReturnType();

                mPostOptions.type = PostOptions.ContentType.Video;
                getFragmentManager()
                        .beginTransaction()
                        .replace(
                                R.id.fragment_container,
                                EditFragment.newInstance(uri, returnType, videoDimen, mPostOptions ),
                                EditFragment.TAG)
                        .addToBackStack(CameraActivity.EDIT_AND_GALLERY_STACK_NAME)
                        .commit();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }
    }

    // we do not have an auto flash
    // some phones crash if both autofocus and flash are on. There is no way to tell from autoflash is
    // flash is going to go off.
    private void setupFlashMode() {
        if (mFlashContainer == null || getContext() == null) return;
        ImageView view = ((ImageView) mFlashContainer.findViewById(R.id.flash_icon));
        if (mFlashOn) view.setColorFilter(Color.YELLOW, PorterDuff.Mode.MULTIPLY);
        else view.clearColorFilter();

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
//        Log.d(TAG, "onSaveInstanceState");
        outState.putInt(CAMERA_ID_KEY, mCameraID);
        outState.putBoolean(CAMERA_FLASH_KEY, mFlashOn);
        super.onSaveInstanceState(outState);
    }


    /**
     * Start the camera preview, replacing the one that's showing
     */
    private void bindCamera() {
        if (mCameraProvider == null || getView() == null || !hasCameraAndWritePermission()) return;

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(mPreviewView.getSurfaceProvider());

        UseCase[] useCases;
        if (mCameraType.contains(CameraType.CAMERA_VIDEO)) {
            mVideoCapture = VideoCapture.withOutput(new Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.SD,
                            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                    .setAspectRatio(AspectRatio.RATIO_4_3)
                    .build());
            useCases = new UseCase[]{preview, mVideoCapture};
        } else {
            useCases = new UseCase[]{preview};
        }

        CameraSelector selector = new CameraSelector.Builder().requireLensFacing(mCameraID).build();

        mCameraProvider.unbindAll();
        try {
            mCamera = mCameraProvider.bindToLifecycle(getViewLifecycleOwner(), selector, useCases);
            mPreviewView.setCamera(mCamera);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "Can't open camera with id " + mCameraID, e);
        }
    }

    private void setSafeToTakePhoto(final boolean isSafeToTakePhoto) {
        mIsSafeToTakePhoto = isSafeToTakePhoto;
    }

    private int getFrontCameraID() {
        PackageManager pm = getActivity().getPackageManager();
        if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
            return CameraSelector.LENS_FACING_FRONT;
        }

        return getBackCameraID();
    }

    private int getBackCameraID() {
        return CameraSelector.LENS_FACING_BACK;
    }

    /**
     * Take a picture
     */
    private void takePicture() {
        if (mIsSafeToTakePhoto && !mIsRecording) {
            setSafeToTakePhoto(false);

            if (mFlashOn) {
                if (!turnOnFlashLight()) {
                    fadeInFlashForgreound(true);
                }

                // need to wait for flashlight to turn on fully
                mRecordHandler.postDelayed(
                        new Runnable() {
                            @Override
                            public void run() {
                                takeImageOfView();
                            }
                        }, 300);

            } else { //flash was off or flash failed to turn on
                takeImageOfView();
            }
        }
    }

    //returns true if flashlight turned on
    private boolean turnOnFlashLight() {
        if (mCamera != null && mCamera.getCameraInfo().hasFlashUnit()) {
            mCamera.getCameraControl().enableTorch(true);
            return true;
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mCamera == null) {
            bindCamera();
        } else {
            setSafeToTakePhoto(mPreviewView.getPreviewStreamState().getValue() == PreviewView.StreamState.STREAMING);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        mRecordHandler.removeCallbacksAndMessages(null);

        if (mStartCameraSubscription != null) mStartCameraSubscription.dispose();
        if (mProcessImageSubscriptions != null) mProcessImageSubscriptions.dispose();

        if (mIsRecording && !mVideoProcessing) {
            resetRecordingViews();
            mDiscardRecording = true;
            mRecording.stop();
            mIsRecording = false;
        }

        CameraSettingPreferences.saveCameraFlashMode(getActivity(), mFlashOn);
    }


    private void takeImageOfView() {
//        subscriber.onNext(ImageUtility.savePicture(getActivity(), mPreviewView.getBitmap()));
        mProcessImageSubscriptions = Completable.complete()
                .subscribeOn(io())
                .observeOn(mainThread())
                .subscribe(
                        new Action() {
                            @Override
                            public void run() {
                                if (getActivity() == null) return;

                                final int returnType = ((CameraActivity) getActivity()).getReturnType();

                                mStartCameraSubscription = Observable
                                        .just(saveBitmap())
                                        .subscribeOn(io())
                                        .observeOn(mainThread())
                                        .subscribe(
                                                new Consumer<Uri>() {
                                                    @Override
                                                    public void accept(Uri uri) {

                                                        BitmapFactory.Options options = new BitmapFactory.Options();
                                                        options.inJustDecodeBounds = true;
                                                        BitmapFactory.decodeFile(uri.getPath(), options);

                                                        Dimens photoDimens = new Dimens(options.outWidth, options.outHeight);

                                                        mPostOptions.type = PostOptions.ContentType.Photo;

                                                        getFragmentManager()
                                                                .beginTransaction()
                                                                .replace(
                                                                        R.id.fragment_container,
                                                                        EditFragment.newInstance(uri, returnType, photoDimens, mPostOptions),
                                                                        EditFragment.TAG)
                                                                .addToBackStack(CameraActivity.EDIT_AND_GALLERY_STACK_NAME)
                                                                .commit();

                                                        setSafeToTakePhoto(true);
                                                    }
                                                }
                                        );
                            }
                        }
                );
    }


    private Uri saveBitmap() {
        //Log.d(TAG, "saveBitmap: "+mPreviewView.getBitmap().getWidth());
        //Log.i(TAG, "saveBitmap: "+(int)(mPreviewView.getBitmap().getWidth() * 6f / 5f));
        int width = mPreviewView.getWidth();
//        Bitmap previewBitmap = mPreviewView.getBitmap(width,
//                ScreenSizeSingleton.getSingleton().mHasRatioRequirement ?
//                        (int) (width * 6f / 5f) :
//                        width);

        Bitmap original = mPreviewView.getBitmap();
        Bitmap previewBitmap =
                Bitmap.createBitmap(original, 0, 0, width,
                        ScreenSizeSingleton.getSingleton().mHasRatioRequirement ?
                                (int) (width * 6f / 5f) :
                                width
                );

        if (previewBitmap != original)
            original.recycle();

        Uri uri = ImageUtility.savePicture(getContext(), previewBitmap);
        previewBitmap.recycle();
        return uri;
    }

    private void fadeInFlashForgreound(final boolean show) {
        mFlashTop.clearAnimation();

        AlphaAnimation alphaAnimation = show ?
                new AlphaAnimation(0f, 1f) : new AlphaAnimation(1f, 0f);

        alphaAnimation.setDuration(200);
        alphaAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mFlashTop.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        mFlashTop.startAnimation(alphaAnimation);
    }

    //Permissions
    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(getActivity(), permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasCameraAndWritePermission() {
        return StoragePermissions.canWrite(getActivity())
                && hasPermission(Manifest.permission.CAMERA)
                && (!mCameraType.contains(CameraType.CAMERA_VIDEO) || hasPermission(Manifest.permission.RECORD_AUDIO));
    }


    private void startRecording() {
        String path = ImageUtility.getVideoUri();
        if (path == null) return;

        if (mFlashOn && !turnOnFlashLight()) {
            fadeInFlashForgreound(true);
        }
        mIsRecording = true;
        if (mToolbar != null) {
            mToolbar.setTitle("Recording...");
        }

        // The edit screen expects the long side as the width, then turns it upright. Whether the
        // camera turns the frames itself or leaves that to the file's rotation varies by phone.
        Size size = mVideoCapture.getResolutionInfo().getResolution();
        mVideoDimen = new Dimens(
                Math.max(size.getWidth(), size.getHeight()),
                Math.min(size.getWidth(), size.getHeight()),
                mCameraID == CameraSelector.LENS_FACING_FRONT
        );

        mVideoUri = Uri.parse(path);
        mRecording = mVideoCapture.getOutput()
                .prepareRecording(getContext(), new FileOutputOptions.Builder(new File(path))
                        .setDurationLimitMillis(MAX_VIDEO_MILLIS)
                        .setFileSizeLimit(MAX_VIDEO_BYTES)
                        .build())
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(getContext()), event -> {
                    if (event instanceof VideoRecordEvent.Start) {
                        onRecordingStarted();
                    } else if (event instanceof VideoRecordEvent.Finalize) {
                        onRecordingFinished((VideoRecordEvent.Finalize) event);
                    }
                });
    }

    private void stopRecording() {
        mVideoProcessing = true;
        mRecording.stop();
    }

    private void onRecordingStarted() {
        if (getContext() == null || mDiscardRecording) return;

        hideCameraButtons(true);

        mRecordProgress.setMax(1000);
        mRecordProgress.setVisibility(View.VISIBLE);
        mProgressAnimator =
                ObjectAnimator.ofInt(mRecordProgress, "progress", 0, 1000);

        mProgressAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if (animation.getCurrentPlayTime() >= MIN_VIDEO_MILLIS) {
                    mProgressAnimator.removeAllUpdateListeners();
                    mRecordProgress.setProgressDrawable(
                            ContextCompat.getDrawable(getContext(), R.drawable.camera_progress));
                }
            }
        });

        mProgressAnimator.setDuration(MAX_VIDEO_MILLIS);
        mProgressAnimator.setInterpolator(new LinearInterpolator());
        mProgressAnimator.start();

        mTakePhotoBtn.setImageResource(R.drawable.square_camera_record);
    }

    private void onRecordingFinished(VideoRecordEvent.Finalize event) {
        if (mDiscardRecording) {
            mDiscardRecording = false;
            return;
        }
        if (getContext() == null) return;

        resetRecordingViews();

        if (mFlashTop.getVisibility() == View.VISIBLE)
            fadeInFlashForgreound(false);

        if (mCamera != null) mCamera.getCameraControl().enableTorch(false);

        mIsRecording = false;
        mVideoProcessing = false;

        int error = event.getError();
        boolean saved = error == VideoRecordEvent.Finalize.ERROR_NONE
                || error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED
                || error == VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED;
        long duration = TimeUnit.NANOSECONDS.toMillis(event.getRecordingStats().getRecordedDurationNanos());

        if (!saved) Log.d(TAG, "Recording failed with error " + error, event.getCause());

        if (saved && duration >= MIN_VIDEO_MILLIS) {  //go to edit video screen
            goToVideoEditFragment(mVideoUri, mVideoDimen);
        } else {
            takePicture();
        }
    }

    private void resetRecordingViews() {
        if (mProgressAnimator != null) {
            mProgressAnimator.removeAllListeners();
            mProgressAnimator.cancel();
        }

        mRecordProgress.setVisibility(View.INVISIBLE);
        mRecordProgress.setProgress(0);
        mRecordProgress.setProgressDrawable(ContextCompat.getDrawable(getContext(), R.drawable.camera_progress_red));

        mTakePhotoBtn.setImageResource(R.drawable.square_camera_capture);

        hideCameraButtons(false);

        if (mToolbar != null) {
            mToolbar.setTitle("Camera");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //delete the cached video if  we can. Takes up a lot of space
        ImageUtility.deleteCachedVideo(mVideoUri);
    }
}
