package com.linute.linute.ProfileCamera;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.google.common.util.concurrent.ListenableFuture;
import com.linute.linute.R;
import com.linute.linute.SquareCamera.CameraPreviewLayout;
import com.linute.linute.SquareCamera.CameraSettingPreferences;
import com.linute.linute.SquareCamera.ImageUtility;
import com.linute.linute.UtilsAndHelpers.StoragePermissions;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Created by QiFeng on 7/5/16.
 */
public class ProfilePictureCamera extends Fragment {

    public static final String TAG = ProfilePictureCamera.class.getSimpleName();
    public static final String CAMERA_ID_KEY = "camera_id";
    public static final String CAMERA_FLASH_KEY = "flash_on";

    //largest 4:3 photo to ask for
    private static final Size MAX_PICTURE_SIZE = new Size(3000, 2250);

    private ProcessCameraProvider mCameraProvider;
    private Camera mCamera;
    private ImageCapture mImageCapture;

    private int mCameraID;
    private boolean mFlashOn;

    private Disposable mProcessImageSubscriptions;

    //private View vFocusCircle;
    private View vFlashContainer;
    private ImageView vFlashIcon;
    private View vReverseCamera;
    private CameraPreviewLayout vPreviewView;

    private boolean mIsSafeToTakePhoto = false;


    public ProfilePictureCamera() {

    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            mCameraID = getFrontCameraID();
            mFlashOn = CameraSettingPreferences.getCameraFlashMode(getActivity());
        } else {
            mCameraID = savedInstanceState.getInt(CAMERA_ID_KEY);
            mFlashOn = savedInstanceState.getBoolean(CAMERA_FLASH_KEY);
        }
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

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_take_profile_picture, container, false);

        vFlashContainer = root.findViewById(R.id.flash_container);
        vFlashIcon = (ImageView) vFlashContainer.findViewById(R.id.flash_icon);
        vPreviewView = (CameraPreviewLayout) root.findViewById(R.id.camera_preview_view);

        Toolbar toolbar = (Toolbar) root.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getActivity() != null)
                    getActivity().setResult(Activity.RESULT_CANCELED);
                getActivity().finish();
            }
        });

        vReverseCamera = root.findViewById(R.id.reverse_container);
        vReverseCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCameraID == CameraSelector.LENS_FACING_FRONT) {
                    mCameraID = getBackCameraID();
                } else {
                    mCameraID = getFrontCameraID();
                }
                bindCamera();
            }
        });

        root.findViewById(R.id.capture_image_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });

        vFlashContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsSafeToTakePhoto && toggleFlash()){
                    setupFlashMode();
                }
            }
        });

        return root;
    }


    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        vPreviewView.getPreviewStreamState().observe(getViewLifecycleOwner(),
                state -> mIsSafeToTakePhoto = state == PreviewView.StreamState.STREAMING);

        ListenableFuture<ProcessCameraProvider> provider = ProcessCameraProvider.getInstance(requireContext());
        provider.addListener(() -> {
            try {
                mCameraProvider = provider.get();
            } catch (ExecutionException | InterruptedException e) {
                Log.d(TAG, "Can't start CameraX", e);
                return;
            }
            if (getView() == null) return;
            if (mCameraProvider.getAvailableCameraInfos().size() < 2) {
                vReverseCamera.setVisibility(View.INVISIBLE);
            }
            bindCamera();
        }, ContextCompat.getMainExecutor(requireContext()));
    }


    @Override
    public void onResume() {
        super.onResume();
        if (mCamera == null) {
            bindCamera();
        } else {
            mIsSafeToTakePhoto = vPreviewView.getPreviewStreamState().getValue() == PreviewView.StreamState.STREAMING;
        }
    }


    @Override
    public void onPause() {
        super.onPause();

        if (mProcessImageSubscriptions != null) mProcessImageSubscriptions.dispose();

        CameraSettingPreferences.saveCameraFlashMode(getActivity(), mFlashOn);
    }


    // we do not have an auto flash
    // some phones crash if both autofocus and flash are on. There is no way to tell from autoflash is
    // flash is going to go off.
    private void setupFlashMode() {
        if (getContext() != null) {
            if (mFlashOn) vFlashIcon.setColorFilter(Color.YELLOW, PorterDuff.Mode.MULTIPLY);
            else vFlashIcon.clearColorFilter();
        }
    }

    /**
     * Take a picture
     */
    private void takePicture() {
        if (mIsSafeToTakePhoto && mImageCapture != null) {
            mIsSafeToTakePhoto = false;

            mImageCapture.takePicture(ContextCompat.getMainExecutor(requireContext()),
                    new ImageCapture.OnImageCapturedCallback() {
                        @Override
                        public void onCaptureStarted() {
                            playShutterSound();
                        }

                        @Override
                        public void onCaptureSuccess(@NonNull ImageProxy image) {
                            onPictureTaken(image);
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException e) {
                            Log.d(TAG, "Can't take picture", e);
                            mIsSafeToTakePhoto = true;
                        }
                    });
        }
    }

    //Permissions
    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(getActivity(), permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasCameraAndWritePermission() {
        return StoragePermissions.canWrite(getActivity())
                && hasPermission(Manifest.permission.CAMERA);
    }

    /**
     * Start the camera preview, replacing the one that's showing
     */
    private void bindCamera() {
        if (mCameraProvider == null || getView() == null || !hasCameraAndWritePermission()) return;

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(vPreviewView.getSurfaceProvider());

        mImageCapture = new ImageCapture.Builder()
                .setResolutionSelector(new ResolutionSelector.Builder()
                        .setResolutionStrategy(new ResolutionStrategy(MAX_PICTURE_SIZE,
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                        .build())
                .build();

        CameraSelector selector = new CameraSelector.Builder().requireLensFacing(mCameraID).build();

        mCameraProvider.unbindAll();
        try {
            mCamera = mCameraProvider.bindToLifecycle(getViewLifecycleOwner(), selector, preview, mImageCapture);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "Can't open camera with id " + mCameraID, e);
            return;
        }
        vPreviewView.setCamera(mCamera);

        if (mCamera.getCameraInfo().hasFlashUnit()) {
            vFlashContainer.setVisibility(View.VISIBLE);
            setupFlashMode();
        } else {
            vFlashContainer.setVisibility(View.INVISIBLE);
            mFlashOn = false;
        }
        mImageCapture.setFlashMode(flashMode());
    }

    private int flashMode() {
        return mFlashOn ? ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_OFF;
    }

    private boolean isFrontFacingCamera() {
        return mCameraID == CameraSelector.LENS_FACING_FRONT;
    }

    private void onPictureTaken(ImageProxy data) {
        if (getActivity() == null) {
            data.close();
            return;
        }

        mProcessImageSubscriptions =
                Observable.just(Optional.ofNullable(ImageUtility.savePicture(getActivity(), rotateImage(data))))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Consumer<Optional<Uri>>() {
                            @Override
                            public void accept(Optional<Uri> uri) {
                                ProfileCameraActivity activity = (ProfileCameraActivity) getActivity();
                                if (activity != null){
                                    activity.replaceFragment(ConfirmProfilePicture.newInstance(uri.orElse(null)));
                                }
                            }
                        });
    }

    /**
     * Turns the image upright, mirrors it if it's a selfie so it matches the preview, and keeps the
     * top square, which is the part that was visible above the buttons.
     */
    private Bitmap rotateImage(ImageProxy data) {
        Bitmap image = data.toBitmap();
        Matrix mat = new Matrix();
        mat.setRotate(data.getImageInfo().getRotationDegrees());
        data.close();

        if (isFrontFacingCamera()) mat.postScale(-1, 1);

        image = Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), mat, true);
        int size = Math.min(image.getWidth(), image.getHeight());
        return Bitmap.createBitmap(image, 0, 0, size, size);
    }

    private void playShutterSound() {
        if (getActivity() == null) return;
        AudioManager mgr = (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);
        mgr.playSoundEffect(AudioManager.FLAG_PLAY_SOUND);
    }


    private boolean toggleFlash(){

        if (mImageCapture == null) return false;

        mFlashOn = !mFlashOn;
        mImageCapture.setFlashMode(flashMode());
        return true;
    }
}
