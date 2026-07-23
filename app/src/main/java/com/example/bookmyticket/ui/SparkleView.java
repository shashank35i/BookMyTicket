package com.example.bookmyticket.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Random;

public class SparkleView extends View {
    private final ArrayList<Confetti> confettiList = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private boolean isAnimating = false;
    private float dialogCenterX = 0;
    private float dialogCenterY = 0;
    private final int[] colors = {
            Color.parseColor("#FFD700"), // Gold
            Color.parseColor("#FF4081"), // Pink
            Color.parseColor("#40C4FF"), // Sky Blue
            Color.parseColor("#FFFFFF"), // White
            Color.parseColor("#F06292"), // Light Pink
            Color.parseColor("#7C4DFF")  // Purple
    };

    public SparkleView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.FILL);
        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setAlpha(120);
    }

    public void setDialogCenter(float centerX, float centerY) {
        this.dialogCenterX = centerX;
        this.dialogCenterY = centerY;
    }

    public void startAnimation() {
        if (isAnimating) return;
        confettiList.clear();
        isAnimating = true;
        for (int i = 0; i < 180; i++) {
            confettiList.add(new Confetti(getWidth(), getHeight(), random, colors, dialogCenterX, dialogCenterY));
        }
        invalidate();
    }

    public void stopAnimation() {
        isAnimating = false;
        confettiList.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!isAnimating) return;

        for (int i = confettiList.size() - 1; i >= 0; i--) {
            Confetti confetti = confettiList.get(i);

            RadialGradient glowGradient = new RadialGradient(
                    confetti.x, confetti.y,
                    confetti.size * 2.5f,
                    confetti.color,
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
            );
            glowPaint.setShader(glowGradient);
            if (confetti.isCircle) {
                canvas.drawCircle(confetti.x, confetti.y, confetti.size * 1.5f, glowPaint);
            } else {
                Path glowPath = new Path();
                glowPath.addRect(
                        confetti.x - confetti.size * 1.2f, confetti.y - confetti.size * 0.6f,
                        confetti.x + confetti.size * 1.2f, confetti.y + confetti.size * 0.6f,
                        Path.Direction.CW
                );
                canvas.rotate(confetti.rotation, confetti.x, confetti.y);
                canvas.drawPath(glowPath, glowPaint);
                canvas.rotate(-confetti.rotation, confetti.x, confetti.y);
            }

            RadialGradient gradient = new RadialGradient(
                    confetti.x, confetti.y,
                    confetti.size,
                    Color.WHITE,
                    confetti.color,
                    Shader.TileMode.CLAMP
            );
            paint.setShader(gradient);
            paint.setAlpha((int) (confetti.alpha * 255));

            if (confetti.isCircle) {
                canvas.drawCircle(confetti.x, confetti.y, confetti.size, paint);
            } else {
                Path path = new Path();
                path.addRect(
                        confetti.x - confetti.size, confetti.y - confetti.size / 2,
                        confetti.x + confetti.size, confetti.y + confetti.size / 2,
                        Path.Direction.CW
                );
                canvas.rotate(confetti.rotation, confetti.x, confetti.y);
                canvas.drawPath(path, paint);
                canvas.rotate(-confetti.rotation, confetti.x, confetti.y);
            }

            confetti.update();

            if (confetti.alpha <= 0 || confetti.y > getHeight() + confetti.size) {
                confettiList.remove(i);
                confettiList.add(new Confetti(getWidth(), getHeight(), random, colors, dialogCenterX, dialogCenterY));
            }
        }

        invalidate();
    }

    private static class Confetti {
        float x, y, size, velocityX, velocityY, alpha, rotation, rotationSpeed;
        int color;
        boolean isCircle;

        Confetti(int width, int height, Random random, int[] colors, float dialogCenterX, float dialogCenterY) {
            this.x = dialogCenterX;
            this.y = dialogCenterY;
            this.size = 3 + random.nextFloat() * 6;
            float angle = (float) (random.nextFloat() * 2 * Math.PI);
            float speed = 5 + random.nextFloat() * 10;
            this.velocityX = (float) Math.cos(angle) * speed;
            this.velocityY = (float) Math.sin(angle) * speed - 5;
            this.alpha = 0.9f + random.nextFloat() * 0.1f;
            this.rotation = random.nextFloat() * 360;
            this.rotationSpeed = (random.nextFloat() - 0.5f) * 25;
            this.color = colors[random.nextInt(colors.length)];
            this.isCircle = random.nextInt(3) != 0;
        }

        void update() {
            x += velocityX;
            y += velocityY;
            velocityY += 0.2f;
            alpha -= 0.01f;
            rotation += rotationSpeed;
            if (x < 0 || x > 1080) velocityX = -velocityX * 0.8f;
        }
    }
}
