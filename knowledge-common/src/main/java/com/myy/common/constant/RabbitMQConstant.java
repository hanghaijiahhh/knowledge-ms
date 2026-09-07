package com.myy.common.constant;

public class RabbitMQConstant {
    public static final String DOC_EXCHANGE = "knowledge.doc.exchange";
    public static final String DOC_CREATE_QUEUE = "knowledge.doc.create.queue";
    public static final String DOC_UPDATE_QUEUE = "knowledge.doc.update.queue";
    public static final String DOC_DELETE_QUEUE = "knowledge.doc.delete.queue";
    public static final String AUDIT_QUEUE = "knowledge.audit.queue";
    public static final String DOC_EMBED_QUEUE = "knowledge.doc.embed.queue";
    public static final String DOC_EMBED_ROUTING_KEY = "doc.embed";

    private RabbitMQConstant() {}
}
