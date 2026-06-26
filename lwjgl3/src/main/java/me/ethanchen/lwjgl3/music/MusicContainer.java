package me.ethanchen.lwjgl3.music;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;

public class MusicContainer {
    public final Music intro;
    public final Music[] loop;
    public final MusicTag[] tags;
    public final double bpm;

    private float currentVolume;
    private boolean playing;
    private boolean introActive;
    private int currentLoopIndex;

    public MusicContainer(String introPath, String[] loopPaths, MusicTag[] tags) {
        this(introPath, loopPaths, tags, -1);
    }

    public MusicContainer(String introPath, String[] loopPaths, MusicTag[] tags, double bpm) {
        this.bpm = bpm;
        this.tags = tags;
        this.intro = Gdx.audio.newMusic(Gdx.files.internal(introPath));
        this.loop = new Music[loopPaths.length];
        for (int i = 0; i < loopPaths.length; i++) {
            this.loop[i] = Gdx.audio.newMusic(Gdx.files.internal(loopPaths[i]));
        }
    }

    public boolean hasTag(MusicTag tag) {
        if (tags == null) return false;
        for (MusicTag t : tags) {
            if (t == tag) return true;
        }
        return false;
    }

    public void play(float volume) {
        stop();
        if (loop.length == 0) return;

        playing = true;
        currentVolume = volume;
        introActive = true;
        currentLoopIndex = 0;

        intro.setLooping(false);
        intro.setVolume(volume);
        intro.setOnCompletionListener(m -> {
            if (!playing) return;
            startLoop();
        });
        intro.play();
    }

    private void startLoop() {
        if (!playing || loop.length == 0) return;

        introActive = false;
        Music track = loop[currentLoopIndex];
        track.setLooping(false);
        track.setVolume(currentVolume);
        track.setOnCompletionListener(m -> {
            if (!playing) return;
            currentLoopIndex = (currentLoopIndex + 1) % loop.length;
            startLoop();
        });
        track.play();
    }

    public void stop() {
        playing = false;
        introActive = false;
        intro.setOnCompletionListener(null);
        intro.stop();
        for (Music m : loop) {
            m.setOnCompletionListener(null);
            m.stop();
        }
    }

    public void setVolume(float v) {
        currentVolume = v;
        if (introActive) {
            intro.setVolume(v);
        } else if (loop.length > 0) {
            loop[currentLoopIndex].setVolume(v);
        }
    }

    public void dispose() {
        stop();
        intro.dispose();
        for (Music m : loop) {
            m.dispose();
        }
    }
}
