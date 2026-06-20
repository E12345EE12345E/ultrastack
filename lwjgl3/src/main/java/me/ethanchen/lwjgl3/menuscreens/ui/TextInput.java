package me.ethanchen.lwjgl3.menuscreens.ui;

public class TextInput {
    // Wrapper for string
    public volatile String input;

    public TextInput() {
        this.input = "";
    }

    public TextInput(String in) {
        this.input = in;
    }

    public void set(String s) { input = s; }
    public String get() { return input; }
}
