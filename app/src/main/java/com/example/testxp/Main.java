package com.example.testxp;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Main implements IXposedHookLoadPackage {
    
    static {
        System.loadLibrary("Yuri");
    }
    
    // Native方法
    public static native void setTimeScale(float scale);
    public static native void updateTimeScaleImmediately();
    
    private int timeScale = 1;
    private View floatingView = null;
    private FrameLayout hostDecorView = null;
    private int screenWidth = 0;
    private int screenHeight = 0;
    private boolean isFloatingWindowCreated = false;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedBridge.log("Yuri: Loading for package: " + lpparam.packageName);
        
        try {
            // Hook Activity的onResume方法，确保在Activity运行后再创建悬浮窗
            XposedHelpers.findAndHookMethod("android.app.Activity", lpparam.classLoader, 
                "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (isFloatingWindowCreated) {
                        return;
                    }
                    
                    Activity activity = (Activity) param.thisObject;
                    XposedBridge.log("Yuri: Activity resumed: " + activity.getClass().getName());
                    
                    // 延迟1秒确保Activity完全初始化
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                attachToActivity(activity);
                                isFloatingWindowCreated = true;
                            } catch (Exception e) {
                                XposedBridge.log("Yuri Error attaching to activity: " + e.getMessage());
                            }
                        }
                    }, 1000);
                }
            });
            
        } catch (Throwable e) {
            XposedBridge.log("Yuri Hook Error: " + e.getMessage());
        }
    }
    
    private void attachToActivity(Activity activity) {
        try {
            // 获取宿主Activity的DecorView
            hostDecorView = (FrameLayout) activity.getWindow().getDecorView();
            
            // 获取屏幕尺寸
            getScreenSize(activity);
            
            // 创建悬浮窗
            createFloatingWindow(activity);
            
            XposedBridge.log("Yuri: Floating window attached to activity successfully");
            
        } catch (Exception e) {
            XposedBridge.log("Yuri Error attaching to activity: " + e.getMessage());
        }
    }
    
    private void getScreenSize(Context context) {
        try {
            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
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
    
    private void createFloatingWindow(final Context context) {
        if (floatingView != null) return;
        
        try {
            // 计算悬浮窗尺寸 - 横屏宽度的1/3，高度的1/2
            final int floatingWidth = screenWidth / 3;
            final int floatingHeight = screenHeight / 2;
            
            // 创建主容器
            LinearLayout mainLayout = new LinearLayout(context);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            
            // 设置背景
            mainLayout.setBackgroundColor(0xCC1A1A1A);
            
            // 创建布局参数
            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                floatingWidth,
                floatingHeight
            );
            layoutParams.gravity = Gravity.TOP | Gravity.END;
            layoutParams.leftMargin = 20;
            layoutParams.topMargin = 100;
            
            // 创建标题栏
            createTitleBar(context, mainLayout);
            
            // 创建内容区域
            createContentArea(context, mainLayout);
            
            // 设置触摸监听实现拖动 - 整个悬浮窗都可以拖动
            setupTouchListener(mainLayout, layoutParams);
            
            floatingView = mainLayout;
            hostDecorView.addView(floatingView, layoutParams);
            
            XposedBridge.log("Yuri: Floating window created successfully - Size: " + 
                floatingWidth + "x" + floatingHeight);
            
        } catch (Exception e) {
            XposedBridge.log("Yuri Error creating window: " + e.getMessage());
        }
    }
    
    private void createTitleBar(Context context, LinearLayout parent) {
        // 标题栏容器
        LinearLayout titleBar = new LinearLayout(context);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setBackgroundColor(0xAA2D2D2D);
        
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(context, 50)
        );
        titleBar.setLayoutParams(titleParams);
        
        // 标题文本
        TextView titleText = new TextView(context);
        titleText.setText("时制域倍率");
        titleText.setTextColor(Color.WHITE);
        titleText.setTextSize(16);
        titleText.setTypeface(Typeface.DEFAULT_BOLD);
        
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        textParams.gravity = Gravity.CENTER_VERTICAL;
        textParams.leftMargin = dpToPx(context, 12);
        titleText.setLayoutParams(textParams);
        
        // 关闭按钮
        TextView closeButton = new TextView(context);
        closeButton.setText("×");
        closeButton.setTextColor(Color.WHITE);
        closeButton.setTextSize(20);
        
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
            dpToPx(context, 50),
            LinearLayout.LayoutParams.MATCH_PARENT
        );
        closeButton.setLayoutParams(closeParams);
        closeButton.setGravity(Gravity.CENTER);
        
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeFloatingWindow();
            }
        });
        
        titleBar.addView(titleText);
        titleBar.addView(closeButton);
        parent.addView(titleBar);
    }
    
    private void createContentArea(Context context, LinearLayout parent) {
        // 滚动容器
        ScrollView scrollView = new ScrollView(context);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        );
        scrollView.setLayoutParams(scrollParams);
        
        // 内容容器
        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(
            dpToPx(context, 16),
            dpToPx(context, 12),
            dpToPx(context, 16),
            dpToPx(context, 12)
        );
        scrollView.addView(contentLayout);
        
        // 添加时间倍率控制
        createTimeScaleControl(context, contentLayout);
        
        parent.addView(scrollView);
    }
    
    private void createTimeScaleControl(Context context, LinearLayout parent) {
        // 速度显示
        final TextView speedText = new TextView(context);
        speedText.setText("Speed: " + timeScale + "x");
        speedText.setTextColor(Color.WHITE);
        speedText.setTextSize(16);
        speedText.setGravity(Gravity.CENTER);
        
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        textParams.topMargin = dpToPx(context, 8);
        textParams.bottomMargin = dpToPx(context, 12);
        speedText.setLayoutParams(textParams);
        parent.addView(speedText);
        
        // 滑条容器
        LinearLayout sliderContainer = new LinearLayout(context);
        sliderContainer.setOrientation(LinearLayout.HORIZONTAL);
        
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        containerParams.bottomMargin = dpToPx(context, 16);
        sliderContainer.setLayoutParams(containerParams);
        
        // 最小值标签
        TextView minLabel = new TextView(context);
        minLabel.setText("1x");
        minLabel.setTextColor(0xFFCCCCCC);
        minLabel.setTextSize(12);
        
        LinearLayout.LayoutParams minParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        minLabel.setLayoutParams(minParams);
        
        // 滑条 - 只接受1-10的整数
        SeekBar seekBar = new SeekBar(context);
        
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        seekParams.leftMargin = dpToPx(context, 8);
        seekParams.rightMargin = dpToPx(context, 8);
        seekBar.setLayoutParams(seekParams);
        
        // 设置滑条范围：1-10，共10个整数档位
        seekBar.setMax(9); // 最大值 = 10 - 1 = 9
        seekBar.setProgress(timeScale - 1); // 初始进度 = 当前值 - 1
        
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    // 进度直接对应1-10的整数
                    timeScale = progress + 1;
                    speedText.setText("Speed: " + timeScale + "x");
                    
                    // 立即应用时间倍率修改
                    applyTimeScaleImmediately();
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 最大值标签
        TextView maxLabel = new TextView(context);
        maxLabel.setText("10x");
        maxLabel.setTextColor(0xFFCCCCCC);
        maxLabel.setTextSize(12);
        
        LinearLayout.LayoutParams maxParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        maxLabel.setLayoutParams(maxParams);
        
        sliderContainer.addView(minLabel);
        sliderContainer.addView(seekBar);
        sliderContainer.addView(maxLabel);
        parent.addView(sliderContainer);
    }
    
    // 立即应用时间倍率修改
    private void applyTimeScaleImmediately() {
        try {
            // 设置新的时间倍率
            setTimeScale((float) timeScale);
            
            // 强制立即更新
            updateTimeScaleImmediately();
            
            XposedBridge.log("Yuri: Time scale applied immediately - " + timeScale + "x");
        } catch (Exception e) {
            XposedBridge.log("Yuri Error applying time scale: " + e.getMessage());
        }
    }
    
    private void setupTouchListener(final View view, final FrameLayout.LayoutParams params) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isDragging = false;
            private long pressStartTime = 0;
            private static final long LONG_PRESS_THRESHOLD = 300; // 长按阈值300毫秒
            
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.leftMargin;
                        initialY = params.topMargin;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        pressStartTime = System.currentTimeMillis();
                        isDragging = false;
                        return true; // 返回true表示我们想处理后续事件
                        
                    case MotionEvent.ACTION_MOVE:
                        long currentTime = System.currentTimeMillis();
                        
                        // 检查是否达到长按时间阈值
                        if (!isDragging && (currentTime - pressStartTime) >= LONG_PRESS_THRESHOLD) {
                            isDragging = true;
                        }
                        
                        if (isDragging) {
                            params.leftMargin = initialX + (int)(event.getRawX() - initialTouchX);
                            params.topMargin = initialY + (int)(event.getRawY() - initialTouchY);
                            
                            // 限制在屏幕范围内
                            params.leftMargin = Math.max(0, Math.min(screenWidth - view.getWidth(), params.leftMargin));
                            params.topMargin = Math.max(0, Math.min(screenHeight - view.getHeight(), params.topMargin));
                            
                            hostDecorView.updateViewLayout(view, params);
                            return true;
                        }
                        break;
                        
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        isDragging = false;
                        pressStartTime = 0;
                        break;
                }
                return false;
            }
        });
    }
    
    private void removeFloatingWindow() {
        try {
            if (floatingView != null && hostDecorView != null) {
                hostDecorView.removeView(floatingView);
                floatingView = null;
                isFloatingWindowCreated = false;
                XposedBridge.log("Yuri: Floating window removed");
            }
        } catch (Exception e) {
            XposedBridge.log("Yuri Error removing window: " + e.getMessage());
        }
    }
    
    private int dpToPx(Context context, int dp) {
        return (int)(dp * context.getResources().getDisplayMetrics().density);
    }
}