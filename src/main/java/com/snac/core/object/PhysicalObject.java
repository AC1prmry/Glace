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
    private final HitBox collisionFinderBox;

    protected PhysicalObject() {
        this(null, Direction.RIGHT.getAngle(), 0, 0);
    }

    protected PhysicalObject(@Nullable Vector2D position, float direction, int width, int height) {
        super(position, direction, width, height);

        this.velocity = new Vector2D(0, 0);
        this.collisionFinderBox = new HitBox(getPosition().getXRound(), getPosition().getYRound(), width, height);
    }

    public void onCollide(List<AbstractObjectBase<I>> collidedObjects) {}

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


    public AbstractObjectBase<I> getSweptCollisionX(PhysicalObject<I> obj, float speed) {

    }

    public AbstractObjectBase<I> getSweptCollisionY(Vector2D startPos, float speed) {

    }

    public boolean isOnGround() {
        return false;
    }

    //https://www.rhetos.de/html/lex/luftwiderstand.htm
    public void getAirResistance(float speed) {}
}
