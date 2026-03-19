package com.snac.core.object;

import com.snac.util.HitBox;
import com.snac.util.Vector2D;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;
import java.util.List;

//Implement Swept AABB Collision detection
@Getter
public abstract class PhysicalObject<I> extends AbstractObjectBase<I> {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Vector2D velocity;

    private final HitBox collisionBox;

    protected PhysicalObject() {
        this(null, Direction.RIGHT.getAngle(), 0, 0);
    }

    protected PhysicalObject(@Nullable Vector2D position, float direction, int width, int height) {
        super(position, direction, width, height);

        this.velocity = new Vector2D(0, 0);
        this.collisionBox = new HitBox(getPosition().getXRound(), getPosition().getYRound(), width, height);
    }

    public void onCollide(List<AbstractObjectBase<?>> collidedObjects) {
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

    public void moveCollisionSafe(float direction, float speed) {
    }

    protected void checkCollisions() {
        var manager = getManager();
        if (manager == null || !manager.collides(this)) {
            return;
        }

        var collisions = manager.getCollisions(this);
    }


    @Nullable
    public HitBox sweptBoxCollisionX(double distance, HitBox... hitBoxes) {
        synchronized (collisionBox) {
            collisionBox.resize(Math.abs(distance), collisionBox.getHeight());
            collisionBox.setPos(distance < 0
                    ? collisionBox.getX() - Math.abs(distance)
                    : collisionBox.getX(), collisionBox.getY());

            double nearestDistance = Double.MAX_VALUE;
            HitBox nearestBox = null;
            for (var box : hitBoxes) {
                if (collisionBox.intersects(box)) {

                    var dist = distance < 0
                            //Direction is left
                            ? collisionBox.getPos().distanceSqrt(box.getX() + box.getWidth(), collisionBox.getY())
                            //Direction is right
                            : box.getPos().distanceSqrt(collisionBox.getX() + collisionBox.getWidth(), collisionBox.getY());

                    if (nearestDistance > dist) {
                        nearestDistance = dist;
                        nearestBox = box;
                    }
                }
            }
            return nearestBox;
        }
    }

    public double sweptCollisionX(double distance, HitBox... hitBoxes) {
        var collisionBox = sweptBoxCollisionX(distance, hitBoxes);
        if (collisionBox == null) {
            return Double.MAX_VALUE;
        }

        return distance < 0
                ? collisionBox.getX() + collisionBox.getWidth()
                : collisionBox.getX();
    }

/*
    public AbstractObjectBase<?> sweptObjectCollisionX(double distance, AbstractObjectBase<?>... objects) {
        synchronized (collisionBox) {
            collisionBox.resize(Math.abs(distance), collisionBox.getHeight());
            collisionBox.setPos(distance < 0
                    ? collisionBox.getX() - Math.abs(distance)
                    : collisionBox.getX(), collisionBox.getY());

            double nearestDistance = Double.MAX_VALUE;
            AbstractObjectBase<?> nearestObj = null;
            for (var obj : objects) {

                obj.getHitBox().childAction();
            }
        }
    }
 */

    public boolean isOnGround() {
        return false;
    }

    //https://www.rhetos.de/html/lex/luftwiderstand.htm
    public void getAirResistance(float speed) {
    }
}
