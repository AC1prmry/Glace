package com.snac.core.object;

import com.snac.util.HitBox;
import com.snac.util.Vector2D;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;
import java.util.List;
import java.util.Optional;

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

        onCollide(collisions);
    }


    public Optional<AbstractObjectBase<I>> sweptCollisionX(float distance) {
        collisionBox.resize((int) distance, collisionBox.getHeight());
        if (getCollisions(collisionBox.getX(), collisionBox.getY(), collisionBox.getWidth(), collisionBox.getHeight(), true).size() > 0) {

        }
    }

    public float sweptCollisionX(float distance, HitBox[] hitBoxes) {
        collisionBox.resize((int) Math.abs(distance), collisionBox.getHeight());
        collisionBox.setPosition((int) (distance < 0 ? collisionBox.getX() - Math.abs(distance) : collisionBox.getX()), collisionBox.getY());

        float nearest = distance + 1;
        for (var box : hitBoxes) {
            if (collisionBox.intersects(box)) {
                var dist = Math.max(Math.abs(box.getX()), Math.abs(collisionBox.getX())) -
                        Math.min(Math.abs(box.getX()), Math.abs(collisionBox.getX()));

                if (nearest == null || dist < Math.abs(nearest.getPosition().getXRound())) {
                    nearest = b;
                }
            }
        }


    }

    public Optional<AbstractObjectBase<I>> sweptCollisionY(float distance) {

    }

    public boolean isOnGround() {
        return false;
    }

    //https://www.rhetos.de/html/lex/luftwiderstand.htm
    public void getAirResistance(float speed) {
    }
}
