package com.example.testxp;

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
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
    
    @SuppressWarnings("ClickableViewAccessibility")
    private void createFloatingWindow(final Context context) {
        if (floatingView != null) return;
        
        try {
            // 计算悬浮窗尺寸 - 横屏宽度的1/3
            final int floatingWidth = screenWidth / 3;
            final int floatingHeight = 400; // 增加高度以容纳更多控件
            
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
            
            // 设置触摸监听实现拖动
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
            dpToPx(context, 60)
        );
        titleBar.setLayoutParams(titleParams);
        
        // 标题文本
        TextView titleText = new TextView(context);
        titleText.setText("Time Controller");
        titleText.setTextColor(Color.WHITE);
        titleText.setTextSize(18);
        titleText.setTypeface(Typeface.DEFAULT_BOLD);
        
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        textParams.gravity = Gravity.CENTER_VERTICAL;
        textParams.leftMargin = dpToPx(context, 16);
        titleText.setLayoutParams(textParams);
        
        // 关闭按钮
        TextView closeButton = new TextView(context);
        closeButton.setText("×");
        closeButton.setTextColor(Color.WHITE);
        closeButton.setTextSize(24);
        
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
            dpToPx(context, 60),
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
        scrollView.addView(contentLayout);
        
        // 添加时间倍率控制
        createTimeScaleControl(context, contentLayout);
        
        // 添加其他控制项（可以根据需要扩展）
        createAdditionalControls(context, contentLayout);
        
        parent.addView(scrollView);
    }
    
    private void createTimeScaleControl(Context context, LinearLayout parent) {
        // 速度显示
        TextView speedText = new TextView(context);
        speedText.setText("Speed: " + String.format("%.2f", timeScale) + "x");
        speedText.setTextColor(Color.WHITE);
        speedText.setTextSize(16);
        speedText.setGravity(Gravity.CENTER);
        
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        textParams.topMargin = dpToPx(context, 16);
        textParams.bottomMargin = dpToPx(context, 8);
        speedText.setLayoutParams(textParams);
        parent.addView(speedText);
        
        // 滑条容器
        LinearLayout sliderContainer = new LinearLayout(context);
        sliderContainer.setOrientation(LinearLayout.HORIZONTAL);
        
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        containerParams.leftMargin = dpToPx(context, 16);
        containerParams.rightMargin = dpToPx(context, 16);
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
        
        // 滑条
        SeekBar seekBar = new SeekBar(context);
        
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        seekParams.leftMargin = dpToPx(context, 8);
        seekParams.rightMargin = dpToPx(context, 8);
        seekBar.setLayoutParams(seekParams);
        
        seekBar.setMax(90); // 1.0到10.0，步长0.1，共90步
        seekBar.setProgress((int)((timeScale - 1.0f) * 10));
        
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    timeScale = 1.0f + (progress / 10.0f);
                    speedText.setText("Speed: " + String.format("%.2f", timeScale) + "x");
                    setTimeScale(timeScale);
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
    
    private void createAdditionalControls(Context context, LinearLayout parent) {
        // 这里可以添加其他控制项
        // 例如：开关、按钮等
        
        // 示例：添加一个说明文本
        TextView infoText = new TextView(context);
        infoText.setText("Adjust game speed using the slider above");
        infoText.setTextColor(0xFFCCCCCC);
        infoText.setTextSize(12);
        infoText.setGravity(Gravity.CENTER);
        
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        infoParams.topMargin = dpToPx(context, 8);
        infoParams.bottomMargin = dpToPx(context, 16);
        infoText.setLayoutParams(infoParams);
        
        parent.addView(infoText);
    }
    
    private void setupTouchListener(final View view, final FrameLayout.LayoutParams params) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.leftMargin;
                        initialY = params.topMargin;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                        
                    case MotionEvent.ACTION_MOVE:
                        params.leftMargin = initialX + (int)(event.getRawX() - initialTouchX);
                        params.topMargin = initialY + (int)(event.getRawY() - initialTouchY);
                        
                        // 限制在屏幕范围内
                        params.leftMargin = Math.max(0, Math.min(screenWidth - view.getWidth(), params.leftMargin));
                        params.topMargin = Math.max(0, Math.min(screenHeight - view.getHeight(), params.topMargin));
                        
                        hostDecorView.updateViewLayout(view, params);
                        return true;
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