package com.example.bookmyticket.features.scanner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.animation.ValueAnimator;

public class ScannerOverlayView extends View {
    private Paint framePaint;
    private Paint linePaint;
    private Paint overlayPaint;
    private RectF scanRect;
    private float lineY;
    private float borderAlpha;
    private ValueAnimator lineAnimator;
    private ValueAnimator borderAnimator;

    public ScannerOverlayView(Context context) {
        super(context);
        init();
    }

    public ScannerOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // Frame paint for corners (sharp, no blur)
        framePaint = new Paint();
        framePaint.setStyle(Paint.Style.FILL); // Use FILL for corners
        framePaint.setAntiAlias(true);
        framePaint.setColor(Color.parseColor("#00FF88")); // Google Pay-style green
        framePaint.setShadowLayer(8f, 0, 0, Color.parseColor("#8000FF88")); // Subtle shadow for depth

        // Line paint with vibrant gradient
        linePaint = new Paint();
        linePaint.setStyle(Paint.Style.FILL);
        linePaint.setAntiAlias(true);
        linePaint.setShadowLayer(6f, 0, 0, Color.parseColor("#8000FF88")); // Subtle glow for line

        // Overlay paint (lighter, ~50% opacity)
        overlayPaint = new Paint();
        overlayPaint.setColor(Color.parseColor("#80000000")); // Lighter overlay
        overlayPaint.setStyle(Paint.Style.FILL);

        // Scanning line animation (fast)
        lineAnimator = ValueAnimator.ofFloat(0f, 1f);
        lineAnimator.setDuration(800); // Fast for snappy feel
        lineAnimator.setRepeatCount(ValueAnimator.INFINITE);
        lineAnimator.setRepeatMode(ValueAnimator.RESTART);
        lineAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        lineAnimator.addUpdateListener(animation -> {
            lineY = (float) animation.getAnimatedValue();
            invalidate();
        });

        // Corner pulse animation (fast and subtle)
        borderAnimator = ValueAnimator.ofFloat(0.8f, 1.0f);
        borderAnimator.setDuration(600); // Fast pulse
        borderAnimator.setRepeatCount(ValueAnimator.INFINITE);
        borderAnimator.setRepeatMode(ValueAnimator.REVERSE);
        borderAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        borderAnimator.addUpdateListener(animation -> {
            borderAlpha = (float) animation.getAnimatedValue();
            framePaint.setAlpha((int) (255 * borderAlpha));
            linePaint.setAlpha((int) (255 * borderAlpha));
            invalidate();
        });

        lineAnimator.start();
        borderAnimator.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float size = 300 * getResources().getDisplayMetrics().density;
        float left = (w - size) / 2;
        float top = (h - size) / 2;
        scanRect = new RectF(left, top, left + size, top + size);

        // Line gradient (vertical, vibrant)
        linePaint.setShader(new LinearGradient(
                scanRect.left, scanRect.top, scanRect.left, scanRect.bottom,
                new int[]{Color.TRANSPARENT, Color.parseColor("#00FF88"), Color.TRANSPARENT},
                new float[]{0.0f, 0.5f, 1.0f},
                Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw overlay
        canvas.drawRect(0, 0, getWidth(), scanRect.top, overlayPaint);
        canvas.drawRect(0, scanRect.top, scanRect.left, scanRect.bottom, overlayPaint);
        canvas.drawRect(scanRect.right, scanRect.top, getWidth(), scanRect.bottom, overlayPaint);
        canvas.drawRect(0, scanRect.bottom, getWidth(), getHeight(), overlayPaint);

        // Draw corner accents (no full square border)
        float cornerLength = 24f * getResources().getDisplayMetrics().density; // Slightly longer for visibility
        float cornerWidth = 10f * getResources().getDisplayMetrics().density; // Thicker for prominence
        drawCorner(canvas, scanRect.left, scanRect.top, cornerLength, cornerWidth, true, true); // Top-left
        drawCorner(canvas, scanRect.right, scanRect.top, cornerLength, cornerWidth, false, true); // Top-right
        drawCorner(canvas, scanRect.left, scanRect.bottom, cornerLength, cornerWidth, true, false); // Bottom-left
        drawCorner(canvas, scanRect.right, scanRect.bottom, cornerLength, cornerWidth, false, false); // Bottom-right

        // Draw scanning line (thicker)
        float linePosition = scanRect.top + (scanRect.height() * lineY);
        canvas.drawRect(scanRect.left, linePosition - 6f, scanRect.right, linePosition + 6f, linePaint);
    }

    private void drawCorner(Canvas canvas, float x, float y, float length, float width, boolean left, boolean top) {
        Path path = new Path();
        float offsetX = left ? width : -width;
        float offsetY = top ? width : -width;
        path.moveTo(x, y);
        path.lineTo(x + (left ? length : -length), y);
        path.lineTo(x + (left ? length : -length), y + offsetY);
        path.lineTo(x + offsetX, y + offsetY);
        path.lineTo(x + offsetX, y + (top ? length : -length));
        path.lineTo(x, y + (top ? length : -length));
        path.close();
        canvas.drawPath(path, framePaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        lineAnimator.cancel();
        borderAnimator.cancel();
    }
}
