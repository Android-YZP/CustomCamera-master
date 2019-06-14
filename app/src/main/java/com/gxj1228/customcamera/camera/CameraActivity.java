package com.gxj1228.customcamera.camera;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.gxj1228.customcamera.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;


/**
 * Created by gxj on 2018/2/18 11:46.
 * 拍照界面
 */
public class CameraActivity extends Activity implements View.OnClickListener {


    public final static int REQUEST_CODE = 0X13;

    private CustomCameraPreview customCameraPreview;
    private View containerView;

    private ImageView mCrop;
    public static final int MAG_TAKE_PHOTO = 101;
    public static final int CODE_SUCCESS = 100;

    private ImageView imageViewTake;
    private TextView mTvRe;
    private String mCodeRe;

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MAG_TAKE_PHOTO:
                    mTvRe.setText("");
                    takePhoto();
                    break;
                case CODE_SUCCESS:
                    mTvRe.setText(mCodeRe);
                    break;
            }
        }
    };


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
        mCrop = findViewById(R.id.crop);
        imageViewTake = findViewById(R.id.camera_take);
        mTvRe = findViewById(R.id.tv_re);
        containerView = findViewById(R.id.camera_crop_container);


        float screenMinSize = Math.min(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels);
        float height = (float) (screenMinSize * 0.75) / 2;
        float width = height * 2;

        LinearLayout.LayoutParams linearParams = (LinearLayout.LayoutParams) mCrop.getLayoutParams(); //取控件textView当前的布局参数 linearParams.height = 20;// 控件的高强制设成20
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

                            mCodeRe = qrCode(resBitmap);
                            if (!mCodeRe.isEmpty()) {
                                Log.e("=======>", mCodeRe + "识别成功");

                                Message msg = new Message();
                                msg.what = CODE_SUCCESS;
                                mHandler.sendMessage(msg);

                                saveBitmap(resBitmap);
                                if (!bitmap.isRecycled()) {
                                    bitmap.recycle();
                                }
                                if (!resBitmap.isRecycled()) {
                                    resBitmap.recycle();
                                }
                            } else {
                                Log.e("=======>", mCodeRe + "识别失败");
                                Message msg = new Message();
                                msg.what = MAG_TAKE_PHOTO;
                                mHandler.sendMessage(msg);
                            }

                        }
                        return;
                    }
                }).start();
            }
        });
    }

    /**
     * 识别bitmap中的二维码
     */
    public String qrCode(Bitmap obmp) {
        //图片质量压缩之后再送给Zxing识别,提高识别率
        Matrix matrix = new Matrix();
        matrix.setScale(0.28f, 0.28f);
        Bitmap obmp1 = Bitmap.createBitmap(obmp, 0, 0, obmp.getWidth(),
                obmp.getHeight(), matrix, true);

        int width = obmp1.getWidth();
        int height = obmp1.getHeight();
        int[] data = new int[width * height];
        obmp1.getPixels(data, 0, width, 0, 0, width, height);
        RGBLuminanceSource source = new RGBLuminanceSource(width, height, data);
        BinaryBitmap bitmap1 = new BinaryBitmap(new HybridBinarizer(source));
        MultiFormatReader reader = new MultiFormatReader();
        Result re = null;
        try {
            re = reader.decode(bitmap1);
        } catch (NotFoundException e) {
            e.printStackTrace();
        }
        if (re == null) {
            return "";
        } else {
            return re.getText();
        }
    }


    private final String SD_PATH = Environment.getExternalStorageDirectory().getPath() + "/OA头像/";

    public void saveBitmap(Bitmap bmp) {
        String savePath = "";
        String fileName = SystemClock.currentThreadTimeMillis() + ".JPEG";
        if (Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED)) {
            savePath = SD_PATH;
        }
        File filePic = new File(savePath + fileName);
        try {
            if (!filePic.exists()) {
                filePic.getParentFile().mkdirs();
                filePic.createNewFile();
            }
            FileOutputStream fos = new FileOutputStream(filePic);
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();
//            Toast.makeText(CameraActivity.this, "保存成功,位置:" + filePic.getAbsolutePath(), Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        // 其次把文件插入到系统图库
        try {
            MediaStore.Images.Media.insertImage(getContentResolver(),
                    filePic.getAbsolutePath(), fileName, null);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
