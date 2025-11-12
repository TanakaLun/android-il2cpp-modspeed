package com.example.testxp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Main implements IXposedHookLoadPackage {
    
    static {
        System.loadLibrary("Yuri");
    }
    
    public static native void setTimeScale(float scale);
    
    private float timeScale = 1.0f;
    private View floatingView = null;
    private WindowManager windowManager = null;
    private int screenWidth = 0;
    private int screenHeight = 0;
    private boolean isFloatingWindowCreated = false;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedBridge.log("Yuri: Loading for package: " + lpparam.packageName);
        }
        
        try {
            XposedHelpers.findAndHookMethod("android.app.Activity", lpparam.classLoader, 
                "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (isFloatingWindowCreated) {
                        return;
                    }
                    
                    Activity activity = (Activity) param.thisObject;
                    XposedBridge.log("Yuri: Activity resumed: " + activity.getClass().getName());
  
                    getScreenSize(activity);
                    
                    createInAppFloatingWindow(activity);
                    
                    isFloatingWindowCreated = true;
                }
            });
            
        } catch (Throwable e) {
            XposedBridge.log("Yuri Hook Error: " + e.getMessage());
        }
    }
    
    private void getScreenSize(Context context) {
        try {
            windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(metrics);
            
            // 横屏应用，宽度是较大的尺寸
            screenWidth = Math.max(metrics.widthPixels, metrics.heightPixels);
            screenHeight = Math.min(metrics.widthPixels, metrics.heightPixels);
            
            XposedBridge.log("Yuri: Screen size - " + screenWidth + "x" + screenHeight);
            
        } catch (Exception e) {
            XposedBridge.log("Yuri Error getting screen size: " + e.getMessage());
            // 默认值
            screenWidth = 1920;
            screenHeight = 1080;
        }
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private void createInAppFloatingWindow(final Context context) {
        if (floatingView != null) return;
        
        try {
            // 计算悬浮窗尺寸 - 横屏宽度的1/3
            final int floatingWidth = screenWidth / 3;
            final int floatingHeight = 280; // 固定高度，适合横屏显示
            
            // 在主线程中创建悬浮窗
            if (context instanceof Activity) {
                ((Activity) context).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            createFloatingView(context, floatingWidth, floatingHeight);
                        } catch (Exception e) {
                            XposedBridge.log("Yuri UI Thread Error: " + e.getMessage());
                        }
                    }
                });
            } else {
                createFloatingView(context, floatingWidth, floatingHeight);
            }
            
        } catch (Exception e) {
            XposedBridge.log("Yuri Error creating window: " + e.getMessage());
        }
    }
    
    private void createFloatingView(Context context, int floatingWidth, int floatingHeight) {
        floatingView = new View(context) {
            private final Paint paint = new Paint();
            private final Paint strokePaint = new Paint();
            private final Paint textPaint = new Paint();
            private final Paint smallTextPaint = new Paint();
            private final Paint hintTextPaint = new Paint();
            private final Paint progressPaint = new Paint();
            private final Paint backgroundPaint = new Paint();
            private final Paint closeButtonPaint = new Paint();
            private final Paint headerPaint = new Paint();
            
            private float lastX = 0;
            private float lastY = 0;
            private boolean isDragging = false;
            
            {
                // 初始化Paint对象
                paint.setColor(0xCC1A1A1A);
                paint.setStyle(Paint.Style.FILL);
                paint.setAntiAlias(true);
                
                strokePaint.setColor(0x33FFFFFF);
                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(2f);
                strokePaint.setAntiAlias(true);
                
                headerPaint.setColor(0xAA2D2D2D);
                headerPaint.setStyle(Paint.Style.FILL);
                headerPaint.setAntiAlias(true);
                
                textPaint.setColor(Color.WHITE);
                textPaint.setTextSize(42f);
                textPaint.setAntiAlias(true);
                textPaint.setTypeface(Typeface.DEFAULT_BOLD);
                
                smallTextPaint.setColor(Color.WHITE);
                smallTextPaint.setTextSize(36f);
                smallTextPaint.setAntiAlias(true);
                
                hintTextPaint.setColor(0xFFCCCCCC);
                hintTextPaint.setTextSize(30f);
                hintTextPaint.setAntiAlias(true);
                
                progressPaint.setColor(0xFF4CAF50);
                progressPaint.setStyle(Paint.Style.FILL);
                progressPaint.setAntiAlias(true);
                
                backgroundPaint.setColor(0x55666666);
                backgroundPaint.setStyle(Paint.Style.FILL);
                backgroundPaint.setAntiAlias(true);
                
                closeButtonPaint.setColor(Color.WHITE);
                closeButtonPaint.setStyle(Paint.Style.STROKE);
                closeButtonPaint.setStrokeWidth(3f);
                closeButtonPaint.setAntiAlias(true);
            }
            
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                
                // 绘制圆角背景
                RectF rect = new RectF(0, 0, getWidth(), getHeight());
                canvas.drawRoundRect(rect, 16, 16, paint);
                canvas.drawRoundRect(rect, 16, 16, strokePaint);
                
                // 绘制标题栏背景
                RectF headerRect = new RectF(0, 0, getWidth(), 80);
                canvas.drawRoundRect(new RectF(0, 0, getWidth(), 80), 16, 16, headerPaint);
                canvas.drawRect(new RectF(0, 40, getWidth(), 80), headerPaint);
                
                // 绘制标题
                canvas.drawText("Time Controller", 24, 50, textPaint);
                
                // 绘制关闭按钮
                canvas.drawLine(getWidth() - 40, 20, getWidth() - 20, 40, closeButtonPaint);
                canvas.drawLine(getWidth() - 20, 20, getWidth() - 40, 40, closeButtonPaint);
                
                // 绘制当前速度
                String speedText = String.format("Speed: %.2fx", timeScale);
                float speedTextWidth = smallTextPaint.measureText(speedText);
                canvas.drawText(speedText, (getWidth() - speedTextWidth) / 2, 130, smallTextPaint);
                
                // 绘制滑条背景
                int sliderMargin = 40;
                canvas.drawRect(sliderMargin, 160, getWidth() - sliderMargin, 190, backgroundPaint);
                
                // 绘制滑条进度
                float progressWidth = (getWidth() - 2 * sliderMargin) * ((timeScale - 1) / 9);
                canvas.drawRect(sliderMargin, 160, sliderMargin + progressWidth, 190, progressPaint);
                
                // 绘制刻度文本
                canvas.drawText("1x", sliderMargin, 230, hintTextPaint);
                
                String maxText = "10x";
                float maxTextWidth = hintTextPaint.measureText(maxText);
                canvas.drawText(maxText, getWidth() - sliderMargin - maxTextWidth, 230, hintTextPaint);
                
                // 绘制当前值标记
                String currentValue = String.format("%.1f", timeScale);
                float valueTextWidth = hintTextPaint.measureText(currentValue);
                float valueX = sliderMargin + progressWidth - (valueTextWidth / 2);
                valueX = Math.max(sliderMargin, Math.min(getWidth() - sliderMargin - valueTextWidth, valueX));
                canvas.drawText(currentValue, valueX, 260, hintTextPaint);
            }
            
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                float x = event.getRawX();
                float y = event.getRawY();
                
                int[] location = new int[2];
                getLocationOnScreen(location);
                int viewX = location[0];
                int viewY = location[1];
                
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // 检查是否点击关闭按钮
                        if (x >= (viewX + getWidth() - 40) && x <= (viewX + getWidth() - 20) &&
                            y >= (viewY + 20) && y <= (viewY + 40)) {
                            removeFloatingWindow();
                            return true;
                        }
                        
                        // 检查是否点击滑条区域
                        int sliderMargin = 40;
                        if (x >= (viewX + sliderMargin) && x <= (viewX + getWidth() - sliderMargin) &&
                            y >= (viewY + 150) && y <= (viewY + 200)) {
                            updateSeekBar(x - viewX);
                            return true;
                        }
                        
                        // 检查是否点击标题栏区域（用于拖动）
                        if (y >= viewY && y <= (viewY + 80)) {
                            lastX = x;
                            lastY = y;
                            isDragging = true;
                            return true;
                        }
                        break;
                        
                    case MotionEvent.ACTION_MOVE:
                        if (isDragging) {
                            WindowManager.LayoutParams params = (WindowManager.LayoutParams) getLayoutParams();
                            params.x += (int) (x - lastX);
                            params.y += (int) (y - lastY);
                            
                            // 限制在屏幕范围内
                            params.x = Math.max(0, Math.min(screenWidth - floatingWidth, params.x));
                            params.y = Math.max(0, Math.min(screenHeight - floatingHeight, params.y));
                            
                            windowManager.updateViewLayout(this, params);
                            lastX = x;
                            lastY = y;
                        } else {
                            // 滑条拖动
                            updateSeekBar(x - viewX);
                        }
                        break;
                        
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        isDragging = false;
                        break;
                }
                return true;
            }
            
            private void updateSeekBar(float x) {
                int sliderMargin = 40;
                float seekStart = sliderMargin;
                float seekEnd = getWidth() - sliderMargin;
                float seekWidth = seekEnd - seekStart;
                
                if (x >= seekStart && x <= seekEnd) {
                    float progress = (x - seekStart) / seekWidth;
                    timeScale = 1.0f + (progress * 9.0f);
                    timeScale = Math.max(1.0f, Math.min(10.0f, timeScale));
                    setTimeScale(timeScale);
                    invalidate();
                }
            }
        };
        
        // 创建布局参数 - 使用应用内窗口类型
        int type = WindowManager.LayoutParams.TYPE_APPLICATION;
        
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            floatingWidth, // 宽度为屏幕宽度的1/3
            floatingHeight, // 固定高度
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | 
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        );
        
        // 初始位置 - 右上角
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 20;
        params.y = 100;
        
        try {
            windowManager.addView(floatingView, params);
            XposedBridge.log("Yuri: Floating window created successfully - Size: " + 
                floatingWidth + "x" + floatingHeight);
        } catch (Exception e) {
            XposedBridge.log("Yuri Error adding view: " + e.getMessage());
            floatingView = null;
        }
    }
    
    private void removeFloatingWindow() {
        try {
            if (floatingView != null) {
                windowManager.removeView(floatingView);
                floatingView = null;
                isFloatingWindowCreated = false;
            }
        } catch (Exception e) {
            XposedBridge.log("Yuri Error removing window: " + e.getMessage());
        }
    }
}