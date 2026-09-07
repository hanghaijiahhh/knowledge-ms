package com.myy.common.constant;

public enum DocEventType {
    CREATE("doc.create"),
    UPDATE("doc.update"),
    DELETE("doc.delete");

    private final String routingKey;

    DocEventType(String routingKey) {
        this.routingKey = routingKey;
    }

    public String getRoutingKey() {
        return routingKey;
    }
}
