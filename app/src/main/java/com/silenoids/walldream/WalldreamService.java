package com.silenoids.walldream;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ConfigurationInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.opengl.GLSurfaceView;
import android.opengl.GLSurfaceView.Renderer;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.service.wallpaper.WallpaperService;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;

import androidx.annotation.RequiresApi;

import com.silenoids.walldream.helper.ObjectFactory;
import com.silenoids.walldream.rendering.CubeRenderer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class WalldreamService extends WallpaperService {

    private static final String TAG = WallpaperService.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();
        ObjectFactory objectFactory = ObjectFactory.getInstance();
        objectFactory.initialize(getApplicationContext());
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    @Override
    public Engine onCreateEngine() {
        return new CubeAdvancedImageGLEngine();
    }

    private abstract class CustomWallpaperEngine extends Engine {
        SurfaceHolder surfaceHolder;
        Handler handler = new Handler();
        boolean visible;
        float xScreenOffset = 0f;
        float yScreenOffset = 0f;

        Runnable drawingRunnable = new Runnable() {
            @Override
            public void run() {
                draw();
            }
        };

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            this.surfaceHolder = surfaceHolder;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            if (visible) {
                handler.post(drawingRunnable);
            } else {
                handler.removeCallbacks(drawingRunnable);
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            handler.removeCallbacks(drawingRunnable);
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset);
            xScreenOffset = xOffset;
            yScreenOffset = yOffset;
        }

        abstract void draw();
    }

    private abstract class CustomGLWallpaperEngine extends Engine {

        //Inner class che conterrà la view
        class WallpaperGLSurfaceView extends GLSurfaceView {

            public WallpaperGLSurfaceView(Context context) {
                super(context);
            }

            @Override
            public SurfaceHolder getHolder() {
                return getSurfaceHolder();
            }

            public void onDestroy() {
                super.onDetachedFromWindow();
            }
        }

        //Contesto applicativo
        SurfaceHolder surfaceHolder;
        Handler handler = new Handler();
        boolean visible;
        //OpenGL
        private WallpaperGLSurfaceView glSurfaceView;
        private boolean rendererHasBeenSet;
        //Dati con cui interagire
        float xScreenOffset = 0f;
        float yScreenOffset = 0f;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            this.surfaceHolder = surfaceHolder;
            glSurfaceView = new WallpaperGLSurfaceView(WalldreamService.this);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            if (rendererHasBeenSet && visible) {
                glSurfaceView.onResume();
            } else {
                glSurfaceView.onPause();
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            glSurfaceView.onDestroy();
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset);
            xScreenOffset = xOffset;
            yScreenOffset = yOffset;
        }

        void setRenderer(Renderer renderer) {
            glSurfaceView.setRenderer(renderer);
            rendererHasBeenSet = true;
        }

        void setEGLContextClientVersion(int version) {
            glSurfaceView.setEGLContextClientVersion(version);
        }

        void setPreserveEGLContextOnPause(boolean preserve) {
            glSurfaceView.setPreserveEGLContextOnPause(preserve);
        }

        abstract Renderer getNewRenderer();
    }

    private class AnimatedImageEngine extends CustomWallpaperEngine {

        SharedPreferences preferences;
        Drawable animatedImageDrawable;

        @RequiresApi(api = Build.VERSION_CODES.P)
        AnimatedImageEngine() {
            try {

                //Caricamento immagini
                AssetManager assets = getApplicationContext().getAssets();
                ImageDecoder.Source source = ImageDecoder.createSource(assets, "Animated/image.gif");
                animatedImageDrawable = ImageDecoder.decodeDrawable(source);

                //Caricamento preferences
                preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.P)
        @Override
        void draw() {
            if (visible) {
                if (animatedImageDrawable instanceof AnimatedImageDrawable) {
                    ((AnimatedImageDrawable) animatedImageDrawable).start();
                }
                Canvas canvas = surfaceHolder.lockCanvas();
                canvas.save();

                if (preferences.getBoolean("scale_image", false)) canvas.scale(2f, 4f);
                else canvas.scale(1f, 1f);

                //animatedImageDrawable.setBounds();

                animatedImageDrawable.draw(canvas);
                Paint paint = new Paint();
                paint.setTextSize(40f);
                paint.setColor(Color.GREEN);
                canvas.drawText(String.valueOf(xScreenOffset), 250, 250, paint);
                canvas.restore();
                surfaceHolder.unlockCanvasAndPost(canvas);
                handler.removeCallbacks(drawingRunnable);
                handler.postDelayed(drawingRunnable, 20);
            }
        }
    }

    private class ParallaxImageEngine extends CustomWallpaperEngine {

        SharedPreferences preferences;
        List<Bitmap> images = new ArrayList<>();
        float scaleMultiplier = 1f;

        ParallaxImageEngine() {
            try {

                //Caricamento immagini
                //Le immagini devono essere della stessa risoluzione
                images.add(BitmapFactory.decodeStream(getAssets().open("Parallax/0.png")));
                images.add(BitmapFactory.decodeStream(getAssets().open("Parallax/1.png")));
                images.add(BitmapFactory.decodeStream(getAssets().open("Parallax/2.png")));
                images.add(BitmapFactory.decodeStream(getAssets().open("Parallax/3.png")));

                scaleMultiplier = calculateScaleScreen(images.get(0));
                Log.i("MAMMT", "fattore di scala: " + scaleMultiplier);

                images.set(0, Bitmap.createScaledBitmap(images.get(0), (int) (images.get(0).getWidth() * scaleMultiplier), (int) (images.get(0).getHeight() * scaleMultiplier), false));
                images.set(1, Bitmap.createScaledBitmap(images.get(1), (int) (images.get(1).getWidth() * scaleMultiplier), (int) (images.get(1).getHeight() * scaleMultiplier), false));
                images.set(2, Bitmap.createScaledBitmap(images.get(2), (int) (images.get(2).getWidth() * scaleMultiplier), (int) (images.get(2).getHeight() * scaleMultiplier), false));
                images.set(3, Bitmap.createScaledBitmap(images.get(3), (int) (images.get(3).getWidth() * scaleMultiplier), (int) (images.get(3).getHeight() * scaleMultiplier), false));

                //Caricamento preferences
                preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private float calculateScaleScreen(Bitmap image) {
            DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
            return (image.getWidth() > image.getHeight()) ? (float) (metrics.heightPixels / image.getHeight()) : (float) (metrics.widthPixels / image.getWidth());
        }

        @RequiresApi(api = Build.VERSION_CODES.P)
        @Override
        void draw() {
            if (visible) {
                Canvas canvas = surfaceHolder.lockCanvas();
                canvas.save();

                //canvas.scale(scaleMultiplier,scaleMultiplier);

                canvas.drawBitmap(images.get(0), 0f, 0f, null);
                canvas.drawBitmap(images.get(1), -xScreenOffset * 200, 0f, null);
                canvas.drawBitmap(images.get(2), -xScreenOffset * 500, 0f, null);
                canvas.drawBitmap(images.get(3), -xScreenOffset * 1000, 0f, null);


                Paint paint = new Paint();
                paint.setTextSize(40f);
                paint.setColor(Color.GREEN);
                canvas.drawText(String.valueOf(xScreenOffset), 250, 250, paint);

                canvas.restore();

                surfaceHolder.unlockCanvasAndPost(canvas);
                handler.removeCallbacks(drawingRunnable);
                handler.postDelayed(drawingRunnable, 20);
            }
        }
    }

    private class CubeAdvancedImageGLEngine extends CustomGLWallpaperEngine {

        SharedPreferences preferences;
        List<Bitmap> images = new ArrayList<>();
        float scaleMultiplier = 1f;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);

            //Caricamento preferences
            preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

            final ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            final ConfigurationInfo configurationInfo = activityManager.getDeviceConfigurationInfo();
            final boolean supportsEs2 = configurationInfo.reqGlEsVersion >= 0x20000;

            if (supportsEs2) {
                Log.i(TAG, "OpenGL 2 supportate!");
                setEGLContextClientVersion(2); //Request an OpenGL ES 2.0 compatible context.
                setPreserveEGLContextOnPause(true);//On Honeycomb+ devices, this improves the performance
                setRenderer(getNewRenderer()); // Set the renderer to our user-defined renderer.
            }
        }

        private float calculateScaleScreen(Bitmap image) {
            DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
            return (image.getWidth() > image.getHeight()) ? (float) (metrics.heightPixels / image.getHeight()) : (float) (metrics.widthPixels / image.getWidth());
        }

        Renderer getNewRenderer() {
            return new CubeRenderer();
        }
    }
}
