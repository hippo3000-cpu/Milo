package de.milo.alive;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Random;

public class MiloView extends View {
    public interface Speaker {
        void say(String text);
    }

    private enum Mood {
        IDLE, BLINK, CURIOUS, JOY, SLEEP, SAD
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint softPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final GestureDetector gestures;
    private final Speaker speaker;
    private final EnumMap<Mood, Bitmap> sprites = new EnumMap<>(Mood.class);
    private final ArrayList<Heart> hearts = new ArrayList<>();

    private final RectF miloRect = new RectF();
    private final RectF syncRect = new RectF();
    private final RectF offlineRect = new RectF();
    private final RectF errorRect = new RectF();

    private Mood mood = Mood.IDLE;
    private long moodUntil = 0L;
    private long lastInteraction = SystemClock.uptimeMillis();
    private long nextBlink = lastInteraction + 2600;
    private long nextCurious = lastInteraction + 8000;
    private boolean touching = false;
    private boolean paused = false;
    private float pointerX = 0.5f;
    private float pointerY = 0.5f;
    private float joy = 0.80f;
    private float energy = 0.70f;
    private float curiosity = 0.90f;

    private static final class Heart {
        float x, y, dx, dy, life, size;
        Heart(float x, float y, float size, float dx) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.dx = dx;
            this.dy = -2.2f - size / 20f;
            this.life = 1f;
        }
    }

    public MiloView(Context context, Speaker speaker) {
        super(context);
        this.speaker = speaker;
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setBackgroundColor(Color.rgb(255, 246, 248));

        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        loadSprites();

        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                interact();
                if (miloRect.contains(e.getX(), e.getY())) {
                    setMood(Mood.BLINK, 900);
                    burstHearts(e.getX(), e.getY(), 6);
                    vibrate(28);
                    speaker.say("Hallo!");
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                interact();
                if (miloRect.contains(e.getX(), e.getY())) {
                    setMood(Mood.JOY, 1900);
                    burstHearts(e.getX(), e.getY(), 12);
                    joy = Math.min(1f, joy + 0.08f);
                    vibrate(55);
                    speaker.say("Juhu!");
                }
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                interact();
                if (miloRect.contains(e.getX(), e.getY())) {
                    setMood(Mood.SLEEP, 6000);
                    speaker.say("Nur ein kleines Nickerchen.");
                    vibrate(22);
                }
            }
        });

        handler.post(frameLoop);
    }

    private void loadSprites() {
        sprites.put(Mood.IDLE, BitmapFactory.decodeResource(getResources(), R.drawable.milo_idle));
        sprites.put(Mood.BLINK, BitmapFactory.decodeResource(getResources(), R.drawable.milo_blink));
        sprites.put(Mood.CURIOUS, BitmapFactory.decodeResource(getResources(), R.drawable.milo_curious));
        sprites.put(Mood.JOY, BitmapFactory.decodeResource(getResources(), R.drawable.milo_joy));
        sprites.put(Mood.SLEEP, BitmapFactory.decodeResource(getResources(), R.drawable.milo_sleep));
        sprites.put(Mood.SAD, BitmapFactory.decodeResource(getResources(), R.drawable.milo_sad));
    }

    public void setPaused(boolean value) {
        paused = value;
        if (!value) {
            lastInteraction = SystemClock.uptimeMillis();
            invalidate();
        }
    }

    private final Runnable frameLoop = new Runnable() {
        @Override
        public void run() {
            if (!paused) update();
            handler.postDelayed(this, 16);
        }
    };

    private void update() {
        long now = SystemClock.uptimeMillis();
        float idleSeconds = (now - lastInteraction) / 1000f;

        if (!touching && now > moodUntil) {
            if (idleSeconds > 45f) {
                setMood(Mood.SLEEP, 5000);
            } else if (now > nextBlink) {
                setMood(Mood.BLINK, 240);
                nextBlink = now + 2200 + random.nextInt(4200);
            } else if (now > nextCurious) {
                setMood(Mood.CURIOUS, 1600);
                nextCurious = now + 7000 + random.nextInt(7000);
            } else {
                mood = Mood.IDLE;
            }
        }

        energy = Math.max(0.35f, 0.70f - idleSeconds / 180f);
        joy += (0.80f - joy) * 0.002f;
        curiosity += (0.90f - curiosity) * 0.0015f;

        for (int i = hearts.size() - 1; i >= 0; i--) {
            Heart h = hearts.get(i);
            h.x += h.dx;
            h.y += h.dy;
            h.dy *= 0.985f;
            h.life -= 0.012f;
            if (h.life <= 0f) hearts.remove(i);
        }
        invalidate();
    }

    private void interact() {
        lastInteraction = SystemClock.uptimeMillis();
        energy = Math.min(1f, energy + 0.03f);
        joy = Math.min(1f, joy + 0.025f);
    }

    private void setMood(Mood value, long durationMs) {
        mood = value;
        moodUntil = SystemClock.uptimeMillis() + durationMs;
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) return;

        final float t = SystemClock.uptimeMillis() / 1000f;
        drawBackground(c, w, h, t);
        drawHeader(c, w, h);
        drawMilo(c, w, h, t);
        drawStats(c, w, h);
        drawCards(c, w, h);
        drawHint(c, w, h);
        drawBottomNav(c, w, h);

        for (Heart heart : hearts) {
            drawHeart(c, heart.x, heart.y, heart.size, heart.life);
        }
    }

    private void drawBackground(Canvas c, int w, int h, float t) {
        paint.setShader(new LinearGradient(
                0, 0, 0, h,
                new int[]{
                        Color.rgb(255, 247, 249),
                        Color.rgb(255, 239, 242),
                        Color.rgb(255, 248, 244)
                },
                new float[]{0f, 0.56f, 1f},
                Shader.TileMode.CLAMP
        ));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        softPaint.setShader(new RadialGradient(
                w * 0.50f, h * 0.31f, w * 0.48f,
                new int[]{0x88FFFFFF, 0x36FFD3DF, 0x00FFFFFF},
                null, Shader.TileMode.CLAMP
        ));
        c.drawCircle(w * 0.50f, h * 0.31f, w * 0.50f, softPaint);
        softPaint.setShader(null);

        // Warm blurred-looking decorative blobs.
        paint.setColor(0x18F6A2B9);
        c.drawCircle(w * 0.08f, h * 0.24f, w * 0.14f, paint);
        c.drawCircle(w * 0.91f, h * 0.22f, w * 0.12f, paint);
        paint.setColor(0x1CFFD38C);
        c.drawCircle(w * 0.85f, h * 0.34f, w * 0.10f, paint);

        paint.setColor(0x88FFFFFF);
        for (int i = 0; i < 7; i++) {
            float x = w * (0.12f + (i * 0.13f));
            float y = h * (0.16f + ((i % 3) * 0.04f));
            float pulse = 2f + 1.2f * (float) Math.sin(t * 2.2f + i);
            drawSparkle(c, x, y, 5f + pulse);
        }
    }

    private void drawHeader(Canvas c, int w, int h) {
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create("sans-serif-rounded", Typeface.BOLD));
        textPaint.setTextSize(w * 0.105f);
        textPaint.setColor(Color.rgb(241, 73, 137));
        textPaint.setShadowLayer(5f, 0f, 4f, 0x448F2352);
        c.drawText("Milo", w / 2f, h * 0.085f, textPaint);
        textPaint.clearShadowLayer();

        // tiny heart on the logo
        drawHeart(c, w * 0.705f, h * 0.055f, w * 0.025f, 0.95f);

        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        textPaint.setTextSize(w * 0.036f);
        textPaint.setColor(Color.rgb(92, 57, 73));
        c.drawText(statusText(), w / 2f, h * 0.125f, textPaint);
    }

    private void drawMilo(Canvas c, int w, int h, float t) {
        Bitmap bitmap = sprites.get(mood);
        if (bitmap == null) bitmap = sprites.get(Mood.IDLE);
        if (bitmap == null) return;

        // Smaller than v1: roughly 58% of screen width.
        float size = Math.min(w * 0.59f, h * 0.325f);
        float baseY = h * 0.195f;
        float bob = 2.5f * (float) Math.sin(t * 2.1f);
        float scale = 1f + 0.007f * (float) Math.sin(t * 2.0f);

        if (mood == Mood.JOY) {
            bob -= 16f * Math.abs((float) Math.sin(t * 5.4f));
            scale += 0.012f;
        } else if (mood == Mood.SLEEP) {
            bob += 5f;
            scale -= 0.02f;
        }

        float followX = (pointerX - 0.5f) * 8f;
        float followY = (pointerY - 0.5f) * 5f;
        if (!touching) {
            followX *= 0.20f;
            followY *= 0.20f;
        }

        float sw = size * scale;
        float sh = size * scale;
        float left = (w - sw) / 2f + followX;
        float top = baseY + bob + followY;
        miloRect.set(left, top, left + sw, top + sh);

        // halo
        softPaint.setShader(new RadialGradient(
                w / 2f, top + sh * 0.53f, sw * 0.60f,
                new int[]{0x42FFFFFF, 0x28FFB5C9, 0x00FFFFFF},
                null, Shader.TileMode.CLAMP
        ));
        c.drawCircle(w / 2f, top + sh * 0.53f, sw * 0.62f, softPaint);
        softPaint.setShader(null);

        // subtle shadow
        paint.setColor(0x1A7E3A55);
        paint.setShadowLayer(22f, 0f, 11f, 0x337E3A55);
        RectF shadow = new RectF(left + 10, top + 14, left + sw - 10, top + sh - 5);
        c.drawRoundRect(shadow, 38f, 38f, paint);
        paint.clearShadowLayer();

        Path clip = new Path();
        clip.addRoundRect(miloRect, 40f, 40f, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        paint.setAlpha(255);
        c.drawBitmap(bitmap, null, miloRect, paint);
        c.restore();

        // soft border highlight
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0x78FFFFFF);
        c.drawRoundRect(miloRect, 40f, 40f, paint);
        paint.setStyle(Paint.Style.FILL);

        if (mood == Mood.JOY) {
            drawHeart(c, left - 10, top + sh * 0.28f, w * 0.028f, 0.9f);
            drawHeart(c, left + sw + 14, top + sh * 0.40f, w * 0.022f, 0.8f);
        }
    }

    private void drawStats(Canvas c, int w, int h) {
        float startY = h * 0.555f;
        float gap = h * 0.055f;
        drawStat(c, w, startY, "Freude", "♥", joy, Color.rgb(242, 70, 132));
        drawStat(c, w, startY + gap, "Energie", "⚡", energy, Color.rgb(244, 167, 45));
        drawStat(c, w, startY + gap * 2f, "Neugier", "●", curiosity, Color.rgb(158, 100, 235));
    }

    private void drawStat(Canvas c, int w, float y, String label, String icon, float value, int color) {
        float left = w * 0.105f;
        float right = w * 0.895f;
        float circleX = left + w * 0.018f;

        paint.setColor(0xDFFFFFFF);
        c.drawCircle(circleX, y, w * 0.032f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0xFFFFFFFF);
        c.drawCircle(circleX, y, w * 0.032f, paint);
        paint.setStyle(Paint.Style.FILL);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        textPaint.setTextSize(w * 0.027f);
        textPaint.setColor(color);
        c.drawText(icon, circleX, y + w * 0.010f, textPaint);

        float textX = left + w * 0.075f;
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        textPaint.setTextSize(w * 0.031f);
        textPaint.setColor(Color.rgb(77, 42, 57));
        c.drawText(label, textX, y - 5f, textPaint);

        textPaint.setTextAlign(Paint.Align.RIGHT);
        c.drawText(Math.round(value * 100) + "%", right, y - 5f, textPaint);

        float barLeft = textX;
        float barTop = y + w * 0.018f;
        float barRight = right;
        float barH = Math.max(8f, w * 0.010f);

        paint.setColor(0x42C98A9E);
        c.drawRoundRect(barLeft, barTop, barRight, barTop + barH, barH, barH, paint);

        paint.setColor(color);
        c.drawRoundRect(barLeft, barTop, barLeft + (barRight - barLeft) * value,
                barTop + barH, barH, barH, paint);
    }

    private void drawCards(Canvas c, int w, int h) {
        float y = h * 0.735f;
        float cardH = h * 0.115f;
        float gap = w * 0.025f;
        float margin = w * 0.055f;
        float cardW = (w - margin * 2f - gap * 2f) / 3f;

        syncRect.set(margin, y, margin + cardW, y + cardH);
        offlineRect.set(syncRect.right + gap, y, syncRect.right + gap + cardW, y + cardH);
        errorRect.set(offlineRect.right + gap, y, offlineRect.right + gap + cardW, y + cardH);

        drawActionCard(c, syncRect, "↻", "SYNC", "Erfolg", Color.rgb(106, 205, 92));
        drawActionCard(c, offlineRect, "⌁", "OFFLINE", "Verwirrt", Color.rgb(55, 151, 245));
        drawActionCard(c, errorRect, "!", "FEHLER", "Traurig", Color.rgb(255, 92, 102));
    }

    private void drawActionCard(Canvas c, RectF r, String icon, String title, String sub, int color) {
        paint.setColor(0xF4FFFFFF);
        paint.setShadowLayer(14f, 0f, 7f, 0x1E6C334A);
        c.drawRoundRect(r, 28f, 28f, paint);
        paint.clearShadowLayer();

        float cx = r.centerX();
        float iconY = r.top + r.height() * 0.30f;
        float radius = r.width() * 0.18f;
        paint.setColor(color);
        c.drawCircle(cx, iconY, radius, paint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        textPaint.setTextSize(r.width() * 0.22f);
        textPaint.setColor(Color.WHITE);
        c.drawText(icon, cx, iconY + r.width() * 0.075f, textPaint);

        textPaint.setTextSize(r.width() * 0.105f);
        textPaint.setColor(Color.rgb(75, 37, 53));
        c.drawText(title, cx, r.top + r.height() * 0.68f, textPaint);

        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        textPaint.setTextSize(r.width() * 0.075f);
        textPaint.setColor(Color.rgb(96, 74, 83));
        c.drawText(sub, cx, r.top + r.height() * 0.86f, textPaint);
    }

    private void drawHint(Canvas c, int w, int h) {
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        textPaint.setTextSize(w * 0.025f);
        textPaint.setColor(0xAA7E5969);
        c.drawText("Tippen: winken  •  Doppeltippen: freuen  •  Halten: schlafen",
                w / 2f, h * 0.885f, textPaint);
    }

    private void drawBottomNav(Canvas c, int w, int h) {
        float left = w * 0.04f;
        float top = h * 0.91f;
        float right = w * 0.96f;
        float bottom = h * 0.987f;

        paint.setColor(0xF1FFFFFF);
        paint.setShadowLayer(15f, 0, -2f, 0x146C334A);
        c.drawRoundRect(left, top, right, bottom, 34f, 34f, paint);
        paint.clearShadowLayer();

        String[] icons = {"⌂", "♥", "●", "⚙"};
        String[] labels = {"Milo", "Stimmung", "Aktionen", "Einstellungen"};
        for (int i = 0; i < 4; i++) {
            float x = left + (i + 0.5f) * ((right - left) / 4f);
            boolean active = i == 0;
            int color = active ? Color.rgb(243, 70, 137) : Color.rgb(145, 132, 140);

            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            textPaint.setTextSize(w * 0.042f);
            textPaint.setColor(color);
            c.drawText(icons[i], x, top + (bottom - top) * 0.38f, textPaint);

            textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            textPaint.setTextSize(w * 0.022f);
            c.drawText(labels[i], x, top + (bottom - top) * 0.72f, textPaint);
        }

        paint.setColor(Color.rgb(243, 70, 137));
        float tabW = (right - left) / 4f;
        c.drawRoundRect(left + tabW * 0.30f, bottom - 7f,
                left + tabW * 0.70f, bottom - 3f, 6f, 6f, paint);
    }

    private String statusText() {
        switch (mood) {
            case BLINK:
                return "Hallo! ♥";
            case CURIOUS:
                return "Was ist denn da?";
            case JOY:
                return "Freut sich!";
            case SLEEP:
                return "Schläft ganz friedlich …";
            case SAD:
                return "Oh … da stimmt etwas nicht";
            default:
                return "Süß. Lebendig. Immer für dich da.";
        }
    }

    private void drawSparkle(Canvas c, float x, float y, float r) {
        paint.setColor(0xBFFFFFFF);
        Path p = new Path();
        p.moveTo(x, y - r);
        p.lineTo(x + r * 0.22f, y - r * 0.22f);
        p.lineTo(x + r, y);
        p.lineTo(x + r * 0.22f, y + r * 0.22f);
        p.lineTo(x, y + r);
        p.lineTo(x - r * 0.22f, y + r * 0.22f);
        p.lineTo(x - r, y);
        p.lineTo(x - r * 0.22f, y - r * 0.22f);
        p.close();
        c.drawPath(p, paint);
    }

    private void drawHeart(Canvas c, float x, float y, float s, float alpha) {
        paint.setColor(Color.argb((int) (235 * alpha), 244, 80, 139));
        Path p = new Path();
        p.moveTo(x, y + s * 0.40f);
        p.cubicTo(x - s, y - s * 0.25f, x - s * 0.78f, y - s, x, y - s * 0.42f);
        p.cubicTo(x + s * 0.78f, y - s, x + s, y - s * 0.25f, x, y + s * 0.40f);
        c.drawPath(p, paint);
    }

    private void burstHearts(float x, float y, int count) {
        for (int i = 0; i < count; i++) {
            float size = 10f + random.nextFloat() * 17f;
            float dx = (random.nextFloat() - 0.5f) * 1.9f;
            hearts.add(new Heart(
                    x + (random.nextFloat() - 0.5f) * 100f,
                    y + (random.nextFloat() - 0.5f) * 50f,
                    size,
                    dx
            ));
        }
    }

    @SuppressWarnings("deprecation")
    private void vibrate(long ms) {
        android.os.Vibrator v =
                (android.os.Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createOneShot(ms, 65));
        } else {
            v.vibrate(ms);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        gestures.onTouchEvent(e);

        if (getWidth() > 0 && getHeight() > 0) {
            pointerX = Math.max(0f, Math.min(1f, e.getX() / getWidth()));
            pointerY = Math.max(0f, Math.min(1f, e.getY() / getHeight()));
        }

        if (e.getAction() == MotionEvent.ACTION_DOWN ||
                e.getAction() == MotionEvent.ACTION_MOVE) {
            touching = true;
            interact();
        } else if (e.getAction() == MotionEvent.ACTION_UP ||
                e.getAction() == MotionEvent.ACTION_CANCEL) {
            touching = false;

            if (syncRect.contains(e.getX(), e.getY())) {
                setMood(Mood.JOY, 2100);
                burstHearts(miloRect.centerX(), miloRect.centerY(), 10);
                joy = Math.min(1f, joy + 0.10f);
                speaker.say("Alles synchronisiert!");
                vibrate(45);
            } else if (offlineRect.contains(e.getX(), e.getY())) {
                setMood(Mood.CURIOUS, 2500);
                speaker.say("Hm, gerade bin ich offline.");
                curiosity = 1f;
                vibrate(24);
            } else if (errorRect.contains(e.getX(), e.getY())) {
                setMood(Mood.SAD, 2800);
                speaker.say("Oh oh. Da ist etwas schiefgelaufen.");
                joy = Math.max(0.35f, joy - 0.12f);
                vibrate(35);
            }
        }
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }
}
