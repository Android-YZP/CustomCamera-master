package com.gxj1228.customcamera.camera;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import com.gxj1228.customcamera.R;
import com.gxj1228.customcamera.util.FileUtil;

/**
 * Created by gxj on 2018/2/18 11:46.
 * 拍照界面
 */
public class CameraActivity extends Activity implements View.OnClickListener {



    public final static int REQUEST_CODE = 0X13;

    private CustomCameraPreview customCameraPreview;
    private View containerView;

    private ImageView mCrop;
    public final int MAG_TAKE_PHOTO = 101;

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MAG_TAKE_PHOTO:
                    takePhoto();
                    break;
            }
        }
    };
    private ImageView imageViewTake;


    /**
     * 跳转到拍照页面
     */
    public static void navToCamera(Context context) {
        Intent intent = new Intent(context, CameraActivity.class);
        ((Activity) context).startActivityForResult(intent, REQUEST_CODE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        customCameraPreview = findViewById(R.id.camera_surface);
        mCrop =  findViewById(R.id.crop);
        imageViewTake = findViewById(R.id.camera_take);
        containerView = findViewById(R.id.camera_crop_container);


        float screenMinSize = Math.min(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels);
        float height = (float) (screenMinSize * 0.75) / 2;
        float width = height * 2;

        RelativeLayout.LayoutParams linearParams = (RelativeLayout.LayoutParams) mCrop.getLayoutParams(); //取控件textView当前的布局参数 linearParams.height = 20;// 控件的高强制设成20
        linearParams.width = (int) width;// 控件的宽强制设成30
        linearParams.height = (int) height;// 控件的宽强制设成30
        mCrop.setLayoutParams(linearParams); //使设置好的布局参数应用到控件

        //相机预览界面设置
        float maxSize = screenMinSize / 1.0f * 1.0f;
        RelativeLayout.LayoutParams layoutParams;
        layoutParams = new RelativeLayout.LayoutParams((int) maxSize, (int) screenMinSize);
        layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        customCameraPreview.setLayoutParams(layoutParams);
        customCameraPreview.setOnClickListener(this);
        imageViewTake.setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.camera_surface:
                customCameraPreview.focus();
                break;
            case R.id.camera_close:
                finish();
                break;
            case R.id.camera_take:
                takePhoto();
        }
    }

    private void takePhoto() {
        customCameraPreview.takePhoto(new Camera.PictureCallback() {
            public void onPictureTaken(final byte[] data, final Camera camera) {
                //子线程处理图片，防止ANR
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Bitmap bitmap = null;
                        if (data != null) {
                            bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                            camera.startPreview();
                        }
                        if (bitmap != null) {
                            Bitmap resBitmap = Bitmap.createBitmap(bitmap,
                                    bitmap.getWidth() / 8,
                                    bitmap.getHeight() * 5 / 16,
                                    bitmap.getWidth() * 3 / 4,
                                    bitmap.getWidth() * 3 / 8);
                            String path = FileUtil.saveBitmap(resBitmap);
                            if (!bitmap.isRecycled()) {
                                bitmap.recycle();
                            }
                            if (!resBitmap.isRecycled()) {
                                resBitmap.recycle();
                            }
                            Log.e("=======>", path + "");
                            Message msg = new Message();
                            msg.what = 101;
                            mHandler.sendMessage(msg);
                        }
                        return;
                    }
                }).start();
            }
        });
    }
}
