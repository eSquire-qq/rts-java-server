package com.artem.rtsserver.match;

public class ResourceNode {
	
	int id;
    String type;
    float x, y;
    int amount;

    public ResourceNode(int id, String type, float x, float y, int amount) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        this.amount = amount;
    }
}
