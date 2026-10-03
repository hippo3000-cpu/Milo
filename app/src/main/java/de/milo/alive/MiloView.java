package de.milo.alive;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
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
        IDLE, BLINK, CURIOUS, SNIFF, WALK, TROT, HOP, JOY,
        WAVE, SIT, LIE, SLEEP, SAD, HAPPY
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final GestureDetector gestures;
    private final Speaker speaker;
    private final EnumMap<Mood, Bitmap> sprites = new EnumMap<>(Mood.class);
    private final ArrayList<Heart> hearts = new ArrayList<>();

    private Mood mood = Mood.IDLE;
    private long moodUntil = 0L;
    private long lastInteraction = SystemClock.uptimeMillis();
    private long nextBlink = lastInteraction + 2200;
    private long nextIdleAction = lastInteraction + 5000;
    private float pointerX = 0.5f;
    private float pointerY = 0.5f;
    private boolean touching = false;
    private boolean paused = false;
    private float energy = 0.92f;
    private float happiness = 0.82f;
    private float curiosity = 0.75f;

    private static class Heart {
        float x;
        float y;
        float vy;
        float life;
        float size;

        Heart(float x, float y, float size) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.vy = -1.5f - size / 35f;
            this.life = 1f;
        }
    }

    public MiloView(Context context, Speaker speaker) {
        super(context);
        this.speaker = speaker;

        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setBackgroundColor(Color.rgb(255, 247, 249));
        loadSprites();

        textPaint.setTypeface(Typeface.create("sans", Typeface.BOLD));
        chipPaint.setTypeface(Typeface.create("sans", Typeface.BOLD));

        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                interact();
                setMood(random.nextBoolean() ? Mood.WAVE : Mood.HAPPY, 1500);
                burstHearts(e.getX(), e.getY(), 7);
                vibrate(30);
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                interact();
                setMood(Mood.JOY, 1800);
                burstHearts(e.getX(), e.getY(), 13);
                vibrate(60);
                speaker.say("Juhu!");
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                interact();
                if (mood == Mood.SLEEP) {
                    setMood(Mood.HAPPY, 1500);
                    speaker.say("Ich bin wach!");
                } else {
                    setMood(Mood.SLEEP, 5000);
                    speaker.say("Nur ein kleines Nickerchen.");
                }
            }
        });

        handler.post(frameLoop);
    }

    private void loadSprites() {
        put(Mood.IDLE, R.drawable.milo_idle);
        put(Mood.BLINK, R.drawable.milo_blink);
        put(Mood.CURIOUS, R.drawable.milo_curious);
        put(Mood.SNIFF, R.drawable.milo_sniff);
        put(Mood.WALK, R.drawable.milo_walk);
        put(Mood.TROT, R.drawable.milo_trot);
        put(Mood.HOP, R.drawable.milo_hop);
        put(Mood.JOY, R.drawable.milo_joy);
        put(Mood.WAVE, R.drawable.milo_wave);
        put(Mood.SIT, R.drawable.milo_sit);
        put(Mood.LIE, R.drawable.milo_lie);
        put(Mood.SLEEP, R.drawable.milo_sleep);
        put(Mood.SAD, R.drawable.milo_sad);
        put(Mood.HAPPY, R.drawable.milo_happy);
    }

    private void put(Mood mood, int res) {
        sprites.put(mood, BitmapFactory.decodeResource(getResources(), res));
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
            if (!paused) {
                update();
            }
            handler.postDelayed(this, 16);
        }
    };

    private void update() {
        long now = SystemClock.uptimeMillis();
        float idleSeconds = (now - lastInteraction) / 1000f;

        if (!touching && now > moodUntil) {
            if (idleSeconds > 55) {
                setMood(Mood.SLEEP, 5000);
            } else if (idleSeconds > 35) {
                setMood(Mood.LIE, 3500);
            } else if (idleSeconds > 22) {
                setMood(Mood.SIT, 3000);
            } else if (now > nextBlink) {
                setMood(Mood.BLINK, 260);
                nextBlink = now + 2000 + random.nextInt(5200);
            } else if (now > nextIdleAction) {
                Mood[] choices = {Mood.CURIOUS, Mood.SNIFF, Mood.WALK, Mood.HOP};
                setMood(choices[random.nextInt(choices.length)], 900 + random.nextInt(1000));
                nextIdleAction = now + 3500 + random.nextInt(5000);
            } else {
                mood = Mood.IDLE;
            }
        }

        energy = Math.max(0.2f, Math.min(1f, 0.95f - idleSeconds / 130f));
        happiness += (0.78f - happiness) * 0.002f;
        curiosity += (0.72f - curiosity) * 0.002f;

        for (int i = hearts.size() - 1; i >= 0; i--) {
            Heart h = hearts.get(i);
            h.y += h.vy;
            h.life -= 0.012f;
            h.x += (float) Math.sin((1 - h.life) * 9 + i) * 0.22f;
            if (h.life <= 0) {
                hearts.remove(i);
            }
        }

        invalidate();
    }

    private void interact() {
        lastInteraction = SystemClock.uptimeMillis();
        happiness = Math.min(1f, happiness + 0.08f);
        energy = Math.min(1f, energy + 0.03f);
    }

    private void setMood(Mood value, long durationMs) {
        mood = value;
        moodUntil = SystemClock.uptimeMillis() + durationMs;
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);

        int w = getWidth();
        int h = getHeight();
        long now = SystemClock.uptimeMillis();
        float t = now / 1000f;

        paint.setShader(new RadialGradient(
                w * 0.5f,
                h * 0.39f,
                w * 0.62f,
                new int[]{Color.WHITE, Color.rgb(255, 239, 244), Color.rgb(255, 247, 249)},
                null,
                Shader.TileMode.CLAMP
        ));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(w * 0.085f);
        textPaint.setColor(Color.rgb(222, 66, 121));
        c.drawText("Milo", w / 2f, h * 0.09f, textPaint);

        textPaint.setTextSize(w * 0.035f);
        textPaint.setColor(Color.rgb(92, 65, 78));
        c.drawText(statusText(), w / 2f, h * 0.135f, textPaint);

        Bitmap b = sprites.get(mood);
        if (b == null) {
            return;
        }

        float baseW = w * 0.72f;
        float baseH = baseW * b.getHeight() / b.getWidth();
        float breathe = 1f + 0.012f * (float) Math.sin(t * 2.15f);
        float bob = (mood == Mood.WALK || mood == Mood.TROT || mood == Mood.HOP || mood == Mood.JOY)
                ? (float) Math.sin(t * 11f) * 8f
                : (float) Math.sin(t * 2.1f) * 3f;
        float followX = (pointerX - 0.5f) * 18f;
        float followY = (pointerY - 0.5f) * 10f;

        if (!touching) {
            followX *= 0.25f;
            followY *= 0.25f;
        }

        float dw = baseW * breathe;
        float dh = baseH * breathe;
        float left = (w - dw) / 2f + followX;
        float top = h * 0.20f + (baseH - dh) / 2f + bob + followY;
        RectF dst = new RectF(left, top, left + dw, top + dh);

        paint.setAlpha(255);
        c.drawBitmap(b, null, dst, paint);

        for (Heart heart : hearts) {
            drawHeart(c, heart.x, heart.y, heart.size, heart.life);
        }

        drawMeter(c, "Freude", happiness, w * 0.10f, h * 0.69f, w * 0.80f);
        drawMeter(c, "Energie", energy, w * 0.10f, h * 0.735f, w * 0.80f);
        drawMeter(c, "Neugier", curiosity, w * 0.10f, h * 0.78f, w * 0.80f);

        drawChip(c, 0, "SYNC", "Erfolg");
        drawChip(c, 1, "OFFLINE", "Verwirrt");
        drawChip(c, 2, "FEHLER", "Traurig");

        textPaint.setTextSize(w * 0.027f);
        textPaint.setColor(Color.rgb(112, 86, 98));
        c.drawText(
                "Tippen: winken  •  Doppeltippen: freuen  •  Halten: schlafen",
                w / 2f,
                h * 0.965f,
                textPaint
        );
    }

    private String statusText() {
        switch (mood) {
            case SLEEP:
                return "Pssst … Milo schläft";
            case SAD:
                return "Milo braucht gerade etwas Nähe";
            case CURIOUS:
                return "Was ist denn da?";
            case SNIFF:
                return "Schnupper, schnupper …";
            case JOY:
                return "Juhu!";
            case WAVE:
                return "Hallo!";
            case HAPPY:
                return "Schön, dass du da bist";
            default:
                return "Süß. Lebendig. Immer für dich da.";
        }
    }

    private void drawMeter(Canvas c, String label, float value, float x, float y, float width) {
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(getWidth() * 0.028f);
        textPaint.setColor(Color.rgb(93, 68, 79));
        c.drawText(label, x, y - 8, textPaint);

        paint.setColor(Color.rgb(242, 220, 228));
        c.drawRoundRect(x, y, x + width, y + 12, 10, 10, paint);

        paint.setColor(Color.rgb(239, 104, 151));
        c.drawRoundRect(x, y, x + width * value, y + 12, 10, 10, paint);

        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    private void drawChip(Canvas c, int index, String top, String bottom) {
        float w = getWidth();
        float chipW = w * 0.27f;
        float gap = w * 0.025f;
        float x = w * 0.07f + index * (chipW + gap);
        float y = getHeight() * 0.835f;

        chipPaint.setColor(Color.WHITE);
        chipPaint.setShadowLayer(14, 0, 5, 0x22000000);
        c.drawRoundRect(x, y, x + chipW, y + getHeight() * 0.085f, 24, 24, chipPaint);
        chipPaint.clearShadowLayer();

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(w * 0.027f);
        textPaint.setColor(Color.rgb(222, 66, 121));
        c.drawText(top, x + chipW / 2f, y + getHeight() * 0.034f, textPaint);

        textPaint.setTextSize(w * 0.021f);
        textPaint.setColor(Color.rgb(104, 80, 90));
        c.drawText(bottom, x + chipW / 2f, y + getHeight() * 0.064f, textPaint);
    }

    private void drawHeart(Canvas c, float x, float y, float s, float alpha) {
        paint.setColor(Color.argb((int) (220 * alpha), 244, 92, 145));
        Path p = new Path();
        p.moveTo(x, y + s * 0.35f);
        p.cubicTo(x - s, y - s * 0.25f, x - s * 0.8f, y - s, x, y - s * 0.45f);
        p.cubicTo(x + s * 0.8f, y - s, x + s, y - s * 0.25f, x, y + s * 0.35f);
        c.drawPath(p, paint);
    }

    private void burstHearts(float x, float y, int count) {
        for (int i = 0; i < count; i++) {
            hearts.add(new Heart(
                    x + (random.nextFloat() - 0.5f) * 120,
                    y + (random.nextFloat() - 0.5f) * 50,
                    10 + random.nextFloat() * 18
            ));
        }
    }

    @SuppressWarnings("deprecation")
    private void vibrate(long ms) {
        android.os.Vibrator vibrator =
                (android.os.Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);

        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, 70));
            } else {
                vibrator.vibrate(ms);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        gestures.onTouchEvent(e);

        pointerX = Math.max(0f, Math.min(1f, e.getX() / getWidth()));
        pointerY = Math.max(0f, Math.min(1f, e.getY() / getHeight()));

        if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE) {
            touching = true;
            interact();
            if (mood == Mood.IDLE) {
                setMood(Mood.CURIOUS, 350);
            }
        } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
            touching = false;
        }

        if (e.getAction() == MotionEvent.ACTION_UP
                && e.getY() > getHeight() * 0.82f
                && e.getY() < getHeight() * 0.94f) {
            handleChipTap(e.getX());
        }

        return true;
    }

    private void handleChipTap(float x) {
        float w = getWidth();
        float chipW = w * 0.27f;
        float gap = w * 0.025f;
        float startX = w * 0.07f;

        for (int i = 0; i < 3; i++) {
            float left = startX + i * (chipW + gap);
            float right = left + chipW;
            if (x >= left && x <= right) {
                if (i == 0) {
                    setMood(Mood.JOY, 1800);
                    burstHearts(w / 2f, getHeight() * 0.42f, 10);
                    speaker.say("Alles synchronisiert!");
                } else if (i == 1) {
                    setMood(Mood.CURIOUS, 2200);
                    speaker.say("Hm, gerade bin ich offline.");
                } else {
                    setMood(Mood.SAD, 2500);
                    speaker.say("Oh oh. Da ist etwas schiefgelaufen.");
                }
                break;
            }
        }
    }
}
