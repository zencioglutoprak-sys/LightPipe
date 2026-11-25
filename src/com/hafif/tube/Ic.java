package com.hafif.tube;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Kod ile çizilen vektör ikonlar (24x24 ızgara). */
final class Ic extends View {
    static final int PLAY = 0, PAUSE = 1, NEXT = 2, PREV = 3, REPEAT = 4, REPEAT1 = 5, SHUFFLE = 6, SEARCH = 7,
            DOWNLOAD = 8, SHARE = 9, LINK = 10, HOME = 11, SETTINGS = 12, CLOSE = 13, CHECK = 14, TRASH = 15,
            FULL = 16, MUSIC = 17, VIDEO = 18, PLUS = 19, BACK = 20, MORE = 21, QUALITY = 22, FOLDER = 23;

    private int type, color;
    private final int sizePx;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();

    Ic(Context c, int type, float sizeDp, int color) {
        super(c);
        this.type = type;
        this.color = color;
        this.sizePx = U.dp(c, sizeDp);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    void setType(int t) { type = t; invalidate(); }
    int type() { return type; }
    void setColor(int c) { color = c; invalidate(); }

    void tint(int to, long ms) {
        ValueAnimator a = ValueAnimator.ofObject(new ArgbEvaluator(), color, to);
        a.setDuration(ms);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator va) { color = (Integer) va.getAnimatedValue(); invalidate(); }
        });
        a.start();
    }

    @Override protected void onMeasure(int w, int h) { setMeasuredDimension(sizePx, sizePx); }

    private void line(Canvas c, float a, float b, float x, float y) { c.drawLine(a, b, x, y, p); }

    private void stroke() { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2f); }
    private void fill() { p.setStyle(Paint.Style.FILL_AND_STROKE); p.setStrokeWidth(1.6f); }

    @Override protected void onDraw(Canvas c) {
        float s = getWidth() / 24f;
        c.save();
        c.scale(s, s);
        p.setColor(color);
        path.reset();
        switch (type) {
            case PLAY:
                fill();
                path.moveTo(8, 5); path.lineTo(19, 12); path.lineTo(8, 19); path.close();
                c.drawPath(path, p);
                break;
            case PAUSE:
                fill();
                rf.set(6.5f, 5, 10, 19); c.drawRoundRect(rf, 1.2f, 1.2f, p);
                rf.set(14, 5, 17.5f, 19); c.drawRoundRect(rf, 1.2f, 1.2f, p);
                break;
            case NEXT:
                fill();
                path.moveTo(6, 5.5f); path.lineTo(16, 12); path.lineTo(6, 18.5f); path.close();
                c.drawPath(path, p);
                stroke(); line(c, 18.5f, 5.5f, 18.5f, 18.5f);
                break;
            case PREV:
                fill();
                path.moveTo(18, 5.5f); path.lineTo(8, 12); path.lineTo(18, 18.5f); path.close();
                c.drawPath(path, p);
                stroke(); line(c, 5.5f, 5.5f, 5.5f, 18.5f);
                break;
            case REPEAT:
            case REPEAT1:
                stroke();
                path.moveTo(17, 3); path.lineTo(21, 7); path.lineTo(17, 11);
                path.moveTo(21, 7); path.lineTo(7, 7); path.quadTo(3, 7, 3, 11); path.lineTo(3, 12);
                path.moveTo(7, 21); path.lineTo(3, 17); path.lineTo(7, 13);
                path.moveTo(3, 17); path.lineTo(17, 17); path.quadTo(21, 17, 21, 13); path.lineTo(21, 12);
                c.drawPath(path, p);
                if (type == REPEAT1) {
                    path.reset();
                    path.moveTo(10.6f, 10.6f); path.lineTo(12.4f, 9.4f); path.lineTo(12.4f, 14.6f);
                    c.drawPath(path, p);
                }
                break;
            case SHUFFLE:
                stroke();
                path.moveTo(3, 7); path.lineTo(7, 7); path.cubicTo(11, 7, 13, 17, 17, 17); path.lineTo(20, 17);
                path.moveTo(3, 17); path.lineTo(7, 17); path.cubicTo(8.6f, 17, 9.8f, 16, 10.8f, 14.6f);
                path.moveTo(13.2f, 9.4f); path.cubicTo(14.2f, 8, 15.4f, 7, 17, 7); path.lineTo(20, 7);
                path.moveTo(17.5f, 4); path.lineTo(20.5f, 7); path.lineTo(17.5f, 10);
                path.moveTo(17.5f, 14); path.lineTo(20.5f, 17); path.lineTo(17.5f, 20);
                c.drawPath(path, p);
                break;
            case SEARCH:
                stroke();
                c.drawCircle(10.5f, 10.5f, 6.5f, p);
                line(c, 15.4f, 15.4f, 20.5f, 20.5f);
                break;
            case DOWNLOAD:
                stroke();
                line(c, 12, 4, 12, 15);
                path.moveTo(7, 10.5f); path.lineTo(12, 15.5f); path.lineTo(17, 10.5f);
                path.moveTo(4.5f, 17); path.lineTo(4.5f, 20); path.lineTo(19.5f, 20); path.lineTo(19.5f, 17);
                c.drawPath(path, p);
                break;
            case SHARE:
                stroke();
                line(c, 12, 15, 12, 3.5f);
                path.moveTo(8, 7.5f); path.lineTo(12, 3.5f); path.lineTo(16, 7.5f);
                path.moveTo(5, 11); path.lineTo(5, 20); path.lineTo(19, 20); path.lineTo(19, 11);
                c.drawPath(path, p);
                break;
            case LINK:
                stroke();
                rf.set(2.5f, 8.5f, 13.5f, 15.5f); c.drawRoundRect(rf, 3.5f, 3.5f, p);
                rf.set(10.5f, 8.5f, 21.5f, 15.5f); c.drawRoundRect(rf, 3.5f, 3.5f, p);
                break;
            case HOME:
                stroke();
                path.moveTo(4, 11); path.lineTo(12, 4); path.lineTo(20, 11); path.lineTo(20, 20);
                path.lineTo(14.5f, 20); path.lineTo(14.5f, 14); path.lineTo(9.5f, 14); path.lineTo(9.5f, 20);
                path.lineTo(4, 20); path.close();
                c.drawPath(path, p);
                break;
            case SETTINGS:
                stroke();
                c.drawCircle(12, 12, 5.6f, p);
                c.drawCircle(12, 12, 2f, p);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
                    line(c, 12 + cs * 7.6f, 12 + sn * 7.6f, 12 + cs * 9.8f, 12 + sn * 9.8f);
                }
                break;
            case CLOSE:
                stroke();
                line(c, 6, 6, 18, 18); line(c, 18, 6, 6, 18);
                break;
            case CHECK:
                stroke();
                path.moveTo(5, 12.5f); path.lineTo(10, 17.5f); path.lineTo(19, 7);
                c.drawPath(path, p);
                break;
            case TRASH:
                stroke();
                line(c, 4, 7, 20, 7);
                path.moveTo(9, 7); path.lineTo(9, 4); path.lineTo(15, 4); path.lineTo(15, 7);
                path.moveTo(6, 7); path.lineTo(7, 20); path.lineTo(17, 20); path.lineTo(18, 7);
                c.drawPath(path, p);
                line(c, 10, 11, 10, 16); line(c, 14, 11, 14, 16);
                break;
            case FULL:
                stroke();
                path.moveTo(4, 9); path.lineTo(4, 4); path.lineTo(9, 4);
                path.moveTo(15, 4); path.lineTo(20, 4); path.lineTo(20, 9);
                path.moveTo(20, 15); path.lineTo(20, 20); path.lineTo(15, 20);
                path.moveTo(9, 20); path.lineTo(4, 20); path.lineTo(4, 15);
                c.drawPath(path, p);
                break;
            case MUSIC:
                stroke();
                p.setStyle(Paint.Style.FILL_AND_STROKE);
                c.drawCircle(7.5f, 17.5f, 3f, p);
                stroke();
                line(c, 10.5f, 17.5f, 10.5f, 5);
                path.moveTo(10.5f, 5); path.lineTo(19, 7.5f); path.lineTo(19, 10.5f); path.lineTo(10.5f, 8);
                c.drawPath(path, p);
                break;
            case VIDEO:
                stroke();
                rf.set(3, 5.5f, 21, 18.5f); c.drawRoundRect(rf, 3, 3, p);
                fill();
                path.moveTo(10, 9.2f); path.lineTo(15, 12); path.lineTo(10, 14.8f); path.close();
                c.drawPath(path, p);
                break;
            case PLUS:
                stroke();
                line(c, 12, 5, 12, 19); line(c, 5, 12, 19, 12);
                break;
            case BACK:
                stroke();
                line(c, 20, 12, 5, 12);
                path.moveTo(11, 6); path.lineTo(5, 12); path.lineTo(11, 18);
                c.drawPath(path, p);
                break;
            case MORE:
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(12, 5.5f, 1.9f, p); c.drawCircle(12, 12, 1.9f, p); c.drawCircle(12, 18.5f, 1.9f, p);
                break;
            case QUALITY:
                stroke();
                line(c, 4, 8, 20, 8); line(c, 4, 16, 20, 16);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(9, 8, 2.6f, p); c.drawCircle(15, 16, 2.6f, p);
                break;
            case FOLDER:
                stroke();
                path.moveTo(3, 7); path.lineTo(3, 19); path.lineTo(21, 19); path.lineTo(21, 9);
                path.lineTo(12, 9); path.lineTo(10, 6); path.lineTo(4, 6); path.close();
                c.drawPath(path, p);
                break;
            default: break;
        }
        c.restore();
    }
}
