package me.ethanchen.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;

public class AudioManager {
    private static AudioManager instance;

    private Sound moveSound;
    private Sound rotateSound;
    private Sound placeSound;
    private Sound[] clearSound;
    private Sound[] holdSound;
    
    private AudioManager() {
        moveSound = Gdx.audio.newSound(Gdx.files.internal("sfx/sfx_move.wav"));
        rotateSound = Gdx.audio.newSound(Gdx.files.internal("sfx/sfx_rotate_updated.wav"));
        placeSound = Gdx.audio.newSound(Gdx.files.internal("sfx/sfx_place.wav"));
        clearSound = new Sound[]{
            Gdx.audio.newSound(Gdx.files.internal("sfx/sfx_combo1.wav")),
            Gdx.audio.newSound(Gdx.files.internal("sfx/sfx_combo2.wav")),
            Gdx.audio.newSound(Gdx.files.internal("sfx/sfx_combo3.wav")),
            Gdx.audio.newSound(Gdx.files.internal("sfx/sfx_combo4.wav")),
            Gdx.audio.newSound(Gdx.files.internal("sfx/sfx_combo5.wav")),
            Gdx.audio.newSound(Gdx.files.internal("sfx/sfx_combo6.wav")),
            Gdx.audio.newSound(Gdx.files.internal("sfx/sfx_combo7.wav")),
            Gdx.audio.newSound(Gdx.files.internal("sfx/sfx_combo8.wav")),
            Gdx.audio.newSound(Gdx.files.internal("sfx/sfx_combo9.wav")),
            Gdx.audio.newSound(Gdx.files.internal("sfx/sfx_combo10.wav")),
            Gdx.audio.newSound(Gdx.files.internal("sfx/sfx_comboplus.wav")),
        };
        holdSound = new Sound[] {
            Gdx.audio.newSound(Gdx.files.internal("sfx/sfx_extra.wav")),
            Gdx.audio.newSound(Gdx.files.internal("sfx/sfx_hold.wav"))
        };
    }

    public static AudioManager getInstance() {
        if (instance == null) {
            instance = new AudioManager();
        }
        return instance;
    }

    public void playMoveSound() {
        moveSound.play(1f);
    }

    public void playRotateSound() {
        rotateSound.play(1f);
    }

    public void playPlaceSound(boolean self) {
        placeSound.play(self ? 1f : 0.5f); // other players at half volume
    }
    
    public void playClearSound(int combo) {
        if (combo < 0) combo = 0;
        if (combo > clearSound.length - 1) combo = clearSound.length - 1;
        clearSound[combo].play(Math.min(combo*0.25f + 0.25f, 1f));
    }

    public void playHoldSound(boolean self) {
        holdSound[self ? 0 : 1].play(1f);
    }

    public void dispose() {
        moveSound.dispose();
        rotateSound.dispose();
        placeSound.dispose();
        for (Sound s : clearSound) s.dispose();
        for (Sound s : holdSound) s.dispose();
    }
}
