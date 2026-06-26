package me.ethanchen.lwjgl3.music;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;

import me.ethanchen.lwjgl3.settings.GameSettings;

public class AudioManager {
    private static AudioManager instance;

    // -------------------------------------------------------------------------
    // Hardcoded base volume multipliers (tuned per-sound, before user settings)
    // -------------------------------------------------------------------------
    private static final float MOVE_BASE        = 0.5f;
    private static final float ROTATE_BASE      = 0.5f;
    private static final float PLACE_SELF_BASE  = 1.0f;
    private static final float PLACE_OTHER_BASE = 0.5f;
    private static final float HOLD_BASE        = 1.0f;
    private static final float MUSIC_BASE       = 0.7f;

    // -------------------------------------------------------------------------
    // Sound assets
    // -------------------------------------------------------------------------
    private Sound moveSound;
    private Sound rotateSound;
    private Sound placeSound;
    private Sound[] clearSound;
    private Sound[] holdSound;

    // -------------------------------------------------------------------------
    // Music registry
    // -------------------------------------------------------------------------
    private final List<MusicContainer> registeredMusic = new ArrayList<>();
    private MusicContainer currentContainer;

    // -------------------------------------------------------------------------
    // Volume settings (set by setVolumeSettings, updated live from settings screen)
    // -------------------------------------------------------------------------
    private GameSettings.VolumeSettings volumeSettings;

    private AudioManager() {
        moveSound   = Gdx.audio.newSound(Gdx.files.internal("sfx/sfx_move.wav"));
        rotateSound = Gdx.audio.newSound(Gdx.files.internal("sfx/sfx_rotate_updated.wav"));
        placeSound  = Gdx.audio.newSound(Gdx.files.internal("sfx/sfx_place.wav"));
        clearSound  = new Sound[]{
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
        holdSound   = new Sound[]{
            Gdx.audio.newSound(Gdx.files.internal("sfx/sfx_extra.wav")),
            Gdx.audio.newSound(Gdx.files.internal("sfx/sfx_hold.wav")),
            Gdx.audio.newSound(Gdx.files.internal("sfx/sfx_holdunable.wav")),
        };
    }

    public static AudioManager getInstance() {
        if (instance == null) {
            instance = new AudioManager();
        }
        return instance;
    }

    // -------------------------------------------------------------------------
    // Volume settings
    // -------------------------------------------------------------------------

    /**
     * Updates the volume settings used by all play methods and immediately
     * re-applies music volume to any currently playing track.
     */
    public void setVolumeSettings(GameSettings.VolumeSettings v) {
        this.volumeSettings = v;
        applyMusicVolume();
    }

    private float masterMult() {
        return volumeSettings != null ? volumeSettings.master / 100f : 0.5f;
    }

    private float sfxMult() {
        return volumeSettings != null ? volumeSettings.sfx / 100f : 1.0f;
    }

    private float musicMult() {
        return volumeSettings != null ? volumeSettings.music / 100f : 0.8f;
    }

    /** Computes a final SFX volume: base * master * sfx. */
    private float sfxVol(float base) {
        return base * masterMult() * sfxMult();
    }

    /** Computes the current music volume: MUSIC_BASE * master * music. */
    private float currentMusicVol() {
        return MUSIC_BASE * masterMult() * musicMult();
    }

    /** Re-applies the current volume formula to the active music container. */
    private void applyMusicVolume() {
        if (currentContainer != null) {
            currentContainer.setVolume(currentMusicVol());
        }
    }

    // -------------------------------------------------------------------------
    // SFX playback
    // -------------------------------------------------------------------------

    public void playMoveSound() {
        moveSound.play(sfxVol(MOVE_BASE));
    }

    public void playRotateSound() {
        rotateSound.play(sfxVol(ROTATE_BASE));
    }

    public void playPlaceSound(boolean self) {
        placeSound.play(sfxVol(self ? PLACE_SELF_BASE : PLACE_OTHER_BASE));
    }

    public void playClearSound(int combo) {
        if (combo < 0) combo = 0;
        if (combo > clearSound.length - 1) combo = clearSound.length - 1;
        float base = Math.min(combo * 0.25f + 0.25f, 1f);
        clearSound[combo].play(sfxVol(base));
    }

    public void playHoldSound(boolean self) {
        playHoldSound(self, true);
    }

    public void playHoldSound(boolean self, boolean success) {
        holdSound[success ? (self ? 0 : 1) : 2].play(sfxVol(HOLD_BASE));
    }

    // -------------------------------------------------------------------------
    // Music registry and playback
    // -------------------------------------------------------------------------

    /** Registers a {@link MusicContainer} for tag-based playback. Call during client init. */
    public void registerMusic(MusicContainer container) {
        registeredMusic.add(container);
    }

    /**
     * Plays the first registered container that matches {@code tag}.
     * Stops any currently playing music before starting.
     */
    public void playMusic(MusicTag tag) {
        for (MusicContainer container : registeredMusic) {
            if (container.hasTag(tag)) {
                stopMusic();
                currentContainer = container;
                currentContainer.play(currentMusicVol());
                return;
            }
        }
    }

    /** Stops the currently playing music container, if any. */
    public void stopMusic() {
        if (currentContainer != null) {
            currentContainer.stop();
            currentContainer = null;
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void dispose() {
        stopMusic();
        moveSound.dispose();
        rotateSound.dispose();
        placeSound.dispose();
        for (Sound s : clearSound) s.dispose();
        for (Sound s : holdSound) s.dispose();
        for (MusicContainer c : registeredMusic) {
            c.dispose();
        }
        registeredMusic.clear();
    }
}
