package com.madovygabor.maturimate.core.config

/**
 * Where the app asks for a link to a content pack: the Cloudflare Worker of `workers/content-link`
 * (docs/runbooks/cloudflare.md). A public address; the Worker checks the user's session.
 *
 */
object ContentConfig {
    const val LINK_URL = "https://maturimate-content-link.madovygabor.workers.dev"
}
