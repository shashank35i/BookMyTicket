package com.example.bookmyticket.features.scanner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class ScannerOverlay extends View {

    private Paint paint;
    private RectF transparentRect;

    public ScannerOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(0x44000000); // Less opaque black
        // Semi-transparent black
        paint.setStyle(Paint.Style.FILL);

        transparentRect = new RectF();  // Transparent cutout area
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        int rectSize = 700; // Scanner cutout size (adjust as needed)

        transparentRect.set(
                (width - rectSize) / 2f,
                (height - rectSize) / 2f,
                (width + rectSize) / 2f,
                (height + rectSize) / 2f
        );

        // Draw full black transparent overlay
        canvas.drawRect(0, 0, width, height, paint);

        // Cut out the scanner area
        Paint clearPaint = new Paint();
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        canvas.drawRect(transparentRect, clearPaint);
    }
}
