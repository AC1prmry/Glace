package com.snac.core.object;

import com.snac.util.HitBox;
import com.snac.util.Vector2D;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;

@Slf4j
@Getter
public abstract class PhysicalObject<I> extends AbstractObjectBase<I> {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Vector2D velocity;

    protected PhysicalObject() {
        this(null, Direction.RIGHT.getAngle(), 0, 0);
    }

    protected PhysicalObject(@Nullable Vector2D position, float direction, int width, int height) {
        super(position, direction, width, height);

        this.velocity = new Vector2D(0, 0);
    }

    @Override
    protected void onUpdate(double deltaTime) {
        position.set(position.getX() + (velocity.getX() * deltaTime), position.getY() + (velocity.getY() * deltaTime));
        if (velocity.getX() != 0) {
            var slowFactor = velocity.getX() * 0.3;
            velocity.set(velocity.getX() * -slowFactor, velocity.getY());
        }
        //checkCollisions();
    }

    public boolean isOnGround() {
        return false;
    }

    //Core method
    public double distanceSwept(double delta, boolean isXAxis, double sX, double sY, double sW, double sH, HitBox hitBox) {
        //Case 1: X-Axis, negative movement
        if (isXAxis && delta < 0) {
            return sX - (hitBox.getX() + hitBox.getWidth());
        }
        //Case 2: X-Axis, positive movement or not moving
        if (isXAxis) {
            return hitBox.getX() - (sX - sW);
        }
        //Case 3: Y-Axis, negative movement
        if (delta < 0) {
            return sY - (hitBox.getY() + hitBox.getHeight());
        }
        //Case 4: Y-Axis, positive movement or not moving
        return hitBox.getY() - (sY - sH);
    }

    //Only use for single calls or if performance doesn't matter
    public double distanceSwept(double delta, boolean isXAxis, HitBox hitBox) {
        double absDelta = Math.abs(delta);
        double posX = getPosition().getX();
        double posY = getPosition().getY();

        double sweptX = isXAxis && delta < 0 ? posX - absDelta : posX;
        double sweptY = !isXAxis && delta < 0 ? posY - absDelta : posY;
        double sweptWidth = isXAxis ? absDelta : getWidth();
        double sweptHeight = !isXAxis ? absDelta : getHeight();

        return distanceSwept(delta, isXAxis, sweptX, sweptY, sweptWidth, sweptHeight, hitBox);
    }

    public record CollisionResult(HitBox box, int boxIndex, double distance) {}
    //When not passing existing array, because of vararg, a new one will be initialized = bad
    @Nullable
    public CollisionResult nearestCollisionSwept(double delta, boolean isXAxis, HitBox... hitBoxes) {
        double nearestDist = Double.MAX_VALUE;
        HitBox nearestBox = null;
        int boxIndex = -1;

        //Calculate only once
        double absDelta = Math.abs(delta);
        double posX = getPosition().getX();
        double posY = getPosition().getY();

        double sweptX = isXAxis && delta < 0 ? posX - absDelta : posX;
        double sweptY = !isXAxis && delta < 0 ? posY - absDelta : posY;
        double sweptWidth = isXAxis ? absDelta : getWidth();
        double sweptHeight = !isXAxis ? absDelta : getHeight();

        for (int i = 0; i < hitBoxes.length; i++) {
            var box = hitBoxes[i];

            if (!box.intersects(sweptX, sweptY, sweptWidth, sweptHeight)) continue;

            var dist = distanceSwept(delta, isXAxis, sweptX, sweptY, sweptWidth, sweptHeight, box);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearestBox = box;
                boxIndex = i;
            }
        }

        if (nearestDist < Double.MAX_VALUE) {
            return new CollisionResult(nearestBox, boxIndex, nearestDist);
        }
        return null;
    }

    //for docs: objects array shouldn't be changed after method call
    @Nullable
    public AbstractObjectBase<?> sweptObjectCollision(double delta, boolean isXAxis, AbstractObjectBase<?>... objects) {
        double nearestDist = Double.MAX_VALUE;
        AbstractObjectBase<?> nearestObject = null;

        double absDelta = Math.abs(delta);
        double posX = getPosition().getX();
        double posY = getPosition().getY();

        double sweptX = isXAxis && delta < 0 ? posX - absDelta : posX;
        double sweptY = !isXAxis && delta < 0 ? posY - absDelta : posY;
        double sweptWidth = isXAxis ? absDelta : getWidth();
        double sweptHeight = !isXAxis ? absDelta : getHeight();

        for (int i = 0; i < objects.length; i++) {
            var obj = objects[i];

            //First check parent box
            var parentBox = obj.getHitBox();
            if (parentBox.intersects(sweptX, sweptY, sweptWidth, sweptHeight)) {
                var dist = distanceSwept(delta, isXAxis, sweptX, sweptY, sweptWidth, sweptHeight, parentBox);
                if (nearestDist > dist) {
                    nearestDist = dist;
                    nearestObject = obj;
                }
            }

            //Then check hitbox children
            var attachedBoxesSnapshot = parentBox.getAllDescendants();
            for (int j = 0; j < attachedBoxesSnapshot.size(); j++) {
                var box = attachedBoxesSnapshot.get(j);
                if (box.intersects(sweptX, sweptY, sweptWidth, sweptHeight)) {
                    var dist = distanceSwept(delta, isXAxis, sweptX, sweptY, sweptWidth, sweptHeight, box);
                    if (nearestDist > dist) {
                        nearestDist = dist;
                        nearestObject = obj;
                    }
                }
            }
        }

        return nearestObject;
    }


    //Max double value when no result
    public double sweptCollisionX(double deltaX, HitBox... hitBoxes) {
        var result = nearestCollisionSwept(deltaX, true, hitBoxes);
        return result != null ? result.distance : Double.MAX_VALUE;
    }

    //Max double value when no result
    public double sweptCollisionY(double deltaY, HitBox... hitBoxes) {
        var result = nearestCollisionSwept(deltaY, false, hitBoxes);
        return result != null ? result.distance : Double.MAX_VALUE;
    }

    @Nullable
    public HitBox sweptBoxCollisionX(double deltaX, HitBox... hitBoxes) {
        var result = nearestCollisionSwept(deltaX, true, hitBoxes);
        return result != null ? result.box : null;
    }

    @Nullable
    public HitBox sweptBoxCollisionY(double deltaY, HitBox... hitBoxes) {
        var result = nearestCollisionSwept(deltaY, false, hitBoxes);
        return result != null ? result.box : null;
    }

    @Nullable
    public AbstractObjectBase<?> sweptObjectCollisionX(double deltaX, AbstractObjectBase<?> objects) {
        return sweptObjectCollision(deltaX, true, objects);
    }

    @Nullable
    public AbstractObjectBase<?> sweptObjectCollisionY(double deltaY, AbstractObjectBase<?> objects) {
        return sweptObjectCollision(deltaY, false, objects);
    }
}
