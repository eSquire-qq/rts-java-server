package com.artem.rtsserver.match;

public class UnitState {

	private int id;
	private int ownerPlayerId;
	private float x, y;
	private float targetX, targetY;
	private boolean hasTarget;

	public UnitState(int id, int ownerPlayerId, float x, float y, float targetX, float targetY, boolean hasTarget) {

		this.id = id;
		this.ownerPlayerId = ownerPlayerId;
		this.x = x;
		this.y = y;
		this.targetX = targetX;
		this.targetY = targetY;
		this.hasTarget = hasTarget;

	}

	public void setTarget(float x, float y) {
		this.targetX = x;
		this.targetY = y;
		this.hasTarget = true;
	}

	public int getId() {
		return id;
	}

	public int getOwnerPlayerId() {
		return ownerPlayerId;
	}

	public float getX() {
		return x;
	}

	public float getY() {
		return y;
	}

	public float getTargetX() {
		return targetX;
	}

	public float getTargetY() {
		return targetY;
	}

	public boolean getHasTarget() {
		return hasTarget;
	}

	public void setPosition(float x, float y) {
		this.x = x;
		this.y = y;
	}

	public void clearTarget() {
		hasTarget = false;
	}

}
