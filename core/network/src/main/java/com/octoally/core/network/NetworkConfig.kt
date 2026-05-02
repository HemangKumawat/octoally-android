package com.octoally.core.network

/**
 * Runtime configuration for OctoAlly HTTP endpoints.
 *
 * The app talks to two services on the same host:
 *   • main OctoAlly server on [mainPort] (default 42010) — sessions, skills, system status
 *   • clipboard-bridge sidecar on [uploadPort] (default 7799) — image uploads
 *
 * Host is user-configurable (Tailscale IP, LAN IP, or localhost). Ports are server-defined.
 */
interface NetworkConfig {
    /** Host without scheme or port, e.g. "100.64.0.1" or "localhost". */
    val mainHost: String

    /** Port for the main OctoAlly server. */
    val mainPort: Int get() = 42010

    /** Port for the clipboard-bridge upload sidecar. */
    val uploadPort: Int get() = 7799

    /** Base URL for the main server — always ends with `/`. */
    val mainBaseUrl: String get() = "http://$mainHost:$mainPort/"

    /** Base URL for the upload sidecar — always ends with `/`. */
    val uploadBaseUrl: String get() = "http://$mainHost:$uploadPort/"
}

/**
 * Default implementation backed by a mutable host reference.
 * Replace the single call-site that constructs this when wiring user settings.
 */
class DefaultNetworkConfig(
    override val mainHost: String,
    override val mainPort: Int = 42010,
    override val uploadPort: Int = 7799,
) : NetworkConfig
