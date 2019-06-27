package com.gxj1228.customcamera.camera;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
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
    private int mTimes = 0;

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


//                                saveBmp(bitmap,"bitmap");
                                saveBmp(resBitmap,"图片"+System.currentTimeMillis());
//                                bytesToImageFile(data);

                                if (!bitmap.isRecycled()) {
                                    bitmap.recycle();
                                }
                                if (!resBitmap.isRecycled()) {
                                    resBitmap.recycle();
                                }

//                                mTimes++;
//                                if (mTimes <= 9) {
//                                    takePhoto();
//                                }

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
        //图片质量压缩之后再送给Zxing识别,调整识别率
        Matrix matrix = new Matrix();
        matrix.setScale(0.30f, 0.30f);
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
        String fileName = SystemClock.currentThreadTimeMillis() + ".bmp";
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


//
//    /**
//     * 将彩色图转换为黑白图
//     *
//     * @param 位图
//     * @return 返回转换好的位图
//     */
//    public static Bitmap convertToBlackWhite(Bitmap bmp) {
//        int width = bmp.getWidth(); // 获取位图的宽
//        int height = bmp.getHeight(); // 获取位图的高
//        int[] pixels = new int[width * height]; // 通过位图的大小创建像素点数组
//
//        bmp.getPixels(pixels, 0, width, 0, 0, width, height);
//        int alpha = 0xFF << 24;
//        for (int i = 0; i < height; i++) {
//            for (int j = 0; j < width; j++) {
//                int grey = pixels[width * i + j];
//
//                int red = ((grey & 0x00FF0000) >> 16);
//                int green = ((grey & 0x0000FF00) >> 8);
//                int blue = (grey & 0x000000FF);
//
//                grey = (int) (red * 0.3 + green * 0.59 + blue * 0.11);
//                grey = alpha | (grey << 16) | (grey << 8) | grey;
//                pixels[width * i + j] = grey;
//            }
//        }
//        Bitmap newBmp = Bitmap.createBitmap(width, height, Config.RGB_565);
//
//        newBmp.setPixels(pixels, 0, width, 0, 0, width, height);
//
//        Bitmap resizeBmp = ThumbnailUtils.extractThumbnail(newBmp, 380, 460);
//        return resizeBmp;
//    }


    private void bytesToImageFile(byte[] bytes) {
        try {
            File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/aaa.bmp");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(bytes, 0, bytes.length);
            fos.flush();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 对图片进行灰度化处理
     * @return 灰度化图片
     */
    public static Bitmap getGrayBitmap(Bitmap bm) {
        Bitmap bitmap = null;
        //获取图片的宽和高
        int width = bm.getWidth();
        int height = bm.getHeight();
        //创建灰度图片
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        //创建画布
        Canvas canvas = new Canvas(bitmap);
        //创建画笔
        Paint paint = new Paint();
        //创建颜色矩阵
        ColorMatrix matrix = new ColorMatrix();
        //设置颜色矩阵的饱和度:0代表灰色,1表示原图
        matrix.setSaturation(0);
        //颜色过滤器
        ColorMatrixColorFilter cmcf = new ColorMatrixColorFilter(matrix);
        //设置画笔颜色过滤器
        paint.setColorFilter(cmcf);
        //画图
        canvas.drawBitmap(bm, 0, 0, paint);
        return bitmap;

    }


    /**
     * 将Bitmap存为 .bmp格式图片
     *
     * @param bitmap
     */
    private void saveBmp(Bitmap bitmap, String name) {
        if (bitmap == null)
            return;
        // 位图大小
        int nBmpWidth = bitmap.getWidth();
        int nBmpHeight = bitmap.getHeight();
        // 图像数据大小
        int bufferSize = nBmpHeight * (nBmpWidth * 3 + nBmpWidth % 4);
        try {
            // 存储文件名
            String filename = "/sdcard/" + name + ".bmp";
            File file = new File(filename);
            if (!file.exists()) {
                file.createNewFile();
            }
            FileOutputStream fileos = new FileOutputStream(filename);
            // bmp文件头
            int bfType = 0x4d42;
            long bfSize = 14 + 40 + bufferSize;
            int bfReserved1 = 0;
            int bfReserved2 = 0;
            long bfOffBits = 14 + 40;
            // 保存bmp文件头
            writeWord(fileos, bfType);
            writeDword(fileos, bfSize);
            writeWord(fileos, bfReserved1);
            writeWord(fileos, bfReserved2);
            writeDword(fileos, bfOffBits);
            // bmp信息头
            long biSize = 40L;
            long biWidth = nBmpWidth;
            long biHeight = nBmpHeight;
            int biPlanes = 1;
            int biBitCount = 24;
            long biCompression = 0L;
            long biSizeImage = 0L;
            long biXpelsPerMeter = 0L;
            long biYPelsPerMeter = 0L;
            long biClrUsed = 0L;
            long biClrImportant = 0L;
            // 保存bmp信息头
            writeDword(fileos, biSize);
            writeLong(fileos, biWidth);
            writeLong(fileos, biHeight);
            writeWord(fileos, biPlanes);
            writeWord(fileos, biBitCount);
            writeDword(fileos, biCompression);
            writeDword(fileos, biSizeImage);
            writeLong(fileos, biXpelsPerMeter);
            writeLong(fileos, biYPelsPerMeter);
            writeDword(fileos, biClrUsed);
            writeDword(fileos, biClrImportant);
            // 像素扫描
            byte bmpData[] = new byte[bufferSize];
            int wWidth = (nBmpWidth * 3 + nBmpWidth % 4);
            for (int nCol = 0, nRealCol = nBmpHeight - 1; nCol < nBmpHeight; ++nCol, --nRealCol)
                for (int wRow = 0, wByteIdex = 0; wRow < nBmpWidth; wRow++, wByteIdex += 3) {
                    int clr = bitmap.getPixel(wRow, nCol);
                    bmpData[nRealCol * wWidth + wByteIdex] = (byte) Color.blue(clr);
                    bmpData[nRealCol * wWidth + wByteIdex + 1] = (byte) Color.green(clr);
                    bmpData[nRealCol * wWidth + wByteIdex + 2] = (byte) Color.red(clr);
                }

            fileos.write(bmpData);
            fileos.flush();
            fileos.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void writeWord(FileOutputStream stream, int value) throws IOException {
        byte[] b = new byte[2];
        b[0] = (byte) (value & 0xff);
        b[1] = (byte) (value >> 8 & 0xff);
        stream.write(b);
    }

    protected void writeDword(FileOutputStream stream, long value) throws IOException {
        byte[] b = new byte[4];
        b[0] = (byte) (value & 0xff);
        b[1] = (byte) (value >> 8 & 0xff);
        b[2] = (byte) (value >> 16 & 0xff);
        b[3] = (byte) (value >> 24 & 0xff);
        stream.write(b);
    }

    protected void writeLong(FileOutputStream stream, long value) throws IOException {
        byte[] b = new byte[4];
        b[0] = (byte) (value & 0xff);
        b[1] = (byte) (value >> 8 & 0xff);
        b[2] = (byte) (value >> 16 & 0xff);
        b[3] = (byte) (value >> 24 & 0xff);
        stream.write(b);
    }
}
