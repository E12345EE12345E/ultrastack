package me.ethanchen.lwjgl3.settings;

import com.badlogic.gdx.Input;

public class GameSettings {
    public MovementKeys movement = new MovementKeys();
    public ColorTweaks colors = new ColorTweaks();
    public VolumeSettings volume = new VolumeSettings();
    public int arr = 33;
    public int das = 133;
    public String lastUsername = "";

    public static class VolumeSettings {
        /** Master volume, 0–100. Applied to all audio. Default 50. */
        public int master = 50;
        /** SFX volume, 0–100. Applied on top of master for sound effects. Default 100. */
        public int sfx = 100;
        /** Music volume, 0–100. Applied on top of master for music. Default 80. */
        public int music = 80;
    }

    public static class PieceColorTweak {
        public int hue = 0;     // -20 to 20, added directly in degrees
        public int sat = 0;     // -20 to 0, divided by 100 at render (-0.2 to 0.0)
        public int bgValue = 0; // -20 to 20, divided by 100 at render (-0.2 to 0.2)
    }

    public static class ColorTweaks {
        public PieceColorTweak I       = new PieceColorTweak();
        public PieceColorTweak J       = new PieceColorTweak();
        public PieceColorTweak L       = new PieceColorTweak();
        public PieceColorTweak O       = new PieceColorTweak();
        public PieceColorTweak S       = new PieceColorTweak();
        public PieceColorTweak T       = new PieceColorTweak();
        public PieceColorTweak Z       = new PieceColorTweak();
        public PieceColorTweak I3      = new PieceColorTweak();
        public PieceColorTweak L3      = new PieceColorTweak();
        public PieceColorTweak garbage = new PieceColorTweak();
    }

    public static class MovementKeys {
        // Keyboard slot 1 (primary)
        public int left       = Input.Keys.LEFT;
        public int right      = Input.Keys.RIGHT;
        public int softDrop   = Input.Keys.DOWN;
        public int rotateCw   = Input.Keys.X;
        public int rotateCcw  = Input.Keys.Z;
        public int rotate180  = Input.Keys.A;
        public int hold       = Input.Keys.C;
        public int hardDrop   = Input.Keys.SPACE;

        // Keyboard slot 2 (secondary); -1 = unbound
        public int left2       = -1;
        public int right2      = -1;
        public int softDrop2   = -1;
        public int rotateCw2   = -1;
        public int rotateCcw2  = -1;
        public int rotate180_2 = -1;
        public int hold2       = -1;
        public int hardDrop2   = -1;

        // Controller slot 1; -1 = unbound
        // SDL2 GameController button order (used by gdx-controllers 2.x):
        // 0=A 1=B 2=X 3=Y 4=Back 5=Guide 6=Start 7=LS 8=RS 9=LB 10=RB
        // 11=D-pad Up 12=D-pad Down 13=D-pad Left 14=D-pad Right
        public int ctrlLeft      = 13; // D-pad Left
        public int ctrlRight     = 14; // D-pad Right
        public int ctrlSoftDrop  = 12; // D-pad Down
        public int ctrlHardDrop  = 11; // D-pad Up
        public int ctrlRotateCw  = 0;  // A
        public int ctrlRotateCcw = 1;  // B
        public int ctrlRotate180 = -1;
        public int ctrlHold      = 9;  // LB

        // Controller slot 2; -1 = unbound
        public int ctrlLeft2      = -1;
        public int ctrlRight2     = -1;
        public int ctrlSoftDrop2  = -1;
        public int ctrlHardDrop2  = -1;
        public int ctrlRotateCw2  = 3;  // Y
        public int ctrlRotateCcw2 = 2;  // X
        public int ctrlRotate180_2 = -1;
        public int ctrlHold2      = 10; // RB
    }
}
