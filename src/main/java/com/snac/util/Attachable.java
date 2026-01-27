package com.snac.util;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A base class that supports parent-child style attachment between objects.
 * <p>
 * Think of it as a lightweight "tree structure" objects can attach to each other,
 * detach, and even notify their children when something changes.
 * </p>
 *
 * <p>
 * The main idea: you can have one parent and multiple attachments (children). Each
 * {@link Attachable} knows who its parent is (if any) and keeps track of what it has attached.
 * <p>
 * If you need a concrete example, check out {@link com.snac.core.gameobject.AbstractObjectBase}.
 * </p>
 *
 * <p>
 * Oh, and yes — you probably noticed the {@code @SuppressWarnings("unchecked")}.
 * Please just use the correct generic type to avoid exceptions.
 * I couldn't figure what's the best way to make it completely type safe.
 * </p>
 *
 * @param <T> the concrete type extending this class (so subclasses can reference themselves)
 */
@Slf4j
@Getter
@SuppressWarnings("unchecked")
public abstract class Attachable<T extends Attachable<T>> {
    /**
     * The parent of this {@link Attachable}. May be null if no parent exists.
     */
    @Nullable
    protected T parent = null;
    /**
     * This set stores every child of this {@link Attachable}
     */
    protected final Set<T> attachments = Collections.synchronizedSet(new HashSet<>());

    /**
     * Attaches another {@link Attachable} object to this one.
     * <p>
     * If the target object is already attached elsewhere, this method will log a warning
     * and skip the operation.
     * </p>
     *
     * @param attachable the object to attach
     */
    public void addAttachment(T attachable) {
        if (attachable.isAttached()) {
            var parent = getParent() == null ? "null" : getParent().getClass().getSimpleName();
            log.warn("Object {} is already attached to {}. It must be detached first.",
                    attachable.getClass().getSimpleName(),
                    parent);
            return;
        }
        attachments.add(attachable);
        attachable.parent = (T) this;
    }

    /**
     * Does the opposite of {@link #addAttachment(Attachable)} or nothing if the passed attachable isn't a child of this object.
     *
     * @param attachable the attachable to remove
     */
    public void removeAttachment(T attachable) {
        if (attachments.contains(attachable)) {
            attachments.remove(attachable);
            attachable.parent = null;
        }
    }

    /**
     * Name says it all I think.
     *
     * @return {@code true} if the object is attached to another object. Otherwise {@code false}
     */
    public boolean isAttached() {
        return parent != null;
    }

    /**
     * You can use this method to apply specific functionality to every child of an object.
     * In following example, every child (and children of child (grandchildren or something)) gets a position update when the parent is moving:
     * <pre>{@code
     *      public void onPositionChange() { //Gets called when position of parent changes
     *         childAction(child -> {
     *             child.position.set(
     *                     child.position.getX() - oldX + newX,
     *                     child.position.getY() - oldY + newY);
     *         });
     *      }
     * }</pre>
     *
     * @param childAction the action to perform on each child
     */
    public void childAction(Consumer<T> childAction) {
        if (attachments.isEmpty()) return;
        synchronized (attachments) {
            attachments.forEach(child -> {
                childAction.accept(child);
                child.childAction(childAction);
            });
        }
    }

    /**
     * Returns the topmost parent (the root) in the attachment hierarchy.
     * <p>
     * If this object has no parent, the method returns a {@link CompletableFuture}
     * that is already completed with this instance.
     * If the object is attached to another object, the method asynchronously walks
     * up the chain of parents until it finds the root parent (an object that has no parent).
     * </p>
     * This method uses {@link CompletableFuture} to not block your thread.
     * Because based on how many children there are, this method may take a while to find the root parent.
     * (I don't know if this is really necessary lol)
     *
     * @return a {@code CompletableFuture} that completes with the root parent of this object
     */
    public final CompletableFuture<T> getRootParent() {
        if (getParent() == null) return CompletableFuture.completedFuture((T) this);
        return CompletableFuture.supplyAsync(() -> {
            var lastParent = getParent();
            while (true) {
                var parent = lastParent.getParent();

                if (parent == null) return lastParent;
                else lastParent = parent;
            }
        });
    }
}
