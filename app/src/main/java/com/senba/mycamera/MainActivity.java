package com.senba.mycamera;


import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Camera;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;

import java.util.Arrays;
import java.util.concurrent.Semaphore;

public class MainActivity extends AppCompatActivity {
    private static final int STATE_PREVIEW         = 1;
    private static final int STATE_WAITING_CAPTURE = 2;

    private SurfaceView mSurfaceView;
    private Camera                               mCamera;
    private int                                  mPreViewSize;
    private String                               mCameraID;
    private CameraManager                        mCameraManager;
    private SurfaceHolder                        mSurfaceHolder;
    private Handler                              mHandler;
    private String                               mCameraId;
    private ImageReader                          mImageReader;
    private ImageReader.OnImageAvailableListener mOnImageAvailableListener;
    private CaptureRequest.Builder               mPreviewBuilder;
    private ImageView mPhoto;

    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    ///为了使照片竖直显示
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private CameraCaptureSession mCameraCaptureSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSurfaceView = findViewById(R.id.surfaceview);
        mPhoto = findViewById(R.id.img);

        mCameraManager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        mSurfaceView = (SurfaceView)findViewById(R.id.surfaceview);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                initCameraAndPreview();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });
    }

    @SuppressLint("MissingPermission")
    private void initCameraAndPreview() {
        Log.d("linc","init camera and preview");
        HandlerThread handlerThread = new HandlerThread("Camera2");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());
        try {
            mCameraId = ""+ CameraCharacteristics.LENS_FACING_FRONT;
            mImageReader = ImageReader.newInstance(mSurfaceView.getWidth(), mSurfaceView.getHeight(),
                    ImageFormat.JPEG,/*maxImages*/7);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mHandler);

            mCameraManager.openCamera(mCameraId, DeviceStateCallback, mHandler);


        } catch (CameraAccessException e) {
            Log.e("linc", "open camera failed." + e.getMessage());
        }
    }


    private CameraDevice                   mCameraDevice;

    private CameraDevice.StateCallback DeviceStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice camera) {
            Log.d("linc","DeviceStateCallback:camera was opend.");
            mCameraOpenCloseLock.release();
            mCameraDevice = camera;
            try {
                createCameraCaptureSession();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {

        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {

        }
    };

    private void createCameraCaptureSession() throws CameraAccessException {
        Log.d("linc","createCameraCaptureSession");

        mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        mPreviewBuilder.addTarget(mSurfaceHolder.getSurface());
        mState = STATE_PREVIEW;
        mCameraDevice.createCaptureSession(
                Arrays.asList(mSurfaceHolder.getSurface(), mImageReader.getSurface()),
                mSessionPreviewStateCallback, mHandler);
    }

    private CameraCaptureSession mSession;
    private CameraCaptureSession.StateCallback mSessionPreviewStateCallback = new
            CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(CameraCaptureSession session) {
                    Log.d("linc","mSessionPreviewStateCallback onConfigured");
                    mSession = session;
                    try {
                        mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                        session.setRepeatingRequest(mPreviewBuilder.build(), mSessionCaptureCallback, mHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                        Log.e("linc","set preview builder failed."+e.getMessage());
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            };

    private int mState;
    private CameraCaptureSession.CaptureCallback mSessionCaptureCallback =
            new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                               TotalCaptureResult result) {
                    //            Log.d("linc","mSessionCaptureCallback, onCaptureCompleted");
                    mSession = session;
                    checkState(result);
                }

                @Override
                public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request,
                                                CaptureResult partialResult) {
                    Log.d("linc","mSessionCaptureCallback,  onCaptureProgressed");
                    mSession = session;
                    checkState(partialResult);
                }

                private void checkState(CaptureResult result) {
                    switch (mState) {
                        case STATE_PREVIEW:
                            // NOTHING
                            break;
                        case STATE_WAITING_CAPTURE:
                            int afState = result.get(CaptureResult.CONTROL_AF_STATE);

                            if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                                    CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState
                                    ||  CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED == afState
                                    || CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED == afState) {
                                //do something like save picture
                            }
                            break;
                    }
                }

            };

    public void paizhao(View v){
        if (mCameraDevice == null) return;
        // 创建拍照需要的CaptureRequest.Builder
        final CaptureRequest.Builder captureRequestBuilder;
        try {
            captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            // 将imageReader的surface作为CaptureRequest.Builder的目标
            captureRequestBuilder.addTarget(mImageReader.getSurface());
            // 自动对焦
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // 自动曝光
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            // 获取手机方向
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            // 根据设备方向计算设置照片的方向
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            //拍照
            CaptureRequest mCaptureRequest = captureRequestBuilder.build();

            mSession.capture(mCaptureRequest, null, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

}
