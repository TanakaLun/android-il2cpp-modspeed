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
    
    public static native void setTimeScale(float scale);
    
    private int timeScale = 1; // 设为整数
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
            final int floatingHeight = screenHeight / 2; // 增加为屏幕高度的一半
            
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
        contentLayout.setPadding(
            dpToPx(context, 16),
            dpToPx(context, 16),
            dpToPx(context, 16),
            dpToPx(context, 16)
        );
        scrollView.addView(contentLayout);
        
        // 添加时间倍率控制
        createTimeScaleControl(context, contentLayout);
        
        // 添加其他控制项（可以根据需要扩展）
        createAdditionalControls(context, contentLayout);
        
        parent.addView(scrollView);
    }
    
    private void createTimeScaleControl(Context context, LinearLayout parent) {
        // 速度显示
        final TextView speedText = new TextView(context);
        speedText.setText("Speed: " + timeScale + "x");
        speedText.setTextColor(Color.WHITE);
        speedText.setTextSize(18);
        speedText.setGravity(Gravity.CENTER);
        
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        textParams.topMargin = dpToPx(context, 8);
        textParams.bottomMargin = dpToPx(context, 16);
        speedText.setLayoutParams(textParams);
        parent.addView(speedText);
        
        // 滑条容器
        LinearLayout sliderContainer = new LinearLayout(context);
        sliderContainer.setOrientation(LinearLayout.HORIZONTAL);
        
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        containerParams.bottomMargin = dpToPx(context, 24);
        sliderContainer.setLayoutParams(containerParams);
        
        // 最小值标签
        TextView minLabel = new TextView(context);
        minLabel.setText("1x");
        minLabel.setTextColor(0xFFCCCCCC);
        minLabel.setTextSize(14);
        
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
        seekParams.leftMargin = dpToPx(context, 12);
        seekParams.rightMargin = dpToPx(context, 12);
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
                    setTimeScale((float) timeScale);
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
        maxLabel.setTextSize(14);
        
        LinearLayout.LayoutParams maxParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        maxLabel.setLayoutParams(maxParams);
        
        sliderContainer.addView(minLabel);
        sliderContainer.addView(seekBar);
        sliderContainer.addView(maxLabel);
        parent.addView(sliderContainer);
        
        // 添加当前值显示
        final TextView currentValueText = new TextView(context);
        currentValueText.setText("Current: " + timeScale + "x");
        currentValueText.setTextColor(0xFF4CAF50);
        currentValueText.setTextSize(16);
        currentValueText.setGravity(Gravity.CENTER);
        
        LinearLayout.LayoutParams currentParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        currentParams.bottomMargin = dpToPx(context, 24);
        currentValueText.setLayoutParams(currentParams);
        parent.addView(currentValueText);
        
        // 保存引用以便预设按钮使用
        final SeekBar finalSeekBar = seekBar;
        
        // 添加预设按钮区域
        LinearLayout presetContainer = new LinearLayout(context);
        presetContainer.setOrientation(LinearLayout.HORIZONTAL);
        
        LinearLayout.LayoutParams presetParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        presetParams.bottomMargin = dpToPx(context, 16);
        presetContainer.setLayoutParams(presetParams);
        
        // 添加预设按钮
        String[] presetValues = {"1x", "2x", "5x", "10x"};
        int[] presetScales = {1, 2, 5, 10};
        
        for (int i = 0; i < presetValues.length; i++) {
            TextView presetButton = new TextView(context);
            final int presetScale = presetScales[i];
            
            presetButton.setText(presetValues[i]);
            presetButton.setTextColor(Color.WHITE);
            presetButton.setTextSize(14);
            presetButton.setGravity(Gravity.CENTER);
            presetButton.setBackgroundColor(0xFF4CAF50);
            presetButton.setPadding(
                dpToPx(context, 12),
                dpToPx(context, 8),
                dpToPx(context, 12),
                dpToPx(context, 8)
            );
            
            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            );
            if (i > 0) {
                buttonParams.leftMargin = dpToPx(context, 8);
            }
            presetButton.setLayoutParams(buttonParams);
            
            presetButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    timeScale = presetScale;
                    // 更新滑条进度
                    finalSeekBar.setProgress(timeScale - 1);
                    // 更新显示文本
                    speedText.setText("Speed: " + timeScale + "x");
                    currentValueText.setText("Current: " + timeScale + "x");
                    // 设置时间倍率
                    setTimeScale((float) timeScale);
                    XposedBridge.log("Yuri: Speed set to " + timeScale + "x");
                }
            });
            
            presetContainer.addView(presetButton);
        }
        
        parent.addView(presetContainer);
    }
    
    private void createAdditionalControls(Context context, LinearLayout parent) {
        // 添加分隔线
        View divider = new View(context);
        divider.setBackgroundColor(0x55666666);
        
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(context, 1)
        );
        dividerParams.bottomMargin = dpToPx(context, 16);
        divider.setLayoutParams(dividerParams);
        parent.addView(divider);
        
        // 添加说明文本
        TextView infoText = new TextView(context);
        infoText.setText("Speed Hack Control\n\nAdjust the game speed using the slider above. " +
                        "Only integer values from 1 to 10 are accepted.");
        infoText.setTextColor(0xFFCCCCCC);
        infoText.setTextSize(14);
        infoText.setGravity(Gravity.CENTER);
        infoText.setLineSpacing(dpToPx(context, 4), 1.0f);
        
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
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