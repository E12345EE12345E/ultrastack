package me.ethanchen.lwjgl3.menuscreens.ui;

public class TextBoxOutput {
    // Wrapper for string
    public volatile String output;

    public TextBoxOutput() {
        this.output = "";
    }

    public void set(String s) { output = s; }
    public String get() { return output; }
}
