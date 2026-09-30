package io.ntole.kvizic.core.network

import web.storage.localStorage

/**
 * Browser-backed [TokenStorage], shared by the JS and Wasm targets: `localStorage` rather than an
 * in-memory map, so a page reload does not silently mint a new guest and orphan the one before.
 */
public class WebTokenStorage : TokenStorage {
    override fun read(key: String): String? = localStorage.getItem(key)

    override suspend fun write(
        key: String,
        value: String,
    ) {
        localStorage.setItem(key, value)
    }

    override suspend fun remove(key: String) {
        localStorage.removeItem(key)
    }
}
