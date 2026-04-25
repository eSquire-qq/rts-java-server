package com.artem.rtsserver.match;

import java.util.LinkedList;
import java.util.Queue;

public class BuildingState {
	private final int id;
	private final int ownerPlayerId;
	private final String buildingType;

	private final float x;
	private final float y;

	private int hp;
	private final int maxHp;

	private boolean training;
	private String trainingUnitType;
	private float trainingRemaining;

	private Queue<String> trainingQueue = new LinkedList<>();
	private float trainingTimer = 0f;
	private String currentUnitType = null;

	public BuildingState(int id, int ownerPlayerId, String buildingType, float x, float y, int maxHp) {
		this.id = id;
		this.ownerPlayerId = ownerPlayerId;
		this.buildingType = buildingType;
		this.x = x;
		this.y = y;
		this.maxHp = maxHp;
		this.hp = maxHp;
	}

	public void damage(int dmg) {
		hp -= dmg;
	}

	public boolean isDead() {
		return hp <= 0;
	}

	
	  public boolean isTraining() { return training; }
	  
	  public String getTrainingUnitType() { return trainingUnitType; }
	  
	  public float getTrainingRemaining() { return trainingRemaining; }
	  
	  public void startTraining(String unitType, float durationSeconds) {
	  this.training = true; this.trainingUnitType = unitType;
	  this.trainingRemaining = durationSeconds; }
	 

	public void updateTraining(float dt) {
		if (currentUnitType == null && !trainingQueue.isEmpty()) {
			currentUnitType = trainingQueue.poll();
			trainingTimer = 3f;
		}

		if (currentUnitType == null)
			return;

		trainingTimer -= dt;

	}

	public boolean hasUnitReady() {
		return currentUnitType != null && trainingTimer <= 0f;
	}

	public String takeTrainedUnit() {
		String result = currentUnitType;
		currentUnitType = null;
		return result;
	}

	/*
	 * public boolean isTrainingFinished() { return training && trainingRemaining <=
	 * 0f; }
	 * 
	 * public void clearTraining() { this.training = false; this.trainingUnitType =
	 * null; this.trainingRemaining = 0f; }
	 */

	public int getId() {
		return id;
	}

	public int getOwnerPlayerId() {
		return ownerPlayerId;
	}

	public String getBuildingType() {
		return buildingType;
	}

	public float getX() {
		return x;
	}

	public float getY() {
		return y;
	}

	public int getHp() {
		return hp;
	}

	public int getMaxHp() {
		return maxHp;
	}

	public void enqueueUnit(String unitType) {
		trainingQueue.add(unitType);
	}
	
	public String getCurrentUnitType() {
	    return currentUnitType;
	}

	public float getTrainingTimer() {
	    return trainingTimer;
	}

	public int getQueueSize() {
	    return trainingQueue.size();
	}

}