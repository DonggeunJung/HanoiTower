package com.example.hanoitower;

import androidx.appcompat.app.AppCompatActivity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.LinkedList;

public class MainActivity extends AppCompatActivity implements JGameLib.GameEvent {
    final int towerMaxW = 14, screenW = towerMaxW*3, screenH = 17;
    JGameLib gameLib;
    int floorMax = 3, selTower = -1;
    ArrayList<LinkedList<JGameLib.Card>> floors = new ArrayList();
    JGameLib.Card[] towerRooms = new JGameLib.Card[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        gameLib = findViewById(R.id.gameLib);
        initGame();
    }

    void initGame() {
        gameLib.listener(this);
        gameLib.setScreenGrid(screenW, screenH);
        gameLib.addCardColor(Color.BLACK);
        JGameLib.Card bar = gameLib.addCardColor(Color.rgb(204,102,0), 0,screenH-2,screenW,2);
        bar.edge(Color.rgb(153,76,0), 0.2);
        int barL = towerMaxW/2 - 1;
        for(int i=0; i < 3; i++) {
            bar = gameLib.addCardColor(Color.rgb(204,102,0), barL,1,2,screenH-3);
            bar.edge(Color.rgb(153,76,0), 0.4);
            barL += towerMaxW;
            floors.add(new LinkedList());
            JGameLib.Card room = gameLib.addCardColor(Color.TRANSPARENT, i*towerMaxW, 0, towerMaxW, screenH-2);
            room.edge(Color.LTGRAY, 0.4);
            room.visible(false);
            towerRooms[i] = room;
        }
        newGame();
    }

    void newGame() {
        int floorW = (floorMax+1) * 2;
        int floorL = (towerMaxW - floorW) / 2;
        for(int i=0; i < floorMax; i++) {
            JGameLib.Card floor = gameLib.addCardColor(Color.rgb(128,128,255), floorL, screenH-i*2-4, floorW, 2);
            floor.edge(Color.rgb(64,64,255), 0.4);
            floor.set(floorMax-i);
            floors.get(0).push(floor);
            floorW -= 2;
            floorL ++;
        }
    }

    void removeFloors() {
        for(int i=0; i < floors.size(); i++) {
            while(!floors.get(i).isEmpty()) {
                JGameLib.Card floor = floors.get(i).pop();
                gameLib.removeCard(floor);
            }
        }
    }

    boolean moveFloor(int from, int to) {
        if(floors.get(from).size() < 1) return false;
        int fromSize = floors.get(from).peek().getInt();
        int toSize = Integer.MAX_VALUE;
        if(floors.get(to).size() > 0)
            toSize = floors.get(to).peek().getInt();
        if(fromSize < toSize) {
            JGameLib.Card floor = floors.get(from).pop();
            int gapH = (to - from) * towerMaxW;
            int gapV = (floors.get(from).size() - floors.get(to).size()) * 2;
            floor.moveGap(gapH, gapV);
            floors.get(to).push(floor);
            return true;
        }
        return false;
    }

    public void onBtnRestart(View v) {
        removeFloors();
        newGame();
    }

    public void onBtnHeight(View v) {
        if(v.getId() == R.id.btnMinus) {
             if(floorMax == 3) return;
            floorMax --;
        } else if(v.getId() == R.id.btnPlus) {
            if(floorMax == 6) return;
            floorMax ++;
        }
        removeFloors();
        newGame();
    }

    @Override
    public void onGameWorkEnded(JGameLib.Card card, JGameLib.WorkType workType) {}

    @Override
    public void onGameTouchEvent(JGameLib.Card card, int action, float x, float y) {
        if(action == MotionEvent.ACTION_UP) {
            int tower = (int) (x / towerMaxW);
            if (y >= 0 && y <= screenH && tower >= 0 && tower < 3) {
                if (selTower < 0) {
                    selTower = tower;
                    towerRooms[tower].visible(true);
                } else {
                    towerRooms[selTower].visible(false);
                    boolean res = moveFloor(selTower, tower);
                    selTower = -1;
                    if(res && tower != 0 && floors.get(tower).size() == floorMax)
                        gameLib.popupDialog(null, "Congratulation! You succeeded.", "Close");
                }
            }
        }
    }

    @Override
    public void onGameSensor(int sensorType, float x, float y, float z) {}

    @Override
    public void onGameCollision(JGameLib.Card card1, JGameLib.Card card2) {}

    @Override
    public void onGameTimer() {}
}