package de.milo.alive;

import android.content.Context;
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
import java.util.Random;

public class MiloView extends View {
    public interface Speaker {
        void say(String text);
    }

    private enum Mood {
        IDLE, BLINK, CURIOUS, SNIFF, WALK, TROT, HOP, JOY,
        WAVE, SIT, LIE, SLEEP, SAD, HAPPY
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final GestureDetector gestures;
    private final Speaker speaker;
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

        textPaint.setTypeface(Typeface.create("sans", Typeface.BOLD));
        chipPaint.setTypeface(Typeface.create("sans", Typeface.BOLD));

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

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

    public void setPaused(boolean value) {
        paused = value;
        if (!value) {
            lastInteraction = SystemClock.uptimeMillis();
            nextBlink = lastInteraction + 1800 + random.nextInt(2500);
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
                setMood(Mood.BLINK, 190);
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
        float t = SystemClock.uptimeMillis() / 1000f;

        drawBackground(c, w, h);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(w * 0.085f);
        textPaint.setColor(Color.rgb(222, 66, 121));
        c.drawText("Milo", w / 2f, h * 0.09f, textPaint);

        textPaint.setTextSize(w * 0.035f);
        textPaint.setColor(Color.rgb(92, 65, 78));
        c.drawText(statusText(), w / 2f, h * 0.135f, textPaint);

        float followX = (pointerX - 0.5f) * 13f;
        float followY = (pointerY - 0.5f) * 8f;
        if (!touching) {
            followX *= 0.25f;
            followY *= 0.25f;
        }

        float bob = 2.5f * (float) Math.sin(t * 2.1f);
        if (mood == Mood.WALK || mood == Mood.TROT) {
            bob = 7f * (float) Math.sin(t * 10.5f);
        } else if (mood == Mood.HOP || mood == Mood.JOY) {
            bob = -20f * Math.abs((float) Math.sin(t * 4.4f));
        }

        float scale = w / 520f;
        c.save();
        c.translate(w / 2f + followX, h * 0.42f + followY + bob);
        c.scale(scale, scale);

        if (mood == Mood.LIE || mood == Mood.SLEEP) {
            c.translate(0, 62);
            c.scale(1.10f, 0.78f);
            c.rotate(4f);
        } else if (mood == Mood.SIT) {
            c.translate(0, 35);
            c.scale(0.96f, 1.03f);
        }

        drawMilo(c, t);
        c.restore();

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

    private void drawBackground(Canvas c, int w, int h) {
        paint.setShader(new RadialGradient(
                w * 0.5f,
                h * 0.38f,
                w * 0.68f,
                new int[]{Color.WHITE, Color.rgb(255, 238, 244), Color.rgb(255, 247, 249)},
                null,
                Shader.TileMode.CLAMP
        ));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        paint.setColor(0x22FFFFFF);
        for (int i = 0; i < 6; i++) {
            float x = w * (0.12f + i * 0.16f);
            float y = h * (0.18f + (i % 2) * 0.06f);
            c.drawCircle(x, y, 4 + (i % 3) * 2, paint);
        }
    }

    private void drawMilo(Canvas c, float t) {
        int pink = Color.rgb(247, 126, 171);
        int pinkDark = Color.rgb(214, 74, 126);
        int pinkLight = Color.rgb(255, 174, 202);
        int snout = Color.rgb(255, 182, 207);
        int outline = Color.rgb(170, 65, 105);
        int eye = Color.rgb(52, 25, 43);

        float breathe = 1f + 0.018f * (float) Math.sin(t * 2.15f);
        float earWiggle = (mood == Mood.CURIOUS || mood == Mood.SNIFF)
                ? 8f * (float) Math.sin(t * 5.0f) : 2f * (float) Math.sin(t * 1.7f);

        c.save();
        c.scale(1f, breathe);

        // Tail behind the body.
        strokePaint.setStrokeWidth(15f);
        strokePaint.setColor(pinkDark);
        Path tail = new Path();
        tail.moveTo(135, 35);
        tail.cubicTo(205, 5, 205, 78, 168, 72);
        c.drawPath(tail, strokePaint);
        paint.setColor(pinkDark);
        c.drawCircle(171, 72, 12, paint);

        // Back legs.
        paint.setColor(pink);
        c.drawOval(new RectF(-126, 72, -50, 158), paint);
        c.drawOval(new RectF(52, 72, 128, 158), paint);

        // Body.
        paint.setColor(pink);
        c.drawOval(new RectF(-154, -8, 154, 137), paint);
        paint.setColor(0x18FFFFFF);
        c.drawOval(new RectF(-105, 8, 95, 102), paint);

        // Front legs.
        float step = (mood == Mood.WALK || mood == Mood.TROT)
                ? 15f * (float) Math.sin(t * 10.5f) : 0f;
        paint.setColor(pink);
        c.drawRoundRect(new RectF(-110, 75 + step, -48, 166 + step), 28, 28, paint);
        c.drawRoundRect(new RectF(52, 75 - step, 114, 166 - step), 28, 28, paint);

        // Hooves.
        paint.setColor(pinkLight);
        c.drawOval(new RectF(-106, 141 + step, -52, 168 + step), paint);
        c.drawOval(new RectF(56, 141 - step, 110, 168 - step), paint);

        // Head.
        paint.setColor(pink);
        c.drawOval(new RectF(-120, -165, 120, 52), paint);

        // Ears.
        c.save();
        c.rotate(-earWiggle, -83, -145);
        paint.setColor(pink);
        c.drawCircle(-88, -147, 42, paint);
        paint.setColor(pinkLight);
        c.drawCircle(-88, -147, 24, paint);
        c.restore();

        c.save();
        c.rotate(earWiggle, 83, -145);
        paint.setColor(pink);
        c.drawCircle(88, -147, 42, paint);
        paint.setColor(pinkLight);
        c.drawCircle(88, -147, 24, paint);
        c.restore();

        // Snout.
        paint.setColor(snout);
        c.drawOval(new RectF(-91, -73, 91, 29), paint);
        paint.setColor(Color.rgb(210, 93, 141));
        c.drawCircle(-38, -27, 7, paint);
        c.drawCircle(38, -27, 7, paint);

        drawEyes(c, eye, t);
        drawBrows(c, outline);
        drawMouth(c, outline);

        // Wave uses one lifted foreleg.
        if (mood == Mood.WAVE) {
            c.save();
            c.rotate(-38f + 8f * (float) Math.sin(t * 8f), 96, 48);
            paint.setColor(pink);
            c.drawRoundRect(new RectF(78, 28, 132, 129), 26, 26, paint);
            paint.setColor(pinkLight);
            c.drawCircle(105, 28, 31, paint);
            paint.setColor(pinkDark);
            strokePaint.setStrokeWidth(4f);
            strokePaint.setColor(pinkDark);
            c.drawLine(94, 17, 94, 1, strokePaint);
            c.drawLine(105, 15, 106, -2, strokePaint);
            c.drawLine(116, 18, 120, 3, strokePaint);
            c.restore();
        }

        // A tiny shine gives the character a toy-like look.
        paint.setColor(0x45FFFFFF);
        c.drawOval(new RectF(-72, -143, -20, -105), paint);

        c.restore();
    }

    private void drawEyes(Canvas c, int eyeColor, float t) {
        float lookX = (pointerX - 0.5f) * 12f;
        float lookY = (pointerY - 0.5f) * 7f;

        boolean closed = mood == Mood.BLINK || mood == Mood.SLEEP;
        if (closed) {
            strokePaint.setColor(eyeColor);
            strokePaint.setStrokeWidth(8f);
            RectF left = new RectF(-75, -118, -19, -72);
            RectF right = new RectF(19, -118, 75, -72);
            c.drawArc(left, 15, 150, false, strokePaint);
            c.drawArc(right, 15, 150, false, strokePaint);
            return;
        }

        paint.setColor(Color.WHITE);
        c.drawOval(new RectF(-78, -126, -18, -57), paint);
        c.drawOval(new RectF(18, -126, 78, -57), paint);

        paint.setColor(eyeColor);
        c.drawCircle(-47 + lookX, -91 + lookY, 21, paint);
        c.drawCircle(47 + lookX, -91 + lookY, 21, paint);

        paint.setColor(Color.rgb(118, 36, 83));
        c.drawCircle(-47 + lookX, -91 + lookY, 11, paint);
        c.drawCircle(47 + lookX, -91 + lookY, 11, paint);

        paint.setColor(Color.WHITE);
        c.drawCircle(-55 + lookX, -100 + lookY, 6, paint);
        c.drawCircle(39 + lookX, -100 + lookY, 6, paint);

        if (mood == Mood.CURIOUS) {
            paint.setColor(0x22FFFFFF);
            c.drawCircle(0, -138, 13 + 2 * (float) Math.sin(t * 3f), paint);
        }
    }

    private void drawBrows(Canvas c, int color) {
        strokePaint.setColor(color);
        strokePaint.setStrokeWidth(5f);

        if (mood == Mood.SAD) {
            c.drawLine(-68, -130, -31, -137, strokePaint);
            c.drawLine(31, -137, 68, -130, strokePaint);
        } else if (mood == Mood.CURIOUS) {
            c.drawLine(-67, -140, -32, -147, strokePaint);
            c.drawLine(32, -133, 67, -128, strokePaint);
        }
    }

    private void drawMouth(Canvas c, int outline) {
        strokePaint.setColor(outline);
        strokePaint.setStrokeWidth(7f);

        if (mood == Mood.SAD) {
            c.drawArc(new RectF(-38, 3, 38, 58), 205, 130, false, strokePaint);
            return;
        }

        if (mood == Mood.JOY || mood == Mood.HAPPY || mood == Mood.WAVE || mood == Mood.HOP) {
            paint.setColor(Color.rgb(104, 25, 58));
            c.drawOval(new RectF(-46, 0, 46, 66), paint);
            paint.setColor(Color.WHITE);
            c.drawOval(new RectF(-21, 4, -1, 24), paint);
            paint.setColor(Color.rgb(255, 113, 157));
            c.drawOval(new RectF(-21, 39, 25, 63), paint);
        } else {
            c.drawArc(new RectF(-40, -1, 40, 42), 25, 130, false, strokePaint);
        }
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
            case SIT:
                return "Ich setz mich kurz zu dir";
            case LIE:
                return "Milo wird langsam müde";
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
