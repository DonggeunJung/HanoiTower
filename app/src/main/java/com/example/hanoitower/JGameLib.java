/* JGameLib_Java : 2D Game library for education      */
/* Date : 2023.Jan.04 ~ 2023.Jan.22                   */
/* Author : Dennis (Donggeun Jung)                    */
/* Contact : topsan72@gmail.com                       */
package com.example.hanoitower;

import static android.content.Context.MODE_PRIVATE;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;

public class JGameLib extends View implements SensorEventListener {
    static String TAG = "JGameLib";
    boolean firstDraw = true;
    float totalPixelW = 480, totalPixelH = 800;
    float blocksW = 480, blocksH = 800;
    float blockSize = totalPixelH / blocksH;
    RectF screenRect;
    int timerGap1 = 50;
    int timerGap2 = 50;
    boolean needDraw = false;
    ArrayList<Card> cards = new ArrayList();
    Card touchedCard = null;
    float touchX = 0;
    float touchY = 0;
    HashSet<Card> removeCards = new HashSet();
    SharedPreferences sharedPref = null;

    public JGameLib(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    void init(Canvas canvas) {
        totalPixelW = canvas.getWidth();
        totalPixelH = canvas.getHeight();
        screenRect = getScreenRect();
        blockSize = screenRect.width() / blocksW;
        timer1.removeMessages(0);
        timer1.sendEmptyMessageDelayed(0, 50);
        timer2.removeMessages(0);
        if(audioBeeps.isEmpty()) {
            for(int i=0; i < 3; i++) {
                audioBeeps.add(new AudioBeep(this.getContext()));
            }
        }
    }

    RectF getScreenRect() {
        float pixelsRatio = totalPixelW / totalPixelH;
        float blocksRatio = blocksW / blocksH;
        RectF rect = new RectF();
        if(pixelsRatio > blocksRatio) {
            rect.top = 0;
            rect.bottom = totalPixelH;
            float screenW = totalPixelH * blocksRatio;
            rect.left = (totalPixelW - screenW) / 2.f;
            rect.right = rect.left + screenW;
        } else {
            rect.left = 0;
            rect.right = totalPixelW;
            float screenH = totalPixelW / blocksRatio;
            rect.top = (totalPixelH - screenH) / 2.f;
            rect.bottom = rect.top + screenH;
        }
        return rect;
    }

    void redraw() {
        this.invalidate();
    }

    public void onDraw(Canvas canvas) {
        if( firstDraw ) {
            firstDraw = false;
            init(canvas);
        }

        Paint pnt = new Paint();
        pnt.setStyle(Paint.Style.FILL);
        pnt.setAntiAlias(true);
        ArrayList<Integer> removeIndices = new ArrayList<>();

        for(int i=0; i < cards.size(); i++) {
            Card card = cards.get(i);
            if(!card.visible) continue;
            if(!removeCards.isEmpty() && removeCards.contains(card)) {
                removeIndices.add(i);
                continue;
            }
            RectF scrRect = screenRect;
            if(card.dstRect != null) {
                scrRect = getDstRect(card);
                if(!checkCollision(scrRect, screenRect)) {
                    if(card.autoRemove)
                        removeIndices.add(i);
                    continue;
                }
            }
            if(card.backType == 1 && card.bmp != null) {
                if(card.srcRect == null) {
                    canvas.drawBitmap(card.bmp, null, scrRect, pnt);
                } else {
                    drawBitmap(canvas, pnt, card.bmp, scrRect, card.srcRect);
                }
            } else {
                drawRect(canvas, pnt, card, scrRect);
            }
            if(card.text != null) {
                drawText(canvas, pnt, scrRect, card);
            }
        }
        checkRemoveCards(removeIndices);
    }

    void checkRemoveCards(ArrayList<Integer> removeIndices) {
        for (int i = removeIndices.size() - 1; i >= 0; i--) {
            int idx = removeIndices.get(i);
            cards.remove(idx);
        }
        removeCards.clear();
    }

    void drawRect(Canvas canvas, Paint pnt, Card card, RectF dstRect) {
        if(card.edgeThick > 0f) {
            pnt.setStyle(Paint.Style.STROKE);
            float strokeWidth = blockSize * card.edgeThick;
            float strokeHalf = strokeWidth / 2f;
            dstRect.left += strokeHalf;
            dstRect.right -= strokeHalf;
            dstRect.top += strokeHalf;
            dstRect.bottom -= strokeHalf;
            pnt.setStrokeWidth(strokeWidth);
            pnt.setColor(card.edgeColor);
            canvas.drawRect(dstRect, pnt);
        }
        pnt.setStyle(Paint.Style.FILL);
        pnt.setColor(card.backColor);
        canvas.drawRect(dstRect, pnt);
    }

    void drawText(Canvas canvas, Paint pnt, RectF dstRect, Card card) {
        int textSizePixel = (int)(blockSize * card.textSize);
        pnt.setTextSize(textSizePixel);
        pnt.setColor(card.textColor);
        pnt.setTextAlign(Paint.Align.CENTER);
        float y = dstRect.centerY() + (textSizePixel / 3f);
        canvas.drawText(card.text, dstRect.centerX(), y, pnt);
    }

    void drawBitmap(Canvas canvas, Paint pnt, Bitmap bmp, RectF dstRect, RectF srcRect) {
        if(bmp == null) return;
        if(srcRect == null) {
            canvas.drawBitmap(bmp, null, dstRect, pnt);
            return;
        }
        float bmpPixelW = bmp.getWidth();
        float bmpPixelH = bmp.getHeight();
        float sourceRectL = srcRect.left / 100f * bmpPixelW;
        float sourceRectR = srcRect.right / 100f * bmpPixelW;
        float sourceRectT = srcRect.top / 100f * bmpPixelH;
        float sourceRectB = srcRect.bottom / 100f * bmpPixelH;
        if(sourceRectL > bmpPixelW || sourceRectT > bmpPixelH) return;
        Rect sourceRect = new Rect((int)sourceRectL, (int)sourceRectT, (int)sourceRectR, (int)sourceRectB);
        RectF screenRect = new RectF(dstRect);
        if(sourceRect.right > bmpPixelW) {
            sourceRect.right = (int)bmpPixelW;
            float firstRate = (float)sourceRect.width() / (sourceRectR - sourceRectL);
            float firstDstWidth = screenRect.width() * firstRate;
            screenRect.right = screenRect.left + firstDstWidth;
            sourceRectR -= sourceRect.right;
            Rect sourceRect2 = new Rect(0, (int)sourceRectT, (int)sourceRectR, (int)sourceRectB);
            RectF screenRect2 = new RectF(dstRect);
            screenRect2.left = screenRect.right;
            canvas.drawBitmap(bmp, sourceRect2, screenRect2, pnt);
        } else if(sourceRect.bottom > bmpPixelH) {
            sourceRect.bottom = (int)bmpPixelH;
            float firstRate = (float)sourceRect.height() / (sourceRectB - sourceRectT);
            float firstDstHeight = screenRect.height() * firstRate;
            screenRect.bottom = screenRect.top + firstDstHeight;
            sourceRectB -= sourceRect.bottom;
            Rect sourceRect2 = new Rect((int)sourceRectL, 0, (int)sourceRectR, (int)sourceRectB);
            RectF screenRect2 = new RectF(dstRect);
            screenRect2.top = screenRect.bottom;
            canvas.drawBitmap(bmp, sourceRect2, screenRect2, pnt);
        } else if(sourceRect.left < 0) {
            sourceRect.left = 0;
            float firstRate = (float)sourceRect.width() / (sourceRectR - sourceRectL);
            float firstDstWidth = screenRect.width() * firstRate;
            screenRect.left = screenRect.right - firstDstWidth;
            sourceRectL += bmpPixelW;
            Rect sourceRect2 = new Rect((int)sourceRectL, (int)sourceRectT, (int)bmpPixelW, (int)sourceRectB);
            RectF screenRect2 = new RectF(dstRect);
            screenRect2.right = screenRect.left;
            canvas.drawBitmap(bmp, sourceRect2, screenRect2, pnt);
        } else if(sourceRect.top < 0) {
            sourceRect.top = 0;
            float firstRate = (float)sourceRect.height() / (sourceRectB - sourceRectT);
            float firstDstHeight = screenRect.height() * firstRate;
            screenRect.top = screenRect.bottom - firstDstHeight;
            sourceRectT += bmpPixelH;
            Rect sourceRect2 = new Rect((int)sourceRectL, (int)sourceRectT, (int)sourceRectR, (int)bmpPixelH);
            RectF screenRect2 = new RectF(dstRect);
            screenRect2.bottom = screenRect.top;
            canvas.drawBitmap(bmp, sourceRect2, screenRect2, pnt);
        }
        canvas.drawBitmap(bmp, sourceRect, screenRect, pnt);
    }

    Handler timer1 = new Handler(new Handler.Callback() {
        public boolean handleMessage(Message msg) {
            if (needDraw) {
                needDraw = false;
                ArrayList<Card> collisionCards = new ArrayList<>();
                for(int i=0; i < cards.size(); i++) {
                    Card card = cards.get(i);
                    card.next();
                    if(card.dstRect != null && card.checkCollision) {
                        for(Card card2 : collisionCards) {
                            if(checkCollision(card.dstRect, card2.dstRect) && listener != null) {
                                listener.onGameCollision(card2, card);
                            }
                        }
                        collisionCards.add(card);
                    }
                }
                redraw();
            }
            timer1.sendEmptyMessageDelayed(0, timerGap1);
            return false;
        }
    });

    Handler timer2 = new Handler(new Handler.Callback() {
        public boolean handleMessage(Message msg) {
            if(listener != null)
                listener.onGameTimer();
            timer2.sendEmptyMessageDelayed(0, timerGap2);
            return false;
        }
    });

    RectF getDstRect(Card card) {
        RectF rect = new RectF(0,0,0,0);
        if(card.dstRect == null) return rect;
        rect.left = screenRect.left + card.dstRect.left * blockSize;
        rect.right = screenRect.left + card.dstRect.right * blockSize;
        rect.top = screenRect.top + card.dstRect.top * blockSize;
        rect.bottom = screenRect.top + card.dstRect.bottom * blockSize;
        return rect;
    }

    float getBlocksHorizontal(float pixelH) {
        return (pixelH-screenRect.left) / blockSize;
    }

    float getBlocksVertical(float pixelV) {
        return (pixelV-screenRect.top) / blockSize;
    }

    Bitmap getBitmap(int resid) {
        return BitmapFactory.decodeResource(getResources(), resid);
    }

    Card findCard(float pixelX, float pixelY) {
        for(Card card : cards) {
            if(!card.visible) continue;
            RectF rect = getDstRect(card);
            if(rect.contains(pixelX, pixelY)) {
                return card;
            }
        }
        return null;
    }

    Bitmap loadBitmap(ArrayList<Integer> resids, double idx) {
        if(resids.isEmpty() || (int)idx < 0 || (int)idx >= resids.size())
            return null;
        int resid = resids.get((int)idx);
        return getBitmap(resid);
    }

    SharedPreferences getSharedPref() {
        if(sharedPref == null)
            sharedPref = getContext().getSharedPreferences(TAG, MODE_PRIVATE);
        return sharedPref;
    }

    SharedPreferences.Editor getSharedPrefEdit() {
        return getSharedPref().edit();
    }

    // Inside Class start ====================================

    class Card {
        ArrayList<Integer> resids = new ArrayList();
        Bitmap bmp;
        double idx = -1;
        double unitIdx = 0, endIdx = 0;
        RectF dstRect = null;
        float unitHrz=0, unitVtc=0;
        float endL, endT;
        float unitW=0, unitH=0;
        float endW, endH;
        RectF srcRect = null;
        float unitSrcL=0, unitSrcT=0;
        float endSrcL, endSrcT;
        boolean visible = true;
        int backColor = 0x00000000;
        int backType = 1;
        int edgeColor = Color.TRANSPARENT;
        float edgeThick = 0f;
        boolean moveEnd = true;
        boolean checkCollision = false;
        String text = null;
        int textColor = Color.rgb(128,128,128);
        double textSize = 10;
        boolean autoRemove = false;
        int valueN = 0;
        double valueF = 0;
        boolean valueB = false;
        String valueS = "";

        Card(int clr, int type) {
            backType = type;
            this.backColor = clr;
        }

        Card(int resid) {
            resids.add(resid);
            idx = 0;
            loadBmp();
        }

        void next() {
            if(!visible) return;
            nextMove();
            nextResize();
            nextImageChange();
            nextSourceRect();
        }

        void nextMove() {
            if(unitHrz == 0 && unitVtc == 0) return;
            float currL = dstRect.left, currT = dstRect.top;
            float nextL = currL + unitHrz, nextT = currT + unitVtc;
            if(!moveEnd) {
                move(nextL, nextT);
                return;
            }

            if((unitHrz != 0 && Math.min(currL,nextL) <= endL && endL <= Math.max(currL,nextL))
                    || (unitVtc != 0 && Math.min(currT,nextT) <= endT && endT <= Math.max(currT,nextT))) {
                unitHrz = unitVtc = 0;
                nextL = endL;
                nextT = endT;
            }
            move(nextL, nextT);
            if(unitHrz == 0 && unitVtc == 0 && listener != null)
                listener.onGameWorkEnded(this, WorkType.MOVE);
        }

        void nextResize() {
            if(unitW == 0 && unitH == 0) return;
            float currW = dstRect.width(), currH = dstRect.height();
            float nextW = currW + unitW, nextH = currH + unitH;
            if((unitW != 0 && Math.min(currW,nextW) <= endW && endW <= Math.max(currW,nextW))
                    || (unitH != 0 && Math.min(currW,nextW) <= endH && endH <= Math.max(currW,nextW))) {
                unitW = unitH = 0;
                nextW = endW;
                nextH = endH;
            }
            resize(nextW, nextH);
            if(unitW == 0 && unitH == 0 && listener != null)
                listener.onGameWorkEnded(this, WorkType.RESIZE);
        }

        void nextImageChange() {
            if(unitIdx == 0) return;
            double curridx = idx;
            double nextIdx = curridx + unitIdx;
            if(nextIdx > endIdx || nextIdx >= resids.size()) {
                unitIdx = 0;
                nextIdx = Math.min((int)endIdx, resids.size()-1);
            }
            idx = nextIdx;
            if((int)nextIdx > (int)curridx) {
                loadBmp();
            }
            if(unitIdx == 0 && listener != null)
                listener.onGameWorkEnded(this, WorkType.IMAGE_CHANGE);
            needDraw = true;
        }

        void nextSourceRect() {
            if(unitSrcL == 0 && unitSrcT == 0) return;
            float currL = srcRect.left, currT = srcRect.top;
            float nextL = currL + unitSrcL, nextT = currT + unitSrcT;

            if((unitSrcL != 0 && Math.min(currL,nextL) <= endSrcL && endSrcL <= Math.max(currL,nextL))
                    || (unitSrcT != 0 && Math.min(currT,nextT) <= endSrcT && endSrcT <= Math.max(currT,nextT))) {
                unitSrcL = unitSrcT = 0;
                nextL = endSrcL;
                nextT = endSrcT;
            }
            sourceRect(nextL, nextT, srcRect.width(), srcRect.height());
            if(unitSrcL == 0 && unitSrcT == 0 && listener != null)
                listener.onGameWorkEnded(this, WorkType.SOURCE_RECT);
        }

        void movingTarget(float l, float t, float unitH, float unitV) {
            this.endL = l;
            this.endT = t;
            this.unitHrz = unitH;
            this.unitVtc = unitV;
            moveEnd = true;
            needDraw = true;
        }

        void loadBmp() {
            bmp = loadBitmap(resids, idx);
        }

        // Card API start ====================================

        public RectF sourceRect() {
            return srcRect;
        }

        public void sourceRect(double l, double t, double w, double h) {
            RectF rect = new RectF((float)l, (float)t, (float)(l+w), (float)(t+h));
            sourceRect(rect);
        }

        public void sourceRect(RectF rect) {
            srcRect = rect;
            needDraw = true;
        }

        public void sourceRectIng(double l, double t, double time) {
            this.endSrcL = (float)l;
            this.endSrcT = (float)t;
            float frames = (float)framesOfTime(time);
            if(frames != 0) {
                this.unitSrcL = (this.endSrcL - this.srcRect.left) / frames;
                this.unitSrcT = (this.endSrcT - this.srcRect.top) / frames;
            } else {
                this.unitSrcL = 0;
                this.unitSrcT = 0;
            }
            needDraw = true;
        }

        public void stopSourceRectIng() {
            this.unitSrcL = 0;
            this.unitSrcT = 0;
        }

        public boolean isSourceRectIng() {
            return unitSrcL != 0 || unitSrcT != 0;
        }

        public void addImage(int resid) {
            resids.add(resid);
        }

        public void removeImage(int idx) {
            if(idx >= resids.size()) return;
            resids.remove(idx);
        }

        public void visible(boolean s) {
            visible = s;
            needDraw = true;
        }

        public RectF screenRect() {
            return new RectF(this.dstRect);
        }

        public void screenRect(double l, double t, double w, double h) {
            this.dstRect.left = (float)l;
            this.dstRect.top = (float)t;
            this.dstRect.right = (float)l + (float)w;
            this.dstRect.bottom = (float)t + (float)h;
            needDraw = true;
        }

        public void screenRectGap(double gapL, double gapT, double gapW, double gapH) {
            this.dstRect.left += (float)(gapL);
            this.dstRect.right += (float)(gapL + gapW);
            this.dstRect.top += (float)(gapT);
            this.dstRect.bottom += (float)(gapT + gapH);
            needDraw = true;
        }

        public void move(double l, double t) {
            float w = this.dstRect.width(), h = this.dstRect.height();
            screenRect(l, t, w, h);
        }

        public void moving(double l, double t, double time) {
            double unitH = 0, unitV = 0;
            double frames = framesOfTime(time);
            if(frames != 0) {
                unitH = (l - this.dstRect.left) / frames;
                unitV = (t - this.dstRect.top) / frames;
            }
            movingTarget((float)l, (float)t, (float)unitH, (float)unitV);
        }

        public void movingSpeed(double l, double t, double speed) {
            double gapX = (l - this.dstRect.left);
            double gapY = (t - this.dstRect.top);
            double diagonal = Math.sqrt(gapX*gapX + gapY*gapY);
            double rate = speed / diagonal;
            float unitH = (float)(gapX * rate);
            float unitV = (float)(gapY * rate);
            movingTarget((float)l, (float)t, unitH, unitV);
        }

        public void movingDir(double hrz, double vtc) {
            this.unitHrz = (float)hrz;
            this.unitVtc = (float)vtc;
            moveEnd = false;
            needDraw = true;
        }

        public void stopMoving() {
            this.unitHrz = 0;
            this.unitVtc = 0;
        }

        public boolean isMoving() {
            return unitHrz != 0 || unitVtc != 0;
        }

        public void moveGap(double gapL, double gapT) {
            move(this.dstRect.left+(float)gapL, this.dstRect.top+(float)gapT);
        }

        public void movingGap(double gapL, double gapT, double time) {
            moving(this.dstRect.left+(float)gapL, this.dstRect.top+(float)gapT, time);
        }

        public void resize(double w, double h) {
            this.dstRect.left = this.dstRect.centerX() - (float)(w / 2.);
            this.dstRect.right = this.dstRect.left + (float)w;
            this.dstRect.top = this.dstRect.centerY() - (float)(h / 2.);
            this.dstRect.bottom = this.dstRect.top + (float)h;
            needDraw = true;
        }

        public void resizing(double w, double h, double time) {
            this.endW = (float)w;
            this.endH = (float)h;
            float frames = (float)framesOfTime(time);
            if(frames != 0) {
                this.unitW = (this.endW - this.dstRect.width()) / frames;
                this.unitH = (this.endH - this.dstRect.height()) / frames;
            } else {
                this.unitW = 0;
                this.unitH = 0;
            }
            needDraw = true;
        }

        public void resizeGap(double w, double h) {
            resize(this.dstRect.width()+w, this.dstRect.height()+h);
        }

        public void resizingGap(double w, double h, double time) {
            resizing(this.dstRect.width()+w, this.dstRect.height()+h, time);
        }

        public void stopResizing() {
            this.unitW = 0;
            this.unitH = 0;
        }

        public boolean isResizing() {
            return unitW != 0 || unitH != 0;
        }

        public int imageIndex() {
            return (int)this.idx;
        }

        public void imageChange(int idx) {
            imageChanging(idx, idx, 0);
        }

        public void imageChanging(double time) {
            if(this.resids.isEmpty()) return;
            imageChanging(0, this.resids.size()-1, time);
        }

        public void imageChanging(int start, int end, double time) {
            if(this.resids.isEmpty()) return;
            if(this.idx != start) {
                this.idx = start;
                this.loadBmp();
            }
            this.endIdx = end;
            double frames = framesOfTime(time);
            if(frames != 0)
                this.unitIdx = (double)(end - start + 1) / frames;
            else
                this.unitIdx = 0;
            needDraw = true;
        }

        public void stopImageChanging() {
            this.unitIdx = 0;
        }

        public boolean isImageChanging() {
            return unitIdx == 0;
        }

        public void deleteAllImages() {
            for(int i = this.resids.size()-1; i >= 0; i--) {
                this.resids.remove(i);
            }
        }

        public void checkCollision() {
            checkCollision(true);
        }

        public void checkCollision(boolean check) {
            checkCollision = check;
        }

        public void text(String str) {
            text(str, textColor, textSize);
        }

        public void text(int n) {
            text("" + n, textColor, textSize);
        }

        public void text(String str, int color, double size) {
            text = str;
            textColor = color;
            textSize = size;
            needDraw = true;
        }

        public String text() {
            return text;
        }

        public int text2int() {
            return Integer.parseInt(text);
        }

        public boolean isTextEmpty() {
            return text == null || text.isEmpty();
        }

        public void backColor(int color) {
            backColor = color;
        }

        public void edgeColor(int color) {
            edgeColor = color;
        }

        public void edgeThick(double thick) {
            edgeThick = (float)thick;
        }

        public void edge(int color, double thick) {
            edgeColor = color;
            edgeThick = (float)thick;
        }

        public DirType collisionDir(Card card2) {
            return collisionDirRect(this.dstRect, card2.dstRect);
        }

        public void stopAllWork() {
            if(this.unitSrcL != 0 || this.unitSrcT != 0)
                stopSourceRectIng();
            if(this.unitHrz != 0 || this.unitVtc != 0)
                stopMoving();
            if(this.unitW != 0 || this.unitH != 0)
                stopResizing();
            if(this.unitIdx != 0)
                stopImageChanging();
        }

        public void autoRemove() {
            autoRemove(true);
        }

        public void autoRemove(boolean remove) {
            this.autoRemove = remove;
        }

        public void set(int value) {
            this.valueN = value;
        }

        public void set(double value) {
            this.valueF = value;
        }

        public void set(boolean value) {
            this.valueB = value;
        }

        public void set(String value) {
            this.valueS = value;
        }

        public int getInt() {
            return this.valueN;
        }

        public double getDouble() {
            return this.valueF;
        }

        public boolean getBoolean() {
            return this.valueB;
        }

        public String getString() {
            return this.valueS;
        }

        // Card API end ====================================

    }

    // Inside Class end ====================================

    // API start ====================================

    public void setScreenGrid(float w, float h) {
        blocksW = w;
        blocksH = h;
        firstDraw = true;
    }

    public Card addCardColor(int clr) {
        Card card = new Card(clr, 0);
        addCard(card);
        return card;
    }

    public Card addCardColor(int clr, double l, double t, double w, double h) {
        Card card = new Card(clr, 0);
        addCard(card, l, t, w, h);
        return card;
    }

    public Card addCard(int resid)  {
        Card card = new Card(resid);
        addCard(card);
        return card;
    }

    public Card addCard(int resid, double l, double t, double w, double h) {
        Card card = new Card(resid);
        addCard(card, l, t, w, h);
        return card;
    }

    public void addCard(Card card, double l, double t, double w, double h) {
        addCard(card);
        card.dstRect = new RectF((float)l, (float)t, (float)(l + w), (float)(t + h));
    }

    public void addCard(Card card) {
        cards.add(card);
        needDraw = true;
    }

    public double framesOfTime(double time) {
        double miliTime = time * 1000.;
        return miliTime / timerGap1;
    }

    public void clearMemory() {
        needDraw = false;
        deleteBGM();
        deleteAllCards();
    }

    public void deleteAllCards() {
        removeCards.clear();
        for(int i = cards.size()-1; i >= 0; i--) {
            Card card = cards.get(i);
            card.deleteAllImages();
            card.bmp = null;
        }
        cards.clear();
    }

    public void popupDialog(String title, String description, String btnText1) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(getContext());
        if(title != null && !title.isEmpty())
            dialog.setTitle(title);
        if(description != null && !description.isEmpty())
            dialog.setMessage(description);
        if(btnText1 != null && !btnText1.isEmpty())
            dialog.setPositiveButton("Close", null);
        dialog.show();
    }

    Vibrator vibrator;

    // To use Vibrator, add below Permission into AndroidManifest.xml
    // <uses-permission android:name="android.permission.VIBRATE"/>
    public void vibrate(double second) {
        if(vibrator == null)
            vibrator = (Vibrator)getContext().getSystemService(Context.VIBRATOR_SERVICE);
        vibrator.vibrate((int)(second * 1000));
    }

    SensorManager sensorMgr = null;

    public void startSensorAccelerometer() {
        if(sensorMgr == null)
            sensorMgr = (SensorManager)getContext().getSystemService(Context.SENSOR_SERVICE);
        Sensor sensorAcceler = sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if( sensorAcceler != null )
            sensorMgr.registerListener(this, sensorAcceler, SensorManager.SENSOR_DELAY_UI);
    }

    public void stopSensorAccelerometer() {
        if(sensorMgr == null) return;
        sensorMgr.unregisterListener(this);
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    public void onSensorChanged(SensorEvent event) {
        float v[] = event.values;
        switch( event.sensor.getType() ) {
            case Sensor.TYPE_ACCELEROMETER :
                if(listener != null) {
                    listener.onGameSensor(Sensor.TYPE_ACCELEROMETER, v[0], v[1], v[2]);
                }
                break;
        }
    }

    public String assetFile(String filePath) {
        String text = null;
        try {
            InputStream is = getContext().getAssets().open(filePath);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            text = new String(buffer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return text;
    }

    public int[][] assetFileIntArray(String filePath) {
        String bufFile = assetFile(filePath);
        bufFile = bufFile.trim();
        String[] lines = bufFile.split("\n");
        int[][] res = null;
        for(int i = 0; i < lines.length; i++) {
            String line = lines[i];
            line = line.trim();
            if(res == null)
                res = new int[lines.length][line.length()];
            for(int j = 0; j < line.length(); j++) {
                int n = line.charAt(j)-'0';
                if(i >= res.length || j >= res[i].length) continue;
                res[i][j] = n;
            }
        }
        return res;
    }

    public int random(int range) {
        return random(0, range-1);
    }

    public int random(int min, int max) {
        return (int)(Math.random() * (max-min+1)) + min;
    }

    public boolean checkCollision(RectF rect1, RectF rect2) {
        if(rect1.top >= rect2.bottom || rect1.bottom <= rect2.top
                || rect1.left >= rect2.right || rect1.right <= rect2.left)
            return false;
        return true;
    }

    public DirType collisionDirRect(RectF rect1, RectF rect2) {
        if(rect2.contains(rect1.left, rect1.top)) {
            if(rect2.right - rect1.left > rect2.bottom - rect1.top)
                return DirType.UP;
            else
                return DirType.LEFT;
        }
        if(rect2.contains(rect1.right, rect1.top)) {
            if(rect1.right - rect2.left > rect2.bottom - rect1.top)
                return DirType.UP;
            else
                return DirType.RIGHT;
        }
        if(rect2.contains(rect1.right, rect1.bottom)) {
            if(rect1.right - rect2.left > rect1.bottom - rect2.top)
                return DirType.DOWN;
            else
                return DirType.RIGHT;
        }
        if(rect2.right - rect1.left > rect1.bottom - rect2.top)
            return DirType.DOWN;
        else
            return DirType.LEFT;
    }

    public void removeCard(Card card) {
        removeCards.add(card);
        needDraw = true;
    }

    public void set(String key, boolean b) {
        SharedPreferences.Editor editor = getSharedPrefEdit();
        editor.putBoolean(key, b);
        editor.commit();
    }

    public void set(String key, int n) {
        SharedPreferences.Editor editor = getSharedPrefEdit();
        editor.putInt(key, n);
        editor.commit();
    }

    public void set(String key, double f) {
        SharedPreferences.Editor editor = getSharedPrefEdit();
        editor.putFloat(key, (float)f);
        editor.commit();
    }

    public void set(String key, String str) {
        SharedPreferences.Editor editor = getSharedPrefEdit();
        editor.putString(key, str);
        editor.commit();
    }

    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    public boolean getBoolean(String key, boolean b) {
        SharedPreferences sp = getSharedPref();
        return sp.getBoolean(key, b);
    }

    public int getInt(String key) {
        return getInt(key, 0);
    }

    public int getInt(String key, int n) {
        SharedPreferences sp = getSharedPref();
        return sp.getInt(key, n);
    }

    public float getDouble(String key) {
        return getDouble(key, 0);
    }

    public float getDouble(String key, double f) {
        SharedPreferences sp = getSharedPref();
        return sp.getFloat(key, (float)f);
    }

    public String getString(String key) {
        return getString(key, "");
    }

    public String getString(String key, String str) {
        SharedPreferences sp = getSharedPref();
        return sp.getString(key, str);
    }

    public void stopAllWork() {
        needDraw = false;
        for(Card card : cards) {
            card.stopAllWork();
        }
    }

    public void startTimer(double timeGap) {
        int milisec = (int)(timeGap * 1000);
        timerGap2 = milisec;
        timer2.sendEmptyMessageDelayed(0, milisec);
    }

    public void stopTimer() {
        timer2.removeMessages(0);
    }

    public int indexOf(Card card) {
        for(int i = cards.size()-1; i >= 0; i--) {
            if(cards.get(i) == card)
                return i;
        }
        return -1;
    }

    public void playAudioBeep(int resid) {
        if(audioBeeps.isEmpty()) return;
        AudioBeep ab = audioBeeps.poll();
        ab.play(resid);
        audioBeeps.add(ab);
    }

    public void loadBGM(int resid) {
        audioSourceId = resid;
        stopBGM();
    }

    public void playBGM(int resid) {
        loadBGM(resid);
        playBGM();
    }

    public void playBGM() {
        mPlayer.start();
    }

    public void pauseBGM() {
        mPlayer.pause();
    }

    public void stopBGM() {
        deleteBGM();
        loadBGM();
    }

    public void audioAutoReplay(boolean autoPlay) {
        audioAutoReply = autoPlay;
    }

    public void listener(GameEvent lsn) { listener = lsn; }

    // API end ====================================

    // Event start ====================================

    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);
        float pixelX = event.getX();
        float pixelY = event.getY();
        float blockX = 0, blockY = 0;
        Card card = touchedCard;

        switch(event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                card = touchedCard = findCard(pixelX, pixelY);
                blockX = getBlocksHorizontal(pixelX);
                blockY = getBlocksVertical(pixelY);
                break;
            case MotionEvent.ACTION_MOVE :
                blockX = getBlocksHorizontal(pixelX) - getBlocksHorizontal(touchX);
                blockY = getBlocksVertical(pixelY) - getBlocksVertical(touchY);
                break;
            case MotionEvent.ACTION_UP :
                touchedCard = null;
                blockX = getBlocksHorizontal(pixelX);
                blockY = getBlocksVertical(pixelY);
                break;
        }
        if(listener != null) {
            listener.onGameTouchEvent(card, event.getAction(), blockX, blockY);
        }
        touchX = pixelX;
        touchY = pixelY;
        return true;
    }

    // Event end ====================================

    // Audio play start ====================================

    LinkedList<AudioBeep> audioBeeps = new LinkedList();

    class AudioBeep {
        SoundPool soundPool = new SoundPool.Builder().build();
        int soundId = -1;
        Context context = null;
        AudioBeep(Context ctx) {
            this.context = ctx;
        }

        public void play(int resid) {
            if (soundId >= 0) {
                soundPool.stop(soundId);
                soundPool = new SoundPool.Builder().build();
            }
            soundId = soundPool.load(context, resid, 1);
            soundPool.setOnLoadCompleteListener(
                    new SoundPool.OnLoadCompleteListener() {
                        @Override
                        public void onLoadComplete(SoundPool soundPool, int id, int status) {
                            soundPool.play(id, 1, 1, 1, 0, 1f);
                        }
                    }
            );
        }
    }

    MediaPlayer mPlayer = null;
    int audioSourceId = -1;
    boolean audioAutoReply = true;

    void loadBGM() {
        mPlayer = MediaPlayer.create(this.getContext(), audioSourceId);
        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                if(audioAutoReply) {
                    stopBGM();
                    playBGM();
                }
                if(listener != null)
                    listener.onGameWorkEnded(null, WorkType.AUDIO_PLAY);
            }
        });
    }

    void deleteBGM() {
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }
    }

    // Audio play end ====================================

    // Interface start ====================================

    GameEvent listener = null;

    interface GameEvent {
        void onGameWorkEnded(Card card, WorkType workType);
        void onGameTouchEvent(Card card, int action, float x, float y);
        void onGameSensor(int sensorType, float x, float y, float z);
        void onGameCollision(Card card1, Card card2);
        void onGameTimer();
    }

    public enum WorkType {
        AUDIO_PLAY, MOVE, RESIZE, IMAGE_CHANGE, SOURCE_RECT
    }

    public enum DirType {
        LEFT, RIGHT, UP, DOWN, LEFT_UP, LEFT_DOWN, RIGHT_UP, RIGHT_DOWN
    }

    // Interface end ====================================

}