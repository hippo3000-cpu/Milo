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

    private static final int MESH_X = 7;
    private static final int MESH_Y = 7;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint softPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final GestureDetector gestures;
    private final Speaker speaker;
    private final EnumMap<Mood, Bitmap> sprites = new EnumMap<>(Mood.class);
    private final ArrayList<Heart> hearts = new ArrayList<>();

    private final RectF miloHitRect = new RectF();
    private final RectF syncRect = new RectF();
    private final RectF offlineRect = new RectF();
    private final RectF errorRect = new RectF();

    private Mood mood = Mood.IDLE;
    private Mood previousMood = Mood.IDLE;
    private long transitionStarted = 0L;
    private long transitionDuration = 240L;
    private long moodUntil = 0L;
    private long lastInteraction = SystemClock.uptimeMillis();
    private long nextBlink = lastInteraction + 2200;
    private long nextCurious = lastInteraction + 6500;

    private boolean touching = false;
    private boolean paused = false;
    private float pointerX = 0.5f;
    private float pointerY = 0.5f;
    private float followX = 0f;
    private float followY = 0f;
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
            this.dy = -2.0f - size / 22f;
            this.life = 1f;
        }
    }

    public MiloView(Context context, Speaker speaker) {
        super(context);
        this.speaker = speaker;

        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setBackgroundColor(Color.rgb(255, 247, 249));
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        sprites.put(Mood.IDLE, BitmapFactory.decodeResource(getResources(), R.drawable.milo_idle));
        sprites.put(Mood.BLINK, BitmapFactory.decodeResource(getResources(), R.drawable.milo_blink));
        sprites.put(Mood.CURIOUS, BitmapFactory.decodeResource(getResources(), R.drawable.milo_curious));
        sprites.put(Mood.JOY, BitmapFactory.decodeResource(getResources(), R.drawable.milo_joy));
        sprites.put(Mood.SLEEP, BitmapFactory.decodeResource(getResources(), R.drawable.milo_sleep));
        sprites.put(Mood.SAD, BitmapFactory.decodeResource(getResources(), R.drawable.milo_sad));

        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                interact();
                if (miloHitRect.contains(e.getX(), e.getY())) {
                    showMood(Mood.BLINK, 1050, 160);
                    burstHearts(e.getX(), e.getY(), 4);
                    vibrate(25);
                    speaker.say("Hallo!");
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                interact();
                if (miloHitRect.contains(e.getX(), e.getY())) {
                    showMood(Mood.JOY, 1900, 170);
                    burstHearts(e.getX(), e.getY(), 11);
                    joy = Math.min(1f, joy + 0.08f);
                    vibrate(55);
                    speaker.say("Juhu!");
                }
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                interact();
                if (miloHitRect.contains(e.getX(), e.getY())) {
                    showMood(Mood.SLEEP, 6200, 420);
                    speaker.say("Nur ein kleines Nickerchen.");
                    vibrate(20);
                }
            }
        });

        handler.post(frameLoop);
    }

    public void setPaused(boolean value) {
        paused = value;
        if (!value) {
            lastInteraction = SystemClock.uptimeMillis();
            nextBlink = lastInteraction + 1800 + random.nextInt(2600);
            invalidate();
        }
    }

    private final Runnable frameLoop = new Runnable() {
        @Override
        public void run() {
            if (!paused) {
                update();
            }
            handler.postDelayed(this, 16);
        }
    };

    private void update() {
        long now = SystemClock.uptimeMillis();
        float idleSeconds = (now - lastInteraction) / 1000f;

        float targetFollowX = touching ? (pointerX - 0.5f) : 0f;
        float targetFollowY = touching ? (pointerY - 0.5f) : 0f;
        followX += (targetFollowX - followX) * 0.12f;
        followY += (targetFollowY - followY) * 0.12f;

        if (!touching && now > moodUntil) {
            if (idleSeconds > 50f) {
                if (mood != Mood.SLEEP) showMood(Mood.SLEEP, 5200, 500);
            } else if (now > nextBlink) {
                showMood(Mood.BLINK, 220, 90);
                nextBlink = now + 2400 + random.nextInt(4300);
            } else if (now > nextCurious) {
                showMood(Mood.CURIOUS, 1350, 250);
                nextCurious = now + 6500 + random.nextInt(6500);
            } else if (mood != Mood.IDLE) {
                showMood(Mood.IDLE, 0, 280);
            }
        }

        energy = Math.max(0.38f, 0.70f - idleSeconds / 190f);
        joy += (0.80f - joy) * 0.0022f;
        curiosity += (0.90f - curiosity) * 0.0018f;

        for (int i = hearts.size() - 1; i >= 0; i--) {
            Heart h = hearts.get(i);
            h.x += h.dx;
            h.y += h.dy;
            h.dy *= 0.988f;
            h.life -= 0.012f;
            if (h.life <= 0f) hearts.remove(i);
        }

        invalidate();
    }

    private void interact() {
        lastInteraction = SystemClock.uptimeMillis();
        energy = Math.min(1f, energy + 0.025f);
        joy = Math.min(1f, joy + 0.02f);
    }

    private void showMood(Mood value, long durationMs, long crossfadeMs) {
        if (value != mood) {
            previousMood = mood;
            mood = value;
            transitionStarted = SystemClock.uptimeMillis();
            transitionDuration = Math.max(1L, crossfadeMs);
        }
        moodUntil = SystemClock.uptimeMillis() + durationMs;
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float t = SystemClock.uptimeMillis() / 1000f;

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
                        Color.rgb(255, 249, 250),
                        Color.rgb(255, 241, 245),
                        Color.rgb(255, 249, 246)
                },
                new float[]{0f, 0.58f, 1f},
                Shader.TileMode.CLAMP
        ));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        softPaint.setShader(new RadialGradient(
                w * 0.50f, h * 0.31f, w * 0.48f,
                new int[]{0xA8FFFFFF, 0x28FFB8CB, 0x00FFFFFF},
                null, Shader.TileMode.CLAMP
        ));
        c.drawCircle(w * 0.50f, h * 0.31f, w * 0.50f, softPaint);
        softPaint.setShader(null);

        paint.setColor(0x12F18BB0);
        c.drawCircle(w * 0.06f, h * 0.25f, w * 0.13f, paint);
        c.drawCircle(w * 0.93f, h * 0.23f, w * 0.12f, paint);
        paint.setColor(0x12FFB974);
        c.drawCircle(w * 0.85f, h * 0.35f, w * 0.09f, paint);

        for (int i = 0; i < 6; i++) {
            float x = w * (0.15f + i * 0.14f);
            float y = h * (0.16f + (i % 2) * 0.035f);
            float pulse = 4.5f + 1.7f * (float) Math.sin(t * 1.8f + i);
            drawSparkle(c, x, y, pulse);
        }
    }

    private void drawHeader(Canvas c, int w, int h) {
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create("sans-serif-rounded", Typeface.BOLD));
        textPaint.setTextSize(w * 0.100f);
        textPaint.setColor(Color.rgb(241, 73, 137));
        textPaint.setShadowLayer(4f, 0f, 3f, 0x337E1C49);
        c.drawText("Milo", w / 2f, h * 0.083f, textPaint);
        textPaint.clearShadowLayer();

        drawHeart(c, w * 0.70f, h * 0.054f, w * 0.020f, 0.94f);

        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        textPaint.setTextSize(w * 0.034f);
        textPaint.setColor(Color.rgb(84, 54, 67));
        c.drawText(statusText(), w / 2f, h * 0.120f, textPaint);
    }

    private void drawMilo(Canvas c, int w, int h, float t) {
        Bitmap current = sprites.get(mood);
        Bitmap previous = sprites.get(previousMood);
        if (current == null) current = sprites.get(Mood.IDLE);
        if (previous == null) previous = current;
        if (current == null) return;

        // Milo is deliberately smaller and has no image-card frame.
        float boxW = w * 0.48f;
        float boxH = h * 0.285f;
        float centerX = w * 0.50f;
        float centerY = h * 0.345f;

        float breathSpeed = mood == Mood.SLEEP ? 1.05f : 2.0f;
        float breath = (float) Math.sin(t * breathSpeed);
        float microSway = (float) Math.sin(t * 1.15f + 0.6f);
        float verticalBob = 2.5f * (float) Math.sin(t * 1.95f);

        if (mood == Mood.JOY) {
            verticalBob -= 13f * Math.abs((float) Math.sin(t * 5.4f));
        } else if (mood == Mood.CURIOUS) {
            verticalBob -= 2f;
        } else if (mood == Mood.SLEEP) {
            verticalBob += 5f;
        }

        float userDx = followX * w * 0.028f;
        float userDy = followY * h * 0.015f;

        miloHitRect.set(
                centerX - boxW * 0.58f,
                centerY - boxH * 0.58f,
                centerX + boxW * 0.58f,
                centerY + boxH * 0.58f
        );

        // Ground shadow only; no rectangle around Milo.
        paint.setColor(0x207B4159);
        paint.setShadowLayer(18f, 0f, 8f, 0x247B4159);
        c.drawOval(
                centerX - boxW * 0.34f,
                centerY + boxH * 0.37f,
                centerX + boxW * 0.34f,
                centerY + boxH * 0.47f,
                paint
        );
        paint.clearShadowLayer();

        long now = SystemClock.uptimeMillis();
        float mix = Math.min(1f, Math.max(0f,
                (now - transitionStarted) / (float) transitionDuration));
        mix = easeInOut(mix);

        if (mix < 1f && previous != current) {
            drawPuppetBitmap(c, previous, centerX, centerY, boxW, boxH,
                    t, breath, microSway, verticalBob, userDx, userDy,
                    1f - mix, previousMood);
        }
        drawPuppetBitmap(c, current, centerX, centerY, boxW, boxH,
                t, breath, microSway, verticalBob, userDx, userDy,
                mix < 1f && previous != current ? mix : 1f, mood);

        // Gentle glow around the actual character, not a card.
        softPaint.setShader(new RadialGradient(
                centerX, centerY, boxW * 0.62f,
                new int[]{0x18FFFFFF, 0x10F77DA7, 0x00FFFFFF},
                null, Shader.TileMode.CLAMP
        ));
        c.drawCircle(centerX, centerY, boxW * 0.64f, softPaint);
        softPaint.setShader(null);
    }

    private void drawPuppetBitmap(
            Canvas c,
            Bitmap bitmap,
            float centerX,
            float centerY,
            float boxW,
            float boxH,
            float t,
            float breath,
            float microSway,
            float verticalBob,
            float userDx,
            float userDy,
            float alpha,
            Mood drawMood
    ) {
        if (bitmap == null || alpha <= 0f) return;

        float aspect = bitmap.getWidth() / (float) bitmap.getHeight();
        float drawW = boxW;
        float drawH = drawW / aspect;
        if (drawH > boxH) {
            drawH = boxH;
            drawW = drawH * aspect;
        }

        float left = centerX - drawW / 2f;
        float top = centerY - drawH / 2f;

        float[] verts = new float[(MESH_X + 1) * (MESH_Y + 1) * 2];
        int index = 0;

        float joyStretch = drawMood == Mood.JOY
                ? 1f + 0.035f * Math.abs((float) Math.sin(t * 5.4f))
                : 1f;
        float sleepySquash = drawMood == Mood.SLEEP ? 0.965f : 1f;

        for (int y = 0; y <= MESH_Y; y++) {
            float ny = y / (float) MESH_Y;
            for (int x = 0; x <= MESH_X; x++) {
                float nx = x / (float) MESH_X;

                float px = left + nx * drawW;
                float py = top + ny * drawH;

                float headFactor = smoothStep(0.68f, 0.05f, ny);
                float bodyFactor = 1f - headFactor;
                float centerOffset = nx - 0.5f;

                // Breathing expands the torso around the middle while keeping feet stable.
                float torso = (float) Math.exp(-Math.pow((ny - 0.62f) / 0.28f, 2));
                px += centerOffset * drawW * torso * (0.010f + 0.010f * breath);

                // Head follows touch more strongly than the feet.
                px += userDx * (0.25f + headFactor * 0.95f);
                py += userDy * (0.15f + headFactor * 0.85f);

                // Subtle head/body sway makes Milo never fully static.
                px += microSway * drawW * 0.008f * headFactor;
                py += verticalBob * (0.35f + headFactor * 0.65f);

                // Tiny faux rotation/lean from mesh displacement.
                px += centerOffset * microSway * drawW * 0.006f * headFactor;

                // Mood-specific body language.
                if (drawMood == Mood.CURIOUS) {
                    py -= headFactor * drawH * 0.018f;
                    px += headFactor * drawW * 0.012f;
                } else if (drawMood == Mood.SAD) {
                    py += headFactor * drawH * 0.014f;
                } else if (drawMood == Mood.SLEEP) {
                    py += bodyFactor * drawH * 0.008f;
                }

                // Squash/stretch on joy without moving the paws too much.
                float relY = (ny - 1f);
                py = centerY + (py - centerY) * joyStretch * sleepySquash
                        + relY * drawH * (joyStretch - 1f) * 0.22f;

                // Light tail-side wiggle in the lower outside area.
                if (nx > 0.72f && ny > 0.42f && ny < 0.82f) {
                    px += drawW * 0.009f * (float) Math.sin(t * 3.0f + ny * 5f);
                }

                verts[index++] = px;
                verts[index++] = py;
            }
        }

        paint.setAlpha(Math.max(0, Math.min(255, (int) (alpha * 255f))));
        c.drawBitmapMesh(
                bitmap,
                MESH_X,
                MESH_Y,
                verts,
                0,
                null,
                0,
                paint
        );
        paint.setAlpha(255);
    }

    private float easeInOut(float x) {
        return x * x * (3f - 2f * x);
    }

    private float smoothStep(float edge0, float edge1, float x) {
        if (edge0 == edge1) return 0f;
        float v = (x - edge0) / (edge1 - edge0);
        v = Math.max(0f, Math.min(1f, v));
        return v * v * (3f - 2f * v);
    }

    private void drawStats(Canvas c, int w, int h) {
        float startY = h * 0.565f;
        float gap = h * 0.055f;
        drawStat(c, w, startY, "Freude", "♥", joy, Color.rgb(242, 70, 132));
        drawStat(c, w, startY + gap, "Energie", "⚡", energy, Color.rgb(244, 167, 45));
        drawStat(c, w, startY + gap * 2f, "Neugier", "•", curiosity, Color.rgb(158, 100, 235));
    }

    private void drawStat(Canvas c, int w, float y, String label, String icon, float value, int color) {
        float left = w * 0.105f;
        float right = w * 0.895f;
        float circleX = left + w * 0.018f;

        paint.setColor(0xE8FFFFFF);
        c.drawCircle(circleX, y, w * 0.031f, paint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        textPaint.setTextSize(w * 0.027f);
        textPaint.setColor(color);
        c.drawText(icon, circleX, y + w * 0.009f, textPaint);

        float textX = left + w * 0.075f;
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        textPaint.setTextSize(w * 0.030f);
        textPaint.setColor(Color.rgb(75, 43, 56));
        c.drawText(label, textX, y - 5f, textPaint);

        textPaint.setTextAlign(Paint.Align.RIGHT);
        c.drawText(Math.round(value * 100) + "%", right, y - 5f, textPaint);

        float barLeft = textX;
        float barTop = y + w * 0.017f;
        float barRight = right;
        float barH = Math.max(7f, w * 0.009f);

        paint.setColor(0x3DC98A9E);
        c.drawRoundRect(barLeft, barTop, barRight, barTop + barH, barH, barH, paint);

        paint.setColor(color);
        c.drawRoundRect(barLeft, barTop, barLeft + (barRight - barLeft) * value,
                barTop + barH, barH, barH, paint);
    }

    private void drawCards(Canvas c, int w, int h) {
        float y = h * 0.745f;
        float cardH = h * 0.110f;
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
        paint.setColor(0xF6FFFFFF);
        paint.setShadowLayer(12f, 0f, 5f, 0x1C6C334A);
        c.drawRoundRect(r, 26f, 26f, paint);
        paint.clearShadowLayer();

        float cx = r.centerX();
        float iconY = r.top + r.height() * 0.31f;
        float radius = r.width() * 0.17f;

        paint.setColor(color);
        c.drawCircle(cx, iconY, radius, paint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        textPaint.setTextSize(r.width() * 0.20f);
        textPaint.setColor(Color.WHITE);
        c.drawText(icon, cx, iconY + r.width() * 0.067f, textPaint);

        textPaint.setTextSize(r.width() * 0.100f);
        textPaint.setColor(Color.rgb(75, 37, 53));
        c.drawText(title, cx, r.top + r.height() * 0.69f, textPaint);

        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        textPaint.setTextSize(r.width() * 0.072f);
        textPaint.setColor(Color.rgb(96, 74, 83));
        c.drawText(sub, cx, r.top + r.height() * 0.87f, textPaint);
    }

    private void drawHint(Canvas c, int w, int h) {
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        textPaint.setTextSize(w * 0.024f);
        textPaint.setColor(0x9B7E5969);
        c.drawText(
                "Tippen: winken  •  Doppeltippen: freuen  •  Halten: schlafen",
                w / 2f,
                h * 0.890f,
                textPaint
        );
    }

    private void drawBottomNav(Canvas c, int w, int h) {
        float left = w * 0.04f;
        float top = h * 0.915f;
        float right = w * 0.96f;
        float bottom = h * 0.988f;

        paint.setColor(0xF4FFFFFF);
        paint.setShadowLayer(14f, 0, -2f, 0x126C334A);
        c.drawRoundRect(left, top, right, bottom, 32f, 32f, paint);
        paint.clearShadowLayer();

        String[] icons = {"⌂", "♥", "•", "⚙"};
        String[] labels = {"Milo", "Stimmung", "Aktionen", "Einstellungen"};

        for (int i = 0; i < 4; i++) {
            float x = left + (i + 0.5f) * ((right - left) / 4f);
            boolean active = i == 0;
            int color = active ? Color.rgb(243, 70, 137) : Color.rgb(145, 132, 140);

            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            textPaint.setTextSize(w * 0.041f);
            textPaint.setColor(color);
            c.drawText(icons[i], x, top + (bottom - top) * 0.38f, textPaint);

            textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            textPaint.setTextSize(w * 0.021f);
            c.drawText(labels[i], x, top + (bottom - top) * 0.72f, textPaint);
        }

        paint.setColor(Color.rgb(243, 70, 137));
        float tabW = (right - left) / 4f;
        c.drawRoundRect(
                left + tabW * 0.31f,
                bottom - 7f,
                left + tabW * 0.69f,
                bottom - 3f,
                6f,
                6f,
                paint
        );
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
        paint.setColor(0xAFFFFFFF);
        Path p = new Path();
        p.moveTo(x, y - r);
        p.lineTo(x + r * 0.20f, y - r * 0.20f);
        p.lineTo(x + r, y);
        p.lineTo(x + r * 0.20f, y + r * 0.20f);
        p.lineTo(x, y + r);
        p.lineTo(x - r * 0.20f, y + r * 0.20f);
        p.lineTo(x - r, y);
        p.lineTo(x - r * 0.20f, y - r * 0.20f);
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
                    x + (random.nextFloat() - 0.5f) * 95f,
                    y + (random.nextFloat() - 0.5f) * 45f,
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

            // While the finger moves near Milo, he leans/follows continuously.
            if (miloHitRect.contains(e.getX(), e.getY()) && mood == Mood.IDLE) {
                if (Math.abs(pointerX - 0.5f) > 0.18f ||
                        Math.abs(pointerY - 0.35f) > 0.18f) {
                    showMood(Mood.CURIOUS, 450, 180);
                }
            }
        } else if (e.getAction() == MotionEvent.ACTION_UP ||
                e.getAction() == MotionEvent.ACTION_CANCEL) {
            touching = false;

            if (syncRect.contains(e.getX(), e.getY())) {
                showMood(Mood.JOY, 2100, 180);
                burstHearts(miloHitRect.centerX(), miloHitRect.centerY(), 10);
                joy = Math.min(1f, joy + 0.10f);
                speaker.say("Alles synchronisiert!");
                vibrate(45);
            } else if (offlineRect.contains(e.getX(), e.getY())) {
                showMood(Mood.CURIOUS, 2600, 250);
                curiosity = 1f;
                speaker.say("Hm, gerade bin ich offline.");
                vibrate(24);
            } else if (errorRect.contains(e.getX(), e.getY())) {
                showMood(Mood.SAD, 3000, 320);
                joy = Math.max(0.35f, joy - 0.12f);
                speaker.say("Oh oh. Da ist etwas schiefgelaufen.");
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
