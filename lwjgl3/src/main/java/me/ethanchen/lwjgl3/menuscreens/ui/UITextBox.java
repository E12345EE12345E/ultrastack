package me.ethanchen.lwjgl3.menuscreens.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import me.ethanchen.lwjgl3.menuscreens.MenuScreen;
import me.ethanchen.util.TextSanitizer;

public class UITextBox extends UIElement {
    public volatile boolean focused;
    public volatile boolean tabLock;
    public String text;
    public TextBoxOutput out;
    public UITextBox next;
    public Runnable runOnEnter;
    public int sanitize;

    public UITextBox(double x, double y, double w, double h) {
        this(x, y, w, h, null, null, null);
    }

    public UITextBox(double x, double y, double w, double h, UITextBox next) {
        this(x, y, w, h, null, next, null);
    }

    public UITextBox(double x, double y, double w, double h, TextBoxOutput out) {
        this(x, y, w, h, out, null, null);
    }

    public UITextBox(double x, double y, double w, double h, TextBoxOutput out, UITextBox next, Runnable run) {
        super(x, y, w, h);
        focused = false;
        text = "";
        this.out = out;
        this.next = next;
        this.tabLock = false;
        this.runOnEnter = run;
        sanitize = 0;
    }

    @Override
    public void render(ShapeRenderer shapes, SpriteBatch sprites, BitmapFont font) {
        float pxWidth = MenuScreen.convertFromRelCoordsX((float) width);
        float pxHeight = (float) (height * Gdx.graphics.getHeight());
        float pxX = MenuScreen.convertFromRelCoordsX((float) centerX) - 0.5f * pxWidth;
        float pxY = (Gdx.graphics.getHeight() - MenuScreen.convertFromRelCoordsY((float) centerY)) - 0.5f * pxHeight;

        // Draw background
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (focused) {
            shapes.setColor(0.18f, 0.18f, 0.22f, 1f);
        } else {
            shapes.setColor(0.08f, 0.08f, 0.08f, 1f);
        }
        shapes.rect(pxX, pxY, pxWidth, pxHeight);
        shapes.end();

        // Draw border
        shapes.begin(ShapeRenderer.ShapeType.Line);
        if (focused) {
            shapes.setColor(Color.YELLOW);
        } else {
            shapes.setColor(Color.GRAY);
        }
        shapes.rect(pxX, pxY, pxWidth, pxHeight);
        shapes.end();

        // Store original scale and calculate window height scale factor
        float originalScaleX = font.getScaleX();
        float originalScaleY = font.getScaleY();
        
        font.getData().setScale(1f);
        float unscaledLineHeight = font.getData().lineHeight;
        float scaleAdjustment = 15f / unscaledLineHeight;
        
        float fontScale = scaleAdjustment * (Gdx.graphics.getHeight() / 640f);
        font.getData().setScale(fontScale);

        // Draw text
        sprites.begin();
        float textX = pxX + 8f * fontScale;
        float textY = pxY + (pxHeight + 15f * fontScale) / 2f - 2f * fontScale;
        String displayText = (text != null) ? text : "";
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            displayText += "|";
        }
        font.setColor(Color.WHITE);
        font.draw(sprites, displayText, textX, textY);
        sprites.end();

        // Restore original scale
        font.getData().setScale(originalScaleX, originalScaleY);

        tabLock = false;
    }

    @Override
    public void onClick() {
        focused = true;
    }

    @Override
    public void handleClick(int screenX, int screenY) {
        boolean success = isClicked(screenX, screenY);
        if (success) {
            onClick();
        } else {
            focused = false;
        }
    }

    @Override
    public void handleKeyTyped(char c) {
        if (!focused) return;
        if (c == '\b' || c == '\u0008') {
            if (text.length() > 0) {
                text = text.substring(0, text.length() - 1);
            }
        } else if (c == '\r' || c == '\n') {
            if (this.runOnEnter != null) {
                runOnEnter.run();
            } else {
                focused = false;
            }
        } else if (c == '\t' || c == '\u0009') {
            if (this.next != null && !this.tabLock) {
                System.out.println(this.next);
                this.next.tabLock = true;
                this.next.focused = true;
                focused = false;
            }
        } else if (c >= 32 && c < 127) {
            text += c;
        }
        switch (sanitize) {
            case 1:
                text = TextSanitizer.sanitizeChat(text);
                break;
            case 2:
                text = TextSanitizer.sanitizeName(text);
                break;
        }
        out.set(text);
        this.tabLock = false;
    }

    @Override
    public void handleKeyDown(int keycode) {
        if (!focused) return;
        boolean ctrl = Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.CONTROL_RIGHT);
        if (ctrl && keycode == com.badlogic.gdx.Input.Keys.C) {
            if (text != null && text.length() > 0) {
                Gdx.app.getClipboard().setContents(text);
            }
        } else if (ctrl && keycode == com.badlogic.gdx.Input.Keys.V) {
            String contents = Gdx.app.getClipboard().getContents();
            if (contents != null) {
                for (int i = 0; i < contents.length(); i++) {
                    char c = contents.charAt(i);
                    if (c >= 32 && c < 127) {
                        text += c;
                    }
                }
                switch (sanitize) {
                    case 1:
                        text = TextSanitizer.sanitizeChat(text);
                        break;
                    case 2:
                        text = TextSanitizer.sanitizeName(text);
                        break;
                }
                out.set(text);
            }
        }
    }

    @Override
    public String toString() {
        return "UITextBox[text=\"" + text + "\" hasNext=" + (next!=null) + "]"; 
    }

    public void setNext(UITextBox newNext) {
        this.next = newNext;
    }
}
