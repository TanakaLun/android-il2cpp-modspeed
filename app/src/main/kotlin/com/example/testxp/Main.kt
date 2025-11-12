package com.example.testxp

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

class Main : IXposedHookLoadPackage {
    
    companion object {
        init {
            System.loadLibrary("Yuri")
        }
        
        @JvmStatic
        external fun setTimeScale(scale: Float)
    }
    
    private var timeScale = 1.0f
    private var floatingView: View? = null
    private var windowManager: WindowManager? = null

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 移除了包名判断，对所有APP生效
        XposedBridge.log("Yuri: Loading for package: ${lpparam.packageName}")
        
        try {
            // 直接尝试显示悬浮窗，不依赖Activity Hook
            showFloatingWindowOnStart()
            
        } catch (e: Throwable) {
            XposedBridge.log("Yuri Init Error: ${e.message}")
        }
    }
    
    @SuppressLint("PrivateApi")
    private fun showFloatingWindowOnStart() {
        try {
            // 通过系统服务获取Context来创建悬浮窗
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThreadMethod = activityThreadClass.getMethod("currentActivityThread")
            val activityThread = currentActivityThreadMethod.invoke(null)
            
            val getSystemContextMethod = activityThreadClass.getMethod("getSystemContext")
            val context = getSystemContextMethod.invoke(activityThread) as Context
            
            createFloatingWindow(context)
            
        } catch (e: Exception) {
            XposedBridge.log("Yuri System Context Error: ${e.message}")
        }
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingWindow(context: Context) {
        if (floatingView != null) return
        
        try {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            // 纯代码创建悬浮窗
            val floatingView = object : View(context) {
                private val paint = Paint().apply {
                    color = 0xCC1A1A1A.toInt()
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                private val strokePaint = Paint().apply {
                    color = 0x33FFFFFF.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                    isAntiAlias = true
                }
                private val textPaint = Paint().apply {
                    color = Color.WHITE
                    textSize = 42f
                    isAntiAlias = true
                    typeface = Typeface.DEFAULT_BOLD
                }
                private val smallTextPaint = Paint().apply {
                    color = Color.WHITE
                    textSize = 36f
                    isAntiAlias = true
                }
                private val hintTextPaint = Paint().apply {
                    color = 0xFFCCCCCC.toInt()
                    textSize = 30f
                    isAntiAlias = true
                }
                
                private var dragX = 0f
                private var dragY = 0f
                private var lastX = 0f
                private var lastY = 0f
                private var isDragging = false
                
                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    
                    // 绘制圆角背景
                    val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
                    canvas.drawRoundRect(rect, 36f, 36f, paint)
                    canvas.drawRoundRect(rect, 36f, 36f, strokePaint)
                    
                    // 绘制标题
                    canvas.drawText("Time Controller", 24f, 60f, textPaint)
                    
                    // 绘制当前速度
                    canvas.drawText("Speed: ${"%.2f".format(timeScale)}x", 24f, 120f, smallTextPaint)
                    
                    // 绘制滑条背景
                    canvas.drawRect(24f, 150f, (width - 24).toFloat(), 180f, 
                        hintTextPaint.apply { color = 0x55666666.toInt() })
                    
                    // 绘制滑条进度
                    val progressWidth = ((width - 48) * ((timeScale - 1) / 9)).toFloat()
                    canvas.drawRect(24f, 150f, 24f + progressWidth, 180f, 
                        hintTextPaint.apply { color = 0xFF4CAF50.toInt() })
                    
                    // 绘制刻度文本
                    canvas.drawText("1x", 24f, 230f, hintTextPaint)
                    canvas.drawText("10x", (width - 60).toFloat(), 230f, hintTextPaint)
                    
                    // 绘制关闭按钮
                    canvas.drawLine((width - 40).toFloat(), 20f, (width - 20).toFloat(), 40f, 
                        strokePaint.apply { color = Color.WHITE; strokeWidth = 3f })
                    canvas.drawLine((width - 20).toFloat(), 20f, (width - 40).toFloat(), 40f, 
                        strokePaint.apply { color = Color.WHITE; strokeWidth = 3f })
                }
                
                override fun onTouchEvent(event: MotionEvent): Boolean {
                    val x = event.rawX
                    val y = event.rawY
                    
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            // 检查是否点击关闭按钮
                            if (x >= (left + width - 40) && x <= (left + width - 20) &&
                                y >= (top + 20) && y <= (top + 40)) {
                                removeFloatingWindow()
                                return true
                            }
                            
                            // 检查是否点击滑条区域
                            if (x >= (left + 24) && x <= (left + width - 24) &&
                                y >= (top + 140) && y <= (top + 190)) {
                                updateSeekBar(x)
                                return true
                            }
                            
                            // 开始拖动
                            lastX = x
                            lastY = y
                            isDragging = true
                        }
                        
                        MotionEvent.ACTION_MOVE -> {
                            if (isDragging) {
                                val params = layoutParams as WindowManager.LayoutParams
                                params.x += (x - lastX).toInt()
                                params.y += (y - lastY).toInt()
                                windowManager?.updateViewLayout(this, params)
                                lastX = x
                                lastY = y
                            } else {
                                // 滑条拖动
                                updateSeekBar(x)
                            }
                        }
                        
                        MotionEvent.ACTION_UP -> {
                            isDragging = false
                        }
                    }
                    return true
                }
                
                private fun updateSeekBar(x: Float) {
                    val seekStart = left + 24
                    val seekEnd = left + width - 24
                    val seekWidth = seekEnd - seekStart
                    
                    if (x in seekStart..seekEnd) {
                        val progress = (x - seekStart) / seekWidth
                        timeScale = 1.0f + (progress * 9.0f)
                        timeScale = timeScale.coerceIn(1.0f, 10.0f)
                        setTimeScale(timeScale)
                        invalidate()
                    }
                }
            }
            
            // 设置布局参数
            val params = WindowManager.LayoutParams().apply {
                width = 600
                height = 280
                type = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                gravity = Gravity.TOP or Gravity.START
                x = 100
                y = 300
            }
            
            this.floatingView = floatingView
            windowManager?.addView(floatingView, params)
            
            XposedBridge.log("Yuri: Floating window created successfully for all apps")
            
        } catch (e: Exception) {
            XposedBridge.log("Yuri Error creating window: ${e.message}")
        }
    }
    
    private fun removeFloatingWindow() {
        try {
            floatingView?.let {
                windowManager?.removeView(it)
                floatingView = null
            }
        } catch (e: Exception) {
            XposedBridge.log("Yuri Error removing window: ${e.message}")
        }
    }
}