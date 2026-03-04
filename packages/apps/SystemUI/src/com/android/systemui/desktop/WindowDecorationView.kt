/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.desktop

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.view.LayoutInflater

/**
 * Custom title bar for freeform windows in Desktop Mode.
 *
 * This view provides:
 * - App icon (left)
 * - App title (center-left)
 * - Window controls (right): minimize, maximize, close
 * - Draggable title bar for window repositioning
 * - Resize handles on edges/corners
 */
class WindowDecorationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "WindowDecorationView"
        private const val RESIZE_HANDLE_SIZE = 16 // dp
        private const val TITLE_BAR_HEIGHT = 40 // dp
    }

    interface WindowDecorationListener {
        fun onWindowMove(deltaX: Float, deltaY: Float)
        fun onWindowResize(edge: Int, delta: Float)
        fun onMinimizeClicked()
        fun onMaximizeClicked()
        fun onCloseClicked()
        fun onMoveStart()
        fun onMoveEnd()
    }

    // Window edges for resize
    object WindowEdge {
        const val NONE = 0
        const val LEFT = 1
        const val TOP = 2
        const val RIGHT = 4
        const val BOTTOM = 8
        const val TOP_LEFT = TOP or LEFT
        const val TOP_RIGHT = TOP or RIGHT
        const val BOTTOM_LEFT = BOTTOM or LEFT
        const val BOTTOM_RIGHT = BOTTOM or RIGHT
    }

    private lateinit var titleBar: LinearLayout
    private lateinit var appIcon: ImageView
    private lateinit var appTitle: TextView
    private lateinit var minimizeButton: ImageButton
    private lateinit var maximizeButton: ImageButton
    private lateinit var closeButton: ImageButton
    private lateinit var contentContainer: FrameLayout

    // Resize handles
    private lateinit var leftHandle: View
    private lateinit var topHandle: View
    private lateinit var rightHandle: View
    private lateinit var bottomHandle: View
    private lateinit var topLeftHandle: View
    private lateinit var topRightHandle: View
    private lateinit var bottomLeftHandle: View
    private lateinit var bottomRightHandle: View

    private var listener: WindowDecorationListener? = null

    // Dragging state
    private var isDragging = false
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var lastDragX = 0f
    private var lastDragY = 0f

    // Current window state
    private var isMaximized = false

    // Resize handle touch targets
    private val resizeHandleSize: Float

    // Border/paint for decoration
    private val borderPaint = Paint().apply {
        color = 0xFF3D3D5C.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    init {
        val density = context.resources.displayMetrics.density
        resizeHandleSize = RESIZE_HANDLE_SIZE * density

        LayoutInflater.from(context).inflate(R.layout.desk_window_decoration, this, true)
        initViews()
        setupInteractions()
    }

    private fun initViews() {
        titleBar = findViewById(R.id.window_title_bar)
        appIcon = findViewById(R.id.window_app_icon)
        appTitle = findViewById(R.id.window_title_text)
        minimizeButton = findViewById(R.id.btn_window_minimize)
        maximizeButton = findViewById(R.id.btn_window_maximize)
        closeButton = findViewById(R.id.btn_window_close)
        contentContainer = findViewById(R.id.window_content_container)

        // Resize handles
        leftHandle = findViewById(R.id.resize_handle_left)
        topHandle = findViewById(R.id.resize_handle_top)
        rightHandle = findViewById(R.id.resize_handle_right)
        bottomHandle = findViewById(R.id.resize_handle_bottom)
        topLeftHandle = findViewById(R.id.resize_handle_top_left)
        topRightHandle = findViewById(R.id.resize_handle_top_right)
        bottomLeftHandle = findViewById(R.id.resize_handle_bottom_left)
        bottomRightHandle = findViewById(R.id.resize_handle_bottom_right)

        // Button click listeners
        minimizeButton.setOnClickListener { listener?.onMinimizeClicked() }
        maximizeButton.setOnClickListener { listener?.onMaximizeClicked() }
        closeButton.setOnClickListener { listener?.onCloseClicked() }
    }

    private fun setupInteractions() {
        // Title bar drag for moving window
        titleBar.setOnTouchListener { _, event ->
            handleTitleBarTouch(event)
        }

        // Resize handles
        leftHandle.setOnTouchListener { _, event -> handleResizeTouch(event, WindowEdge.LEFT) }
        topHandle.setOnTouchListener { _, event -> handleResizeTouch(event, WindowEdge.TOP) }
        rightHandle.setOnTouchListener { _, event -> handleResizeTouch(event, WindowEdge.RIGHT) }
        bottomHandle.setOnTouchListener { _, event -> handleResizeTouch(event, WindowEdge.BOTTOM) }
        topLeftHandle.setOnTouchListener { _, event -> handleResizeTouch(event, WindowEdge.TOP_LEFT) }
        topRightHandle.setOnTouchListener { _, event -> handleResizeTouch(event, WindowEdge.TOP_RIGHT) }
        bottomLeftHandle.setOnTouchListener { _, event -> handleResizeTouch(event, WindowEdge.BOTTOM_LEFT) }
        bottomRightHandle.setOnTouchListener { _, event -> handleResizeTouch(event, WindowEdge.BOTTOM_RIGHT) }
    }

    private fun handleTitleBarTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                dragStartX = event.rawX
                dragStartY = event.rawY
                lastDragX = event.rawX
                lastDragY = event.rawY
                listener?.onMoveStart()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val deltaX = event.rawX - lastDragX
                    val deltaY = event.rawY - lastDragY
                    listener?.onWindowMove(deltaX, deltaY)
                    lastDragX = event.rawX
                    lastDragY = event.rawY
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    listener?.onMoveEnd()
                    return true
                }
            }
        }
        return false
    }

    private var isResizing = false
    private var resizeEdge = WindowEdge.NONE
    private var resizeStartX = 0f
    private var resizeStartY = 0f

    private fun handleResizeTouch(event: MotionEvent, edge: Int): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isResizing = true
                resizeEdge = edge
                resizeStartX = event.rawX
                resizeStartY = event.rawY
                lastDragX = event.rawX
                lastDragY = event.rawY
                listener?.onMoveStart()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isResizing) {
                    val delta = if (edge == WindowEdge.LEFT || edge == WindowEdge.RIGHT) {
                        event.rawX - lastDragX
                    } else {
                        event.rawY - lastDragY
                    }
                    listener?.onWindowResize(edge, delta)
                    lastDragX = event.rawX
                    lastDragY = event.rawY
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isResizing) {
                    isResizing = false
                    resizeEdge = WindowEdge.NONE
                    listener?.onMoveEnd()
                    return true
                }
            }
        }
        return false
    }

    /**
     * Set the listener for window decoration events
     */
    fun setWindowDecorationListener(listener: WindowDecorationListener) {
        this.listener = listener
    }

    /**
     * Set the app icon
     */
    fun setAppIcon(icon: Drawable?) {
        appIcon.setImageDrawable(icon)
    }

    /**
     * Set the window title
     */
    fun setWindowTitle(title: String) {
        appTitle.text = title
    }

    /**
     * Set the content view for the window
     */
    fun setContentView(view: View) {
        contentContainer.removeAllViews()
        contentContainer.addView(view)
    }

    /**
     * Update window state (for minimize/maximize buttons)
     */
    fun setWindowState(maximized: Boolean) {
        isMaximized = maximized
        // Update maximize button icon to reflect current state
        maximizeButton.setImageResource(
            if (maximized) R.drawable.ic_desk_window_maximize else R.drawable.ic_desk_window_maximize
        )
    }

    /**
     * Show/hide resize handles based on whether window is maximized
     */
    fun setResizeHandlesVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        leftHandle.visibility = visibility
        topHandle.visibility = visibility
        rightHandle.visibility = visibility
        bottomHandle.visibility = visibility
        topLeftHandle.visibility = visibility
        topRightHandle.visibility = visibility
        bottomLeftHandle.visibility = visibility
        bottomRightHandle.visibility = visibility
    }

    /**
     * Set the window bounds (for drawing border)
     */
    fun setWindowBounds(left: Int, top: Int, right: Int, bottom: Int) {
        // This can be used for custom border drawing if needed
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw border around the decoration view
        val rect = RectF(1f, 1f, width - 1f, height - 1f)
        canvas.drawRoundRect(rect, 8f, 8f, borderPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        // Ensure resize handles have proper touch targets
        val handleSize = MeasureSpec.makeMeasureSpec(
            (resizeHandleSize * 2).toInt(),
            MeasureSpec.EXACTLY
        )

        leftHandle.measure(handleSize, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        rightHandle.measure(handleSize, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        topHandle.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), handleSize)
        bottomHandle.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), handleSize)
    }
}
