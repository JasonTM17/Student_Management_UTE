package io.campuscore.restfulapi.auth.mail;

/** Mail transport boundary used only after the auth transaction commits. */
public interface AuthMailDelivery {

    void send(AuthMailEvent event);
}
