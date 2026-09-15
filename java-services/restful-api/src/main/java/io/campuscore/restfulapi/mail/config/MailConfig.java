package io.campuscore.restfulapi.mail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Mail subsystem configuration.
 * Enables async execution for non-blocking email dispatch.
 */
@Configuration
@EnableAsync
@ConfigurationProperties(prefix = "mail")
public class MailConfig {

    private String from = "conbocuoi1721@gmail.com";
    private String senderName = "CampusUTE - Đại học Công Nghệ Kĩ thuật TP.HCM";
    private boolean enabled = true;

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
