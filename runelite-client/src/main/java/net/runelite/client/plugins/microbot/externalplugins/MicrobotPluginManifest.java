/*
 * Copyright (c) 2023 Microbot
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.microbot.externalplugins;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Represents a plugin in the Microbot Plugin Hub
 */
@Data
public class MicrobotPluginManifest {
    private static final int NEWLY_ADDED_DAYS = 30;

    /**
     * Unique identifier for the plugin
     */
    private String internalName;

    /**
     * Display name of the plugin
     */
    @SerializedName("name")
    private String displayName;

    /**
     * Plugin description
     */
    private String description;

    /**
     * Plugin authors (optional)
     */
    private String[] authors;

    /**
     * Plugin version
     */
    private String version;

    /**
     * Optional artifact identifier; defaults to {@link #internalName} when missing.
     */
    private String artifactId;

    /**
     * Optional fully qualified download URL overriding the default GitHub release pattern.
     */
    private String downloadUrl;

    /**
     * Optional GitHub release tag to use when building download URLs; falls back to "v" + version.
     */
    private String releaseTag;

    /**
     * Minimum client version required for this plugin
     */
    private String minClientVersion;

    /**
     * SHA256 hash of the plugin JAR
     */
    private String sha256;

    /**
     * URL to download the plugin JAR
     */
    private String url;

    /**
     * URL to the plugin's icon (optional)
     */
    private String iconUrl;

    /**
     * URL to the plugin's card image (optional)
     */
    private String cardUrl;

    /**
     * Flag indicating the plugin is disabled. (optional)
     * This is used for plugins that are no longer functional or have been deprecated.
     */
    private boolean disable;

    @JsonAdapter(MicrobotPluginHealth.LenientAdapterFactory.class)
    private MicrobotPluginHealth.Metadata health;

    /**
     * Tags for the plugin (optional)
     */
    private String[] tags;

    /**
     * Timestamp for when this plugin was added to the hub.
     */
    @SerializedName(value = "addedAt", alternate = {"dateAdded", "createdAt", "created_at", "publishedAt", "published_at"})
    private String addedAt;

    /**
     * Complete version list pulled from the Microbot Nexus repository.
     */
    private List<String> availableVersions = Collections.emptyList();

    /**
     * Gets a warning message for this plugin, if any
     */
    public String getWarning() {
        return null; // No warnings for now, could be added in future
    }

    /**
     * Gets a reason why this plugin is unavailable, if applicable
     */
    public String getUnavailableReason() {
        return null; // No unavailability reason for now
    }

    /**
     * Checks if this plugin has an icon
     */
    public boolean hasIcon() {
        return iconUrl != null && !iconUrl.isEmpty();
    }

    /**
     * Gets the author(s) as a single string.
     */
    public String getAuthor() {
        if (authors == null || authors.length == 0) {
            return "Unknown";
        }
        if (authors.length == 1) {
            return authors[0];
        }
        return String.join(", ", authors);
    }

    /**
     * Ensures callers always receive an immutable list of versions.
     */
    public List<String> getAvailableVersions() {
        return availableVersions == null ? Collections.emptyList() : availableVersions;
    }

    public void setAvailableVersions(List<String> versions) {
        if (versions == null || versions.isEmpty()) {
            this.availableVersions = Collections.emptyList();
            return;
        }
        this.availableVersions = Collections.unmodifiableList(new ArrayList<>(versions));
    }

    public boolean isNewlyAdded() {
        return getAddedInstant()
            .map(added -> added.isAfter(Instant.now().minus(NEWLY_ADDED_DAYS, ChronoUnit.DAYS)))
            .orElse(false);
    }

    public Optional<Instant> getAddedInstant() {
        if (addedAt == null || addedAt.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(Instant.parse(addedAt));
        } catch (DateTimeParseException ignored) {
            try {
                return Optional.of(LocalDate.parse(addedAt).atStartOfDay().toInstant(ZoneOffset.UTC));
            } catch (DateTimeParseException ignoredAgain) {
                return Optional.empty();
            }
        }
    }
}
