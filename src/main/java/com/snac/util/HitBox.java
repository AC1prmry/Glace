package com.snac.util;

import lombok.AccessLevel;
import lombok.Getter;

import java.awt.*;
import java.io.Serializable;
import java.util.function.Consumer;

@Getter
public class HitBox extends Attachable<HitBox> implements Serializable {
    private int x;
    private int y;
    private int width;
    private int height;
    @Getter(AccessLevel.NONE)
    private final Point location = new Point();

    public HitBox(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public boolean intersects(HitBox hitBox) {
        return intersects(hitBox.getX(), hitBox.getY(), hitBox.getWidth(), hitBox.getHeight());
    }

    public boolean intersects(int x, int y, int width, int height) {
        return this.x <= x + width &&
                this.x + this.width >= x &&
                this.y <= y + height &&
                this.y + this.height >= y;
    }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void move(int dx, int dy) {
        var oldX = this.x;
        var oldY = this.y;

        onMove(x + dx, y + dy);
        x += dx;
        y += dy;
        onMoved(oldX, oldY);
    }

    public void setBounds(int x, int y, int width, int height) {
        var oldX = this.x;
        var oldY = this.y;

        onMove(x, y);
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        onMoved(oldX, oldY);
    }

    public void setPosition(int x, int y) {
        var oldX = this.x;
        var oldY = this.y;

        onMove(x, y);
        this.x = x;
        this.y = y;
        onMoved(oldX, oldY);
    }

    public void setBounds(HitBox hitBox) {
        var oldX = this.x;
        var oldY = this.y;

        onMove(hitBox.getX(), hitBox.getY());
        this.x = hitBox.getX();
        this.y = hitBox.getY();
        this.width = hitBox.getWidth();
        this.height = hitBox.getHeight();
        onMoved(oldX, oldY);
    }

    protected void onMoved(int oldX, int oldY) {
        childAction(child -> {
            child.move(getX() - oldX, getY() - oldY);
        });
    }

    public Point getLocation() {
        location.move(this.x, this.y);
        return location;
    }

    protected void onMove(int newX, int newY) {}

    @Override
    public void childAction(Consumer<HitBox> childAction) {
        if (attachments.isEmpty()) return;
        synchronized (attachments) {
            attachments.forEach(childAction);
        }
    }

    @Override
    public String toString() {
        return "HitBox [x=" + x + ", y=" + y + ", width=" + width + ", height=" + height;
    }
}
