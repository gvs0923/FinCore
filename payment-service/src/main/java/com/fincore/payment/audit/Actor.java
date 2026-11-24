package com.fincore.payment.audit;

public record Actor(String subject, String name, String roles) {
    public static final String SUBJECT_HEADER = "X-Fincore-Actor-Subject";
    public static final String NAME_HEADER = "X-Fincore-Actor-Name";
    public static final String ROLES_HEADER = "X-Fincore-Actor-Roles";

    public static Actor system(String job) {
        return new Actor("system:" + job, job, "");
    }
}
